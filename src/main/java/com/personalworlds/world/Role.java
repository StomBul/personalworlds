package com.personalworlds.world;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.personalworlds.text.Msg;

/** Роль игрока в личном мире. Чем больше rank, тем больше прав. */
public enum Role {
    GUEST(0), BUILDER(1), ADMIN(2), OWNER(3);

    public final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public boolean atLeast(Role other) {
        return rank >= other.rank;
    }

    public boolean canBuild() {
        return atLeast(BUILDER);
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return Msg.raw("role." + id());
    }

    /** Роли, которые можно выдать командой (владелец не выдаётся). */
    @Nullable
    public static Role parseAssignable(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "admin", "админ" -> ADMIN;
            case "builder", "строитель" -> BUILDER;
            case "guest", "гость" -> GUEST;
            default -> null;
        };
    }

    public static Role byName(String s, Role fallback) {
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
