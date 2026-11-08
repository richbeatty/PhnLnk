package android.telephony;
public class TelephonyManager {
    public static final int CALL_STATE_OFFHOOK = 2;
    public static volatile int state;
    public int getCallState() { return state; }
}
