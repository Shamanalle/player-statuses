package de.maxhenkel.voicechat.api.events;

import de.maxhenkel.voicechat.api.VoicechatClientApi;

public interface ClientEvent extends Event {
    VoicechatClientApi getVoicechat();
}
