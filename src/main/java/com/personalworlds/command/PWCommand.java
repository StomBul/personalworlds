package com.personalworlds.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.personalworlds.config.PWConfig;
import com.personalworlds.gen.FlatPresets;
import com.personalworlds.player.PlayerFlow;
import com.personalworlds.player.PlayerState;
import com.personalworlds.player.PlayerStates;
import com.personalworlds.player.Teleports;
import com.personalworlds.text.Msg;
import com.personalworlds.world.Access;
import com.personalworlds.world.PersonalServerLevel;
import com.personalworlds.world.PersonalWorld;
import com.personalworlds.world.Role;
import com.personalworlds.world.WorldManager;
import com.personalworlds.world.WorldType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;

/** Команда /pw: создание, переход, доступ, роли, настройки и администрирование личных миров. */
public final class PWCommand {
    private interface TypeAction {
        int run(CommandContext<CommandSourceStack> ctx, WorldType type, @Nullable String preset, @Nullable String layers) throws CommandSyntaxException;
    }

    private record Pending(Runnable action, long expires) {}

    private static final Map<UUID, Pending> CONFIRM = new HashMap<>();

    private static final List<String> SETTING_KEYS = List.of(
            "name", "builder_mode", "guest_mode", "offline_access", "pvp", "daylight", "weather_cycle",
            "mobs", "fire", "fall_damage", "keep_inventory", "mob_griefing", "time", "weather");

    // ---------------------------------------------------------------- подсказки

    private static WorldManager m() {
        return WorldManager.get();
    }

