package com.personalworlds.player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.personalworlds.config.PWConfig;
import com.personalworlds.text.Msg;
import com.personalworlds.world.WorldManager;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Задержка перед телепортом из основного мира: нельзя мгновенно сбежать из боя в свой мир. */
public final class Teleports {
    private static final class Pending {
        final Vec3 start;
        final float health;
        int ticksLeft;
        final Runnable action;

        Pending(Vec3 start, float health, int ticksLeft, Runnable action) {
            this.start = start;
            this.health = health;
            this.ticksLeft = ticksLeft;
            this.action = action;
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    public static void schedule(ServerPlayer p, Runnable action) {
        int seconds = PWConfig.WARMUP_SECONDS.get();
        if (seconds <= 0 || WorldManager.isPersonal(p.level()) || p.isCreative() || p.isSpectator() || p.hasPermissions(2)) {
            PENDING.remove(p.getUUID());
            action.run();
            return;
        }
        PENDING.put(p.getUUID(), new Pending(p.position(), p.getHealth(), seconds * 20, action));
        Msg.send(p, "tp.warmup", seconds);
    }

    public static void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Pending>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Pending t = e.getValue();
            if (p == null) {
                it.remove();
                continue;
            }
            if (p.position().distanceToSqr(t.start) > 1.0 || p.getHealth() < t.health) {
                it.remove();
                Msg.send(p, "tp.cancelled");
                continue;
            }
            if (--t.ticksLeft <= 0) {
                it.remove();
                t.action.run();
            } else if (t.ticksLeft % 20 == 0) {
                Msg.actionBar(p, "tp.warmup", t.ticksLeft / 20);
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }

    private Teleports() {}
}
