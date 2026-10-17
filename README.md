# Music for Galaxy Watch

A standalone Wear OS music player built for the **Samsung Galaxy Watch8 (44 mm, 480 × 480)**. It is
modeled on One UI Watch's own Music app and plays only the music stored on the watch. No cloud,
no phone, no network permission.

| Swipe right ← | Home | → Swipe left |
|---|---|---|
| **Favorites**, newest first, grouped *Today / This week / This month / Earlier* | **Now playing**: cover-tinted background, progress ring around the bezel, ♥ to like | **Library**: Music and Podcasts folders, Favorites, Recently added, your own and preconfigured playlists |

## Features

- **Same files as Samsung's player.** Reads the watch's shared music library (MediaStore), which is
  where Galaxy Wearable puts the tracks you send from your phone. It also plays anything in its own
  folder, which needs no permission at all.
- **Cover-reflecting background.** Embedded album art (or `cover.jpg` / `folder.jpg` next to the
  file) is blurred behind the player. The accent color for the ring, highlights and the volume arc
  is taken from the cover.
- **Music and Podcasts folders.** *Music* is one big folder with Shuffle on top and every song
  below. *Podcasts* has Shuffle all on top and a folder per show. Each show lists its episodes
  newest first, with dates and time left. Episodes resume where you left off. While one plays,
  the player swaps previous/next for back 10 s / forward 30 s, and the options sheet adds
  playback speed (1× to 2×).
- **Options sheet (⋯).** Switch playlist (Music, Juice WRLD and your playlists, Favorites,
  Podcasts), shuffle, repeat, playback speed for podcasts, equalizer, add to playlist, up
  next and audio output. Works whether or not something is playing.
- **Equalizer.** Android's built-in equalizer on the player, with presets (Flat, Bass boost,
  Hip-hop, Vocal, Spoken word, Treble) and a slider per band. Settings are remembered.
- **One-tap favorites.** The heart on the player saves the song to Favorites instantly, and the system
  media controls get the same heart button.
- **Playlists.** Create them on the watch (keyboard, handwriting or voice), add/remove songs,
  rename, delete. Preconfigured playlists ship inside the app and install themselves on first launch.
- **Watch-native controls.** Bezel/rotary for volume, Horologist volume screen with output switching
  (watch speaker or Bluetooth), background playback with the Media3 session (works with the One UI
  Now bar and headset buttons), resumes your last song after a restart or reboot.
- **On-watch storage only.** Favorites, playlists and the last session are small JSON files in the
  app's private storage, written atomically.

## Build and install

Requirements: Android Studio (or JDK 17+ and the Android SDK, platform 37).

```bash
./gradlew :app:assembleRelease
```

To install on the watch, enable **Developer options → ADB debugging** and **Wireless debugging**
on the watch, then pair and connect:

```bash
adb pair <watch-ip>:<pairing-port>
```

```bash
adb connect <watch-ip>:<port>
```

```bash
./gradlew :app:installDebug
```

## Getting music onto the watch

### From this Mac, sized to fit (recommended)

[`tools/watch_sync.py`](tools/watch_sync.py) copies the Juice WRLD folder and the podcast
folders from `~/Music` to the watch over Wi-Fi debugging. It never changes the originals.

- **Music:** the audio is copied as-is. Tags are rewritten as ID3v2.3 and oversized embedded
  covers are scaled to 600 px. Android's scanner otherwise drops artists and cuts titles at 30
  characters, and the covers alone were half of some files.
- **Podcasts:** episodes are re-encoded to mono AAC at 48 kbps (clear for speech, about ¼ of
  the original size) and put in `Podcasts/<Show>/`, so the app files them under Podcasts.
- **Fitting:** all the music goes first. Then podcasts are added round-robin, newest episode
  of each show first, until the watch's free space (minus a 3 GB reserve) is used. Run it again
  any time; files already on the watch are skipped.

```bash
python3 tools/watch_sync.py prepare
```

```bash
python3 tools/watch_sync.py plan -s WATCH_IP:PORT
```

```bash
python3 tools/watch_sync.py push -s WATCH_IP:PORT
```

Edit `MUSIC_FOLDERS` and `PODCAST_FOLDERS` at the top of the script to change what goes over.

### Other ways

Any of these work, and the app picks changes up automatically:

1. **Galaxy Wearable app** on your phone: *Watch settings → Manage content → Add tracks*.
2. **adb to the shared library** (shows up in Samsung's player too):
   ```bash
   adb push "My Album/" /sdcard/Music/
   ```
3. **adb to the app's own folder** (only this app sees it, no permission needed):
   ```bash
   adb push "My Album/" /sdcard/Android/data/com.example.music/files/Music/
   ```

### About permissions

Android only lets an app read music that other apps or sync tools put on the watch if it holds
`READ_MEDIA_AUDIO`. The app asks for it **once**, on first launch, and never again. To skip even
that prompt, grant it from your computer:

```bash
adb shell pm grant com.example.music android.permission.READ_MEDIA_AUDIO
```

Files in the app's own folder (option 3 above) are always readable without any permission.

## Preconfigured playlists

Edit [`app/src/main/assets/default_playlists.json`](app/src/main/assets/default_playlists.json)
before building. Each playlist is installed once, the first time the app runs (and new ids are
installed after an update). After that it behaves like any playlist made on the watch.

```json
{
  "id": "preset-workout",
  "name": "Workout",
  "rules": { "folders": ["Workout", "Gym"], "artists": [], "albums": [], "titles": [] },
  "tracks": []
}
```

- `rules`: songs match if a folder in their path, the artist, the album or the exact title
  matches. New files dropped into a `Workout/` folder appear in the playlist automatically.
- `tracks`: exact track keys, e.g. `media:Music/Running/Song.mp3` for the shared library or
  `file:Running/Song.mp3` for the app folder.

## Project layout

```
app/src/main/java/com/example/music/
├─ MusicApplication.kt          app-wide object graph
├─ data/                        Track scanning, artwork, favorites, playlists, episode progress (JSON)
├─ playback/                    Media3 PlaybackService, MediaController wrapper, session restore
└─ presentation/
   ├─ WatchMusicApp.kt          navigation + the three-page home pager
   ├─ player/                   Now playing, options, up next
   ├─ favorites/                Favorites page
   ├─ playlists/                Library page, Music/show/playlist detail, Podcasts folder, pickers
   ├─ components/               Cover background, rows, list scaffold, icons, text input
   └─ theme/                    One UI Watch colors, cover-driven accent
```

## Libraries

| Purpose | Library |
|---|---|
| UI | Jetpack Compose for Wear OS, Material 3 (1.7) |
| Playback & media session | AndroidX Media3 ExoPlayer + Session (1.11) |
| Volume screen, bezel volume, output switcher | Google Horologist audio (0.8) |
| Icons | Material Symbols Rounded via `com.composables` icon packages |
| Cover colors | AndroidX Palette |

Unit tests cover favorites ordering and grouping, playlist rules, persistence and corrupt-file
recovery (`./gradlew :app:testDebugUnitTest`). See [docs/PLAN.md](docs/PLAN.md) for the design
plan.
