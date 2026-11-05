package com.kasper.playerstatuses.client;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusClient;
import com.kasper.playerstatuses.VoiceChatState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.player.Player;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The client half on Fabric, Forge and NeoForge: one {@link StatusClient}, input & screen tracking,
 * Tab list decoration and overhead visibility checks.
 */
public final class ClientSide {

    public interface PacketSender {
        void send(byte[] data);
    }

    private static final int RELOAD_CHECK_TICKS = 40;

    private static final StatusClient CORE = new StatusClient();
    private static final AtomicInteger OVERHEAD_DRAWS = new AtomicInteger();
    private static Path configDir;
    private static PacketSender sender;
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
        if (data != null) {
            core().onServerMessage(data, System.currentTimeMillis());
        }
    }

    public static void tick(Minecraft mc) {
        StatusClient client = core();
        long now = System.currentTimeMillis();
        if (mc.level == null || mc.player == null || mc.getConnection() == null) {
            if (wasInWorld) {
                wasInWorld = false;
                client.reset(now);
                TabListDecorator.clear();
            }
            return;
        }
        if (!wasInWorld) {
            wasInWorld = true;
            client.reset(now);
            TabListDecorator.clear();
        }

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

        TabListDecorator.tick(mc, client, now);

        if (++ticks % RELOAD_CHECK_TICKS == 0) {
            client.reloadIfChanged();
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
        boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
        boolean invisible = !isSelf && target.isInvisibleTo(mc.player);
        boolean spectator = target.isSpectator();
        boolean riding = target.isPassenger() || target.isVehicle();
        boolean sneaking = target.isDiscrete();
        double distSq = target.distanceToSqr(mc.player);
        if (!client.shouldShowOverhead(status, ClientAccess.hideGui(mc), isSelf, thirdPerson, invisible, spectator, riding, sneaking, distSq)) {
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
        return client.statusOf(target.getUUID(), System.currentTimeMillis());
    }

    public static void markOverheadDrawn() {
        OVERHEAD_DRAWS.incrementAndGet();
    }

    public static int overheadDraws() {
        return OVERHEAD_DRAWS.get();
    }
}
