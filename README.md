# YouTube Guide — SmartTube-based Google TV fork (experimental source build)

This project builds **one Android TV APK** based on the MIT-licensed [SmartTube](https://github.com/yuliskov/SmartTube) source. It does not require launching or installing a separate SmartTube app. It pulls your public `channels.json` directly from `anticgoldfish/youtube-guide-surfer` and never needs YouTube or GitHub API keys to read it.

## What the native guide implements

- TV remote / D-pad navigation, one launcher tile: **YouTube Guide**, no regular SmartTube browsing launcher.
- Native category-filtered channel list, time navigation ±30, ±60, ±120 minutes, NOW.
- Muted moving video in the upper-left preview, using SmartTube's embedded player, with a static thumbnail fallback.
- Live clock: select a channel and play its current YouTube video through **the SmartTube playback presenter inside the same APK** at the scheduled video offset.
- Automatic transition to the next scheduled video while in fullscreen playback (5-second check). This is experimental and depends on SmartTube player internals remaining compatible.
- Public `channels.json`: read-only; refreshes every 5 minutes, caches last successful copy for network outages.
- Scheduled `schedule` / `nextSchedule` interpreted at UTC-millisecond timestamps; if both are exhausted, locally repeats saved videos as a fallback.
- Directional shortcut **UP UP DOWN DOWN LEFT RIGHT LEFT RIGHT** shows diagnostics and a force refresh action.
- No channel editing or credential entry on Google TV.

## Important limitations / current status

**This is a source overlay plus an automated Android APK builder, not a verified built APK.** The execution container lacks Android SDK / Gradle, so this build has not been compiled or run on a TV. Run its GitHub Actions workflow to build; inspect errors if SmartTube's upstream third-party dependencies have changed.

**The upper-left preview now attempts a muted moving preview with SmartTube's `EmbedPlayerView`.** The thumbnail remains underneath as a fallback if a video will not load. The transition from preview to fullscreen, memory use on slower Google TV hardware, and video syncing remain unverified until a device test.

**Full-screen playback uses native SmartTube components** (rather than sending an intent to a separately installed app). SmartTube's seek UI might still allow seeking outside the live point. Playback & automatic transitions have not been verified on hardware.

**No ad-free guarantee.** SmartTube's own player is used; availability, playback behavior and service changes remain outside this project's control.

## Build on GitHub (no local Android SDK required)

1. Create a new GitHub repository e.g. `youtube-guide-tv-apk` (separate from the public website repository).
2. Upload everything **inside this ZIP** to the repository root, including `.github/workflows/build-smarttube-guide-apk.yml`.
3. Open **Actions → Build YouTube Guide — SmartTube Fork APK → Run workflow**.
4. If it succeeds, open the run and download the **YouTube-Guide-SmartTube-Fork-APK** artifact; unzip it and locate the `.apk`.
5. Install this debug APK on a supported Google TV/Android TV (e.g. transfer with Send Files to TV, or with Android Debug Bridge using `adb install -r filename.apk`). Android TV may require you to allow installation from the transferring app; follow the TV security prompt.
6. Launch **YouTube Guide** on the TV. An internet connection is needed for playback and updates, but a previously fetched channel listing is cached.

The public website and its GitHub Actions replenishment workflow stay separate. Add a channel to the website and, after it is published in the shared JSON, this TV app will pick it up automatically without building a new APK.

## Why SmartTube source is cloned during build

SmartTube is a large multi-module Android project with Git submodules and native dependencies. The build workflow fetches an exact known source commit and applies the modifications, rather than distributing SmartTube's source code or binary in this ZIP. The installed program contains SmartTube code under its upstream MIT license and retains its third-party license notices.

SmartTube upstream: https://github.com/yuliskov/SmartTube
SmartTube license: https://github.com/yuliskov/SmartTube/blob/master/LICENSE

## How the APK relates to the website

The app only **reads** the GitHub Pages JSON (and raw GitHub fallback); it never writes to the repository and does not contain API keys. The website manages channels and the existing website GitHub Action replenishes their schedules. The TV app reads those updates approximately every five minutes, with a cached copy if offline. No APK rebuild is necessary when channels are added.

## Verified here vs. requiring a TV

Verified by source checks: Android launcher replacement, native GuideActivity, the internal `PlaybackPresenter` call with scheduled offset, JSON loading, D-pad debug sequence, and the GitHub Actions Gradle builder. The source overlay has not completed a real Android compile in this execution environment. SmartTube's internals can change; the build pins upstream commit `2bcad2c130f5789af22094a59c029d14461825f0`.
