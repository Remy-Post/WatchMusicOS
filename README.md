# Music for Galaxy Watch

A standalone Wear OS music player built for the **Samsung Galaxy Watch8 (44 mm, 480 × 480)**. It is
modeled on One UI Watch's own Music app and plays only the music stored on the watch. No cloud,
no phone, no network permission.

| Swipe right ← | Home | → Swipe left |
|---|---|---|
| **Favorites**, newest first, grouped *Today / This week / This month / Earlier* | **Now playing**: cover-tinted background, progress ring around the bezel, ♥ to like | **Playlists**: All songs, Recently added, Favorites, plus your own and preconfigured lists |

## Features

- **Same files as Samsung's player.** Reads the watch's shared music library (MediaStore), which is
  where Galaxy Wearable puts the tracks you send from your phone. It also plays anything in its own
  folder, which needs no permission at all.
- **Cover-reflecting background.** Embedded album art (or `cover.jpg` / `folder.jpg` next to the
  file) is blurred behind the player. The accent color for the ring, highlights and the volume arc
  is taken from the cover.
- **One-tap favorites.** The ♥ on the player saves the song to Favorites instantly, and the system
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
├─ data/                        Track scanning, artwork, favorites & playlists (JSON), pure logic
├─ playback/                    Media3 PlaybackService, MediaController wrapper, session restore
└─ presentation/
   ├─ WatchMusicApp.kt          navigation + the three-page home pager
   ├─ player/                   Now playing, options, up next
   ├─ favorites/                Favorites page
   ├─ playlists/                Playlists, playlist detail, pickers
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
