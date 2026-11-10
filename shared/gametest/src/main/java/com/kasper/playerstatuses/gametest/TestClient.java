package com.kasper.playerstatuses.gametest;

import com.kasper.playerstatuses.Activity;
import com.kasper.playerstatuses.ClientSettings;
import com.kasper.playerstatuses.PlayerStatus;
import com.kasper.playerstatuses.StatusMessages;
import com.kasper.playerstatuses.StatusRelay;
import com.kasper.playerstatuses.client.ClientAccess;
import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.ScreenDetector;
import com.kasper.playerstatuses.client.SettingsScreen;
import com.kasper.playerstatuses.client.TabListDecorator;
import com.kasper.playerstatuses.server.ServerSide;
import com.kasper.playerstatuses.server.VanillaBridge;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;

/** Small client calls of the in-game tests; a band with other ones has its own copy of this file. */
final class TestClient {

    static final UUID FAKE_UUID = UUID.fromString("00000000-0000-4000-8000-000000000002");
    private static final int FAKE_ENTITY_ID = -777;
    private static volatile CompletableFuture<?> reload;
    private static volatile CompletableFuture<BlockPos> sceneStage;
    private static volatile BlockPos sceneAnchor;

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

    /**
     * Builds a sunlit timber-and-stone multiplayer workshop courtyard on the integrated server so
     * store screenshots have warm, authentic gameplay framing and clear sky contrast above Alex.
     */
    static void prepareScene(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        UUID playerId = client.player.getUUID();
        CompletableFuture<BlockPos> future = new CompletableFuture<>();
        sceneStage = future;
        server.execute(() -> {
            try {
                ServerPlayer sp = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = server.overworld();
                if (sp == null || level == null) {
                    future.complete(BlockPos.ZERO);
                    return;
                }
                var silentSource = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(silentSource, "weather clear");
                server.getCommands().performPrefixedCommand(silentSource, "time set 3500");

                BlockPos anchor = sp.blockPosition();
                buildWorkshopCourtyard(level, anchor);
                sceneAnchor = anchor;
                sp.teleportTo(anchor.getX() + 3.7, anchor.getY(), anchor.getZ() + 0.5);
                future.complete(anchor);
            } catch (RuntimeException e) {
                future.completeExceptionally(e);
            }
        });
    }

    static boolean sceneReady(Minecraft client) {
        CompletableFuture<BlockPos> f = sceneStage;
        return f == null || f.isDone();
    }

    private static void buildWorkshopCourtyard(ServerLevel level, BlockPos anchor) {
        int bx = anchor.getX();
        int by = anchor.getY();
        int bz = anchor.getZ();

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState sprucePlanks = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState oakPlanks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState stoneBricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState mossyBricks = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState oakLog = Blocks.OAK_LOG.defaultBlockState();
        BlockState brickWall = Blocks.STONE_BRICK_WALL.defaultBlockState();
        BlockState spruceFence = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState oakLeaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState azaleaLeaves = Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState standingLantern = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false);

