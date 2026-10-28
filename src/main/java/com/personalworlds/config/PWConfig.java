package com.personalworlds.config;

import java.util.List;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Настройки мода: config/personalworlds-common.toml. Тексты сообщений лежат отдельно, в config/personalworlds/messages.json. */
public final class PWConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue DEFAULT_BORDER;
    public static final ModConfigSpec.IntValue MAX_BORDER;
    public static final ModConfigSpec.IntValue MIN_BORDER;
    public static final ModConfigSpec.IntValue AUTO_UNLOAD_MINUTES;
    public static final ModConfigSpec.IntValue WARMUP_SECONDS;
    public static final ModConfigSpec.IntValue RESET_COOLDOWN_MINUTES;
    public static final ModConfigSpec.IntValue REQUEST_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue FLAT_SURFACE_Y;
    public static final ModConfigSpec.IntValue MAX_FLAT_THICKNESS;
    public static final ModConfigSpec.ConfigValue<String> BUILD_DIMENSION_TYPE;
    public static final ModConfigSpec.BooleanValue ALLOW_COMMAND_BLOCKS;
    public static final ModConfigSpec.BooleanValue KICK_ON_OWNER_LOGOUT;

    public static final ModConfigSpec.ConfigValue<List<? extends String>> ADMIN_COMMANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> NON_PLAYER_COMMANDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLOCKED_GAMERULES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Размеры и поведение личных миров").push("worlds");
        DEFAULT_BORDER = b.comment("Размер границы мира (сторона квадрата в блоках) при создании")
                .defineInRange("defaultBorderSize", 512, 16, 60_000_000);
        MAX_BORDER = b.comment("Максимальный размер границы, который игрок может выставить сам. Админ сервера может поднять лимит конкретному игроку: /pw admin limit <ник> <размер>")
                .defineInRange("maxBorderSize", 2048, 16, 60_000_000);
        MIN_BORDER = b.comment("Минимальный размер границы").defineInRange("minBorderSize", 32, 16, 60_000_000);
        AUTO_UNLOAD_MINUTES = b.comment("Через сколько минут без игроков мир выгружается из памяти (0 — никогда)")
                .defineInRange("autoUnloadMinutes", 5, 0, 10_080);
        RESET_COOLDOWN_MINUTES = b.comment("Задержка между пересозданиями мира одним игроком, минут")
                .defineInRange("resetCooldownMinutes", 10, 0, 100_000);
        FLAT_SURFACE_Y = b.comment("Высота поверхности плоского мира: первый блок воздуха над верхним слоем")
                .defineInRange("flatSurfaceY", 64, -60, 319);
        MAX_FLAT_THICKNESS = b.comment("Максимальная суммарная толщина слоёв плоского мира")
                .defineInRange("maxFlatThickness", 384, 1, 4096);
        BUILD_DIMENSION_TYPE = b.comment("Тип измерения для плоских и пустых миров. personalworlds:build — обычная высота (-64..320).",
                        "Если с ним будут проблемы, можно поставить minecraft:overworld (тогда высота как в основном мире).")
                .define("buildDimensionType", "personalworlds:build");
        b.pop();

        b.push("teleport");
        WARMUP_SECONDS = b.comment("Сколько секунд стоять на месте перед телепортом из основного мира (в выживании). 0 — без задержки")
                .defineInRange("warmupSeconds", 3, 0, 60);
        REQUEST_TIMEOUT_SECONDS = b.comment("Сколько секунд действует запрос на вход в мир")
                .defineInRange("requestTimeoutSeconds", 120, 10, 3600);
        KICK_ON_OWNER_LOGOUT = b.comment("Выгонять гостей из мира, когда владелец вышел, если в мире выключен вход без владельца")
                .define("kickOnOwnerLogout", true);
        b.pop();

        b.comment("Права администратора мира. Все команды действуют только внутри его мира:",
                "селекторы (@a, @e, ники) видят только сущности этого мира, переход в другие измерения запрещён.").push("permissions");
        ADMIN_COMMANDS = b.comment("Команды, доступные владельцу и администраторам мира",
                        "(worldborder нет специально: границу меняют через /pw border, чтобы соблюдался лимит)")
                .defineListAllowEmpty("adminCommands", List.of(
                        "time", "weather", "gamerule", "setworldspawn",
                        "gamemode", "tp", "teleport", "effect", "clear", "give",
                        "fill", "setblock", "clone", "place", "fillbiome",
                        "summon", "kill", "data", "item",
                        "particle", "playsound", "title", "tellraw"
                ), () -> "", o -> o instanceof String);
        NON_PLAYER_COMMANDS = b.comment("Команды, которые могут выполнять командные блоки и компьютеры CC:Tweaked внутри личных миров")
                .defineListAllowEmpty("nonPlayerCommands", List.of(
                        "say", "tellraw", "title", "particle", "playsound", "setblock", "fill", "clone", "summon", "effect", "time", "weather"
                ), () -> "", o -> o instanceof String);
        BLOCKED_GAMERULES = b.comment("Правила игры, которые администратор мира менять не может (влияют на весь сервер или на нагрузку)")
                .defineListAllowEmpty("blockedGameRules", List.of(
                        "spawnChunkRadius", "randomTickSpeed", "commandModificationBlockLimit", "maxCommandChainLength",
                        "maxCommandForkCount", "maxEntityCramming", "reducedDebugInfo", "doImmediateRespawn",
                        "limitedCrafting", "logAdminCommands", "sendCommandFeedback", "spectatorsGenerateChunks"
                ), () -> "", o -> o instanceof String);
        ALLOW_COMMAND_BLOCKS = b.comment("Разрешить работу командных блоков в личных мирах (они тоже ограничены своим миром)")
                .define("allowCommandBlocks", false);
        b.pop();

        SPEC = b.build();
    }

    private PWConfig() {}
}
