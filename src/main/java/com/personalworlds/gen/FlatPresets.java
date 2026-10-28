package com.personalworlds.gen;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.personalworlds.PersonalWorlds;
import com.personalworlds.config.PWConfig;
import com.personalworlds.text.Msg;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Пресеты плоского мира (config/personalworlds/flat_presets.json) и разбор строки слоёв
 * в формате ванильного суперплоского мира: снизу вверх, «N*блок», через запятую.
 */
public final class FlatPresets {
    public record Preset(String id, String name, String layers, @Nullable String grid) {}

    /** Готовые слои, проверенные на корректность. */
    public record Parsed(List<LayeredFlatGenerator.Layer> layers, @Nullable BlockState grid, int thickness) {}

    public static class LayersException extends Exception {
        public LayersException(String message) {
            super(message);
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, Preset> PRESETS = new LinkedHashMap<>();

    private static Map<String, Preset> defaults() {
        Map<String, Preset> m = new LinkedHashMap<>();
        put(m, "classic", "Классический", "bedrock,58*stone,4*dirt,grass_block", null);
        put(m, "blueprint", "Чертёж: сетка по чанкам", "bedrock,62*stone,white_concrete", "light_gray_concrete");
        put(m, "grass_grid", "Трава с сеткой по чанкам", "bedrock,58*stone,4*dirt,grass_block", "moss_block");
        put(m, "stone", "Камень", "bedrock,63*stone", null);
        put(m, "sand", "Пустыня", "bedrock,56*stone,4*sandstone,4*sand", null);
        put(m, "snow", "Снег", "bedrock,58*stone,4*dirt,snow_block", null);
        put(m, "thin", "Тонкий (4 блока)", "bedrock,2*dirt,grass_block", null);
        return m;
    }

    private static void put(Map<String, Preset> m, String id, String name, String layers, @Nullable String grid) {
        m.put(id, new Preset(id, name, layers, grid));
    }

    public static void load() {
        PRESETS.clear();
        Path file = Msg.configDir().resolve("flat_presets.json");
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) {
                JsonObject root = new JsonObject();
                for (Preset p : defaults().values()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", p.name());
                    o.addProperty("layers", p.layers());
                    if (p.grid() != null) o.addProperty("grid", p.grid());
                    root.add(p.id(), o);
                }
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(root, w);
                }
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                for (var e : root.entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    String id = e.getKey().toLowerCase(Locale.ROOT);
                    String grid = o.has("grid") ? o.get("grid").getAsString() : null;
                    Preset p = new Preset(id, o.has("name") ? o.get("name").getAsString() : id, o.get("layers").getAsString(), grid);
                    try {
                        parse(p.layers(), p.grid());
                        PRESETS.put(id, p);
                    } catch (LayersException ex) {
                        PersonalWorlds.LOG.error("Пресет «{}» в {} пропущен: {}", id, file, ex.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            PersonalWorlds.LOG.error("Не удалось прочитать {}, используются пресеты по умолчанию", file, e);
            PRESETS.putAll(defaults());
        }
        if (PRESETS.isEmpty()) PRESETS.putAll(defaults());
    }

    public static Map<String, Preset> all() {
        return PRESETS;
    }

    @Nullable
    public static Preset get(String id) {
        return PRESETS.get(id.toLowerCase(Locale.ROOT));
    }

    public static Preset defaultPreset() {
        Preset p = PRESETS.get("classic");
        return p != null ? p : PRESETS.values().iterator().next();
    }

    public static Parsed parse(String layers, @Nullable String grid) throws LayersException {
        List<LayeredFlatGenerator.Layer> out = new ArrayList<>();
        int total = 0;
        for (String token : layers.split(",")) {
            token = token.trim();
            if (token.isEmpty()) continue;
            int count = 1;
            int star = token.indexOf('*');
            if (star > 0) {
                String n = token.substring(0, star).trim();
                token = token.substring(star + 1).trim();
                try {
                    count = Integer.parseInt(n);
                } catch (NumberFormatException e) {
                    throw new LayersException(Msg.raw("err.layers_count", n));
                }
                if (count < 1) throw new LayersException(Msg.raw("err.layers_count", n));
            }
            out.add(new LayeredFlatGenerator.Layer(block(token), count));
            total += count;
        }
        if (out.isEmpty()) throw new LayersException(Msg.raw("err.layers_empty"));
        int max = PWConfig.MAX_FLAT_THICKNESS.get();
        if (total > max) throw new LayersException(Msg.raw("err.layers_thick", total, max));
        BlockState gridState = grid == null || grid.isBlank() ? null : block(grid.trim());
        return new Parsed(out, gridState, total);
    }

    private static BlockState block(String id) throws LayersException {
        ResourceLocation rl = ResourceLocation.tryParse(id.toLowerCase(Locale.ROOT));
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) throw new LayersException(Msg.raw("err.layers_block", id));
        Block b = BuiltInRegistries.BLOCK.get(rl);
        return b.defaultBlockState();
    }

    private FlatPresets() {}
}
