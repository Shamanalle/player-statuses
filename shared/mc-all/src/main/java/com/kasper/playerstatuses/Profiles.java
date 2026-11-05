package com.kasper.playerstatuses;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Reads {@link UUID} and name from {@link GameProfile} across both pre-1.21.9 ({@code getId()}/{@code getName()})
 * and 1.21.9+ ({@code id()}/{@code name()} record accessors).
 */
public final class Profiles {

    private static final Method ID_METHOD = find("id", "getId");
    private static final Method NAME_METHOD = find("name", "getName");

    private Profiles() {
    }

    public static UUID id(GameProfile profile) {
        if (profile == null || ID_METHOD == null) {
            return null;
        }
        try {
            return (UUID) ID_METHOD.invoke(profile);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    public static String name(GameProfile profile) {
        if (profile == null || NAME_METHOD == null) {
            return "";
        }
        try {
            String s = (String) NAME_METHOD.invoke(profile);
            return s != null ? s : "";
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    private static Method find(String modern, String legacy) {
        try {
            return GameProfile.class.getMethod(modern);
        } catch (NoSuchMethodException e) {
            try {
                return GameProfile.class.getMethod(legacy);
            } catch (NoSuchMethodException ex) {
                return null;
            }
        }
    }
}
