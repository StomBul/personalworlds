package com.personalworlds.perm;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.personalworlds.PersonalWorlds;
import com.personalworlds.config.PWConfig;
import com.personalworlds.text.Msg;
import com.personalworlds.world.Access;
import com.personalworlds.world.PersonalServerLevel;
import com.personalworlds.world.Role;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Права администратора личного мира.
 * <ul>
 * <li>Владелец и админы мира получают доступ к командам из списка adminCommands, но только стоя в своём мире:
 * требования корневых команд оборачиваются при регистрации, общий уровень прав игрока не меняется.</li>
 * <li>Любой «неоператорский» источник команд в личном мире (админ мира, командный блок, компьютер CC:Tweaked)
 * ограничен этим миром: селекторы находят только его сущности, аргументы-измерения и execute in в другие
 * измерения запрещены.</li>
 * </ul>
 */
public final class CommandGuard {
    private record Frame(CommandSourceStack source, String root) {}

    private static final ThreadLocal<Deque<Frame>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static Field requirementField;

    // ---------------------------------------------------------------- кто есть кто

    /** Источник — администратор/владелец личного мира, стоящий в этом мире. */
    public static boolean elevated(CommandSourceStack s) {
        return s.getEntity() instanceof ServerPlayer p
                && s.getLevel() instanceof PersonalServerLevel level
                && p.serverLevel() == level
                && !Access.isOp(p)
                && isAtLeastAdmin(level, p);
    }

    private static boolean isAtLeastAdmin(PersonalServerLevel level, ServerPlayer p) {
        Role r = level.world().roleOf(p.getUUID());
        return r != null && r.atLeast(Role.ADMIN) && !level.world().banned.contains(p.getUUID());
    }

    /** Оператор сервера, консоль, RCON — без ограничений. */
    public static boolean trusted(CommandSourceStack s) {
        if (s.hasPermission(3)) return true;
        return s.getEntity() instanceof ServerPlayer p && Access.isOp(p);
    }

    /** Источник в личном мире без прав оператора — ограничен этим миром. */
    public static boolean restricted(CommandSourceStack s) {
        return s.getLevel() instanceof PersonalServerLevel && !trusted(s);
    }

    public static boolean isAdminCommand(String root) {
        return PWConfig.ADMIN_COMMANDS.get().contains(root);
    }

    // ---------------------------------------------------------------- регистрация

    /** Оборачивает требования корневых команд: «как было» ИЛИ «админ личного мира и команда в списке». */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void wrapRequirements(CommandDispatcher<CommandSourceStack> dispatcher) {
        try {
            if (requirementField == null) {
                requirementField = CommandNode.class.getDeclaredField("requirement");
                requirementField.setAccessible(true);
            }
            int n = 0;
            for (CommandNode<CommandSourceStack> node : dispatcher.getRoot().getChildren()) {
                String name = node.getName();
                Predicate<CommandSourceStack> orig = node.getRequirement();
                Predicate<CommandSourceStack> wrapped = s -> orig.test(s) || (isAdminCommand(name) && elevated(s));
                requirementField.set(node, wrapped);
                n++;
            }
            PersonalWorlds.LOG.info("Права личных миров подключены к {} командам", n);
        } catch (ReflectiveOperationException | RuntimeException e) {
            PersonalWorlds.LOG.error("Не удалось подключить права администратора миров к командам — админы миров не смогут использовать команды", e);
        }
    }

    // ---------------------------------------------------------------- выполнение

    public static void push(ParseResults<CommandSourceStack> parse) {
        STACK.get().push(new Frame(parse.getContext().getSource(), rootName(parse)));
    }

    public static void pop() {
        Deque<Frame> d = STACK.get();
        if (!d.isEmpty()) d.pop();
    }

    @Nullable
    private static Frame current() {
        return STACK.get().peek();
    }

    /** Сейчас выполняется команда от доверенного источника (оператор/консоль) — например, /tp игрока оператором. */
    public static boolean trustedCommandRunning() {
        for (Frame f : STACK.get()) {
            if (trusted(f.source())) return true;
        }
        return false;
    }

    private static String rootName(ParseResults<CommandSourceStack> parse) {
        List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
        return nodes.isEmpty() ? "" : nodes.get(0).getNode().getName();
    }

    /** Проверка перед выполнением любой команды (событие NeoForge). */
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandSourceStack src = parse.getContext().getSource();
        if (!restricted(src)) return;
        String root = rootName(parse);
        if (root.isEmpty()) return;

        String deny = null;
        if (!(src.getEntity() instanceof ServerPlayer) && !PWConfig.NON_PLAYER_COMMANDS.get().contains(root)) {
            deny = "err.blocked_command";
        } else if (mentionsForeignDimension(parse, src)) {
            deny = "err.blocked_dimension";
        } else if (root.equals("gamerule")) {
            List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
            if (nodes.size() > 1 && PWConfig.BLOCKED_GAMERULES.get().contains(nodes.get(1).getNode().getName())) {
                deny = "err.blocked_gamerule";
            }
        }
        if (deny != null) {
            event.setCanceled(true);
            Msg.fail(src, deny);
        }
    }

    private static boolean mentionsForeignDimension(ParseResults<CommandSourceStack> parse, CommandSourceStack src) {
        ResourceLocation here = src.getLevel().dimension().location();
        CommandContextBuilder<CommandSourceStack> ctx = parse.getContext();
        while (ctx != null) {
            for (ParsedCommandNode<CommandSourceStack> pn : ctx.getNodes()) {
                if (pn.getNode() instanceof ArgumentCommandNode<?, ?> an && an.getType() instanceof DimensionArgument) {
                    ParsedArgument<CommandSourceStack, ?> arg = ctx.getArguments().get(an.getName());
                    if (arg != null && arg.getResult() instanceof ResourceLocation rl && !rl.equals(here)) return true;
                }
            }
            ctx = ctx.getChild();
        }
        return false;
    }

    /** Селекторы и ники для ограниченных источников находят только сущности их мира. */
    public static <T extends Entity> List<T> filter(CommandSourceStack src, List<T> found) {
        if (found.isEmpty() || !restricted(src)) return found;
        Frame f = current();
        boolean player = src.getEntity() instanceof ServerPlayer;
        // Обычные команды игроков (/msg, /tell, ...) работают как всегда, ограничиваются только права админа мира.
        if (player && (f == null || !isAdminCommand(f.root()))) return found;
        boolean kill = f != null && f.root().equals("kill");
        List<T> out = new ArrayList<>(found.size());
        for (T e : found) {
            if (e.level() != src.getLevel()) continue;
            if (kill && e instanceof ServerPlayer && e != src.getEntity()) continue;
            out.add(e);
        }
        return out;
    }

    private CommandGuard() {}
}
