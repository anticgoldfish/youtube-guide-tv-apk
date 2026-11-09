#!/usr/bin/env python3
"""Apply the native YouTube Guide launcher to a pinned SmartTube MIT-licensed checkout."""
from pathlib import Path
import shutil
import sys

ROOT=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path('SmartTube').resolve()
HERE=Path(__file__).resolve().parent
TV=ROOT/'smarttubetv'
PACKAGE=TV/'src/main/java/com/youtubeguide/tv'
PACKAGE.mkdir(parents=True,exist_ok=True)
for src in (HERE/'fork-overlay/src/com/youtubeguide/tv').glob('*.java'):
    shutil.copy2(src,PACKAGE/src.name)

def replace_exact(path,old,new):
    raw=path.read_text()
    if old not in raw:raise RuntimeError(f'Expected SmartTube code not found in {path}: {old[:110]!r}')
    path.write_text(raw.replace(old,new,1))

manifest=TV/'src/main/AndroidManifest.xml'
raw=manifest.read_text()
needle='''            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.DEFAULT"/>
                <category android:name="android.intent.category.LAUNCHER" />
                <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
            </intent-filter>'''
if needle not in raw:raise RuntimeError('SmartTube launcher intent filter changed')
raw=raw.replace(needle,'',1)
anchor='''        <!-- This activity's name used inside atv channels -->'''
entry='''        <!-- YouTube Guide: the ONLY TV launcher, with SmartTube's playback engine inside this APK. -->
        <activity android:name="com.youtubeguide.tv.GuideActivity"
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
'''
if anchor not in raw:raise RuntimeError('SmartTube manifest insertion point missing')
raw=raw.replace(anchor,entry+anchor,1)
# Remove alternate launcher to avoid a path to regular SmartTube navigation.
alt_start=raw.index('        <!-- Alt app launcher icon -->')
alt_end=raw.index('        <!-- noHistory is essential',alt_start)
raw=raw[:alt_start]+raw[alt_end:]
raw=raw.replace('android:label="@string/app_name"','android:label="YouTube Guide"',1)
manifest.write_text(raw)

app=TV/'src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/main/MainApplication.java'
replace_exact(app,'viewManager.setRoot(BrowseActivity.class);','viewManager.setRoot(com.youtubeguide.tv.GuideActivity.class);')
replace_exact(app,'viewManager.register(PlaybackView.class, PlaybackActivity.class, BrowseActivity.class);','viewManager.register(PlaybackView.class, PlaybackActivity.class, com.youtubeguide.tv.GuideActivity.class);')
# Route other smarttube presenters back to Guide when they need a parent.
# We avoid editing normal SmartTube player dependencies, enabling upstream player fixes.

player=TV/'src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/PlaybackActivity.java'
src=player.read_text()
needle='''    private boolean gamepadTriggerPressed = false;'''
inject='''    private final android.os.Handler guideHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable guideAutoNext = new Runnable() {
        @Override public void run() {
            try {
                com.youtubeguide.tv.GuideRepository r = com.youtubeguide.tv.GuidePlayback.repo;
                if (r != null) r.refresh(false, null);
                com.youtubeguide.tv.GuideRepository.Program p = com.youtubeguide.tv.GuidePlayback.current();
                if (p != null && !p.id.equals(com.youtubeguide.tv.GuidePlayback.activeVideo)) {
                    com.youtubeguide.tv.GuidePlayback.activeVideo = p.id;
                    com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter.instance(PlaybackActivity.this)
                       .openVideo(p.id, false, Math.max(0, System.currentTimeMillis() - p.start), false);
                }
            } catch (Exception ignored) { }
            guideHandler.postDelayed(this, 5000);
        }
    };
    @Override protected void onResume() {
        super.onResume();
        guideHandler.removeCallbacks(guideAutoNext);
        guideHandler.postDelayed(guideAutoNext, 2000);
    }
'''
if needle not in src:raise RuntimeError('PlaybackActivity field not found')
src=src.replace(needle,needle+'\n'+inject,1)
needle='''    protected void onPause() {
        boolean hasDialogBug'''
if needle not in src:raise RuntimeError('PlaybackActivity onPause not found')
src=src.replace(needle,'''    protected void onPause() {
        guideHandler.removeCallbacks(guideAutoNext);
        boolean hasDialogBug''',1)
player.write_text(src)

fragment=TV/'src/main/java/com/liskovsoft/smartyoutubetv2/tv/ui/playback/PlaybackFragment.java'
src=fragment.read_text()
needle='''    public void showSuggestions(boolean show) {
        if (forbidShowOverlay(show))'''
if needle not in src:raise RuntimeError('SmartTube suggestions method changed')
src=src.replace(needle,'''    public void showSuggestions(boolean show) {
        if (com.youtubeguide.tv.GuidePlayback.activeChannel != null) return;
        if (forbidShowOverlay(show))''',1)
fragment.write_text(src)

# Install beside any existing SmartTube build rather than replacing it.
app_gradle=TV/'build.gradle'
replace_exact(app_gradle,'applicationId "org.smarttube.stable"','applicationId "com.youtubeguide.smarttube"')

# SmartTube's original google-services.json registers its own Android package IDs.
# The guide is a separate app (com.youtubeguide.smarttube), so that Firebase
# config cannot be used and triggers processStstableDebugGoogleServices failure.
# SmartTube's build.gradle applies Google Services / Crashlytics only when the
# root google-services.json exists for the stable/beta build. Remove the cloned
# upstream config BEFORE Gradle evaluates the project; no Firebase keys or new
# Firebase project are necessary to build YouTube Guide.
upstream_google_services = TV / 'google-services.json'
if upstream_google_services.is_file():
    upstream_google_services.unlink()
    print('Removed upstream Firebase config (package ID does not match YouTube Guide)')

# Keep the SmartTube MIT attribution and original third-party notices intact.
print('Applied YouTube Guide overlay to',ROOT)
