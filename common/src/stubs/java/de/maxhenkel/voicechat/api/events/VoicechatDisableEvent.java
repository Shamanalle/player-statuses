package de.maxhenkel.voicechat.api.events;

public interface VoicechatDisableEvent extends ClientEvent {
    boolean isDisabled();
}
