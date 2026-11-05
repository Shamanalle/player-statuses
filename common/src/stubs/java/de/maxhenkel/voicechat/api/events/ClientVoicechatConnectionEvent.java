package de.maxhenkel.voicechat.api.events;

public interface ClientVoicechatConnectionEvent extends ClientEvent {
    boolean isConnected();
}
