package com.kasper.playerstatuses;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatClientApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatInitializationEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophoneMuteEvent;
import de.maxhenkel.voicechat.api.events.VoicechatDisableEvent;

/**
 * Simple Voice Chat plugin for Fabric, Forge and NeoForge. Tracks whether the local player's
 * microphone is muted or voice chat sound is disabled while connected to a voice chat server.
 */
@ForgeVoicechatPlugin
public final class PlayerStatusesVoiceChatPlugin implements VoicechatPlugin {

    private static volatile VoicechatClientApi clientApi;
    private static volatile boolean connected;

    public PlayerStatusesVoiceChatPlugin() {
        VoiceChatState.bind(PlayerStatusesVoiceChatPlugin::micOff, PlayerStatusesVoiceChatPlugin::soundOff);
    }

    @Override
    public String getPluginId() {
        return BuildInfo.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatClientApi) {
            clientApi = (VoicechatClientApi) api;
        }
        VoiceChatState.bind(PlayerStatusesVoiceChatPlugin::micOff, PlayerStatusesVoiceChatPlugin::soundOff);
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(ClientVoicechatInitializationEvent.class, e -> {
            clientApi = e.getVoicechat();
            connected = false;
        });
        registration.registerEvent(ClientVoicechatConnectionEvent.class, e -> {
            clientApi = e.getVoicechat();
            connected = e.isConnected();
        });
        registration.registerEvent(MicrophoneMuteEvent.class, PlayerStatusesVoiceChatPlugin::onClientEvent);
        registration.registerEvent(VoicechatDisableEvent.class, PlayerStatusesVoiceChatPlugin::onClientEvent);
    }

    private static void onClientEvent(ClientEvent event) {
        clientApi = event.getVoicechat();
    }

    private static boolean isActive(VoicechatClientApi api) {
        return api != null && connected && !api.isDisconnected();
    }

    private static boolean micOff() {
        VoicechatClientApi api = clientApi;
        return isActive(api) && (api.isMuted() || api.isDisabled());
    }

    private static boolean soundOff() {
        VoicechatClientApi api = clientApi;
        return isActive(api) && api.isDisabled();
    }
}