    private static final SuggestionProvider<CommandSourceStack> PRESETS =
            (c, b) -> SharedSuggestionProvider.suggest(FlatPresets.all().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> ROLES =
            (c, b) -> SharedSuggestionProvider.suggest(List.of("admin", "builder", "guest"), b);
    private static final SuggestionProvider<CommandSourceStack> PLAYERS = (c, b) -> {
        Set<String> names = new LinkedHashSet<>(List.of(c.getSource().getServer().getPlayerNames()));
        names.addAll(m().knownNames());
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<CommandSourceStack> OWNERS =
            (c, b) -> SharedSuggestionProvider.suggest(m().all().stream().map(w -> w.ownerName).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> VISITABLE = (c, b) -> {
        ServerPlayer p = c.getSource().getPlayer();
        List<String> out = new ArrayList<>();
        for (PersonalWorld w : m().all()) {
            if (p == null || Access.isOp(p) || (w.roleOf(p.getUUID()) != null && !w.banned.contains(p.getUUID()))) out.add(w.ownerName);
        }
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (c, b) -> {
        ServerPlayer p = c.getSource().getPlayer();
        PersonalWorld w = p == null ? null : contextWorld(p);
        if (w == null) return b.buildFuture();
        return SharedSuggestionProvider.suggest(w.members.keySet().stream().map(m()::nameOf).toList(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> BANNED = (c, b) -> {
        ServerPlayer p = c.getSource().getPlayer();
        PersonalWorld w = p == null ? null : contextWorld(p);
        if (w == null) return b.buildFuture();
        return SharedSuggestionProvider.suggest(w.banned.stream().map(m()::nameOf).toList(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> INSIDE = (c, b) -> {
        ServerPlayer p = c.getSource().getPlayer();
        PersonalWorld w = p == null ? null : contextWorld(p);
        PersonalServerLevel level = w == null ? null : m().loaded(w);
        if (level == null) return b.buildFuture();
        return SharedSuggestionProvider.suggest(level.players().stream().map(x -> x.getGameProfile().getName()).toList(), b);
    };
    private static final SuggestionProvider<CommandSourceStack> REQUESTERS = (c, b) -> {
        ServerPlayer p = c.getSource().getPlayer();
        List<String> out = new ArrayList<>();
        if (p != null) {
            for (PersonalWorld w : managedWorlds(p)) Requests.of(w.owner).keySet().forEach(id -> out.add(m().nameOf(id)));
        }
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> SETTINGS =
            (c, b) -> SharedSuggestionProvider.suggest(SETTING_KEYS, b);
    private static final SuggestionProvider<CommandSourceStack> SETTING_VALUES = (c, b) -> {
        String key = StringArgumentType.getString(c, "key");
        List<String> v = switch (key) {
            case "builder_mode", "guest_mode" -> List.of("creative", "survival", "adventure", "spectator");
            case "time" -> List.of("day", "noon", "night", "midnight");
            case "weather" -> List.of("clear", "rain", "thunder");
            case "name" -> List.of();
            default -> List.of("on", "off");
        };
        return SharedSuggestionProvider.suggest(v, b);
    };

    // ---------------------------------------------------------------- регистрация

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("pw").executes(PWCommand::help);
        root.then(literal("help").executes(PWCommand::help));
        root.then(typeBranch(literal("create").executes(PWCommand::createMenu), PWCommand::create));
        root.then(literal("home").executes(PWCommand::home));
        root.then(literal("leave").executes(PWCommand::leave));
        root.then(literal("visit").then(argument("owner", StringArgumentType.word()).suggests(VISITABLE).executes(PWCommand::visit)));
        root.then(literal("request").then(argument("owner", StringArgumentType.word()).suggests(OWNERS).executes(PWCommand::request)));
        root.then(literal("accept").then(argument("player", StringArgumentType.word()).suggests(REQUESTERS)
                .executes(c -> accept(c, Role.GUEST))
                .then(argument("role", StringArgumentType.word()).suggests(ROLES).executes(c -> accept(c, roleArg(c))))));
        root.then(literal("deny").then(argument("player", StringArgumentType.word()).suggests(REQUESTERS).executes(PWCommand::deny)));
        root.then(literal("access").executes(PWCommand::accessList)
                .then(literal("list").executes(PWCommand::accessList))
                .then(literal("add").then(argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .executes(c -> accessAdd(c, Role.BUILDER, false))
                        .then(argument("role", StringArgumentType.word()).suggests(ROLES).executes(c -> accessAdd(c, roleArg(c), false)))))
                .then(literal("remove").then(argument("player", StringArgumentType.word()).suggests(MEMBERS).executes(PWCommand::accessRemove))));
        root.then(literal("role").then(argument("player", StringArgumentType.word()).suggests(MEMBERS)
                .then(argument("role", StringArgumentType.word()).suggests(ROLES).executes(c -> accessAdd(c, roleArg(c), true)))));
        root.then(literal("kick").then(argument("player", StringArgumentType.word()).suggests(INSIDE).executes(PWCommand::kick)));
        root.then(literal("ban").then(argument("player", StringArgumentType.word()).suggests(PLAYERS).executes(PWCommand::ban)));
        root.then(literal("unban").then(argument("player", StringArgumentType.word()).suggests(BANNED).executes(PWCommand::unban)));
        root.then(literal("setspawn").executes(PWCommand::setSpawn));
        root.then(literal("border").executes(PWCommand::borderInfo)
                .then(argument("size", IntegerArgumentType.integer(16)).executes(PWCommand::border)));
        root.then(literal("setting").executes(PWCommand::settingsMenu)
                .then(argument("key", StringArgumentType.word()).suggests(SETTINGS)
                        .then(argument("value", StringArgumentType.greedyString()).suggests(SETTING_VALUES).executes(PWCommand::setting))));
        root.then(literal("info").executes(c -> info(c, null))
                .then(argument("owner", StringArgumentType.word()).suggests(OWNERS).executes(c -> info(c, StringArgumentType.getString(c, "owner")))));
        root.then(literal("list").executes(PWCommand::list));
        root.then(typeBranch(literal("reset").executes(PWCommand::resetMenu), PWCommand::reset));
        root.then(literal("delete").executes(PWCommand::delete));
        root.then(literal("transfer").then(argument("player", StringArgumentType.word()).suggests(PLAYERS).executes(PWCommand::transfer)));
        root.then(literal("confirm").executes(PWCommand::confirm));
        root.then(literal("admin").requires(s -> s.hasPermission(2))
                .then(literal("limit").then(argument("player", StringArgumentType.word()).suggests(PLAYERS)
                        .then(argument("size", IntegerArgumentType.integer(16)).executes(PWCommand::adminLimit))))
                .then(literal("delete").then(argument("owner", StringArgumentType.word()).suggests(OWNERS).executes(PWCommand::adminDelete)))
                .then(literal("unload").then(argument("owner", StringArgumentType.word()).suggests(OWNERS).executes(PWCommand::adminUnload)))
                .then(literal("list").executes(PWCommand::adminList))
                .then(literal("reload").executes(PWCommand::adminReload)));
        d.register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> typeBranch(LiteralArgumentBuilder<CommandSourceStack> base, TypeAction action) {
        return base
                .then(literal("flat").executes(c -> action.run(c, WorldType.FLAT, null, null))
                        .then(literal("custom").then(argument("layers", StringArgumentType.greedyString())
                                .executes(c -> action.run(c, WorldType.FLAT, null, StringArgumentType.getString(c, "layers")))))
                        .then(argument("preset", StringArgumentType.word()).suggests(PRESETS)
                                .executes(c -> action.run(c, WorldType.FLAT, StringArgumentType.getString(c, "preset"), null))))
                .then(literal("void").executes(c -> action.run(c, WorldType.VOID, null, null)))
                .then(literal("normal").executes(c -> action.run(c, WorldType.NORMAL, null, null)))
                .then(literal("seed").executes(c -> action.run(c, WorldType.SEED, null, null)));
    }

    // ---------------------------------------------------------------- общие проверки

    private static Role roleArg(CommandContext<CommandSourceStack> c) {
        String s = StringArgumentType.getString(c, "role");
        Role r = Role.parseAssignable(s);
        if (r == null) {
            Msg.fail(c.getSource(), "err.bad_role", s);
            return null;
        }
        return r;
    }

    /** Мир, которым управляет игрок: тот, где он стоит и имеет права админа, иначе его собственный. */
    @Nullable
    static PersonalWorld contextWorld(ServerPlayer p) {
        if (p.serverLevel() instanceof PersonalServerLevel l) {
            Role r = Access.role(l.world(), p);
            if (r != null && r.atLeast(Role.ADMIN)) return l.world();
        }
        return m().ofOwner(p.getUUID());
    }

    /** Миры, где игрок — админ или владелец (для обработки запросов). */
    private static List<PersonalWorld> managedWorlds(ServerPlayer p) {
        List<PersonalWorld> out = new ArrayList<>();
        PersonalWorld here = contextWorld(p);
        if (here != null) out.add(here);
        for (PersonalWorld w : m().all()) {
            Role r = w.roleOf(p.getUUID());
            if (w != here && r != null && r.atLeast(Role.ADMIN)) out.add(w);
        }
        return out;
    }

    /** Мир для управления с проверкой минимальной роли; null — уже отправлена ошибка. */
    @Nullable
    private static PersonalWorld managed(CommandSourceStack src, ServerPlayer p, Role min) {
        PersonalWorld w = contextWorld(p);
        if (w == null) {
            Msg.fail(src, "err.no_world");
            return null;
        }
        Role r = Access.role(w, p);
        if (r == null || !r.atLeast(min)) {
            Msg.fail(src, min == Role.OWNER ? "err.owner_only" : "err.not_allowed");
            return null;
        }
        return w;
    }

    @Nullable
    private static UUID target(CommandSourceStack src, String name) {
        UUID id = m().resolve(name);
        if (id == null) Msg.fail(src, "err.no_player", name);
        return id;
    }

    private static void askConfirm(ServerPlayer p, String key, Runnable action, Object... args) {
        CONFIRM.put(p.getUUID(), new Pending(action, System.currentTimeMillis() + 30_000));
        p.sendSystemMessage(Msg.p(key, args));
        p.sendSystemMessage(Msg.button("btn.confirm", "/pw confirm").append(" ").append(Msg.t("confirm.timeout")));
    }

    // ---------------------------------------------------------------- справка

    private static int help(CommandContext<CommandSourceStack> c) {
        CommandSourceStack s = c.getSource();
        s.sendSystemMessage(Msg.t("help.header"));
        line(s, "/pw create", "help.create");
        line(s, "/pw home", "help.home");
        line(s, "/pw leave", "help.leave");
        line(s, "/pw visit <ник>", "help.visit");
        line(s, "/pw request <ник>", "help.request");
        line(s, "/pw list", "help.list");
        line(s, "/pw info", "help.info");
        line(s, "/pw access", "help.access");
        line(s, "/pw role <ник> <роль>", "help.role");
        line(s, "/pw kick|ban|unban <ник>", "help.kick");
        line(s, "/pw setting", "help.setting");
        line(s, "/pw setspawn", "help.setspawn");
        line(s, "/pw border <размер>", "help.border");
        line(s, "/pw reset | delete | transfer", "help.reset");
        return 1;
    }

    private static void line(CommandSourceStack s, String cmd, String descKey) {
        int cut = cmd.length();
        for (char ch : new char[] {'<', '|'}) {
            int i = cmd.indexOf(ch);
            if (i >= 0) cut = Math.min(cut, i);
        }
        String suggest = cmd.substring(0, cut);
        if (cut < cmd.length() && cmd.charAt(cut) == '|') suggest = suggest.substring(0, suggest.lastIndexOf(' ') + 1);
        s.sendSystemMessage(Msg.suggest(Msg.raw("help.line", cmd, Msg.raw(descKey)), suggest));
    }

    // ---------------------------------------------------------------- создание

    private static int createMenu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (m().ofOwner(p.getUUID()) != null) {
            Msg.fail(c.getSource(), "err.has_world");
            return 0;
        }
        typeMenu(p, "/pw create");
        return 1;
    }

    private static int resetMenu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (m().ofOwner(p.getUUID()) == null) {
            Msg.fail(c.getSource(), "err.no_world");
            return 0;
        }
        typeMenu(p, "/pw reset");
        return 1;
    }

    private static void typeMenu(ServerPlayer p, String base) {
        p.sendSystemMessage(Msg.p("create.choose"));
        MutableComponent row = Component.empty();
        for (WorldType t : WorldType.values()) {
            row.append(Msg.run("&a[" + t.display() + "]", base + " " + t.id())).append(" ");
        }
        p.sendSystemMessage(row);
        p.sendSystemMessage(Msg.t("create.flat_choose"));
        MutableComponent presets = Component.empty();
        for (FlatPresets.Preset pr : FlatPresets.all().values()) {
            presets.append(Msg.suggest("&a[" + pr.name() + "] ", base + " flat " + pr.id()));
        }
        p.sendSystemMessage(presets);
    }

    @Nullable
    private static FlatPresets.Preset checkFlat(CommandSourceStack src, @Nullable String presetId, @Nullable String layers) {
        if (layers != null) {
            try {
                FlatPresets.parse(layers, null);
            } catch (FlatPresets.LayersException e) {
                Msg.fail(src, "err.bad_layers", e.getMessage());
                return null;
            }
            return FlatPresets.defaultPreset();
        }
        if (presetId == null) return FlatPresets.defaultPreset();
        FlatPresets.Preset p = FlatPresets.get(presetId);
        if (p == null) Msg.fail(src, "err.bad_preset", presetId, String.join(", ", FlatPresets.all().keySet()));
        return p;
    }

    private static int create(CommandContext<CommandSourceStack> c, WorldType type, @Nullable String presetId, @Nullable String layers)
            throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (m().ofOwner(p.getUUID()) != null) {
            Msg.fail(c.getSource(), "err.has_world");
            return 0;
        }
        FlatPresets.Preset preset = null;
        if (type == WorldType.FLAT) {
            preset = checkFlat(c.getSource(), presetId, layers);
            if (preset == null) return 0;
        }
        Msg.ok(c.getSource(), "create.creating");
        m().create(p, type, layers == null ? preset : null, layers);
        Msg.ok(c.getSource(), "create.done", type.display());
        goHome(p);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c, WorldType type, @Nullable String presetId, @Nullable String layers)
            throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = m().ofOwner(p.getUUID());
        if (w == null) {
            Msg.fail(c.getSource(), "err.no_world");
            return 0;
        }
        long cooldown = PWConfig.RESET_COOLDOWN_MINUTES.get() * 60_000L;
        long left = w.lastResetAt + cooldown - System.currentTimeMillis();
        if (left > 0 && !Access.isOp(p)) {
            Msg.fail(c.getSource(), "err.cooldown", PWConfig.RESET_COOLDOWN_MINUTES.get(), (left + 59_999) / 60_000);
            return 0;
        }
        FlatPresets.Preset preset = null;
        if (type == WorldType.FLAT) {
            preset = checkFlat(c.getSource(), presetId, layers);
            if (preset == null) return 0;
        }
        FlatPresets.Preset fp = layers == null ? preset : null;
        askConfirm(p, "confirm.reset", () -> {
            PersonalWorld cur = m().ofOwner(p.getUUID());
            if (cur != w) return;
            m().reset(w, type, fp, layers);
            Msg.send(p, "reset.done");
            goHome(p);
        });
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = m().ofOwner(p.getUUID());
        if (w == null) {
            Msg.fail(c.getSource(), "err.no_world");
            return 0;
        }
        askConfirm(p, "confirm.delete", () -> {
            if (m().ofOwner(p.getUUID()) != w) return;
            m().delete(w);
            Msg.send(p, "delete.done");
        });
        return 1;
    }

    private static int transfer(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = m().ofOwner(p.getUUID());
        if (w == null) {
            Msg.fail(c.getSource(), "err.no_world");
            return 0;
        }
        String name = StringArgumentType.getString(c, "player");
        UUID to = target(c.getSource(), name);
        if (to == null) return 0;
        if (to.equals(p.getUUID())) {
            Msg.fail(c.getSource(), "err.self");
            return 0;
        }
        if (m().ofOwner(to) != null) {
            Msg.fail(c.getSource(), "err.target_has_world", name);
            return 0;
        }
        String realName = m().nameOf(to);
        askConfirm(p, "confirm.transfer", () -> {
            if (m().ofOwner(p.getUUID()) != w || m().ofOwner(to) != null) return;
            m().transfer(w, to, realName);
            Msg.send(p, "transfer.done", realName);
            ServerPlayer t = p.server.getPlayerList().getPlayer(to);
            if (t != null) Msg.send(t, "transfer.received", p.getGameProfile().getName());
        }, realName);
        return 1;
    }

    private static int confirm(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Pending pending = CONFIRM.remove(p.getUUID());
        if (pending == null || pending.expires() < System.currentTimeMillis()) {
            Msg.fail(c.getSource(), "err.no_confirm");
            return 0;
        }
        pending.action().run();
        return 1;
    }

    // ---------------------------------------------------------------- перемещение

    private static void goHome(ServerPlayer p) {
        String err = PlayerFlow.preTravelCheck(p);
        if (err != null) {
            Msg.send(p, err);
            return;
        }
        Teleports.schedule(p, () -> {
            PersonalWorld w = m().ofOwner(p.getUUID());
            if (w != null && !p.hasDisconnected()) PlayerFlow.enter(p, w);
        });
    }

    private static int home(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (m().ofOwner(p.getUUID()) == null) {
            Msg.fail(c.getSource(), "err.no_world");
            return 0;
        }
        goHome(p);
        return 1;
    }

    private static int visit(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "owner");
        UUID owner = target(c.getSource(), name);
        if (owner == null) return 0;
        PersonalWorld w = m().ofOwner(owner);
        if (w == null) {
            Msg.fail(c.getSource(), "err.no_world_of", name);
            return 0;
        }
        String reason = Access.denyReason(w, p);
        if (reason != null) {
            Msg.fail(c.getSource(), reason, w.ownerName);
            return 0;
        }
        String err = PlayerFlow.preTravelCheck(p);
        if (err != null) {
            Msg.fail(c.getSource(), err);
            return 0;
        }
        Teleports.schedule(p, () -> {
            if (p.hasDisconnected() || m().ofOwner(owner) != w) return;
            String r = Access.denyReason(w, p);
            if (r != null) {
                Msg.send(p, r, w.ownerName);
                return;
            }
            PlayerFlow.enter(p, w);
        });
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PlayerState s = PlayerStates.get(p.getUUID());
        if (!s.inside && !WorldManager.isPersonal(p.level())) {
            Msg.fail(c.getSource(), "err.not_inside");
            return 0;
        }
        PlayerFlow.leave(p, false);
        return 1;
    }

    // ---------------------------------------------------------------- запросы и доступ

    private static int request(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "owner");
        UUID owner = target(c.getSource(), name);
        if (owner == null) return 0;
        PersonalWorld w = m().ofOwner(owner);
        if (w == null) {
            Msg.fail(c.getSource(), "err.no_world_of", name);
            return 0;
        }
        if (owner.equals(p.getUUID())) {
            Msg.fail(c.getSource(), "err.self");
            return 0;
        }
        if (w.banned.contains(p.getUUID())) {
            Msg.fail(c.getSource(), "err.banned", w.ownerName);
            return 0;
        }
        if (w.roleOf(p.getUUID()) != null) {
            Msg.fail(c.getSource(), "err.already_member", w.ownerName);
            return 0;
        }
        Requests.add(owner, p.getUUID());
        String me = p.getGameProfile().getName();
        boolean delivered = false;
        for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
            Role r = w.roleOf(other.getUUID());
            if (r != null && r.atLeast(Role.ADMIN)) {
                notifyRequest(other, me);
                delivered = true;
            }
        }
        Msg.ok(c.getSource(), "request.sent", w.ownerName);
        if (!delivered) Msg.ok(c.getSource(), "request.offline", PWConfig.REQUEST_TIMEOUT_SECONDS.get());
        return 1;
    }

    public static void notifyRequest(ServerPlayer to, String requester) {
        to.sendSystemMessage(Msg.p("request.received", requester));
        to.sendSystemMessage(Msg.button("btn.accept_guest", "/pw accept " + requester + " guest").append(" ")
                .append(Msg.button("btn.accept_builder", "/pw accept " + requester + " builder")).append(" ")
                .append(Msg.button("btn.deny", "/pw deny " + requester)));
    }

    private static int accept(CommandContext<CommandSourceStack> c, @Nullable Role role) throws CommandSyntaxException {
        if (role == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        for (PersonalWorld w : managedWorlds(p)) {
            if (!Requests.of(w.owner).containsKey(who)) continue;
            Role actor = Access.role(w, p);
            if (role == Role.ADMIN && actor != Role.OWNER) {
                Msg.fail(c.getSource(), "err.not_allowed");
                return 0;
            }
            Requests.take(w.owner, who);
            w.members.put(who, role);
            w.banned.remove(who);
            m().markDirty();
            Msg.ok(c.getSource(), "access.added", m().nameOf(who), role.display());
            ServerPlayer t = p.server.getPlayerList().getPlayer(who);
            if (t != null) {
                t.sendSystemMessage(Msg.p("request.accepted", w.ownerName).append(" ")
                        .append(Msg.button("btn.enter", "/pw visit " + w.ownerName)));
            }
            return 1;
        }
        Msg.fail(c.getSource(), "err.no_request", name);
        return 0;
    }

    private static int deny(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        for (PersonalWorld w : managedWorlds(p)) {
            if (Requests.take(w.owner, who)) {
                Msg.ok(c.getSource(), "request.denied_owner", m().nameOf(who));
                ServerPlayer t = p.server.getPlayerList().getPlayer(who);
                if (t != null) Msg.send(t, "request.denied", w.ownerName);
                return 1;
            }
        }
        Msg.fail(c.getSource(), "err.no_request", name);
        return 0;
    }

    private static int accessList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        p.sendSystemMessage(Msg.t("access.header", w.displayName));
        p.sendSystemMessage(Msg.t("access.entry", w.ownerName, Role.OWNER.display()));
        if (w.members.isEmpty()) p.sendSystemMessage(Msg.t("access.empty"));
        w.members.forEach((id, role) -> {
            String n = m().nameOf(id);
            p.sendSystemMessage(Msg.t("access.entry", n, role.display()).append(" ")
                    .append(Msg.suggest("&e[роль]", "/pw role " + n + " ")).append(" ")
                    .append(Msg.button("btn.remove", "/pw access remove " + n)));
        });
        if (!w.banned.isEmpty()) {
            p.sendSystemMessage(Msg.t("access.banned", String.join(", ", w.banned.stream().map(m()::nameOf).toList())));
        }
        p.sendSystemMessage(Msg.suggest("&a[+ добавить игрока]", "/pw access add "));
        return 1;
    }

    private static int accessAdd(CommandContext<CommandSourceStack> c, @Nullable Role role, boolean mustBeMember) throws CommandSyntaxException {
        if (role == null) return 0;
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        if (who.equals(w.owner)) {
            Msg.fail(c.getSource(), "err.is_owner");
            return 0;
        }
        Role actor = Access.role(w, p);
        Role current = w.members.get(who);
        if (mustBeMember && current == null) {
            Msg.fail(c.getSource(), "access.not_member", name);
            return 0;
        }
        if (!Access.canManage(actor, current) || (role == Role.ADMIN && actor != Role.OWNER)) {
            Msg.fail(c.getSource(), "err.target_higher");
            return 0;
        }
        w.members.put(who, role);
        w.banned.remove(who);
        m().markDirty();
        String n = m().nameOf(who);
        Msg.ok(c.getSource(), mustBeMember ? "access.role_set" : "access.added", n, role.display());
        ServerPlayer t = p.server.getPlayerList().getPlayer(who);
        if (t != null) {
            if (t.serverLevel() instanceof PersonalServerLevel l && l.world() == w) {
                PlayerFlow.applyMode(t, w);
                t.server.getCommands().sendCommands(t);
            }
            if (!mustBeMember) {
                t.sendSystemMessage(Msg.p("access.added_target", w.ownerName, role.display()).append(" ")
                        .append(Msg.button("btn.enter", "/pw visit " + w.ownerName)));
            } else {
                Msg.send(t, "access.role_set", t.getGameProfile().getName(), role.display());
            }
        }
        return 1;
    }

    private static int accessRemove(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        Role current = w.members.get(who);
        if (current == null) {
            Msg.fail(c.getSource(), "access.not_member", name);
            return 0;
        }
        if (!Access.canManage(Access.role(w, p), current)) {
            Msg.fail(c.getSource(), "err.target_higher");
            return 0;
        }
        w.members.remove(who);
        m().markDirty();
        kickIfInside(p, w, who);
        Msg.ok(c.getSource(), "access.removed", m().nameOf(who));
        return 1;
    }

    private static boolean kickIfInside(ServerPlayer actor, PersonalWorld w, UUID who) {
        ServerPlayer t = actor.server.getPlayerList().getPlayer(who);
        if (t != null && t.serverLevel() instanceof PersonalServerLevel l && l.world() == w && !Access.isOp(t)) {
            PlayerFlow.leave(t, true);
            Msg.send(t, "tp.kicked", w.ownerName);
            return true;
        }
        return false;
    }

    private static int kick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        if (who.equals(p.getUUID())) {
            Msg.fail(c.getSource(), "err.self");
            return 0;
        }
        if (!Access.canManage(Access.role(w, p), w.roleOf(who))) {
            Msg.fail(c.getSource(), "err.target_higher");
            return 0;
        }
        if (!kickIfInside(p, w, who)) {
            Msg.fail(c.getSource(), "kick.not_inside", name);
            return 0;
        }
        Msg.ok(c.getSource(), "kick.done", m().nameOf(who));
        return 1;
    }

    private static int ban(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        if (who.equals(p.getUUID()) || who.equals(w.owner)) {
            Msg.fail(c.getSource(), who.equals(w.owner) ? "err.is_owner" : "err.self");
            return 0;
        }
        if (!Access.canManage(Access.role(w, p), w.roleOf(who))) {
            Msg.fail(c.getSource(), "err.target_higher");
            return 0;
        }
        w.members.remove(who);
        w.banned.add(who);
        Requests.take(w.owner, who);
        m().markDirty();
        kickIfInside(p, w, who);
        Msg.ok(c.getSource(), "ban.done", m().nameOf(who));
        return 1;
    }

    private static int unban(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        w.banned.remove(who);
        m().markDirty();
        Msg.ok(c.getSource(), "unban.done", m().nameOf(who));
        return 1;
    }

    // ---------------------------------------------------------------- настройки мира

    private static int setSpawn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!(p.serverLevel() instanceof PersonalServerLevel level)) {
            Msg.fail(c.getSource(), "err.not_in_world");
            return 0;
        }
        Role r = Access.role(level.world(), p);
        if (r == null || !r.atLeast(Role.ADMIN)) {
            Msg.fail(c.getSource(), "err.not_allowed");
            return 0;
        }
        level.setDefaultSpawnPos(p.blockPosition(), p.getYRot());
        m().markDirty();
        Msg.ok(c.getSource(), "spawn.set", p.getBlockX(), p.getBlockY(), p.getBlockZ());
        return 1;
    }

    private static int borderInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        Msg.ok(c.getSource(), "border.current", w.borderSize, PWConfig.MIN_BORDER.get(), m().maxBorder(w.owner));
        return 1;
    }

