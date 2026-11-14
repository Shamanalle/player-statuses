package com.kasper.playerstatuses;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Idle detection, the send limiter, privacy filters and texts of the client. */
class ClientLogicTest {

    @Test
    void idleTrackerCountsTurnsAndInputs() {
        IdleTracker t = new IdleTracker(0);
        assertFalse(t.look(1000, 10, 5));
        assertEquals(1000, t.idleMillis(1000));
        assertTrue(t.look(2000, 11, 5));
        assertEquals(0, t.idleMillis(2000));
        assertFalse(t.look(200_000, 11, 5));
        assertTrue(t.isAfk(200_000, 3));
        assertFalse(t.isAfk(200_000, 0));
        t.input(200_001);
        assertFalse(t.isAfk(200_001, 3));

        // Backward wall-clock jump (e.g. NTP step) must not pin idle state permanently
        t.input(1_000_000L);
        assertEquals(0L, t.idleMillis(500_000L));
        t.input(500_000L);
        assertEquals(5_000L, t.idleMillis(505_000L));
    }

    @Test
    void limiterSendsChangesOnlyAndNotTooOften() {
        SendLimiter l = new SendLimiter(250);
        assertEquals(PlayerStatus.NONE, l.poll(PlayerStatus.NONE, 0), "the first poll always sends");
        assertNull(l.poll(PlayerStatus.NONE, 1000));
        assertEquals(PlayerStatus.of(Activity.INVENTORY), l.poll(PlayerStatus.of(Activity.INVENTORY), 1000));
        assertNull(l.poll(PlayerStatus.NONE, 1100), "too soon");
        assertEquals(PlayerStatus.NONE, l.poll(PlayerStatus.NONE, 1250));
        // Idle seconds alone are no reason to send
        PlayerStatus afk = new PlayerStatus(Activity.NONE, StatusFlag.AFK.bit(), 180);
        assertEquals(afk, l.poll(afk, 2000));
        assertNull(l.poll(afk.withIdleSeconds(181), 3000));
        // Backward clock jump does not block new status updates
        assertEquals(PlayerStatus.of(Activity.TYPING), l.poll(PlayerStatus.of(Activity.TYPING), 500));
        l.reset();
        assertNotNull(l.poll(afk, 3001));
    }

    @Test
    void privacyTakesCategoriesOut() {
        ClientSettings s = new ClientSettings();
        s.set(ClientSettings.send(Category.INVENTORY), false);
        s.set(ClientSettings.send(Category.VOICE), false);
        PlayerStatus inInventory = PlayerStatus.of(Activity.INVENTORY, StatusFlag.AFK, StatusFlag.MIC_OFF);
        assertEquals(PlayerStatus.of(Activity.NONE, StatusFlag.AFK), s.outgoing(inInventory));
        assertEquals(PlayerStatus.of(Activity.CONTAINER), s.outgoing(PlayerStatus.of(Activity.CONTAINER)));
        s.set(ClientSettings.show(Category.TYPING), false);
        assertTrue(s.incoming(PlayerStatus.of(Activity.COMMAND)).isNone());
    }

    @Test
    void idleText() {
        assertEquals("", IdleText.duration(59));
        assertEquals("12m", IdleText.duration(12 * 60 + 30));
        assertEquals("1h 05m", IdleText.duration(3900));
        assertEquals("2d 3h", IdleText.duration((48 + 3) * 3600 + 59));
    }

    @Test
    void modScreens() {
        assertEquals(Activity.NONE, ScreenRules.modScreen("io.github.kosmx.emotes.main.screen.EmoteMenu", ScreenRules.DEFAULT_IGNORED));
        assertEquals(Activity.MAP, ScreenRules.modScreen("xaero.map.gui.GuiMap", ScreenRules.DEFAULT_IGNORED));
        assertEquals(Activity.BUSY, ScreenRules.modScreen("mezz.jei.gui.recipes.RecipesGui", ScreenRules.DEFAULT_IGNORED));
        // A prefix is a package or a class, not the start of any name
        assertFalse(ScreenRules.matches("journeymapper.Screen", Arrays.asList("journeymap")));
        assertTrue(ScreenRules.matches("com.example.Menu$Inner", Arrays.asList("com.example.Menu")));
        assertTrue(ScreenRules.matches("com.example.ui.Menu", Arrays.asList("com.example.")));
    }

