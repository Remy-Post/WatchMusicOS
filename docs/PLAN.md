# Watch Music: implementation plan

Target device: **Samsung Galaxy Watch8, 44 mm**: 1.47" round Super AMOLED, 480 × 480 px
(about 226 dp at 340 dpi), Exynos W1000, 2 GB RAM, 32 GB storage, Wear OS 6 / One UI 8 Watch.

## Goals (from the brief)

| # | Requirement | How it is met |
|---|-------------|---------------|
| 1 | Close one-to-one feel of Samsung's native watch Music player | Round-first now-playing screen: edge progress ring, centered title/artist, prev/play/next row, bottom utility row, bezel/rotary volume with an edge arc, black AMOLED UI |
| 2 | Tailored to 44 mm / 480 px | Layout is computed from the screen diameter (`BoxWithConstraints`), tuned for 226 dp. A matching 480 × 480 emulator profile is used for verification |
| 3 | Same files as the native app, no permission barrier | Reads the watch's shared `Music/` library through MediaStore, which includes tracks synced by Galaxy Wearable. Android requires `READ_MEDIA_AUDIO` for this, so it is asked **once**, automatically, on first launch. The app's own folder `Android/data/<pkg>/files/Music` needs no permission at all |
| 4 | Background reflects the cover | Embedded album art (or `cover.jpg`/`folder.jpg`), blurred and scrimmed full-screen, crossfading on track change; the accent color comes from the artwork (Palette) |
| 5 | Swipe left → playlists | Horizontal pager: `[Favorites] ← [Now playing] → [Playlists]` |
| 6 | Preconfigured + on-watch playlists | `assets/default_playlists.json` is installed on first launch. Playlists can be created, renamed, edited and deleted on the watch |
| 7 | Swipe right → favorites, most recent first | Favorites page grouped into *Today / This week / This month / Earlier*, newest first |
| 8 | Heart icon adds to Favorites | Heart on the now-playing screen and in the system media controls (MediaSession command button) |
| 9 | On-watch storage only, no cloud | All state is plain JSON in the app's private storage (`files/library/*.json`), written atomically. No network permission is declared |

## Architecture

```
MusicApplication ── AppGraph (singletons, one process)
  ├─ data/
  │   ├─ Track, Playlist, Favorite              (models)
  │   ├─ TrackScanner                           (MediaStore + app folder walk)
  │   ├─ TrackRepository  StateFlow<Library>    (refresh, ContentObserver)
  │   ├─ LibraryStore     favorites.json / playlists.json / seed-from-assets
  │   ├─ PlaylistRules    folder/artist/album/title matching for preconfigured lists
  │   └─ ArtworkLoader    embedded art → downsampled bitmap, LRU cache, palette
  ├─ playback/
  │   ├─ PlaybackService  Media3 MediaSessionService + ExoPlayer, heart button,
  │   │                   last-session persistence + playback resumption
  │   └─ PlayerConnection MediaController → StateFlow<PlayerUiState>
  └─ ui/
      ├─ MainActivity     one-time permission, edge-to-edge, keep-alive
      ├─ WatchMusicApp    SwipeDismissableNavHost
      ├─ home/HomePager   HorizontalPagerScaffold (3 pages, starts on Now playing)
      ├─ player/          NowPlaying, ProgressRing, VolumeArc, PlayerOptions
      ├─ favorites/       FavoritesScreen (recency groups)
      ├─ playlists/       PlaylistsScreen, PlaylistDetail, AddToPlaylist, Rename
      └─ components/      CoverBackground, TrackChip, Material Symbols icons
```

## Tasks

1. **Scaffold.** Update the version catalog (Wear Compose M3 1.7.1, Media3 1.11.1, navigation, palette, wear-input, Horologist audio, Material Symbols icons), set up the manifest (service, permissions, `<queries>` for the output switcher) and the app icon.
2. **Storage layer.** `LibraryStore` uses JSON with atomic writes, seeds from assets once, and exposes flows. Unit tests cover ordering, grouping, rule matching and persistence round trips.
3. **Track scanning.** MediaStore query (music only), app-folder walk with metadata cache, change observer.
4. **Artwork.** Embedded picture → sampled bitmap, folder-art fallback, LRU cache, dominant color.
5. **Playback.** `PlaybackService` (ExoPlayer, audio focus, becoming-noisy, wake lock), favorite command button, last-session restore.
6. **Now playing UI.** Cover background, edge progress ring, controls, heart, volume (button + bezel), options.
7. **Favorites page.** Grouped by recency, tap to play the favorites queue from that track.
8. **Playlists page.** Built-in smart lists (All songs, Recently added) plus user lists. Create, rename, add/remove tracks, delete.
9. **Verify.** Build, run the unit tests, then install on the 480 × 480 emulator with sample tracks, check every gesture and screenshot each screen.
10. **Ship.** README covering how to load music onto the watch and how to preconfigure playlists, then commit and push to the provided repo.

## Notes and constraints

- Wear OS guidance discourages media on the built-in speaker. Galaxy Watch8 has one, and the native Samsung player allows it, so playback is **not** suppressed. The output button opens the system output switcher, with Bluetooth settings as a fallback.
- No `INTERNET` permission is declared, so the app cannot reach any cloud service.
- `READ_MEDIA_AUDIO` cannot be avoided for files other apps put on the watch; it is the only runtime permission requested. For a zero-prompt setup it can be pre-granted from a computer:
  `adb shell pm grant com.example.music android.permission.READ_MEDIA_AUDIO`.