    private static int border(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        int size = IntegerArgumentType.getInteger(c, "size");
        int min = PWConfig.MIN_BORDER.get();
        int max = m().maxBorder(w.owner);
        if ((size < min || size > max) && !Access.isOp(p)) {
            Msg.fail(c.getSource(), "err.border_range", min, max);
            return 0;
        }
        w.borderSize = size;
        PersonalServerLevel level = m().loaded(w);
        if (level != null) level.getWorldBorder().setSize(size);
        m().markDirty();
        Msg.ok(c.getSource(), "border.set", size);
        return 1;
    }

    private static int settingsMenu(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        PersonalServerLevel level = m().getOrLoad(w);
        if (level == null) {
            Msg.fail(c.getSource(), "err.load_failed");
            return 0;
        }
        GameRules rules = level.getGameRules();
        p.sendSystemMessage(Msg.t("setting.header", w.displayName));
        p.sendSystemMessage(Msg.t("setting.line", Msg.raw("setting.name"), w.displayName).append(Msg.suggest("&e[изменить]", "/pw setting name ")));
        modeLine(p, "builder_mode", w.builderMode);
        modeLine(p, "guest_mode", w.guestMode);
        toggle(p, "offline_access", w.offlineAccess);
        toggle(p, "pvp", w.pvp);
        toggle(p, "daylight", rules.getBoolean(GameRules.RULE_DAYLIGHT));
        toggle(p, "weather_cycle", rules.getBoolean(GameRules.RULE_WEATHER_CYCLE));
        toggle(p, "mobs", rules.getBoolean(GameRules.RULE_DOMOBSPAWNING));
        toggle(p, "fire", rules.getBoolean(GameRules.RULE_DOFIRETICK));
        toggle(p, "fall_damage", rules.getBoolean(GameRules.RULE_FALL_DAMAGE));
        toggle(p, "keep_inventory", rules.getBoolean(GameRules.RULE_KEEPINVENTORY));
        toggle(p, "mob_griefing", rules.getBoolean(GameRules.RULE_MOBGRIEFING));
        p.sendSystemMessage(Msg.t("setting.time_hint"));
        return 1;
    }

