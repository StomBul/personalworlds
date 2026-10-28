package com.personalworlds.world;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import com.personalworlds.text.Msg;

public enum WorldType {
    /** Свой плоский генератор со слоями. */
    FLAT,
    /** Пустота с платформой на спавне. */
    VOID,
    /** Генерация основного мира (со всеми модами генерации), случайный сид. */
    NORMAL,
    /** Генерация основного мира с его же сидом. */
    SEED;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return Msg.raw("type." + id());
    }

    /** Плоский и пустой миры используют свой тип измерения обычной высоты. */
    public boolean usesBuildDimension() {
        return this == FLAT || this == VOID;
    }

    @Nullable
    public static WorldType parse(String s) {
        for (WorldType t : values()) {
            if (t.id().equals(s.toLowerCase(Locale.ROOT))) return t;
        }
        return null;
    }
}
