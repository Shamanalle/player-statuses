package com.kasper.playerstatuses;

import com.kasper.playerstatuses.client.ClientSide;
import com.kasper.playerstatuses.client.SettingsKey;
import com.kasper.playerstatuses.network.StatusPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;

/** Client entrypoint on Fabric / Quilt (1.20.5–1.21.11). */
public class PlayerStatusesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientSide.init(FabricLoader.getInstance().getConfigDir());
        ClientSide.bindSettingsKey(KeyBindingHelper.registerKeyBinding(SettingsKey.create()));
        FabricPayloads.register();
        ClientPlayNetworking.registerGlobalReceiver(StatusPayload.TYPE, (payload, context) -> {
            byte[] data = payload.data();
            context.client().execute(() -> ClientSide.onServerMessage(data));
        });
        ClientSide.useSender(data -> {
            if (ClientPlayNetworking.canSend(StatusPayload.TYPE)) {
                ClientPlayNetworking.send(new StatusPayload(data));
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientSide::tick);
    }
}
