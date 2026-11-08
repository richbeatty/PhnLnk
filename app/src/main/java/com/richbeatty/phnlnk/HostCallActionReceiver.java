package com.richbeatty.phnlnk;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class HostCallActionReceiver extends BroadcastReceiver {
    static final String ANSWER = "com.richbeatty.phnlnk.ANSWER_HOST_CALL";
    static final String END = "com.richbeatty.phnlnk.END_HOST_CALL";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ANSWER.equals(intent.getAction())) PhoneInCallService.answerCall();
        if (END.equals(intent.getAction())) PhoneInCallService.endCall();
    }
}
