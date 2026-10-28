package com.personalworlds.text;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.personalworlds.PersonalWorlds;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Все тексты мода. Хранятся в config/personalworlds/messages.json, коды цветов через &amp; (как в плагинах),
 * подстановки {0}, {1}... При запуске недостающие ключи дописываются в файл.
 */
public final class Msg {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    private static final Map<String, String> TEXTS = new LinkedHashMap<>();

    static {
        d("prefix", "&8[&bМиры&8] &r");

        d("help.header", "&b&lЛичные миры &7— команды:");
        d("help.line", "&e{0} &7— {1}");
        d("help.create", "создать свой мир");
        d("help.home", "перейти в свой мир");
        d("help.leave", "вернуться в основной мир");
        d("help.visit", "зайти в мир игрока");
        d("help.request", "попросить доступ в мир игрока");
        d("help.list", "миры, куда у вас есть доступ");
        d("help.info", "информация о мире");
        d("help.access", "кто имеет доступ к миру");
        d("help.role", "выдать роль: admin, builder, guest");
        d("help.kick", "выгнать / забанить / разбанить в своём мире");
        d("help.setting", "настройки мира");
        d("help.setspawn", "точка появления в мире");
        d("help.border", "размер границы мира");
        d("help.reset", "пересоздать / удалить / передать мир");

        d("role.owner", "Владелец");
        d("role.admin", "Админ");
        d("role.builder", "Строитель");
        d("role.guest", "Гость");

        d("type.flat", "Плоский");
        d("type.void", "Пустота");
        d("type.normal", "Обычный");
        d("type.seed", "Копия сида основного мира");

        d("btn.hover", "&7Нажмите, чтобы выполнить:\n&f{0}");

        d("err.no_world", "&cУ вас ещё нет мира. Создайте: &e/pw create");
        d("err.has_world", "&cУ вас уже есть мир. Пересоздать его можно командой &e/pw reset");
        d("err.no_player", "&cИгрок &e{0}&c не найден.");
        d("err.no_world_of", "&cУ игрока &e{0}&c нет мира.");
        d("err.no_access", "&cУ вас нет доступа в мир игрока &e{0}&c. Попросите: &e/pw request {0}");
        d("err.banned", "&cВы заблокированы в мире игрока &e{0}&c.");
        d("err.owner_offline", "&cВладелец мира &e{0}&c не в сети, а вход без него в этом мире выключен.");
        d("err.not_allowed", "&cНедостаточно прав в этом мире.");
        d("err.owner_only", "&cЭто может сделать только владелец мира.");
        d("err.not_in_world", "&cВы не в личном мире.");
        d("err.already_inside", "&cВы уже в этом мире.");
        d("err.not_inside", "&cВы и так в основном мире.");
        d("err.carrying", "&cПоложите то, что несёте в руках (Carry On), и попробуйте снова.");
        d("err.riding", "&cСлезьте с транспорта или существа.");
        d("err.self", "&cНельзя сделать это с самим собой.");
        d("err.is_owner", "&cЭто владелец мира.");
        d("err.target_higher", "&cНельзя менять права игрока с такой же или более высокой ролью.");
        d("err.bad_role", "&cНеизвестная роль &e{0}&c. Доступно: &eadmin&c, &ebuilder&c, &eguest");
        d("err.bad_type", "&cНеизвестный тип мира &e{0}&c. Доступно: &eflat&c, &evoid&c, &enormal&c, &eseed");
        d("err.bad_preset", "&cНет пресета &e{0}&c. Доступные: {1}");
        d("err.bad_layers", "&cОшибка в слоях: {0}");
        d("err.layers_block", "неизвестный блок «{0}»");
        d("err.layers_count", "неверное количество «{0}»");
        d("err.layers_empty", "не указано ни одного слоя");
        d("err.layers_thick", "слишком толсто: {0} блоков (максимум {1})");
        d("err.bad_value", "&cНеверное значение &e{0}&c.");
        d("err.bad_setting", "&cНет такой настройки &e{0}&c. Список: &e/pw setting");
        d("err.border_range", "&cРазмер границы должен быть от &e{0}&c до &e{1}&c.");
        d("err.cooldown", "&cПересоздавать мир можно не чаще раза в {0} мин. Подождите ещё {1} мин.");
        d("err.no_confirm", "&cНечего подтверждать (или время вышло).");
        d("err.target_has_world", "&cУ игрока &e{0}&c уже есть свой мир.");
        d("err.no_request", "&cОт игрока &e{0}&c нет запроса.");
        d("err.already_member", "&cУ вас уже есть доступ в этот мир. Заходите: &e/pw visit {0}");
        d("err.load_failed", "&cНе удалось загрузить мир. Подробности в логе сервера.");
        d("err.blocked_command", "&cЭта команда недоступна в личном мире.");
        d("err.blocked_dimension", "&cВ личном мире команды действуют только внутри этого мира.");
        d("err.blocked_gamerule", "&cЭто правило нельзя менять в личном мире.");
        d("err.no_travel_in", "&cВ личные миры можно попасть только командами &e/pw home&c и &e/pw visit&c.");
        d("err.guest", "&7Вы гость в этом мире — строить и взаимодействовать нельзя.");
        d("err.no_spawn_set", "&cВ личном мире нельзя установить точку возрождения.");
        d("err.portal", "&cВ личных мирах порталы не работают.");

        d("create.choose", "&bВыберите тип мира:");
        d("create.flat_choose", "&bВыберите пресет плоского мира (или свои слои: &e/pw create flat custom bedrock,3*dirt,grass_block&b):");
        d("create.creating", "&7Создаём мир…");
        d("create.done", "&aМир создан! &7Тип: &f{0}&7. Перенос…");

        d("tp.warmup", "&eТелепорт через {0} с. Не двигайтесь…");
        d("tp.cancelled", "&cТелепорт отменён: вы сдвинулись или получили урон.");
        d("tp.entered", "&aВы в мире игрока &e{0}&a. Ваша роль: &f{1}&a. Вернуться: &e/pw leave");
        d("tp.entered_own", "&aДобро пожаловать в свой мир! Вернуться в основной мир: &e/pw leave");
        d("tp.left", "&aВы вернулись в основной мир. Инвентарь восстановлен.");
        d("tp.kicked", "&cВас выгнали из мира игрока &e{0}&c.");
        d("tp.world_closed", "&cМир игрока &e{0}&c закрыт: вы перенесены в основной мир.");
        d("tp.owner_left", "&eВладелец мира вышел с сервера, вход без него выключен — вы перенесены в основной мир.");

        d("info.header", "&b&lМир «{0}»");
        d("info.owner", "&7Владелец: &f{0}");
        d("info.type", "&7Тип: &f{0}");
        d("info.border", "&7Граница: &f{0}&7 блоков (максимум {1})");
        d("info.members", "&7Участников: &f{0}");
        d("info.loaded", "&7Состояние: &f{0}");
        d("info.loaded_yes", "загружен, игроков внутри: {0}");
        d("info.loaded_no", "выгружен (загрузится при входе)");

        d("list.header", "&b&lДоступные миры:");
        d("list.entry", "&f{0} &7({1}) &8— &7роль: &f{2}");
        d("list.empty", "&7Пока ни одного. Создайте свой: &e/pw create");
        d("btn.enter", "&a[Войти]");

        d("access.header", "&b&lДоступ к миру «{0}»:");
        d("access.entry", "&f{0} &8— &7{1}");
        d("access.banned", "&cЗаблокированы: &f{0}");
        d("access.empty", "&7Кроме владельца, доступа ни у кого нет. Добавить: &e/pw access add <ник> [роль]");
        d("access.added", "&aИгрок &e{0}&a получил доступ к миру с ролью &f{1}&a.");
        d("access.added_target", "&aИгрок &e{0}&a дал вам доступ в свой мир (роль: &f{1}&a).");
        d("access.removed", "&aИгрок &e{0}&a больше не имеет доступа к миру.");
        d("access.role_set", "&aРоль игрока &e{0}&a: &f{1}");
        d("access.not_member", "&cУ игрока &e{0}&c нет доступа к этому миру.");
        d("btn.remove", "&c[✕]");

        d("kick.done", "&aИгрок &e{0}&a выгнан из мира.");
        d("kick.not_inside", "&cИгрока &e{0}&c нет в этом мире.");
        d("ban.done", "&aИгрок &e{0}&a заблокирован в мире.");
        d("unban.done", "&aИгрок &e{0}&a разблокирован.");

        d("request.sent", "&aЗапрос на вход отправлен игроку &e{0}&a.");
        d("request.received", "&eИгрок &f{0}&e просится в ваш мир.");
        d("request.offline", "&7Владелец не в сети — он увидит запрос, если зайдёт в течение {0} с.");
        d("request.accepted", "&aИгрок &e{0}&a пустил вас в свой мир!");
        d("request.denied", "&cИгрок &e{0}&c отклонил ваш запрос.");
        d("request.denied_owner", "&7Запрос игрока &f{0}&7 отклонён.");
        d("btn.accept_guest", "&a[Пустить гостем]");
        d("btn.accept_builder", "&a[Пустить строителем]");
        d("btn.deny", "&c[Отклонить]");

        d("setting.header", "&b&lНастройки мира «{0}»:");
        d("setting.line", "&7{0}: &f{1} ");
        d("setting.set", "&aНастройка &e{0}&a = &f{1}");
        d("setting.on", "вкл");
        d("setting.off", "выкл");
        d("btn.on", "&a[вкл]");
        d("btn.off", "&c[выкл]");
        d("setting.name", "Название");
        d("setting.builder_mode", "Режим для участников");
        d("setting.guest_mode", "Режим для гостей");
        d("setting.offline_access", "Вход без владельца");
        d("setting.pvp", "PvP");
        d("setting.daylight", "Смена дня и ночи");
        d("setting.weather_cycle", "Смена погоды");
        d("setting.mobs", "Спавн мобов");
        d("setting.fire", "Распространение огня");
        d("setting.fall_damage", "Урон от падения");
        d("setting.keep_inventory", "Сохранение инвентаря при смерти");
        d("setting.mob_griefing", "Мобы ломают блоки");
        d("setting.time_hint", "&7Время: &e/pw setting time <day|noon|night|midnight|число>&7, погода: &e/pw setting weather <clear|rain|thunder>");

        d("spawn.set", "&aТочка появления мира установлена: &f{0} {1} {2}");
        d("border.current", "&7Граница мира: &f{0}&7 (можно от {1} до {2}). Изменить: &e/pw border <размер>");
        d("border.set", "&aГраница мира: &f{0}&a блоков.");

        d("confirm.reset", "&eМир будет &cполностью пересоздан&e, все постройки пропадут. Доступы и настройки сохранятся.");
        d("confirm.delete", "&eМир будет &cудалён навсегда&e вместе со всеми постройками.");
        d("confirm.transfer", "&eМир перейдёт игроку &f{0}&e, вы станете в нём админом.");
        d("btn.confirm", "&c[Подтвердить]");
        d("confirm.timeout", "&7(действует 30 секунд)");
        d("reset.done", "&aМир пересоздан.");
        d("delete.done", "&aМир удалён.");
        d("transfer.done", "&aМир передан игроку &e{0}&a.");
        d("transfer.received", "&aИгрок &e{0}&a передал вам свой мир!");

        d("admin.limit", "&aЛимит границы для &e{0}&a: &f{1}");
        d("admin.unloaded", "&aМир игрока &e{0}&a выгружен.");
        d("admin.list_header", "&b&lВсе личные миры ({0}):");
        d("admin.list_entry", "&f{0} &7— {1}, граница {2}, {3}");
        d("admin.reloaded", "&aТексты сообщений и пресеты перечитаны.");

        d("tab.own", " &8[&a✦ у себя&8]");
        d("tab.other", " &8[&b✦ &3{0}&8]");

        d("state.restored", "&eВаш личный мир недоступен — вы перенесены в основной мир, инвентарь восстановлен.");
    }

