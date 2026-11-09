
#!/usr/bin/env python3
"""Apply YouTube Guide modifications to the SmartTube source."""

from pathlib import Path
import shutil
import sys

ROOT = (
    Path(sys.argv[1]).resolve()
    if len(sys.argv) > 1
    else Path("SmartTube").resolve()
)

HERE = Path(__file__).resolve().parent
TV = ROOT / "smarttubetv"
PACKAGE = TV / "src/main/java/com/youtubeguide/tv"

PACKAGE.mkdir(parents=True, exist_ok=True)

# Copy YouTube Guide's Java source into SmartTube.
for src in (HERE / "fork-overlay/src/com/youtubeguide/tv").glob("*.java"):
    shutil.copy2(src, PACKAGE / src.name)


def replace_exact(path, old, new):
    raw = path.read_text(encoding="utf-8")

    if old not in raw:
        raise RuntimeError(
            f"Expected SmartTube code not found in {path}: {old[:110]!r}"
        )

    path.write_text(
        raw.replace(old, new, 1),
        encoding="utf-8"
    )


# --------------------------------------------------
# ANDROID MANIFEST
# --------------------------------------------------

manifest = TV / "src/main/AndroidManifest.xml"
raw = manifest.read_text(encoding="utf-8")

# Remove SmartTube's original TV launcher.
needle = """            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>"""

if needle not in raw:
    raise RuntimeError("SmartTube launcher intent filter changed")

raw = raw.replace(needle, "", 1)

# Add YouTube Guide as the TV launcher.
anchor = "        <!-- This activity's name used inside atv channels -->"

entry = """        <!-- YouTube Guide: standalone Google TV launcher. -->
        <activity
            android:name="com.youtubeguide.tv.GuideActivity"
            android:exported="true"
            android:screenOrientation="landscape"
            android:theme="@android:style/Theme.Material.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>
        </activity>
"""

if anchor not in raw:
    raise RuntimeError("SmartTube manifest insertion point missing")

raw = raw.replace(anchor, entry + anchor, 1)

# Remove SmartTube's alternate launcher.
alt_start = raw.index("        <!-- Alt app launcher icon -->")
alt_end = raw.index(
    "        <!-- noHistory is essential",
    alt_start
)

raw = raw[:alt_start] + raw[alt_end:]

# Change application display name.
raw = raw.replace(
    'android:label="@string/app_name"',
    'android:label="YouTube Guide"',
    1
)

# FIX: Prevent the Android manifest label conflict.
# SmartTube's leanbackassistant module declares its own app label.
# Tell Android's manifest merger that YouTube Guide takes priority.
old = 'android:theme,android:allowBackup'
new = 'android:theme,android:allowBackup,android:label'

if old not in raw:
    raise RuntimeError(
        "Could not locate Android manifest merge settings"
    )

raw = raw.replace(old, new, 1)

manifest.write_text(raw, encoding="utf-8")

print("YouTube Guide manifest configured")


# --------------------------------------------------
# SMARTTUBE APPLICATION NAVIGATION
# --------------------------------------------------

app = (
    TV
    / "src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/main/MainApplication.java"
)

replace_exact(
    app,
    "viewManager.setRoot(BrowseActivity.class);",
    "viewManager.setRoot(com.youtubeguide.tv.GuideActivity.class);"
)

replace_exact(
    app,
    "viewManager.register(PlaybackView.class, PlaybackActivity.class, BrowseActivity.class);",
    "viewManager.register(PlaybackView.class, PlaybackActivity.class, com.youtubeguide.tv.GuideActivity.class);"
)


# --------------------------------------------------
# AUTOMATIC TV PROGRAM TRANSITIONS
# --------------------------------------------------

player = (
    TV
    / "src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/PlaybackActivity.java"
)

src = player.read_text(encoding="utf-8")

needle = "    private boolean gamepadTriggerPressed = false;"

inject = """
    private final android.os.Handler guideHandler =
        new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable guideAutoNext = new Runnable() {
        @Override
        public void run() {
            try {
                com.youtubeguide.tv.GuideRepository r =
                    com.youtubeguide.tv.GuidePlayback.repo;

                if (r != null) {
                    r.refresh(false, null);
                }

                com.youtubeguide.tv.GuideRepository.Program p =
                    com.youtubeguide.tv.GuidePlayback.current();

                if (p != null &&
                    !p.id.equals(com.youtubeguide.tv.GuidePlayback.activeVideo)) {

                    com.youtubeguide.tv.GuidePlayback.activeVideo = p.id;

                    com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter
                        .instance(PlaybackActivity.this)
                        .openVideo(
                            p.id,
                            false,
                            Math.max(
                                0,
                                System.currentTimeMillis() - p.start
                            ),
                            false
                        );
                }
            } catch (Exception ignored) {
            }

            guideHandler.postDelayed(this, 5000);
        }
    };

    @Override
    protected void onResume() {
        super.onResume();

        guideHandler.removeCallbacks(guideAutoNext);
        guideHandler.postDelayed(guideAutoNext, 2000);
    }
"""

if needle not in src:
    raise RuntimeError("PlaybackActivity field not found")

src = src.replace(needle, needle + "\n" + inject, 1)

needle = """    protected void onPause() {
        boolean hasDialogBug"""

if needle not in src:
    raise RuntimeError("PlaybackActivity onPause not found")

src = src.replace(
    needle,
    """    protected void onPause() {
        guideHandler.removeCallbacks(guideAutoNext);
        boolean hasDialogBug""",
    1
)

player.write_text(src, encoding="utf-8")


# --------------------------------------------------
# REMOVE STANDARD SMARTTUBE RECOMMENDATIONS
# --------------------------------------------------

fragment = (
    TV
    / "src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/PlaybackFragment.java"
)

src = fragment.read_text(encoding="utf-8")

needle = """    public void showSuggestions(boolean show) {
        if (forbidShowOverlay(show))"""

if needle not in src:
    raise RuntimeError(
        "SmartTube suggestions method changed"
    )

src = src.replace(
    needle,
    """    public void showSuggestions(boolean show) {
        if (com.youtubeguide.tv.GuidePlayback.activeChannel != null) return;
        if (forbidShowOverlay(show))""",
    1
)

fragment.write_text(src, encoding="utf-8")


# --------------------------------------------------
# UNIQUE YOUTUBE GUIDE APPLICATION ID
# --------------------------------------------------

app_gradle = TV / "build.gradle"

replace_exact(
    app_gradle,
    'applicationId "org.smarttube.stable"',
    'applicationId "com.youtubeguide.smarttube"'
)


# --------------------------------------------------
# REMOVE INCOMPATIBLE FIREBASE CONFIGURATION
# --------------------------------------------------

# SmartTube's Firebase configuration uses its original package IDs.
# YouTube Guide has a separate package ID and does not need that file.

upstream_google_services = TV / "google-services.json"

if upstream_google_services.is_file():
    upstream_google_services.unlink()
    print("Removed incompatible SmartTube Firebase configuration")


# --------------------------------------------------
# COMPLETE
# --------------------------------------------------

print("YouTube Guide overlay successfully applied to:", ROOT)
