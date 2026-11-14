package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.HoloGeometry;
import com.kasper.playerstatuses.HoloState;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.VoiceChatState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.scores.Team;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The client half on Fabric, Forge and NeoForge: one {@link StatusClient}, input & screen tracking,
 * Tab list decoration, overhead visibility checks, and interactive 3D Holo-Projections.
 */
public final class ClientSide {

    public interface PacketSender {
        void send(byte[] data);
    }

    private static final int RELOAD_CHECK_TICKS = 40;

    private static final StatusClient CORE = new StatusClient();
    private static final AtomicInteger OVERHEAD_DRAWS = new AtomicInteger();
    private static final AtomicInteger HOLO_DRAWS = new AtomicInteger();
    private static final Map<UUID, Integer> HOVERED_SLOTS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_KNOCK_SENT_MS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> FALLBACK_SHOWN_SINCE = new ConcurrentHashMap<>();
    private static volatile Method profileNameMethod;
    private static volatile Class<?> profileNameOwner;
    private static Path configDir;
    private static PacketSender sender;
    private static KeyMapping settingsKey;
    private static KeyMapping holoPingKey;
    private static UUID aimedTarget;
    private static int aimedSlot = -1;
    private static boolean lastHoloRightClickDown;
    private static long lastPingSentMs;
    private static boolean started;
    private static boolean wasInWorld;
    private static Screen lastScreen;
    private static String lastChatText = "";
    private static double lastMouseX = Double.NaN;
    private static double lastMouseY = Double.NaN;
    private static int ticks;

    private ClientSide() {
    }

    public static void init(Path config) {
        configDir = config;
    }

    public static void useSender(PacketSender s) {
        sender = s;
    }

    public static void bindSettingsKey(KeyMapping key) {
        settingsKey = key;
    }

    public static void bindHoloPingKey(KeyMapping key) {
        holoPingKey = key;
    }

    public static synchronized StatusClient core() {
        if (!started) {
            started = true;
            if (configDir != null) {
                CORE.start(configDir.resolve(ClientSettings.FILE));
            }
        }
        return CORE;
    }

    public static void onServerMessage(byte[] data) {
        if (data == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && !mc.isSameThread()) {
            mc.execute(() -> handleServerMessage(mc, data));
        } else {
            handleServerMessage(mc, data);
        }
    }

