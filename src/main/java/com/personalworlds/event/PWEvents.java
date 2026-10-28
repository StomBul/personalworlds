package com.personalworlds.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.personalworlds.command.PWCommand;
import com.personalworlds.command.Requests;
import com.personalworlds.config.PWConfig;
import com.personalworlds.gen.FlatPresets;
import com.personalworlds.perm.CommandGuard;
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

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.portal.DimensionTransition;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSetSpawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Все игровые события мода. */
public final class PWEvents {
    /** Действия, отложенные до конца тика (нельзя телепортировать игрока прямо внутри события смены измерения). */
    private static final List<Runnable> DEFERRED = new ArrayList<>();
    private static final Map<UUID, Long> GUEST_HINT = new HashMap<>();

    public static void defer(Runnable r) {
        DEFERRED.add(r);
    }

    // ---------------------------------------------------------------- жизненный цикл сервера

    @SubscribeEvent
    public static void onAboutToStart(ServerAboutToStartEvent e) {
        Msg.load();
        FlatPresets.load();
        WorldManager.start(e.getServer());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        PlayerStates.saveAll();
        if (WorldManager.get() != null) WorldManager.get().saveData();
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent e) {
        WorldManager.stop();
        PlayerStates.clear();
        Teleports.clear();
        Requests.clear();
        DEFERRED.clear();
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post e) {
        if (WorldManager.get() == null) return;
        if (!DEFERRED.isEmpty()) {
            List<Runnable> run = new ArrayList<>(DEFERRED);
            DEFERRED.clear();
            run.forEach(Runnable::run);
        }
        Teleports.tick(e.getServer());
        WorldManager.get().tick();
    }

    @SubscribeEvent
    public static void onLevelSave(LevelEvent.Save e) {
        if (e.getLevel() instanceof Level l && l.dimension() == Level.OVERWORLD && WorldManager.get() != null) {
            WorldManager.get().saveData();
            PlayerStates.saveAll();
        }
    }