    @Test
    void statusClientCountsIdleAndChecksOverheadVisibility() {
        StatusClient client = new StatusClient();
        java.util.UUID id = java.util.UUID.randomUUID();
        byte[] packet = StatusMessages.statuses(java.util.Collections.singletonList(
                new StatusMessages.Entry(id, new PlayerStatus(Activity.INVENTORY, StatusFlag.AFK.bit(), 180))
        ));
        assertTrue(client.onServerMessage(packet, 10_000L));
        PlayerStatus atLater = client.statusOf(id, 70_000L);
        assertEquals(Activity.INVENTORY, atLater.activity());
        assertTrue(atLater.has(StatusFlag.AFK));
        assertEquals(240, atLater.idleSeconds());

        assertTrue(client.shouldShowOverhead(atLater, false, false, false, false, false, false, false, 16 * 16));
        assertFalse(client.shouldShowOverhead(atLater, true, false, false, false, false, false, false, 16 * 16), "F1 hides GUI");
        assertFalse(client.shouldShowOverhead(atLater, false, false, false, true, false, false, false, 16 * 16), "invisible hides icon");
        assertFalse(client.shouldShowOverhead(atLater, false, false, false, false, false, true, false, 16 * 16), "riding hides icon");
        assertFalse(client.shouldShowOverhead(atLater, false, false, false, false, false, false, true, 16 * 16), "sneaking hides icon by default");
        assertFalse(client.shouldShowOverhead(atLater, false, true, true, false, false, false, false, 0), "self off by default");
        client.settings().set(ClientSettings.SHOW_OWN, true);
        assertTrue(client.shouldShowOverhead(atLater, false, true, true, false, false, false, false, 0), "self in 3rd person when enabled");
        assertFalse(client.shouldShowOverhead(atLater, false, true, false, false, false, false, false, 0), "never in 1st person");
    }

    @Test
    void statusIconsRenderAllActivitiesAndFlags() {
        for (Activity a : Activity.values()) {
            assertNotNull(StatusIcon.sprite(a));
        }
        for (StatusFlag f : StatusFlag.values()) {
            assertNotNull(StatusIcon.sprite(f));
        }
        java.util.List<int[]> rects = new java.util.ArrayList<>();
        PlayerStatus status = PlayerStatus.of(Activity.TYPING, StatusFlag.AFK, StatusFlag.UNFOCUSED);
        StatusIcon.CardLayout layout = StatusIcon.drawCard(
                (x0, y0, x1, y1, argb) -> rects.add(new int[]{(int) x0, (int) y0, (int) x1, (int) y1, argb}),
                status,
                36,
                90,
                500L
        );
        assertTrue(layout.width() > 50);
        assertEquals(2, layout.extraFlags().size());
        assertFalse(rects.isEmpty());

        // When sound is deafened, mic-off is implied and visually deduplicated on the badge
        PlayerStatus deafened = PlayerStatus.of(Activity.NONE, StatusFlag.MIC_OFF, StatusFlag.SOUND_OFF);
        StatusIcon.CardLayout deafLayout = StatusIcon.layout(deafened, 28, 90);
        assertEquals(StatusIcon.sprite(StatusFlag.SOUND_OFF).accentRgb(), deafLayout.accentRgb());
        assertTrue(deafLayout.extraFlags().isEmpty());
    }

    @Test
    void armPosesForActivitiesAndHandedness() {
        assertTrue(StatusArmPose.forStatus(PlayerStatus.NONE, true, 0f).isDefault());
        assertTrue(StatusArmPose.forStatus(PlayerStatus.of(Activity.LOADING), true, 0f).isDefault());

        StatusArmPose.Pair inv = StatusArmPose.forStatus(PlayerStatus.of(Activity.INVENTORY), true, 0f);
        assertEquals(StatusArmPose.HIGH, inv.left());
        assertEquals(StatusArmPose.HIGH, inv.right());

        StatusArmPose.Pair chestRight = StatusArmPose.forStatus(PlayerStatus.of(Activity.CONTAINER), true, 0f);
        assertEquals(StatusArmPose.LOW, chestRight.left());
        assertEquals(StatusArmPose.HIGH, chestRight.right());

        StatusArmPose.Pair chestLeft = StatusArmPose.forStatus(PlayerStatus.of(Activity.CONTAINER), false, 0f);
        assertEquals(StatusArmPose.HIGH, chestLeft.left());
        assertEquals(StatusArmPose.LOW, chestLeft.right());

        // Typing alternates every 2 ticks unless AFK or unfocused
        StatusArmPose.Pair t0 = StatusArmPose.forStatus(PlayerStatus.of(Activity.TYPING), true, 0f);
        StatusArmPose.Pair t2 = StatusArmPose.forStatus(PlayerStatus.of(Activity.TYPING), true, 2f);
        assertNotEquals(t0.right(), t2.right());
        assertNotEquals(t0.left(), t2.left());

        StatusArmPose.Pair tAfk0 = StatusArmPose.forStatus(PlayerStatus.of(Activity.TYPING, StatusFlag.AFK), true, 0f);
        StatusArmPose.Pair tAfk2 = StatusArmPose.forStatus(PlayerStatus.of(Activity.TYPING, StatusFlag.AFK), true, 2f);
        assertEquals(tAfk0.right(), tAfk2.right());
    }

