package com.personalworlds.test;

import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.personalworlds.PersonalWorlds;
import com.personalworlds.world.PersonalServerLevel;
import com.personalworlds.world.PersonalWorld;
import com.personalworlds.world.Role;
import com.personalworlds.world.WorldManager;
import com.personalworlds.world.WorldType;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Сквозной сценарий: создание мира, обмен инвентаря, права админа, гость, выгрузка/загрузка, пустота и обычный мир. */
@GameTestHolder(PersonalWorlds.MODID)
@PrefixGameTestTemplate(false)
public class PWGameTests {
    private static ServerPlayer mock(GameTestHelper h, String name) {
        ServerPlayer p = mock(h, new GameProfile(UUID.randomUUID(), name));
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static ServerPlayer mock(GameTestHelper h, GameProfile profile) {
        MinecraftServer server = h.getLevel().getServer();
        String name = profile.getName();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public void sendSystemMessage(net.minecraft.network.chat.Component message, boolean overlay) {
                PersonalWorlds.LOG.info("[тест] -> {}: {}", name, message.getString());
                super.sendSystemMessage(message, overlay);
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, p, cookie);
        return p;
    }

    private static void cmd(ServerPlayer p, String c) {
        PersonalWorlds.LOG.info("[тест] {}: /{}", p.getGameProfile().getName(), c);
        p.server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), c);
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new GameTestAssertException(msg);
        PersonalWorlds.LOG.info("[тест] OK: {}", msg);
    }

    @GameTest(template = "empty", timeoutTicks = 4000)
    public static void fullFlow(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer alice = mock(h, "Alice");
        ServerPlayer bob = mock(h, "Bob");
        check(!alice.hasPermissions(2), "Alice не оператор");
        alice.getInventory().add(new ItemStack(Items.DIAMOND, 5));
        int owSleep = server.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE);

        cmd(alice, "pw create flat blueprint");

        h.runAfterDelay(80, () -> {
            check(alice.level() instanceof PersonalServerLevel, "Alice в личном мире (сейчас " + alice.level().dimension().location() + ")");
            PersonalServerLevel lvl = (PersonalServerLevel) alice.level();
            check(alice.getInventory().countItem(Items.DIAMOND) == 0, "алмазы основного мира не попали в личный мир");
            check(alice.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "владелец в креативе");
            check(lvl.getBlockState(new BlockPos(5, 63, 5)).is(Blocks.WHITE_CONCRETE), "верхний слой бетона на Y=63: " + lvl.getBlockState(new BlockPos(5, 63, 5)));
            check(lvl.getBlockState(new BlockPos(16, 63, 5)).is(Blocks.LIGHT_GRAY_CONCRETE), "сетка на границе чанка");
            check(lvl.getBlockState(new BlockPos(5, 64, 5)).isAir(), "над поверхностью воздух");
            check(lvl.getBlockState(new BlockPos(5, 0, 5)).is(Blocks.BEDROCK), "бедрок в основании (Y=0)");
            check(lvl.getBlockState(new BlockPos(5, -1, 5)).isAir(), "под бедроком пустота");
            check(alice.getY() >= 64 && alice.getY() < 66, "игрок стоит на поверхности, Y=" + alice.getY());
            check(lvl.getWorldBorder().getSize() == 512, "граница 512: " + lvl.getWorldBorder().getSize());
            check(alice.getTabListDisplayName() != null && alice.getTabListDisplayName().getString().equals("Alice [✦ у себя]"),
                    "Tab: метка своего мира — " + alice.getTabListDisplayName());

            long owTime = server.overworld().getDayTime();
            cmd(alice, "time set 18000");
            check(lvl.getDayTime() % 24000 == 18000, "/time изменил время личного мира");
            check(Math.abs(server.overworld().getDayTime() - owTime) < 5, "/time не тронул основной мир");

            cmd(alice, "gamerule playersSleepingPercentage 42");
            check(lvl.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) == 42, "/gamerule изменил правило личного мира");
            check(server.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) == owSleep, "/gamerule не тронул сервер");
            cmd(alice, "gamerule randomTickSpeed 1000");
            check(lvl.getGameRules().getInt(GameRules.RULE_RANDOMTICKING) == 3, "запрещённое правило randomTickSpeed не изменилось");

            cmd(alice, "weather thunder");
            check(lvl.getLevelData().isThundering() && !server.overworld().getLevelData().isThundering(), "/weather только в личном мире");

            cmd(alice, "give @s minecraft:stone 3");
            check(alice.getInventory().countItem(Items.STONE) == 3, "/give @s работает");
            cmd(alice, "give Bob minecraft:stone 3");
            check(bob.getInventory().countItem(Items.STONE) == 0, "/give не достаёт игрока в основном мире");
            cmd(alice, "kill Bob");
            check(bob.isAlive(), "/kill не достаёт игрока в основном мире");

            cmd(alice, "setblock 3 64 3 minecraft:gold_block");
            check(lvl.getBlockState(new BlockPos(3, 64, 3)).is(Blocks.GOLD_BLOCK), "/setblock в своём мире");
            server.overworld().setBlockAndUpdate(new BlockPos(0, 120, 0), Blocks.AIR.defaultBlockState());
            cmd(alice, "execute in minecraft:overworld run setblock 0 120 0 minecraft:gold_block");
            check(server.overworld().getBlockState(new BlockPos(0, 120, 0)).isAir(), "execute in overworld недоступен");
            cmd(alice, "clone from minecraft:overworld 0 0 0 1 1 1 to " + lvl.dimension().location() + " 10 64 10");
            check(lvl.getBlockState(new BlockPos(10, 64, 10)).isAir(), "/clone из основного мира запрещён");

            cmd(alice, "pw access add Bob guest");
            check(lvl.world().roleOf(bob.getUUID()) == Role.GUEST, "Bob добавлен гостем");
            cmd(bob, "pw visit Alice");
        });

        h.runAfterDelay(160, () -> {
            check(bob.level() == alice.level(), "Bob зашёл в мир Alice");
            PersonalServerLevel lvl = (PersonalServerLevel) alice.level();
            check(bob.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "гость в режиме приключения");
            check(bob.getTabListDisplayName() != null && bob.getTabListDisplayName().getString().equals("Bob [✦ Alice]"),
                    "Tab: метка чужого мира — " + bob.getTabListDisplayName());
            cmd(bob, "setblock 4 64 4 minecraft:gold_block");
            check(lvl.getBlockState(new BlockPos(4, 64, 4)).isAir(), "гостю команды недоступны");
            cmd(bob, "pw kick Alice");
            check(alice.level() == lvl, "гость не может выгнать владельца");

            cmd(alice, "pw role Bob admin");
            check(lvl.world().roleOf(bob.getUUID()) == Role.ADMIN, "Bob теперь админ");
            check(bob.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "админ получил режим участника");
            cmd(bob, "setblock 4 64 4 minecraft:emerald_block");
            check(lvl.getBlockState(new BlockPos(4, 64, 4)).is(Blocks.EMERALD_BLOCK), "админ мира может /setblock");
            cmd(bob, "pw access add Alice guest");
            check(lvl.world().roleOf(alice.getUUID()) == Role.OWNER, "админ не может понизить владельца");

            cmd(alice, "pw leave");
            cmd(bob, "pw leave");
            check(alice.level().dimension() == Level.OVERWORLD, "Alice вернулась в основной мир");
            check(alice.getInventory().countItem(Items.DIAMOND) == 5, "алмазы вернулись");
            check(alice.getInventory().countItem(Items.STONE) == 0, "камень из личного мира не вынесен");
            check(alice.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "режим выживания восстановлен");
            check(bob.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "у Bob тоже выживание");
            check(alice.getTabListDisplayName() == null, "Tab: в основном мире метки нет");

            PersonalWorld w = lvl.world();
            ResourceKey<Level> key = lvl.dimension();
            WorldManager.get().unload(lvl, true);
            check(server.getLevel(key) == null, "мир выгружен");
            PersonalServerLevel re = WorldManager.get().getOrLoad(w);
            check(re != null && re != lvl, "мир загружен заново");
            check(re.getBlockState(new BlockPos(3, 64, 3)).is(Blocks.GOLD_BLOCK), "постройка сохранилась после выгрузки");
            check(re.getGameRules().getInt(GameRules.RULE_PLAYERS_SLEEPING_PERCENTAGE) == 42, "правила игры сохранились");
            long t = re.getDayTime() % 24000;
            check(t >= 18000 && t < 18200, "время сохранилось: " + t);

            cmd(alice, "pw home");
        });

        h.runAfterDelay(240, () -> {
            check(alice.level() instanceof PersonalServerLevel, "Alice снова в своём мире");
            check(alice.getInventory().countItem(Items.STONE) == 3, "инвентарь личных миров сохранился (камень)");
            check(alice.getInventory().countItem(Items.DIAMOND) == 0, "алмазы снова остались в основном мире");

            // Выход с сервера, стоя в личном мире, пока мир выгружен.
            PersonalServerLevel lvl = (PersonalServerLevel) alice.level();
            GameProfile profile = alice.getGameProfile();
            server.getPlayerList().remove(alice);
            WorldManager.get().unload(lvl, false);
            check(server.getLevel(lvl.dimension()) == null, "мир выгружен после выхода игрока");
            ServerPlayer again = mock(h, profile);
            check(again.level() instanceof PersonalServerLevel, "после перезахода игрок снова в личном мире (мир загрузился сам)");
            check(again.getInventory().countItem(Items.STONE) == 3, "после перезахода инвентарь личных миров на месте");

            // Смерть в личном мире.
            again.kill();
            ServerPlayer respawned = server.getPlayerList().respawn(again, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
            check(respawned.level() instanceof PersonalServerLevel, "после смерти возрождение в том же личном мире");
            check(respawned.getInventory().countItem(Items.STONE) == 3, "keepInventory личного мира сохранил вещи");
            check(respawned.gameMode.getGameModeForPlayer() == GameType.CREATIVE, "после смерти режим участника");
            cmd(respawned, "pw leave");
            check(respawned.level().dimension() == Level.OVERWORLD, "после смерти и выхода — основной мир");
            check(respawned.getInventory().countItem(Items.DIAMOND) == 5 && respawned.getInventory().countItem(Items.STONE) == 0,
                    "основной инвентарь цел после перезахода и смерти");
            check(respawned.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "выживание после всего");
            cmd(bob, "pw create void");
        });

        h.runAfterDelay(320, () -> {
            check(bob.level() instanceof PersonalServerLevel p && p.world().type == WorldType.VOID, "Bob в своём мире-пустоте");
            PersonalServerLevel lvl = (PersonalServerLevel) bob.level();
            check(lvl.getBlockState(new BlockPos(0, 63, 0)).is(Blocks.SMOOTH_STONE), "платформа на спавне");
            check(lvl.getBlockState(new BlockPos(40, 63, 40)).isAir(), "вокруг пустота");
            cmd(bob, "pw leave");
            cmd(bob, "pw reset normal");
            cmd(bob, "pw confirm");
        });

        h.runAfterDelay(500, () -> {
            check(bob.level() instanceof PersonalServerLevel p && p.world().type == WorldType.NORMAL, "Bob в пересозданном обычном мире");
            PersonalServerLevel lvl = (PersonalServerLevel) bob.level();
            check(lvl.getSeed() != server.overworld().getSeed(), "у обычного мира свой сид");
            check(!lvl.getBlockState(bob.blockPosition().below()).isAir() || bob.getY() > lvl.getMinBuildHeight(), "игрок на поверхности");
            cmd(bob, "pw leave");
            check(bob.level().dimension() == Level.OVERWORLD, "Bob вернулся");
            h.succeed();
        });
    }
}