    private static void handleServerMessage(Minecraft mc, byte[] data) {
        long now = System.currentTimeMillis();
        UUID localId = (mc != null && mc.player != null) ? mc.player.getUUID() : null;
        HoloState.Event ev = core().onServerMessageWithEvent(data, localId, now);
        if (ev != null && mc != null && localId != null && localId.equals(ev.target()) && mc.level != null) {
            ClientSettings settings = core().settings();
            if (Boolean.TRUE.equals(settings.get(ClientSettings.ALLOW_HOLO_PINGS))) {
                ClientAccess.showOverlayMessage(mc, HoloCapture.pingBannerComponent(mc, ev));
                if (Boolean.TRUE.equals(settings.get(ClientSettings.HOLO_SOUND)) && mc.getSoundManager() != null) {
                    float pitch = ev.action() == HoloState.Action.KNOCK_AFK ? 1.15f : 1.45f;
                    mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, pitch, 0.35f)
                    );
                }
            }
        }
    }

    private static void resetHoloState() {
        HOVERED_SLOTS.clear();
        LAST_KNOCK_SENT_MS.clear();
        FALLBACK_SHOWN_SINCE.clear();
        aimedTarget = null;
        aimedSlot = -1;
        lastHoloRightClickDown = false;
        lastPingSentMs = 0L;
        HoloCapture.resetPulse();
    }

    public static void tick(Minecraft mc) {
        StatusClient client = core();
        long now = System.currentTimeMillis();
        while (settingsKey != null && settingsKey.consumeClick()) {
            Screen current = ClientAccess.screen(mc);
            if (current == null) {
                ClientAccess.setScreen(mc, new SettingsScreen(null));
            }
        }
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            if (wasInWorld) {
                wasInWorld = false;
                client.reset(now);
                resetHoloState();
                TabListDecorator.clear();
            }
            return;
        }
        if (!wasInWorld) {
            wasInWorld = true;
            client.reset(now);
            resetHoloState();
            TabListDecorator.clear();
        }

        HoloScreenOverlay.attachIfNeeded(ClientAccess.screen(mc));
        trackInput(mc, client, now);

        Activity activity = ScreenDetector.detect(mc, client.settings().get(ClientSettings.IGNORED_SCREENS));
        boolean unfocused = !mc.isWindowActive();
        PlayerStatus own = client.composeOwn(
                activity,
                unfocused,
                VoiceChatState.isMicOff(),
                VoiceChatState.isSoundOff(),
                now
        );
        client.setStatus(mc.player.getUUID(), own, now);

        byte[] packet = client.pollSend(own, now);
        if (packet != null && sender != null) {
            sender.send(packet);
        }

        boolean sendProj = Boolean.TRUE.equals(client.settings().get(ClientSettings.SEND_PROJECTIONS));
        boolean sendSlots = Boolean.TRUE.equals(client.settings().get(ClientSettings.SEND_PROJECTION_SLOTS));
        HoloState.Projection ownProj = sendProj ? HoloCapture.capture(mc, own, sendSlots) : HoloState.Projection.EMPTY;
        client.setProjection(mc.player.getUUID(), ownProj, now);
        byte[] projPacket = client.pollProjectionSend(ownProj, now);
        if (projPacket != null && sender != null) {
            sender.send(projPacket);
        }

        updateHoloInteraction(mc, client, now);
        TabListDecorator.tick(mc, client, now);

        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            client.reloadIfChanged();
        }
    }

    private static void updateHoloInteraction(Minecraft mc, StatusClient client, long now) {
        HOVERED_SLOTS.clear();
        if (ClientAccess.screen(mc) != null
                || mc.player.isSpectator()
                || !Boolean.TRUE.equals(client.settings().get(ClientSettings.HOLO_PROJECTIONS))) {
            while (holoPingKey != null && holoPingKey.consumeClick()) {
                // drain presses buffered outside active world view
            }
            aimedTarget = null;
            aimedSlot = -1;
            lastHoloRightClickDown = false;
            return;
        }

        double eyeX = mc.player.getX();
        double eyeY = mc.player.getEyeY();
        double eyeZ = mc.player.getZ();
        double yawRad = Math.toRadians(mc.player.getYRot());
        double pitchRad = Math.toRadians(mc.player.getXRot());
        double cosP = Math.cos(pitchRad);
        double dirX = -Math.sin(yawRad) * cosP;
        double dirY = -Math.sin(pitchRad);
        double dirZ = Math.cos(yawRad) * cosP;

        double maxReach = 4.5;
        if (mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            double blockDist = Math.sqrt(mc.hitResult.getLocation().distanceToSqr(eyeX, eyeY, eyeZ));
            maxReach = Math.min(maxReach, blockDist);
        }

        double sFactor = Math.max(0.5, client.settings().get(ClientSettings.ICON_SCALE) / 100.0);
        double bestDist = maxReach;
        Player bestTarget = null;
        HoloGeometry.Hit bestHit = null;

        for (Player p : mc.level.players()) {
            if (p == mc.player || p.getUUID().equals(mc.player.getUUID())) {
                continue;
            }
            PlayerStatus st = client.statusOf(p.getUUID(), now);
            HoloState.Projection pr = holoToRender(p, st, now);
            if (pr.isNone()) {
                continue;
            }
            HoloGeometry.Basis basis = HoloGeometry.basisFor(p.getX(), p.getY(), p.getZ(), p.yBodyRot, p.isDiscrete());
            double sEyeX = basis.cx + (eyeX - basis.cx) / sFactor;
            double sEyeY = basis.cy + (eyeY - basis.cy) / sFactor;
            double sEyeZ = basis.cz + (eyeZ - basis.cz) / sFactor;
            HoloGeometry.Hit hit = HoloGeometry.raycast(basis, sEyeX, sEyeY, sEyeZ, dirX, dirY, dirZ, pr, maxReach / sFactor);
            if (hit != null) {
                double realDist = hit.distance * sFactor;
                if (realDist < bestDist && realDist <= maxReach) {
                    bestDist = realDist;
                    bestHit = hit;
                    bestTarget = p;
                }
            }
        }

        if (bestTarget != null && bestHit != null && bestHit.hoveredSlot >= 0) {
            aimedTarget = bestTarget.getUUID();
            aimedSlot = bestHit.hoveredSlot;
            HOVERED_SLOTS.put(bestTarget.getUUID(), Integer.valueOf(bestHit.hoveredSlot));
        } else {
            aimedTarget = null;
            aimedSlot = -1;
        }

        boolean keyTrig = false;
        while (holoPingKey != null && holoPingKey.consumeClick()) {
            keyTrig = true;
        }
        boolean useDown = mc.options.keyUse.isDown();
        boolean rightClickTrig = useDown && !lastHoloRightClickDown
                && (mc.player.getMainHandItem().isEmpty() || mc.options.keyShift.isDown());
        lastHoloRightClickDown = useDown;

        if ((keyTrig || rightClickTrig) && bestTarget != null && bestHit != null && bestHit.hoveredSlot >= 0 && sender != null) {
            UUID targetId = bestTarget.getUUID();
            HoloState.Action action = bestHit.hoveredSlot == HoloState.AFK_CALL_BUTTON_SLOT
                    ? HoloState.Action.KNOCK_AFK
                    : HoloState.Action.PING_SLOT;
            if (action == HoloState.Action.KNOCK_AFK) {
                Long prev = LAST_KNOCK_SENT_MS.get(targetId);
                if (prev != null && now >= prev.longValue() && now - prev.longValue() < 20_000L) {
                    return;
                }
                LAST_KNOCK_SENT_MS.put(targetId, Long.valueOf(now));
            } else {
                if (now >= lastPingSentMs && now - lastPingSentMs < 1_200L) {
                    return;
                }
                lastPingSentMs = now;
            }

            String itemKey = (action == HoloState.Action.PING_SLOT
                    && (mc.options.keyShift.isDown() || keyTrig))
                    ? HoloCapture.heldGhostHint(mc.player)
                    : "";
            sender.send(StatusMessages.holoInteract(
                    new HoloState.Interact(targetId, action, bestHit.hoveredSlot, itemKey)
            ));
            String targetName = bestTarget.getName().getString();
            ClientAccess.showOverlayMessage(
                    mc,
                    action == HoloState.Action.KNOCK_AFK
                            ? StatusText.holoKnockSent(targetName)
                            : StatusText.holoPingSent(targetName)
            );
            if (Boolean.TRUE.equals(client.settings().get(ClientSettings.HOLO_SOUND)) && mc.getSoundManager() != null) {
                mc.getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.65f, 0.18f)
                );
            }
        }
    }

    private static void trackInput(Minecraft mc, StatusClient client, long now) {
        client.idle().look(now, mc.player.getYRot(), mc.player.getXRot());
        if (mc.options.keyUp.isDown()
                || mc.options.keyDown.isDown()
                || mc.options.keyLeft.isDown()
                || mc.options.keyRight.isDown()
                || mc.options.keyJump.isDown()
                || mc.options.keyShift.isDown()
                || mc.options.keyAttack.isDown()
                || mc.options.keyUse.isDown()) {
            client.idle().input(now);
        }
        Screen screen = ClientAccess.screen(mc);
        if (screen != lastScreen) {
            lastScreen = screen;
            client.idle().input(now);
        }
        String chat = ScreenDetector.chatText(screen);
        if (!chat.equals(lastChatText)) {
            lastChatText = chat;
            client.idle().input(now);
        }
        double mx = mc.mouseHandler.xpos();
        double my = mc.mouseHandler.ypos();
        if (screen != null && (!Double.isNaN(lastMouseX) && (Math.abs(mx - lastMouseX) > 0.5 || Math.abs(my - lastMouseY) > 0.5))) {
            client.idle().input(now);
        }
        lastMouseX = mx;
        lastMouseY = my;
    }

    /**
     * Returns the status to draw above {@code target}, or {@link PlayerStatus#NONE} if hidden.
     */
    public static PlayerStatus statusToRender(Player target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || target == null) {
            return PlayerStatus.NONE;
        }
        StatusClient client = core();
        PlayerStatus status = client.statusOf(target.getUUID(), System.currentTimeMillis());
        if (status.isNone()) {
            return PlayerStatus.NONE;
        }
        boolean isSelf = target == mc.player || target.getUUID().equals(mc.player.getUUID());
        if (!isSelf && !isTeamNameVisible(target, mc.player)) {
            return PlayerStatus.NONE;
        }
        boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
        boolean invisible = !isSelf && target.isInvisibleTo(mc.player);
        boolean spectator = target.isSpectator();
        boolean hasPassengers = target.isVehicle();
        boolean sneaking = target.isDiscrete();
        double distSq = target.distanceToSqr(mc.player);
        if (!client.shouldShowOverhead(status, ClientAccess.hideGui(mc), isSelf, thirdPerson, invisible, spectator, hasPassengers, sneaking, distSq)) {
            return PlayerStatus.NONE;
        }
        return status;
    }

    /**
     * Returns the status to pose {@code target}'s arms for, or {@link PlayerStatus#NONE} if arm poses
     * are disabled or {@code target} should not be posed.
     */
    public static PlayerStatus statusForArmPose(Player target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || target == null) {
            return PlayerStatus.NONE;
        }
        StatusClient client = core();
        if (!client.settings().get(ClientSettings.ARM_POSES)) {
            return PlayerStatus.NONE;
        }
        boolean isSelf = target == mc.player || target.getUUID().equals(mc.player.getUUID());
        if (isSelf && mc.options.getCameraType().isFirstPerson()) {
            return PlayerStatus.NONE;
        }
        if ((!isSelf && target.isInvisibleTo(mc.player)) || target.isSpectator()) {
            return PlayerStatus.NONE;
        }
        int maxDist = client.settings().get(ClientSettings.VIEW_DISTANCE);
        if (target.distanceToSqr(mc.player) > (double) maxDist * maxDist) {
            return PlayerStatus.NONE;
        }
        return client.statusOf(target.getUUID(), System.currentTimeMillis());
    }

    public static boolean isTeamNameVisible(Player target, Player viewer) {
        Team team = target.getTeam();
        if (team == null) {
            return true;
        }
        Team viewerTeam = viewer != null ? viewer.getTeam() : null;
        Team.Visibility vis = team.getNameTagVisibility();
        if (vis == Team.Visibility.NEVER) {
            return false;
        }
        if (vis == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
            return viewerTeam != null && team.isAlliedTo(viewerTeam);
        }
        if (vis == Team.Visibility.HIDE_FOR_OWN_TEAM) {
            return viewerTeam == null || !team.isAlliedTo(viewerTeam);
        }
        return true;
    }

    public static void markOverheadDrawn() {
        OVERHEAD_DRAWS.incrementAndGet();
    }

    public static int overheadDraws() {
        return OVERHEAD_DRAWS.get();
    }

    public static HoloState.Projection holoToRender(Player target, PlayerStatus status, long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null || target == null || status == null || status.isNone()) {
            return HoloState.Projection.EMPTY;
        }
        StatusClient client = core();
        HoloState.Projection proj = client.projectionOf(target.getUUID(), status, now);
        if (proj.isNone()) {
            FALLBACK_SHOWN_SINCE.remove(target.getUUID());
            return HoloState.Projection.EMPTY;
        }
        boolean isSelf = target == mc.player || target.getUUID().equals(mc.player.getUUID());
        if (!isSelf && !isTeamNameVisible(target, mc.player)) {
            return HoloState.Projection.EMPTY;
        }
        boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
        boolean invisible = !isSelf && target.isInvisibleTo(mc.player);
        boolean spectator = target.isSpectator();
        boolean hasPassengers = target.isVehicle();
        boolean sneaking = target.isDiscrete();
        double distSq = target.distanceToSqr(mc.player);
        if (!client.shouldShowHolo(status, proj, ClientAccess.hideGui(mc), isSelf, thirdPerson, invisible, spectator, hasPassengers, sneaking, distSq)) {
            FALLBACK_SHOWN_SINCE.remove(target.getUUID());
            return HoloState.Projection.EMPTY;
        }
        return proj;
    }

    public static long projectionShownSince(UUID playerId, long now) {
        if (playerId == null) {
            return now;
        }
        long live = core().projectionShownSinceMs(playerId, -1L);
        if (live >= 0L) {
            FALLBACK_SHOWN_SINCE.remove(playerId);
            return live;
        }
        return FALLBACK_SHOWN_SINCE.computeIfAbsent(playerId, id -> Long.valueOf(now)).longValue();
    }

    public static int hoveredSlotFor(UUID playerId) {
        if (playerId == null) {
            return -1;
        }
        Integer slot = HOVERED_SLOTS.get(playerId);
        return slot == null ? -1 : slot.intValue();
    }

    public static HoloState.Event latestWorldEvent(UUID playerId, long now) {
        if (playerId == null) {
            return null;
        }
        java.util.List<HoloState.Event> events = core().activeWorldEvents(playerId, now);
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    public static float holoWobbleRad(UUID playerId, long now) {
        if (playerId == null) {
            return 0.0f;
        }
        java.util.List<HoloState.Event> events = core().activeWorldEvents(playerId, now);
        for (int i = events.size() - 1; i >= 0; i--) {
            HoloState.Event ev = events.get(i);
            if (ev.action() == HoloState.Action.KNOCK_AFK) {
                return HoloGeometry.wobbleAngleRad(Math.max(0L, now - ev.createdAtMs()));
            }
        }
        return 0.0f;
    }

    public static UUID aimedTarget() {
        return aimedTarget;
    }

    public static int aimedSlot() {
        return aimedSlot;
    }

    public static void markHoloDrawn() {
        HOLO_DRAWS.incrementAndGet();
    }

    public static int holoDraws() {
        return HOLO_DRAWS.get();
    }

    public static String playerName(Minecraft mc, UUID id) {
        if (id == null) {
            return "?";
        }
        if (mc != null && mc.level != null) {
            Player p = mc.level.getPlayerByUUID(id);
            if (p != null) {
                return p.getName().getString();
            }
        }
        if (mc != null && mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                Component display = info.getTabListDisplayName();
                if (display != null && !display.getString().isEmpty()) {
                    return display.getString();
                }
                Object profile = info.getProfile();
                if (profile != null) {
                    String resolved = readProfileName(profile);
                    if (resolved != null && !resolved.isEmpty()) {
                        return resolved;
                    }
                }
            }
        }
        return "?";
    }

    private static String readProfileName(Object profile) {
        try {
            Class<?> cls = profile.getClass();
            Method m = profileNameMethod;
            if (m == null || profileNameOwner != cls) {
                try {
                    m = cls.getMethod("name");
                } catch (NoSuchMethodException ignored) {
                    m = cls.getMethod("getName");
                }
                m.setAccessible(true);
                profileNameMethod = m;
                profileNameOwner = cls;
            }
            Object result = m.invoke(profile);
            return result instanceof String ? (String) result : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
