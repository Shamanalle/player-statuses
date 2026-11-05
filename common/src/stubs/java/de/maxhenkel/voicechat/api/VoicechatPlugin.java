package de.maxhenkel.voicechat.api;

import de.maxhenkel.voicechat.api.events.EventRegistration;

public interface VoicechatPlugin {
    default String getPluginId() {
        return null;
    }

    default void initialize(VoicechatApi api) {
    }

    default void registerEvents(EventRegistration registration) {
    }
}
