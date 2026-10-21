package com.kasper.playerstatuses.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs {@code /playerstatuses} and {@code /afk} as a player in a real client, in English and in
 * Russian, and takes a screenshot of the chat after each (uploaded by CI). Fails on a text key that
 * was not translated, on a missing reply and on an unknown command.
 */
public class CommandsGameTest implements FabricClientGameTest {

    private static final String[] LANGUAGES = {"en_us", "ru_ru"};
    private static final List<String> RECEIVED = new CopyOnWriteArrayList<>();

    @Override
    public void runTest(ClientGameTestContext context) {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> RECEIVED.add(message.getString()));
        try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(TestClient::allowCommands).create()) {
            context.waitTicks(40);
            for (String language : LANGUAGES) {
                context.runOnClient(client -> TestClient.language(client, language));
                context.waitTicks(5);
                context.waitFor(TestClient::loaded, 1200);
                // The server answers in the player's language: tell it the new one
                context.runOnClient(TestClient::sendOptions);
                context.waitTicks(60);
                run(context, language, "status", "playerstatuses", language.equals("en_us") ? "Players with the mod" : "Игроки с модом");
                run(context, language, "afk", "afk", language.equals("en_us") ? "You are AFK now" : "Теперь вы АФК");
                run(context, language, "back", "afk", language.equals("en_us") ? "Welcome back" : "С возвращением");
            }
        }
    }

    private static void run(ClientGameTestContext context, String language, String name, String command, String expected) {
        RECEIVED.clear();
        context.runOnClient(client -> TestClient.command(client, command));
        context.waitTicks(20);
        context.takeScreenshot("commands-" + name + "-" + language);
        if (RECEIVED.isEmpty()) {
            throw new AssertionError("No reply to /" + command + " (" + language + ")");
        }
        for (String line : RECEIVED) {
            if (line.contains("command.playerstatuses.") || line.contains("gui.playerstatuses.")) {
                throw new AssertionError("Untranslated text in " + language + ": " + line);
            }
            if (line.contains("Unknown or incomplete command") || line.contains("Неизвестная или неполная команда")) {
                throw new AssertionError("Command not found (" + language + "): " + line);
            }
        }
        if (RECEIVED.stream().noneMatch(l -> l.contains(expected))) {
            throw new AssertionError("/" + command + " (" + language + ") did not answer \"" + expected + "\": " + RECEIVED);
        }
    }
}