    private static void toggle(ServerPlayer p, String key, boolean value) {
        p.sendSystemMessage(Msg.t("setting.line", Msg.raw("setting." + key), Msg.raw(value ? "setting.on" : "setting.off"))
                .append(Msg.button("btn.on", "/pw setting " + key + " on")).append(" ")
                .append(Msg.button("btn.off", "/pw setting " + key + " off")));
    }

    private static void modeLine(ServerPlayer p, String key, GameType mode) {
        MutableComponent line = Msg.t("setting.line", Msg.raw("setting." + key), mode.getLongDisplayName().getString());
        for (GameType g : GameType.values()) {
            line.append(Msg.run("&e[" + g.getName() + "]", "/pw setting " + key + " " + g.getName())).append(" ");
        }
        p.sendSystemMessage(line);
    }

    @Nullable
    private static Boolean bool(String v) {
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1", "вкл", "да" -> true;
            case "off", "false", "no", "0", "выкл", "нет" -> false;
            default -> null;
        };
    }

    private static int setting(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w = managed(c.getSource(), p, Role.ADMIN);
        if (w == null) return 0;
        String key = StringArgumentType.getString(c, "key").toLowerCase(Locale.ROOT);
        String value = StringArgumentType.getString(c, "value").trim();
        Role actor = Access.role(w, p);
        if (!SETTING_KEYS.contains(key)) {
            Msg.fail(c.getSource(), "err.bad_setting", key);
            return 0;
        }
        if ((key.equals("name") || key.equals("offline_access")) && actor != Role.OWNER) {
            Msg.fail(c.getSource(), "err.owner_only");
            return 0;
        }
        PersonalServerLevel level = m().getOrLoad(w);
        if (level == null) {
            Msg.fail(c.getSource(), "err.load_failed");
            return 0;
        }
        GameRules rules = level.getGameRules();
        Boolean b = bool(value);
        String shown = value;
        switch (key) {
            case "name" -> {
                if (value.isEmpty() || value.length() > 32) return badValue(c, value);
                w.displayName = value;
            }
            case "builder_mode", "guest_mode" -> {
                GameType g = GameType.byName(value.toLowerCase(Locale.ROOT), null);
                if (g == null) return badValue(c, value);
                if (key.equals("builder_mode")) w.builderMode = g;
                else w.guestMode = g;
                for (ServerPlayer t : level.players()) PlayerFlow.applyMode(t, w);
            }
            case "offline_access" -> {
                if (b == null) return badValue(c, value);
                w.offlineAccess = b;
            }
            case "pvp" -> {
                if (b == null) return badValue(c, value);
                w.pvp = b;
            }
            case "daylight" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_DAYLIGHT).set(b, p.server);
            }
            case "weather_cycle" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(b, p.server);
            }
            case "mobs" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(b, p.server);
            }
            case "fire" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_DOFIRETICK).set(b, p.server);
            }
            case "fall_damage" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_FALL_DAMAGE).set(b, p.server);
            }
            case "keep_inventory" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_KEEPINVENTORY).set(b, p.server);
            }
            case "mob_griefing" -> {
                if (b == null) return badValue(c, value);
                rules.getRule(GameRules.RULE_MOBGRIEFING).set(b, p.server);
            }
            case "time" -> {
                long t = switch (value.toLowerCase(Locale.ROOT)) {
                    case "day" -> 1000;
                    case "noon" -> 6000;
                    case "night" -> 13000;
                    case "midnight" -> 18000;
                    default -> {
                        try {
                            yield Long.parseLong(value);
                        } catch (NumberFormatException e) {
                            yield -1;
                        }
                    }
                };
                if (t < 0) return badValue(c, value);
                level.setDayTime(t);
            }
            case "weather" -> {
                switch (value.toLowerCase(Locale.ROOT)) {
                    case "clear" -> level.setWeatherParameters(12000, 0, false, false);
                    case "rain" -> level.setWeatherParameters(0, 12000, true, false);
                    case "thunder" -> level.setWeatherParameters(0, 12000, true, true);
                    default -> {
                        return badValue(c, value);
                    }
                }
            }
            default -> {
                return badValue(c, value);
            }
        }
        if (b != null) shown = Msg.raw(b ? "setting.on" : "setting.off");
        m().markDirty();
        Msg.ok(c.getSource(), "setting.set", Msg.raw("setting." + key).startsWith("setting.") ? key : Msg.raw("setting." + key), shown);
        return 1;
    }

    private static int badValue(CommandContext<CommandSourceStack> c, String value) {
        Msg.fail(c.getSource(), "err.bad_value", value);
        return 0;
    }

    // ---------------------------------------------------------------- информация

    private static int info(CommandContext<CommandSourceStack> c, @Nullable String ownerName) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        PersonalWorld w;
        if (ownerName != null) {
            UUID owner = target(c.getSource(), ownerName);
            if (owner == null) return 0;
            w = m().ofOwner(owner);
            if (w == null) {
                Msg.fail(c.getSource(), "err.no_world_of", ownerName);
                return 0;
            }
        } else {
            w = p.serverLevel() instanceof PersonalServerLevel l ? l.world() : m().ofOwner(p.getUUID());
            if (w == null) {
                Msg.fail(c.getSource(), "err.no_world");
                return 0;
            }
        }
        PersonalServerLevel level = m().loaded(w);
        p.sendSystemMessage(Msg.t("info.header", w.displayName));
        p.sendSystemMessage(Msg.t("info.owner", w.ownerName));
        p.sendSystemMessage(Msg.t("info.type", w.type.display()));
        p.sendSystemMessage(Msg.t("info.border", w.borderSize, m().maxBorder(w.owner)));
        p.sendSystemMessage(Msg.t("info.members", w.members.size()));
        p.sendSystemMessage(Msg.t("info.loaded", level != null
                ? Msg.raw("info.loaded_yes", level.players().size()) : Msg.raw("info.loaded_no")));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        p.sendSystemMessage(Msg.t("list.header"));
        int n = 0;
        List<PersonalWorld> worlds = new ArrayList<>();
        PersonalWorld own = m().ofOwner(p.getUUID());
        if (own != null) worlds.add(own);
        for (PersonalWorld w : m().all()) {
            if (w != own && w.roleOf(p.getUUID()) != null && !w.banned.contains(p.getUUID())) worlds.add(w);
        }
        for (PersonalWorld w : worlds) {
            Role r = w.roleOf(p.getUUID());
            String cmd = w == own ? "/pw home" : "/pw visit " + w.ownerName;
            p.sendSystemMessage(Msg.t("list.entry", w.displayName, w.ownerName, r == null ? "-" : r.display())
                    .append(" ").append(Msg.button("btn.enter", cmd)));
            n++;
        }
        if (n == 0) p.sendSystemMessage(Msg.t("list.empty"));
        return n;
    }

    // ---------------------------------------------------------------- администрирование сервера

    private static int adminLimit(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "player");
        UUID who = target(c.getSource(), name);
        if (who == null) return 0;
        int size = IntegerArgumentType.getInteger(c, "size");
        m().setLimit(who, size);
        Msg.ok(c.getSource(), "admin.limit", m().nameOf(who), size);
        return 1;
    }

    @Nullable
    private static PersonalWorld worldArg(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "owner");
        UUID owner = target(c.getSource(), name);
        if (owner == null) return null;
        PersonalWorld w = m().ofOwner(owner);
        if (w == null) Msg.fail(c.getSource(), "err.no_world_of", name);
        return w;
    }

    private static int adminDelete(CommandContext<CommandSourceStack> c) {
        PersonalWorld w = worldArg(c);
        if (w == null) return 0;
        m().delete(w);
        Msg.ok(c.getSource(), "delete.done");
        return 1;
    }

    private static int adminUnload(CommandContext<CommandSourceStack> c) {
        PersonalWorld w = worldArg(c);
        if (w == null) return 0;
        PersonalServerLevel level = m().loaded(w);
        if (level != null) m().unload(level, true);
        Msg.ok(c.getSource(), "admin.unloaded", w.ownerName);
        return 1;
    }

    private static int adminList(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSystemMessage(Msg.t("admin.list_header", m().all().size()));
        for (PersonalWorld w : m().all()) {
            PersonalServerLevel level = m().loaded(w);
            c.getSource().sendSystemMessage(Msg.t("admin.list_entry", w.ownerName, w.type.display(), w.borderSize,
                    level != null ? Msg.raw("info.loaded_yes", level.players().size()) : Msg.raw("info.loaded_no")));
        }
        return m().all().size();
    }

    private static int adminReload(CommandContext<CommandSourceStack> c) {
        Msg.load();
        FlatPresets.load();
        Msg.ok(c.getSource(), "admin.reloaded");
        return 1;
    }

    private PWCommand() {}
}