        // Clear volume above the courtyard
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                for (int dy = 0; dy <= 7; dy++) {
                    put(level, bx + dx, by + dy, bz + dz, air);
                }
            }
        }

        // Warm patio floor: stone-brick border around spruce planks with an oak center runner
        for (int dx = -4; dx <= 5; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                boolean border = dx == -4 || dx == 5 || dz == -4 || dz == 4;
                BlockState floor;
                if (border) {
                    floor = ((dx + dz) & 1) == 0 ? stoneBricks : mossyBricks;
                } else if (Math.abs(dz) <= 1 && dx >= -2 && dx <= 4) {
                    floor = oakPlanks;
                } else {
                    floor = sprucePlanks;
                }
                put(level, bx + dx, by - 1, bz + dz, floor);
            }
        }

        // Low workshop wall at dx = -3 so open blue sky sits directly behind the overhead status pill
        for (int dz = -3; dz <= 3; dz++) {
            put(level, bx - 3, by, bz + dz, stoneBricks);
            if (Math.abs(dz) >= 2) {
                put(level, bx - 3, by + 1, bz + dz, brickWall);
            }
        }
        // Timber corner posts with lanterns
        for (int dz : new int[]{-3, 3}) {
            put(level, bx - 3, by, bz + dz, oakLog);
            put(level, bx - 3, by + 1, bz + dz, oakLog);
            put(level, bx - 3, by + 2, bz + dz, standingLantern);
        }
        // Side fence rails + lantern posts framing left (+Z) and right (-Z)
        for (int dx = -2; dx <= 1; dx++) {
            put(level, bx + dx, by, bz - 4, spruceFence);
            put(level, bx + dx, by, bz + 4, spruceFence);
        }
        put(level, bx + 1, by + 1, bz - 4, standingLantern);
        put(level, bx + 1, by + 1, bz + 4, standingLantern);

        // Left side (+Z when looking West): crafting station, storage chest & barrel, potted poppy
        put(level, bx - 2, by, bz + 2, Blocks.CRAFTING_TABLE.defaultBlockState());
        put(level, bx - 2, by, bz + 3, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST));
        put(level, bx - 3, by + 1, bz + 2, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.EAST));
        put(level, bx - 2, by + 1, bz + 2, Blocks.POTTED_POPPY.defaultBlockState());

        // Right side (-Z when looking West): furnace, anvil, bookshelves & flowering azalea
        put(level, bx - 2, by, bz - 2, Blocks.FURNACE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        put(level, bx - 2, by, bz - 3, Blocks.ANVIL.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        put(level, bx - 3, by + 1, bz - 2, Blocks.BOOKSHELF.defaultBlockState());
        put(level, bx - 1, by, bz - 3, Blocks.FLOWERING_AZALEA.defaultBlockState());
        put(level, bx - 1, by, bz + 3, Blocks.POTTED_FERN.defaultBlockState());

        // Leafy garden trees/hedges at the outer corners (dz = +-5) for natural framing
        for (int dz : new int[]{-5, 5}) {
            put(level, bx - 3, by, bz + dz, oakLog);
            put(level, bx - 3, by + 1, bz + dz, oakLog);
            put(level, bx - 3, by + 2, bz + dz, oakLog);
            for (int lx = -4; lx <= -2; lx++) {
                for (int lz = dz - 1; lz <= dz + 1; lz++) {
                    put(level, bx + lx, by + 2, bz + lz, (lx + lz) % 2 == 0 ? azaleaLeaves : oakLeaves);
                    if (Math.abs(lx + 3) + Math.abs(lz - dz) <= 1) {
                        put(level, bx + lx, by + 3, bz + lz, oakLeaves);
                    }
                }
            }
        }
    }

    private static void put(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, 3);
    }

    /** Spawns a second player in front of the camera for overhead status rendering and store screenshots. */
    static void spawnFakePlayer(Minecraft client) {
        if (client.level == null || client.player == null) {
            return;
        }
        BlockPos anchor = sceneAnchor != null ? sceneAnchor : client.player.blockPosition();
        double camX = anchor.getX() + 3.7;
        double camY = anchor.getY();
        double camZ = anchor.getZ() + 0.5;
        client.player.setPos(camX, camY, camZ);
        client.player.xo = camX;
        client.player.yo = camY;
        client.player.zo = camZ;
        client.player.xOld = camX;
        client.player.yOld = camY;
        client.player.zOld = camZ;
        // Look West (+X -> -X) so morning sun shines from behind the camera onto Alex and the workshop
        client.player.setYRot(90f);
        client.player.setXRot(2.5f);
        client.player.yRotO = 90f;
        client.player.xRotO = 2.5f;
        client.player.yHeadRot = 90f;
        client.player.yHeadRotO = 90f;
        client.player.yBodyRot = 90f;
        client.player.yBodyRotO = 90f;

        if (client.level.getEntity(FAKE_ENTITY_ID) != null) {
            return;
        }
        GameProfile profile = new GameProfile(FAKE_UUID, "Alex");
        RemotePlayer fake = new RemotePlayer(client.level, profile);
        fake.setId(FAKE_ENTITY_ID);
        double x = anchor.getX() + 0.5;
        double y = anchor.getY();
        double z = anchor.getZ() + 0.5;
        fake.setPos(x, y, z);
        fake.xo = x;
        fake.yo = y;
        fake.zo = z;
        fake.xOld = x;
        fake.yOld = y;
        fake.zOld = z;
        // Slight three-quarter body angle so 3D status arm poses read clearly
        fake.setYRot(-86f);
        fake.yRotO = -86f;
        fake.yHeadRot = -88f;
        fake.yHeadRotO = -88f;
        fake.yBodyRot = -72f;
        fake.yBodyRotO = -72f;
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

    static Activity openSettingsScreen(Minecraft client) {
        client.options.guiScale().set(6);
        ClientAccess.setScreen(client, new SettingsScreen(null));
        return ScreenDetector.detect(client, ClientSide.core().settings().get(ClientSettings.IGNORED_SCREENS));
    }

    static void closeScreen(Minecraft client) {
        ClientAccess.setScreen(client, null);
        client.options.guiScale().set(0);
    }

    static boolean verifyVanillaBridge(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return false;
        }
        UUID id = client.player.getUUID();
        long now = System.currentTimeMillis();
        StatusRelay relay = ServerSide.core().relay();
        relay.remove(id);
        ServerSide.detector().setOpenContainer(id, Activity.FURNACE, relay, now);
        ServerSide.detector().updatePlayer(id, ServerSide.core().settings(), relay, now);
        VanillaBridge.tick(server, ServerSide.core(), now);
        boolean spawned = VanillaBridge.activeOverheadCount() == 1
                && relay.current(id, now).activity() == Activity.FURNACE;

        relay.onClientMessage(id, StatusMessages.update(PlayerStatus.NONE), now + 100);
        VanillaBridge.tick(server, ServerSide.core(), now + 100);
        boolean cleaned = VanillaBridge.activeOverheadCount() == 0;
        return spawned && cleaned;
    }
}
