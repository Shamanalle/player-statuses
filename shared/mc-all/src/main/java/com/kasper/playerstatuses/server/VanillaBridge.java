package com.kasper.playerstatuses.server;

import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.ServerSettings;
import com.kasper.playerstatuses.StatusCommands;
import com.kasper.playerstatuses.StatusRelay;
import com.kasper.playerstatuses.StatusServer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Displays statuses to vanilla clients on a Fabric, Forge or NeoForge server via a {@link Display.TextDisplay}
 * above each player's head and localized status suffixes in the Tab list.
 */
public final class VanillaBridge {

    public static final String OVERHEAD_TAG = "playerstatuses.overhead";
    private static final String TAB_INSERTION_TAG = "playerstatuses";
    private static final int JOIN_GRACE_TICKS = 20;

    private static final Method SET_TEXT = findMethod(Display.TextDisplay.class, Component.class);
    private static final Method SET_BILLBOARD = findMethod(Display.class, Display.BillboardConstraints.class);
    private static final Field PACKET_ENTRIES = findEntriesField();

    private static final class OverheadEntry {
        final Display.TextDisplay display;
        String lastText;
        final Set<UUID> hiddenFrom = new HashSet<>();

        OverheadEntry(Display.TextDisplay display, String lastText) {
            this.display = display;
            this.lastText = lastText;
        }
    }

    private static final Map<UUID, OverheadEntry> OVERHEADS = new HashMap<>();
    private static final Map<UUID, Map<UUID, String>> SENT_TAB_NAMES = new HashMap<>();
    private static int tickCounter;

    private VanillaBridge() {
    }

    public static void tick(MinecraftServer server, StatusServer core, long now) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        StatusRelay relay = core.relay();
        ServerSettings settings = core.settings();

        boolean hasVanillaViewer = false;
        for (ServerPlayer p : players) {
            if (!relay.hasMod(p.getUUID()) && p.tickCount >= JOIN_GRACE_TICKS) {
                hasVanillaViewer = true;
                break;
            }
        }

        tickOverheads(players, relay, settings, hasVanillaViewer, now);
        tickTabList(players, relay, settings, now);

