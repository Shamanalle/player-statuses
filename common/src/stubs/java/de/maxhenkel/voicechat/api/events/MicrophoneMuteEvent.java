package de.maxhenkel.voicechat.api.events;

public interface MicrophoneMuteEvent extends ClientEvent {
    boolean isDisabled();
}
