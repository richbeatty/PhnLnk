package com.richbeatty.phnlnk;
import android.content.Context;
final class AudioAccessState {
    static volatile boolean enabled = true;
    static boolean autoReconnect(Context ignored) { return enabled; }
    static String captureMethod(Context ignored) { return "AUTO"; }
    static String uplinkMethod(Context ignored) { return "AUTO"; }
}
