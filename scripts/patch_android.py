import pathlib, shutil, sys

APP_ID = "com.thepkub.rc4wd"
root = pathlib.Path("android/app/src/main")
manifest = root / "AndroidManifest.xml"
if not manifest.exists():
    sys.exit("android project not found - run `npx cap add android` first")

s = manifest.read_text(encoding="utf-8")
perms = """
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
"""
if "BLUETOOTH_CONNECT" not in s:
    s = s.replace("</manifest>", perms + "</manifest>")
if "usesCleartextTraffic" not in s:
    s = s.replace("<application", '<application android:usesCleartextTraffic="true"', 1)
if "screenOrientation" not in s:
    s = s.replace("<activity", '<activity android:screenOrientation="sensorLandscape"', 1)
for needle in ("sensorLandscape", "usesCleartextTraffic", "BLUETOOTH_CONNECT"):
    assert needle in s, f"manifest patch failed: {needle}"
manifest.write_text(s, encoding="utf-8")

java_dir = root / "java" / pathlib.Path(*APP_ID.split("."))
java_dir.mkdir(parents=True, exist_ok=True)
shutil.copy("native/CarLinkPlugin.java", java_dir / "CarLinkPlugin.java")

(java_dir / "MainActivity.java").write_text(f"""package {APP_ID};

import android.os.Bundle;
import android.view.WindowManager;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {{
    @Override
    public void onCreate(Bundle savedInstanceState) {{
        registerPlugin(CarLinkPlugin.class);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideBars();
    }}

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {{
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }}

    private void hideBars() {{
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }}
}}
""", encoding="utf-8")
print("patched")
