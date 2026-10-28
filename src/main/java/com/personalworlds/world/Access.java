package com.personalworlds.world;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;

/** Проверки прав игроков в личных мирах. Операторы сервера (уровень 2+) везде имеют права владельца. */
public final class Access {
    public static boolean isOp(ServerPlayer p) {
        return p.hasPermissions(2);
    }

    /** Роль игрока в мире с учётом прав оператора; null — доступа нет. */
    @Nullable
    public static Role role(PersonalWorld w, ServerPlayer p) {
        Role r = w.roleOf(p.getUUID());
        if (isOp(p)) return Role.OWNER;
        if (r != null && r != Role.OWNER && w.banned.contains(p.getUUID())) return null;
        return r;
    }

    /** Роль игрока в мире, где он сейчас стоит (null — он не в личном мире или без доступа). */
    @Nullable
    public static Role roleHere(ServerPlayer p) {
        return p.serverLevel() instanceof PersonalServerLevel l ? role(l.world(), p) : null;
    }

    /** Ключ сообщения с причиной отказа во входе или null, если войти можно. */
    @Nullable
    public static String denyReason(PersonalWorld w, ServerPlayer p) {
        if (isOp(p) || p.getUUID().equals(w.owner)) return null;
        if (w.banned.contains(p.getUUID())) return "err.banned";
        Role r = w.roleOf(p.getUUID());
        if (r == null) return "err.no_access";
        if (!w.offlineAccess && p.server.getPlayerList().getPlayer(w.owner) == null) return "err.owner_offline";
        return null;
    }

    /** Можно ли менять права игрока target (у которого роль targetRole). */
    public static boolean canManage(Role actor, @Nullable Role targetRole) {
        if (actor == Role.OWNER) return targetRole != Role.OWNER;
        if (actor == Role.ADMIN) return targetRole == null || targetRole.rank < Role.ADMIN.rank;
        return false;
    }

    private Access() {}
}
