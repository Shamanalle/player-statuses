package com.kasper.playerstatuses.gametest;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.client.ClientAccess;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.TabListDecorator;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.level.gamerules.GameRules;

/** Small client calls of the in-game tests; a band with other ones has its own copy of this file. */
final class TestClient {

    static final UUID FAKE_UUID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final int FAKE_ENTITY_ID = -777;
    private static volatile CompletableFuture<?> reload;

    private TestClient() {
    }

    /** Switches the game's language; the resources reload after it. */
    static void language(Minecraft client, String code) {
        client.options.languageCode = code;
        client.getLanguageManager().setSelected(code);
        reload = client.reloadResourcePacks();
    }

    static boolean loaded(Minecraft client) {
        CompletableFuture<?> r = reload;
        return r == null || r.isDone();
    }

    /** A world where the player may use commands (cheats on). */
    static void allowCommands(WorldCreationUiState settings) {
        settings.setAllowCommands(true);
        settings.getGameRules().set(GameRules.RESPAWN_RADIUS, 0, null);
    }

    /** Sends a command as if typed in chat, without the slash. */
    static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    /** Sends the client's options (its language) to the server. */
    static void sendOptions(Minecraft client) {
        client.options.broadcastOptions();
    }

    /** Clears chat messages before taking store screenshots. */
    static void clearChat(Minecraft client) {
        ClientAccess.clearChat(client);
    }

    /** Spawns a second player in front of the camera for overhead status rendering and store screenshots. */
    static void spawnFakePlayer(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        client.player.setYRot(0f);
        client.player.setXRot(8f);
        client.player.yRotO = 0f;
        client.player.xRotO = 8f;
        client.player.yHeadRot = 0f;
        client.player.yHeadRotO = 0f;
        client.player.yBodyRot = 0f;
        client.player.yBodyRotO = 0f;

        if (client.level.getEntity(FAKE_ENTITY_ID) != null) {
            return;
        }
        GameProfile profile = new GameProfile(FAKE_UUID, "Alex");
        RemotePlayer fake = new RemotePlayer(client.level, profile);
        fake.setId(FAKE_ENTITY_ID);
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ() + 3.2;
        fake.setPos(x, y, z);
        fake.xo = x;
        fake.yo = y;
        fake.zo = z;
        fake.xOld = x;
        fake.yOld = y;
        fake.zOld = z;
        fake.setYRot(180f);
        fake.yRotO = 180f;
        fake.yHeadRot = 180f;
        fake.yHeadRotO = 180f;
        fake.yBodyRot = 180f;
        fake.yBodyRotO = 180f;
        client.level.addEntity(fake);
    }

    static void setFakeStatus(PlayerStatus status) {
        ClientSide.core().setStatus(FAKE_UUID, status, System.currentTimeMillis());
    }

    static void setOwnStatus(Minecraft client, PlayerStatus status) {
        if (client.player != null) {
            long now = System.currentTimeMillis();
            ClientSide.core().setStatus(client.player.getUUID(), status, now);
            TabListDecorator.tick(client, ClientSide.core(), now);
        }
    }

    static String ownTabName(Minecraft client) {
        if (client.player == null || client.getConnection() == null) {
            return "";
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (info == null || info.getTabListDisplayName() == null) {
            return "";
        }
        return info.getTabListDisplayName().getString();
    }

    static int overheadDraws() {
        return ClientSide.overheadDraws();
    }
}
