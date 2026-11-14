package com.kasper.playerstatuses.gametest;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusFlag;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * Runs {@code /playerstatuses} and {@code /afk} as a player in a real client, in English and in
 * Russian, spawns a second player in front of the camera to verify overhead status cards and Tab
 * list indicators, and takes 3840x2160 {@code store-*} screenshots.
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

            // Switch back to English for store screenshots and visual / Tab assertions
            context.runOnClient(client -> TestClient.language(client, "en_us"));
            context.waitTicks(5);
            context.waitFor(TestClient::loaded, 1200);
            context.waitTicks(40);
            context.runOnClient(TestClient::prepareScene);
            context.waitFor(TestClient::sceneReady, 400);
            context.waitTicks(30);
            context.runOnClient(client -> {
                TestClient.clearChat(client);
                TestClient.spawnFakePlayer(client);
            });
            context.getInput().resizeWindow(3840, 2160);
            context.waitTicks(15);

            storeScreenshot(context, "store-typing", new PlayerStatus(Activity.TYPING, 0, 0));
            storeScreenshot(context, "store-inventory", new PlayerStatus(Activity.INVENTORY, 0, 0));
            storeScreenshot(context, "store-container", new PlayerStatus(Activity.CONTAINER, 0, 0));
            storeScreenshot(context, "store-crafting", new PlayerStatus(Activity.CRAFTING, 0, 0));
            storeScreenshot(context, "store-afk", new PlayerStatus(Activity.NONE, StatusFlag.AFK.bit() | StatusFlag.UNFOCUSED.bit(), 120));

            // Verify 3D analytical raycast hits close-range hologram chest panel
            context.runOnClient(client -> {
                TestClient.setFakeStatus(new PlayerStatus(Activity.CRAFTING, 0, 0));
                TestClient.aimAtFakeHolo(client);
            });
            context.waitTicks(4);
            java.util.UUID aimed = context.computeOnClient(client -> TestClient.aimedHoloTarget());
            if (!TestClient.FAKE_UUID.equals(aimed)) {
                throw new AssertionError("Expected 3D holo raycast target " + TestClient.FAKE_UUID + ", got " + aimed);
            }
            context.runOnClient(TestClient::restoreScreenshotCamera);

            // Verify Tab list decoration
            String tab = context.computeOnClient(client -> {
                TestClient.setOwnStatus(client, new PlayerStatus(Activity.CRAFTING, StatusFlag.AFK.bit(), 120));
                return TestClient.ownTabName(client);
            });
            if (!tab.contains("Crafting") || !tab.contains("2m") || tab.contains("gui.playerstatuses.")) {
                throw new AssertionError("Unexpected Tab list display name: " + tab);
            }
            context.runOnClient(client -> TestClient.setOwnStatus(client, PlayerStatus.NONE));

            // Verify client SettingsScreen renders and is detected as Activity.SETTINGS
            context.runOnClient(client -> TestClient.setFakeStatus(new PlayerStatus(Activity.TYPING, 0, 0)));
            Activity screenActivity = context.computeOnClient(TestClient::openSettingsScreen);
            if (screenActivity != Activity.SETTINGS) {
                throw new AssertionError("Expected Activity.SETTINGS for SettingsScreen, got " + screenActivity);
            }
            context.waitTicks(8);
            context.takeScreenshot(
                TestScreenshotOptions.of("store-settings").disableCounterPrefix().withSize(3840, 2160)
            );
            context.runOnClient(TestClient::closeScreen);

            // Verify server-side VanillaBridge (TextDisplay + Tab list for vanilla clients)
            if (!context.computeOnClient(TestClient::verifyVanillaBridge)) {
                throw new AssertionError("VanillaBridge did not spawn and clean up overhead TextDisplay");
            }
        }
    }

    private static void storeScreenshot(ClientGameTestContext context, String name, PlayerStatus status) {
        int[] before = context.computeOnClient(client -> {
            TestClient.setFakeStatus(status);
            return new int[]{TestClient.overheadDraws(), TestClient.holoDraws()};
        });
        context.waitTicks(5);
        context.takeScreenshot(
            TestScreenshotOptions.of(name).disableCounterPrefix().withSize(3840, 2160)
        );
        int[] after = context.computeOnClient(client -> new int[]{TestClient.overheadDraws(), TestClient.holoDraws()});
        if (after[0] <= before[0]) {
            throw new AssertionError("Overhead status card was not rendered for " + name + " (" + status + ")");
        }
        if (after[1] <= before[1]) {
            throw new AssertionError("3D holo-projection was not rendered for " + name + " (" + status + ")");
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