    // ---------------------------------------------------------------- команды

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent e) {
        PWCommand.register(e.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommandsLast(RegisterCommandsEvent e) {
        CommandGuard.wrapRequirements(e.getDispatcher());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onCommand(CommandEvent e) {
        CommandGuard.onCommand(e);
    }

    // ---------------------------------------------------------------- перемещения

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTravel(EntityTravelToDimensionEvent e) {
        if (e.isCanceled()) return;
        Entity entity = e.getEntity();
        ResourceKey<Level> dest = e.getDimension();
        boolean fromPersonal = WorldManager.isPersonal(entity.level());
        boolean toPersonal = WorldManager.isPersonalKey(dest);
        if (!fromPersonal && !toPersonal) return;

        if (!(entity instanceof ServerPlayer p)) {
            // Предметы, мобы, вагонетки, поезда не переходят между личными и основными мирами.
            e.setCanceled(true);
            return;
        }
        if (PlayerFlow.isOurs() || CommandGuard.trustedCommandRunning() || Access.isOp(p)) {
            PlayerFlow.beforeTravel(p, dest);
            return;
        }
        // Порталы, предметы-телепорты других модов и т.п.
        e.setCanceled(true);
        if (fromPersonal && !toPersonal) {
            defer(() -> {
                if (!p.hasDisconnected()) PlayerFlow.leave(p, true);
            });
        } else {
            Msg.send(p, toPersonal && !fromPersonal ? "err.no_travel_in" : "err.portal");
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && WorldManager.get() != null) {
            PlayerFlow.afterChange(p, e.getFrom());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || WorldManager.get() == null) return;
        WorldManager.get().rememberName(p.getUUID(), p.getGameProfile().getName());
        PlayerState s = PlayerStates.get(p.getUUID());
        ResourceKey<Level> loginDim = LoginTracker.take(p.getUUID());
        boolean inPersonal = p.serverLevel() instanceof PersonalServerLevel;

        if (s.inside && !inPersonal) {
            if (loginDim != null && WorldManager.isPersonalKey(loginDim)) {
                // Игрок вышел в личном мире, а мир с тех пор удалён: вернуть основной инвентарь и место.
                PlayerFlow.swapOut(p, s);
                PlayerFlow.leave(p, true);
                Msg.send(p, "state.restored");
            } else {
                // Сервер упал до сохранения игрока: его файл ещё «до входа», он и есть правильный.
                s.inside = false;
                s.mainSnapshot = null;
                PlayerStates.save(p.getUUID());
            }
        } else if (!s.inside && inPersonal) {
            s.inside = true;
            PlayerStates.save(p.getUUID());
        }

        if (p.serverLevel() instanceof PersonalServerLevel level) {
            PersonalWorld w = level.world();
            if (Access.denyReason(w, p) != null) {
                defer(() -> {
                    if (!p.hasDisconnected()) PlayerFlow.leave(p, true);
                });
            } else {
                PlayerFlow.applyMode(p, w);
            }
        }
        // Запросы, пришедшие, пока владелец был не в сети.
        PersonalWorld own = WorldManager.get().ofOwner(p.getUUID());
        if (own != null) {
            for (UUID req : Requests.of(p.getUUID()).keySet()) PWCommand.notifyRequest(p, WorldManager.get().nameOf(req));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || WorldManager.get() == null) return;
        PersonalWorld own = WorldManager.get().ofOwner(p.getUUID());
        if (own != null && !own.offlineAccess && PWConfig.KICK_ON_OWNER_LOGOUT.get()) {
            PersonalServerLevel level = WorldManager.get().loaded(own);
            if (level != null) {
                for (ServerPlayer other : List.copyOf(level.players())) {
                    if (other == p || Access.isOp(other)) continue;
                    PlayerFlow.leave(other, true);
                    Msg.send(other, "tp.owner_left");
                }
            }
        }
        PlayerStates.unload(p.getUUID());
    }

    // ---------------------------------------------------------------- смерть и спавн

    @SubscribeEvent
    public static void onRespawnPosition(PlayerRespawnPositionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.level() instanceof PersonalServerLevel level)) return;
        if (!PlayerStates.get(p.getUUID()).inside || Access.denyReason(level.world(), p) != null) return;
        // Умер в личном мире — возрождается на спавне этого же мира.
        BlockPos spawn = level.getSharedSpawnPos();
        e.setDimensionTransition(new DimensionTransition(level, Vec3.atBottomCenterOf(spawn), Vec3.ZERO,
                level.getSharedSpawnAngle(), 0f, DimensionTransition.DO_NOTHING));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || WorldManager.get() == null) return;
        PlayerState s = PlayerStates.get(p.getUUID());
        if (p.serverLevel() instanceof PersonalServerLevel level) {
            PlayerFlow.applyMode(p, level.world());
        } else if (s.inside) {
            PlayerFlow.swapOut(p, s);
            Msg.send(p, "tp.left");
            p.server.getCommands().sendCommands(p);
        }
    }

    @SubscribeEvent
    public static void onSetSpawn(PlayerSetSpawnEvent e) {
        if (e.getNewSpawn() != null && WorldManager.isPersonalKey(e.getSpawnLevel())) {
            e.setCanceled(true);
            if (e.getEntity() instanceof ServerPlayer p) Msg.actionBar(p, "err.no_spawn_set");
        }
    }

    @SubscribeEvent
    public static void onPortalSpawn(BlockEvent.PortalSpawnEvent e) {
        if (e.getLevel() instanceof PersonalServerLevel) e.setCanceled(true);
    }

    // ---------------------------------------------------------------- список игроков (Tab)

    /** Метка личного мира у ника в Tab: «Steve [✦ Alex]» или «Alex [✦ у себя]». */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onTabListName(PlayerEvent.TabListNameFormat e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(p.serverLevel() instanceof PersonalServerLevel level)) return;
        PersonalWorld w = level.world();
        // Основа — то, что уже выставили другие моды, или обычное имя с цветом команды.
        net.minecraft.network.chat.Component base = e.getDisplayName() != null ? e.getDisplayName() : p.getDisplayName();
        String suffix = p.getUUID().equals(w.owner) ? Msg.raw("tab.own") : Msg.raw("tab.other", w.displayName);
        e.setDisplayName(base.copy().append(Msg.parse(suffix)));
    }

    // ---------------------------------------------------------------- гости и PvP

    private static boolean isGuest(Player player) {
        if (!(player instanceof ServerPlayer p) || !(p.serverLevel() instanceof PersonalServerLevel level)) return false;
        Role r = Access.role(level.world(), p);
        return r == null || !r.canBuild();
    }

    private static void denyGuest(Player player, ICancellableEvent e) {
        if (!isGuest(player)) return;
        e.setCanceled(true);
        long now = System.currentTimeMillis();
        Long last = GUEST_HINT.get(player.getUUID());
        if (last == null || now - last > 3000) {
            GUEST_HINT.put(player.getUUID(), now);
            Msg.actionBar((ServerPlayer) player, "err.guest");
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        denyGuest(e.getPlayer(), e);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof Player p) denyGuest(p, e);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        denyGuest(e.getEntity(), e);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        denyGuest(e.getEntity(), e);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        denyGuest(e.getEntity(), e);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        denyGuest(e.getEntity(), e);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        denyGuest(e.getEntity(), e);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        denyGuest(e.getEntity(), e);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim) || !(victim.serverLevel() instanceof PersonalServerLevel level)) return;
        if (level.world().pvp) return;
        if (e.getSource().getEntity() instanceof Player attacker && attacker != victim) e.setCanceled(true);
    }

    static boolean isPersonalAccessor(LevelAccessor l) {
        return l instanceof PersonalServerLevel;
    }

    private PWEvents() {}
}