        int count = ++tickCounter;
        if (count % 20 == 0) {
            for (OverheadEntry e : OVERHEADS.values()) {
                e.hiddenFrom.clear();
            }
        }
        if (count % 100 == 1) {
            purgeOrphansAround(players);
        }
    }

    public static void removePlayer(UUID playerId) {
        OverheadEntry entry = OVERHEADS.remove(playerId);
        if (entry != null && !entry.display.isRemoved()) {
            entry.display.discard();
        }
        for (OverheadEntry e : OVERHEADS.values()) {
            e.hiddenFrom.remove(playerId);
        }
        SENT_TAB_NAMES.remove(playerId);
        for (Map<UUID, String> map : SENT_TAB_NAMES.values()) {
            map.remove(playerId);
        }
    }

    public static void clearAll() {
        for (OverheadEntry entry : OVERHEADS.values()) {
            if (!entry.display.isRemoved()) {
                entry.display.discard();
            }
        }
        OVERHEADS.clear();
        SENT_TAB_NAMES.clear();
    }

    public static int activeOverheadCount() {
        return OVERHEADS.size();
    }

    private static void tickOverheads(
            List<ServerPlayer> players,
            StatusRelay relay,
            ServerSettings settings,
            boolean hasVanillaViewer,
            long now
    ) {
        boolean enabled = settings.get(ServerSettings.VANILLA_OVERHEAD) && hasVanillaViewer && SET_TEXT != null;
        if (!enabled) {
            if (!OVERHEADS.isEmpty()) {
                for (OverheadEntry entry : OVERHEADS.values()) {
                    if (!entry.display.isRemoved()) {
                        entry.display.discard();
                    }
                }
                OVERHEADS.clear();
            }
            return;
        }

        String defaultLang = settings.get(ServerSettings.LANGUAGE);
        Set<UUID> onlineIds = new HashSet<>(players.size());

        for (ServerPlayer target : players) {
            UUID id = target.getUUID();
            onlineIds.add(id);
            PlayerStatus status = relay.current(id, now);
            Team targetTeam = target.getTeam();
            boolean teamAllowsAny = targetTeam == null || targetTeam.getNameTagVisibility() != Team.Visibility.NEVER;
            boolean show = !status.isNone()
                    && teamAllowsAny
                    && target.isAlive()
                    && !target.isSpectator()
                    && !target.isInvisible()
                    && !target.isDiscrete()
                    && !target.isVehicle();

            OverheadEntry entry = OVERHEADS.get(id);
            if (!show) {
                if (entry != null) {
                    if (!entry.display.isRemoved()) {
                        entry.display.discard();
                    }
                    OVERHEADS.remove(id);
                }
                continue;
            }

            String text = StatusCommands.describe(defaultLang, status);
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() + 0.55;
            double z = target.getZ();

            if (entry == null || entry.display.isRemoved() || entry.display.level() != target.level()) {
                if (entry != null && !entry.display.isRemoved()) {
                    entry.display.discard();
                }
                EntityType<?> type = findTextDisplayType();
                if (type == null) {
                    return;
                }
                Display.TextDisplay display = new Display.TextDisplay(type, target.level());
                display.setPos(x, y, z);
                display.addTag(OVERHEAD_TAG);
                configureDisplay(display, text);
                target.level().addFreshEntity(display);
                entry = new OverheadEntry(display, text);
                OVERHEADS.put(id, entry);
            } else {
                entry.display.setPos(x, y, z);
                if (!text.equals(entry.lastText)) {
                    entry.lastText = text;
                    setDisplayText(entry.display, text);
                }
            }

            for (ServerPlayer viewer : players) {
                boolean hide = viewer == target
                        || relay.hasMod(viewer.getUUID())
                        || !canViewerSeeNameTag(targetTeam, viewer.getTeam());
                if (hide && viewer.level() == target.level() && entry.hiddenFrom.add(viewer.getUUID())) {
                    viewer.connection.send(new ClientboundRemoveEntitiesPacket(entry.display.getId()));
                }
            }
        }

        for (Iterator<Map.Entry<UUID, OverheadEntry>> it = OVERHEADS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, OverheadEntry> me = it.next();
            if (!onlineIds.contains(me.getKey())) {
                if (!me.getValue().display.isRemoved()) {
                    me.getValue().display.discard();
                }
                it.remove();
            }
        }
    }

    private static void purgeOrphansAround(List<ServerPlayer> players) {
        if (players.isEmpty()) {
            return;
        }
        Set<Entity> active = new HashSet<>(OVERHEADS.size());
        for (OverheadEntry e : OVERHEADS.values()) {
            active.add(e.display);
        }
        List<Entity> orphans = new ArrayList<>();
        for (ServerPlayer p : players) {
            List<Entity> nearby = p.level().getEntities((Entity) null, p.getBoundingBox().inflate(24.0),
                    entity -> entity != null && !active.contains(entity) && !entity.isRemoved()
                            && entity.removeTag(OVERHEAD_TAG));
            orphans.addAll(nearby);
        }
        for (Entity orphan : orphans) {
            if (!orphan.isRemoved()) {
                orphan.discard();
            }
        }
    }

    private static boolean canViewerSeeNameTag(Team targetTeam, Team viewerTeam) {
        if (targetTeam == null) {
            return true;
        }
        Team.Visibility vis = targetTeam.getNameTagVisibility();
        if (vis == Team.Visibility.ALWAYS) {
            return true;
        }
        if (vis == Team.Visibility.NEVER) {
            return false;
        }
        boolean sameTeam = viewerTeam != null && targetTeam.isAlliedTo(viewerTeam);
        if (vis == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
            return sameTeam;
        }
        if (vis == Team.Visibility.HIDE_FOR_OWN_TEAM) {
            return !sameTeam;
        }
        return true;
    }

    private static void tickTabList(
            List<ServerPlayer> players,
            StatusRelay relay,
            ServerSettings settings,
            long now
    ) {
        boolean enabled = settings.get(ServerSettings.VANILLA_TAB_LIST);
        String fallbackLang = settings.get(ServerSettings.LANGUAGE);

        for (ServerPlayer viewer : players) {
            UUID viewerId = viewer.getUUID();
            if (!enabled || relay.hasMod(viewerId) || viewer.tickCount < JOIN_GRACE_TICKS) {
                Map<UUID, String> prevMap = SENT_TAB_NAMES.remove(viewerId);
                if (prevMap != null && !prevMap.isEmpty()) {
                    for (ServerPlayer target : players) {
                        if (prevMap.containsKey(target.getUUID())) {
                            sendTabDisplayName(viewer, target, null);
                        }
                    }
                }
                continue;
            }

            Map<UUID, String> viewerMap = SENT_TAB_NAMES.computeIfAbsent(viewerId, k -> new HashMap<>());
            String viewerLang = PlayerLanguage.of(viewer);
            if (viewerLang == null || viewerLang.isEmpty()) {
                viewerLang = fallbackLang;
            }

            for (ServerPlayer target : players) {
                UUID targetId = target.getUUID();
                boolean hidden = (target != viewer && target.isInvisibleTo(viewer)) || target.isSpectator();
                PlayerStatus status = hidden ? PlayerStatus.NONE : relay.current(targetId, now);
                String desc = status.isNone() ? "" : StatusCommands.describe(viewerLang, status);
                String prev = viewerMap.getOrDefault(targetId, "");
                if (desc.equals(prev)) {
                    continue;
                }
                if (desc.isEmpty()) {
                    viewerMap.remove(targetId);
                    sendTabDisplayName(viewer, target, null);
                } else {
                    viewerMap.put(targetId, desc);
                    Component base = target.getTabListDisplayName();
                    if (base == null) {
                        base = PlayerTeam.formatNameForTeam(target.getTeam(), target.getName());
                    }
                    Component decorated = base.copy().append(
                            Component.literal(" [" + desc + "]")
                                    .withStyle(s -> s.withColor(ChatFormatting.GRAY).withInsertion(TAB_INSERTION_TAG)));
                    sendTabDisplayName(viewer, target, decorated);
                }
            }
        }
    }

    private static void configureDisplay(Display.TextDisplay display, String text) {
        try {
            if (SET_BILLBOARD != null) {
                SET_BILLBOARD.invoke(display, Display.BillboardConstraints.CENTER);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        setDisplayText(display, text);
    }

    private static void setDisplayText(Display.TextDisplay display, String text) {
        try {
            if (SET_TEXT != null) {
                SET_TEXT.invoke(display, Component.literal(text).withStyle(ChatFormatting.GRAY));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static void sendTabDisplayName(ServerPlayer viewer, ServerPlayer target, Component customName) {
        try {
            ClientboundPlayerInfoUpdatePacket pkt = new ClientboundPlayerInfoUpdatePacket(
                    ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, target);
            if (customName != null && PACKET_ENTRIES != null && !pkt.entries().isEmpty()) {
                Object oldEntry = pkt.entries().get(0);
                Object newEntry = withDisplayName(oldEntry, customName);
                if (newEntry != null) {
                    PACKET_ENTRIES.set(pkt, Collections.singletonList(newEntry));
                }
            }
            viewer.connection.send(pkt);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Object withDisplayName(Object entry, Component displayName) throws ReflectiveOperationException {
        RecordComponent[] components = entry.getClass().getRecordComponents();
        if (components == null) {
            return null;
        }
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            if (types[i] == Component.class) {
                args[i] = displayName;
            } else {
                Method accessor = components[i].getAccessor();
                accessor.setAccessible(true);
                args[i] = accessor.invoke(entry);
            }
        }
        Constructor<?> ctor = entry.getClass().getDeclaredConstructor(types);
        ctor.setAccessible(true);
        return ctor.newInstance(args);
    }

    private static Method findMethod(Class<?> owner, Class<?> paramType) {
        for (Method m : owner.getDeclaredMethods()) {
            if (m.getReturnType() == void.class
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == paramType) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    private static EntityType<?> findTextDisplayType() {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if ("text_display".equals(type.toShortString())) {
                return type;
            }
        }
        return null;
    }

    private static Field findEntriesField() {
        for (Field f : ClientboundPlayerInfoUpdatePacket.class.getDeclaredFields()) {
            if (f.getType() == List.class) {
                f.setAccessible(true);
                return f;
            }
        }
        return null;
    }
}
