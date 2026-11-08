package com.richbeatty.phnlnk;

import android.content.Context;
import java.util.Base64;

/** Test-only stand-in. Never compile into an Android APK. */
final class RemoteClient {
    static String TOKEN=fixtureToken();
    private RemoteClient() { }
    static String voiceToken(Context ignored) {return TOKEN;}
    private static String fixtureToken() {
        byte[] raw=new byte[32];
        for(int i=0;i<raw.length;i++)raw[i]=(byte)(i*7+3);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
