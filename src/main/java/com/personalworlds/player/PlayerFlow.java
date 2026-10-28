package com.personalworlds.player;

import org.jetbrains.annotations.Nullable;

import com.personalworlds.PersonalWorlds;
import com.personalworlds.compat.Compat;
import com.personalworlds.text.Msg;
import com.personalworlds.world.Access;
import com.personalworlds.world.PersonalServerLevel;
import com.personalworlds.world.PersonalWorld;
import com.personalworlds.world.Role;
import com.personalworlds.world.WorldManager;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Переходы между основным миром и личными мирами. Обмен инвентаря происходит в момент смены измерения
 * (событие PlayerChangedDimensionEvent), поэтому срабатывает при любом способе перемещения, включая /tp оператора.
 */
public final class PlayerFlow {
    /** Перемещение запущено самим модом — его не надо блокировать. */
    private static final ThreadLocal<Boolean> OURS = ThreadLocal.withInitial(() -> false);

    public static boolean isOurs() {
        return OURS.get();
    }

    public static boolean teleport(ServerPlayer p, ServerLevel level, Vec3 pos, float yRot, float xRot) {
        level.getChunk(Mth.floor(pos.x) >> 4, Mth.floor(pos.z) >> 4); // сгенерировать/загрузить чанк заранее
        boolean prev = OURS.get();
        OURS.set(true);
        try {
            if (p.isPassenger()) p.stopRiding();
            p.ejectPassengers();
            p.teleportTo(level, pos.x, pos.y, pos.z, yRot, xRot);
        } finally {
            OURS.set(prev);
        }
        return p.level() == level;
    }

    /** Проверки перед командами home/visit. Возвращает ключ ошибки или null. */
    @Nullable
    public static String preTravelCheck(ServerPlayer p) {
        if (Compat.isCarrying(p)) return "err.carrying";
        return null;
    }

    /** Перенос в личный мир (права уже проверены). */
    public static boolean enter(ServerPlayer p, PersonalWorld w) {
        PersonalServerLevel level = WorldManager.get().getOrLoad(w);
        if (level == null) {
            Msg.send(p, "err.load_failed");
            return false;
        }
        if (p.level() == level) {
            Msg.send(p, "err.already_inside");
            return false;
        }
        PlayerState s = PlayerStates.get(p.getUUID());
        PlayerState.Location last = s.lastPositions.get(level.dimension().location().toString());
        Vec3 pos;
        float yRot = 0, xRot = 0;
        if (last != null && level.getWorldBorder().isWithinBounds(BlockPos.containing(last.pos()))) {
            pos = last.pos();
            yRot = last.yRot();
            xRot = last.xRot();
        } else {
            BlockPos spawn = level.getSharedSpawnPos();
            pos = Vec3.atBottomCenterOf(spawn);
            yRot = level.getSharedSpawnAngle();
        }
        return teleport(p, level, pos, yRot, xRot);
    }

    /** Возврат в основной мир, туда, откуда игрок ушёл. */
    public static void leave(ServerPlayer p, boolean force) {
        MinecraftServer server = p.server;
        PlayerState s = PlayerStates.get(p.getUUID());
        PlayerState.Location ret = s.returnTo;
        ServerLevel target = ret == null ? null : server.getLevel(ret.dimension());
        Vec3 pos;
        float yRot = 0, xRot = 0;
        if (target == null || WorldManager.isPersonal(target)) {
            target = server.overworld();
            pos = Vec3.atBottomCenterOf(target.getSharedSpawnPos());
        } else {
            pos = ret.pos();
            yRot = ret.yRot();
            xRot = ret.xRot();
        }
        teleport(p, target, pos, yRot, xRot);
    }

    /** Вызывается до смены измерения: запоминаем, откуда ушёл игрок. */
    public static void beforeTravel(ServerPlayer p, ResourceKey<Level> dest) {
        PlayerState s = PlayerStates.get(p.getUUID());
        PlayerState.Location here = new PlayerState.Location(p.level().dimension(), p.position(), p.getYRot(), p.getXRot());
        if (WorldManager.isPersonal(p.level())) {
            s.lastPositions.put(p.level().dimension().location().toString(), here);
        } else if (WorldManager.isPersonalKey(dest)) {
            s.returnTo = here;
        }
    }

    /** Вызывается после смены измерения: обмен инвентаря, режим игры по роли, обновление списка команд. */
    public static void afterChange(ServerPlayer p, ResourceKey<Level> from) {
        PlayerState s = PlayerStates.get(p.getUUID());
        boolean toPersonal = p.serverLevel() instanceof PersonalServerLevel;
        if (toPersonal) {
            if (!s.inside) swapIn(p, s);
            PersonalWorld w = ((PersonalServerLevel) p.serverLevel()).world();
            applyMode(p, w);
            Role r = Access.role(w, p);
            if (p.getUUID().equals(w.owner)) {
                Msg.send(p, "tp.entered_own");
            } else {
                Msg.send(p, "tp.entered", w.displayName, r == null ? Role.GUEST.display() : r.display());
            }
        } else if (s.inside) {
            swapOut(p, s);
            Msg.send(p, "tp.left");
        }
        if (toPersonal || WorldManager.isPersonalKey(from)) {
            p.server.getCommands().sendCommands(p);
            p.refreshTabListName();
        }
    }

    public static void swapIn(ServerPlayer p, PlayerState s) {
        s.mainSnapshot = Snapshot.capture(p);
        s.inside = true;
        Snapshot.apply(p, s.buildSnapshot, GameType.CREATIVE);
        PlayerStates.save(p.getUUID());
        PersonalWorlds.LOG.debug("{}: инвентарь основного мира сохранён, выдан инвентарь личных миров", p.getGameProfile().getName());
    }

    public static void swapOut(ServerPlayer p, PlayerState s) {
        s.buildSnapshot = Snapshot.capture(p);
        Snapshot.apply(p, s.mainSnapshot, p.server.getDefaultGameType());
        s.mainSnapshot = null;
        s.inside = false;
        PlayerStates.save(p.getUUID());
        PersonalWorlds.LOG.debug("{}: инвентарь основного мира восстановлен", p.getGameProfile().getName());
    }

    /** Режим игры по роли: участники — режим «для участников», гости — «для гостей». */
    public static void applyMode(ServerPlayer p, PersonalWorld w) {
        Role r = Access.role(w, p);
        GameType mode = (r == null || r == Role.GUEST) ? w.guestMode : w.builderMode;
        if (p.gameMode.getGameModeForPlayer() != mode) p.setGameMode(mode);
    }

    private PlayerFlow() {}
}
