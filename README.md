# NILKON for Android (v1.7.2)

Same player as the Windows version, built for phones: Kotlin + Jetpack Compose, Media3 ExoPlayer.

## Build the APK (pick one)

**A. GitHub (no installation needed)** - create a free GitHub repository, upload this whole folder, open the
*Actions* tab → *Build NILKON APK* → *Run workflow*. After ~5 minutes download `NILKON-apk` → `app-release.apk`.

**B. Android Studio** - install Android Studio, *Open* this folder, wait for Gradle sync, then
*Build → Build APK(s)*. The file is in `app/build/outputs/apk/`.

Copy the `.apk` to your phone and open it (allow "install unknown apps" when asked).

## What is inside (same as the Windows version 1.7.2)
- **Look and animation** - the exact cat headphone logo (eyes follow your finger, blinks, sleeps, happy on tap), cat-ear
  control buttons, turntable with spinning vinyl and moving tonearm, mini disc, flowing / bars / mirror sound waves,
  waveform seek bar, 8 colour themes, "adapt colours to album art", dark / light, Aurora / Midnight / Deep Space /
  Blue Haze / Super Dark / your own background with darkness and blur. All drawn from the same vector art and formulas.
- **Pages** - Home (wave hero, Recently played, Suggested), Online, Library (All tracks, Liked, Recently played,
  playlists), Now Playing (turntable, spectrum, queue), Settings (Playback, Appearance, Visualizer, Audio, Online, Library, About).
- **Library** - phone music, add files / add folder, playlists with long-press drag ordering, favourites, history, rename,
  remove from library, delete file, search, suggestions weighted by your listening.
- **Online** - search while typing, paste a song link, stream, similar-songs autoplay, download (M4A), progress ring.
- **Sound** - 10-band equalizer, 24 presets, draggable curve, bass / treble, preamp, smart headroom, saved presets,
  all-songs or per-song EQ, even-out-loudness.
- **Real waves** - local and downloaded songs are decoded and analysed (32 bands, beat-accurate); streaming songs use the
  live audio session (needs the "record audio" permission - nothing is recorded) or simulated waves.
- **Controls everywhere** - notification panel, lock screen, headset / Bluetooth buttons, screen off: all work through the
  Media3 media session. Pauses on calls / unplugged headphones; resumes when the internet returns.
- Sleep timer, shuffle, repeat, volume and skip steps, resume last song, start page, pop-up messages on/off.

## Honest notes
- **This project has not been compiled by me** (the authoring environment cannot reach Google / Maven servers). It was
  written carefully against Compose BOM 2024.09, Media3 1.4.1 and AndroidSVG 1.4, but if the build shows an error, open the
  `build-log` artifact (GitHub) or the Android Studio message and send it to me - I will fix it right away.
- Different on Android: no Discord Rich Presence (needs the Discord desktop app), no browser-cookie option (not needed),
  no window-title option (the notification shows the song), downloads are M4A only (no MP3 conversion), the download
  folder is fixed to the app's Music folder, and playlist ordering uses long-press drag instead of mouse drag.
- Online features use public music data and NewPipeExtractor; they can break when the service changes and are for
  personal use under the terms of the services involved.
- The release APK is signed with the debug key so it installs directly; use your own key if you publish it.
