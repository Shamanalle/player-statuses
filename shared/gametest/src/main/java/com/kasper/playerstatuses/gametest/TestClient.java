package com.kasper.playerstatuses.gametest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;

import java.util.concurrent.CompletableFuture;

/** Small client calls of the in-game tests; a band with other ones has its own copy of this file. */
final class TestClient {

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
    }

    /** Sends a command as if typed in chat, without the slash. */
    static void command(Minecraft client, String command) {
        client.player.connection.sendCommand(command);
    }

    /** Sends the client's options (its language) to the server. */
    static void sendOptions(Minecraft client) {
        client.options.broadcastOptions();
    }
}