    private static void d(String key, String value) {
        DEFAULTS.put(key, value);
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve(PersonalWorlds.MODID);
    }

    /** Загружает файл с текстами, дописывает в него новые ключи. */
    public static void load() {
        TEXTS.clear();
        TEXTS.putAll(DEFAULTS);
        Path file = configDir().resolve("messages.json");
        JsonObject json = new JsonObject();
        try {
            Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject read = GSON.fromJson(r, JsonObject.class);
                    if (read != null) json = read;
                }
            }
            boolean changed = false;
            for (var e : DEFAULTS.entrySet()) {
                if (json.has(e.getKey())) {
                    TEXTS.put(e.getKey(), json.get(e.getKey()).getAsString());
                } else {
                    json.addProperty(e.getKey(), e.getValue());
                    changed = true;
                }
            }
            if (changed) {
                try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    GSON.toJson(json, w);
                }
            }
        } catch (Exception e) {
            PersonalWorlds.LOG.error("Не удалось прочитать {}, используются тексты по умолчанию", file, e);
        }
    }

    public static String raw(String key, Object... args) {
        String s = TEXTS.getOrDefault(key, DEFAULTS.getOrDefault(key, key));
        for (int i = 0; i < args.length; i++) {
            s = s.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return s;
    }

    /** Текст по ключу без префикса. */
    public static MutableComponent t(String key, Object... args) {
        return parse(raw(key, args));
    }

    /** Текст по ключу с префиксом мода. */
    public static MutableComponent p(String key, Object... args) {
        return parse(raw("prefix")).append(t(key, args));
    }

    public static void send(ServerPlayer player, String key, Object... args) {
        player.sendSystemMessage(p(key, args));
    }

    public static void ok(CommandSourceStack src, String key, Object... args) {
        src.sendSuccess(() -> p(key, args), false);
    }

    public static void fail(CommandSourceStack src, String key, Object... args) {
        src.sendFailure(p(key, args));
    }

    public static void actionBar(ServerPlayer player, String key, Object... args) {
        player.displayClientMessage(t(key, args), true);
    }

    /** Кликабельная кнопка, выполняющая команду. */
    public static MutableComponent button(String key, String command, Object... args) {
        return t(key, args).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, t("btn.hover", command))));
    }

    /** Кнопка с произвольным текстом, выполняющая команду. */
    public static MutableComponent run(String text, String command) {
        return parse(text).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, t("btn.hover", command))));
    }

    /** Кнопка, вставляющая команду в строку чата (для команд с аргументами). */
    public static MutableComponent suggest(String text, String command) {
        return parse(text).withStyle(s -> s
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, t("btn.hover", command))));
    }

    /** Разбор цветовых кодов &amp;0-&amp;f, &amp;k-&amp;o, &amp;r. */
    public static MutableComponent parse(String text) {
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '&' && i + 1 < text.length()) {
                ChatFormatting f = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
                if (f != null) {
                    if (!buf.isEmpty()) {
                        root.append(Component.literal(buf.toString()).setStyle(style));
                        buf.setLength(0);
                    }
                    style = f == ChatFormatting.RESET ? Style.EMPTY : (f.isColor() ? Style.EMPTY.applyFormat(f) : style.applyFormat(f));
                    i++;
                    continue;
                }
            }
            buf.append(c);
        }
        if (!buf.isEmpty()) root.append(Component.literal(buf.toString()).setStyle(style));
        return root;
    }

    private Msg() {}
}
