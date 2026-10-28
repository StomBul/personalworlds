package com.personalworlds.world;

import com.mojang.serialization.Dynamic;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.level.timers.TimerCallbacks;
import net.minecraft.world.level.timers.TimerQueue;

/**
 * Данные уровня личного мира. Ванильные дополнительные измерения берут время, погоду и правила игры
 * у основного мира; здесь у каждого личного мира они свои.
 */
public class PersonalLevelData extends DerivedLevelData {
    private final String name;
    private BlockPos spawn = new BlockPos(0, 64, 0);
    private float spawnAngle;
    private long gameTime;
    private long dayTime = 6000L;
    private boolean raining;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;
    private int clearWeatherTime;
    private float dayTimeFraction;
    private float dayTimePerTick = -1f;
    private final GameRules gameRules;
    private final TimerQueue<MinecraftServer> scheduledEvents;

    public PersonalLevelData(WorldData worldData, ServerLevelData overworld, String name, CompoundTag tag) {
        super(worldData, overworld);
        this.name = name;
        if (tag.contains("SpawnX")) {
            spawn = new BlockPos(tag.getInt("SpawnX"), tag.getInt("SpawnY"), tag.getInt("SpawnZ"));
            spawnAngle = tag.getFloat("SpawnAngle");
        }
        if (tag.contains("DayTime")) {
            gameTime = tag.getLong("Time");
            dayTime = tag.getLong("DayTime");
        }
        raining = tag.getBoolean("Raining");
        rainTime = tag.getInt("RainTime");
        thundering = tag.getBoolean("Thundering");
        thunderTime = tag.getInt("ThunderTime");
        clearWeatherTime = tag.getInt("ClearWeatherTime");
        dayTimeFraction = tag.getFloat("DayTimeFraction");
        dayTimePerTick = tag.contains("DayTimePerTick") ? tag.getFloat("DayTimePerTick") : -1f;
        gameRules = tag.contains("GameRules")
                ? new GameRules(new Dynamic<>(NbtOps.INSTANCE, tag.getCompound("GameRules")))
                : defaultRules();
        scheduledEvents = new TimerQueue<>(TimerCallbacks.SERVER_CALLBACKS,
                tag.getList("ScheduledEvents", Tag.TAG_COMPOUND).stream().map(t -> new Dynamic<>(NbtOps.INSTANCE, t)));
    }

    /** Правила по умолчанию для нового мира: удобно строить. Владелец может поменять через /pw setting. */
    public static GameRules defaultRules() {
        GameRules r = new GameRules();
        r.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        r.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        r.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        r.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
        r.getRule(GameRules.RULE_MOBGRIEFING).set(false, null);
        r.getRule(GameRules.RULE_KEEPINVENTORY).set(true, null);
        r.getRule(GameRules.RULE_DOINSOMNIA).set(false, null);
        r.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, null);
        r.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, null);
        r.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, null);
        r.getRule(GameRules.RULE_DISABLE_RAIDS).set(true, null);
        r.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        // Спавн-чанки не держим загруженными: пустой мир ничего не стоит серверу.
        r.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0, null);
        return r;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("SpawnX", spawn.getX());
        t.putInt("SpawnY", spawn.getY());
        t.putInt("SpawnZ", spawn.getZ());
        t.putFloat("SpawnAngle", spawnAngle);
        t.putLong("Time", gameTime);
        t.putLong("DayTime", dayTime);
        t.putBoolean("Raining", raining);
        t.putInt("RainTime", rainTime);
        t.putBoolean("Thundering", thundering);
        t.putInt("ThunderTime", thunderTime);
        t.putInt("ClearWeatherTime", clearWeatherTime);
        t.putFloat("DayTimeFraction", dayTimeFraction);
        t.putFloat("DayTimePerTick", dayTimePerTick);
        t.put("GameRules", gameRules.createTag());
        t.put("ScheduledEvents", scheduledEvents.store());
        return t;
    }

    @Override
    public String getLevelName() {
        return name;
    }

    @Override
    public BlockPos getSpawnPos() {
        return spawn;
    }

    @Override
    public float getSpawnAngle() {
        return spawnAngle;
    }

    @Override
    public void setSpawn(BlockPos spawnPoint, float angle) {
        this.spawn = spawnPoint.immutable();
        this.spawnAngle = angle;
    }

    @Override
    public long getGameTime() {
        return gameTime;
    }

    @Override
    public void setGameTime(long time) {
        this.gameTime = time;
    }

    @Override
    public long getDayTime() {
        return dayTime;
    }

    @Override
    public void setDayTime(long time) {
        this.dayTime = time;
    }

    @Override
    public boolean isRaining() {
        return raining;
    }

    @Override
    public void setRaining(boolean isRaining) {
        this.raining = isRaining;
    }

    @Override
    public int getRainTime() {
        return rainTime;
    }

    @Override
    public void setRainTime(int time) {
        this.rainTime = time;
    }

    @Override
    public boolean isThundering() {
        return thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
    }

    @Override
    public int getThunderTime() {
        return thunderTime;
    }

    @Override
    public void setThunderTime(int time) {
        this.thunderTime = time;
    }

    @Override
    public int getClearWeatherTime() {
        return clearWeatherTime;
    }

    @Override
    public void setClearWeatherTime(int time) {
        this.clearWeatherTime = time;
    }

    @Override
    public GameRules getGameRules() {
        return gameRules;
    }

    @Override
    public TimerQueue<MinecraftServer> getScheduledEvents() {
        return scheduledEvents;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    @Override
    public float getDayTimeFraction() {
        return dayTimeFraction;
    }

    @Override
    public float getDayTimePerTick() {
        return dayTimePerTick;
    }

    @Override
    public void setDayTimeFraction(float dayTimeFraction) {
        this.dayTimeFraction = dayTimeFraction;
    }

    @Override
    public void setDayTimePerTick(float dayTimePerTick) {
        this.dayTimePerTick = dayTimePerTick;
    }
}