    @Test
    void simpleVoiceChatPluginUpdatesVoiceChatState() {
        PlayerStatusesVoiceChatPlugin plugin = new PlayerStatusesVoiceChatPlugin();
        assertEquals("playerstatuses", plugin.getPluginId());
        boolean[] state = {false, false, false}; // muted, disabled, disconnected
        de.maxhenkel.voicechat.api.VoicechatClientApi api = new de.maxhenkel.voicechat.api.VoicechatClientApi() {
            @Override
            public boolean isMuted() {
                return state[0];
            }

            @Override
            public boolean isDisabled() {
                return state[1];
            }

            @Override
            public boolean isDisconnected() {
                return state[2];
            }
        };
        java.util.Map<Class<?>, java.util.function.Consumer<Object>> handlers = new java.util.HashMap<>();
        plugin.initialize(api);
        plugin.registerEvents(new de.maxhenkel.voicechat.api.events.EventRegistration() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <T extends de.maxhenkel.voicechat.api.events.Event> void registerEvent(
                    Class<T> eventClass,
                    java.util.function.Consumer<T> onEvent,
                    int priority
            ) {
                handlers.put(eventClass, (java.util.function.Consumer) onEvent);
            }
        });

        // Not connected yet: no flags
        state[0] = true;
        assertFalse(VoiceChatState.isMicOff());
        assertFalse(VoiceChatState.isSoundOff());

        // Connect
        handlers.get(de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent.class).accept(
                new de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent() {
                    @Override
                    public boolean isConnected() {
                        return true;
                    }

                    @Override
                    public de.maxhenkel.voicechat.api.VoicechatClientApi getVoicechat() {
                        return api;
                    }
                }
        );
        assertTrue(VoiceChatState.isMicOff());
        assertFalse(VoiceChatState.isSoundOff());

        // Disable sound -> both micOff and soundOff
        state[0] = false;
        state[1] = true;
        assertTrue(VoiceChatState.isMicOff());
        assertTrue(VoiceChatState.isSoundOff());

        // Disconnect -> both false
        state[2] = true;
        assertFalse(VoiceChatState.isMicOff());
        assertFalse(VoiceChatState.isSoundOff());
        VoiceChatState.bind(null, null);
    }

    @Test
    void holoProjectionRateLimitingPrivacyAndEvents() {
        StatusClient client = new StatusClient();
        java.util.UUID peer = new java.util.UUID(3, 3);
        java.util.UUID local = new java.util.UUID(4, 4);

        HoloState.Projection first = new HoloState.Projection(
                HoloState.LayoutKind.CRAFTING, new byte[]{1, 0, 3, 4}, 0.2f, 0.4f, 1, 0, 0);
        byte[] sent1 = client.pollProjectionSend(first, 1000L);
        assertNotNull(sent1);
        assertEquals(first, StatusMessages.readProjectionUpdate(sent1));

        // Sub-2.5% cursor jitter inside rate window sends nothing
        assertNull(client.pollProjectionSend(first.withCursor(0.21f, 0.41f), 1020L));
        // Within 100ms holds even larger cursor move until interval elapses
        assertNull(client.pollProjectionSend(first.withCursor(0.8f, 0.8f), 1050L));
        assertNotNull(client.pollProjectionSend(first.withCursor(0.8f, 0.8f), 1100L));
        // Closing sends immediately
        assertEquals(HoloState.Projection.EMPTY,
                StatusMessages.readProjectionUpdate(client.pollProjectionSend(HoloState.Projection.EMPTY, 1110L)));

        // Turning off SEND_PROJECTION_SLOTS strips slot silhouettes
        client.settings().set(ClientSettings.SEND_PROJECTION_SLOTS, false);
        byte[] stripped = client.pollProjectionSend(first, 1500L);
        assertNotNull(stripped);
        assertEquals(0, StatusMessages.readProjectionUpdate(stripped).slotCount());

        // Fallback procedural layout for unmodded peers + shouldShowHolo distance check
        PlayerStatus peerStatus = PlayerStatus.of(Activity.CONTAINER);
        client.setStatus(peer, peerStatus, 2000L);
        HoloState.Projection fallback = client.projectionOf(peer, peerStatus, 2000L);
        assertEquals(HoloState.LayoutKind.CONTAINER_27, fallback.layout());
        assertTrue(client.shouldShowHolo(peerStatus, fallback, false, false, false, false, false, false, false, 6 * 6));
        assertFalse(client.shouldShowHolo(peerStatus, fallback, false, false, false, false, false, false, false, 12 * 12));

        // Local ping event filtering against ALLOW_HOLO_PINGS
        byte[] eventBytes = StatusMessages.holoEvent(new HoloState.Event(
                peer, local, HoloState.Action.PING_SLOT, 5, "item.minecraft.iron_ingot", 3000L));
        assertNotNull(client.onServerMessageWithEvent(eventBytes, local, 3000L));
        assertEquals(1, client.activeLocalScreenPings(3100L).size());

        client.settings().set(ClientSettings.ALLOW_HOLO_PINGS, false);
        assertNull(client.onServerMessageWithEvent(eventBytes, local, 3500L));
    }
}
