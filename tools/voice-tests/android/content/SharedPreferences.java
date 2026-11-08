package android.content;
/** Test helper authorization only; never used in Android builds. */
public class SharedPreferences {
    public String getString(String name, String fallback) { return "a".repeat(64); }
}
