package android.content;

/** Minimal offline JVM stub for the Android Context type. */
public class Context {
    public static final String TELEPHONY_SERVICE = "phone";
    public static final int MODE_PRIVATE = 0;
    public Context getApplicationContext() { return this; }
    public Object getSystemService(String name) { return new android.telephony.TelephonyManager(); }
    public SharedPreferences getSharedPreferences(String name, int mode) { return new SharedPreferences(); }
}
