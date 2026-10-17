#!/usr/bin/env python3
"""
Copy music and podcasts from this Mac onto the watch, sized to fit.

  python3 tools/watch_sync.py prepare            # convert podcasts for the watch (cached, re-runnable)
  python3 tools/watch_sync.py plan  -s SERIAL    # what fits in the watch's free space
  python3 tools/watch_sync.py push  -s SERIAL    # copy it over and index it

Layout on the watch (the shared library Samsung's player also reads):
  /sdcard/Music/<Artist folder>/...      music, same audio, tags cleaned up for Android
  /sdcard/Podcasts/<Show>/<file>.m4a     episodes, re-encoded to mono AAC for speech

Music audio is never re-encoded. Its tags are rewritten as plain ID3v2.3 (no ID3v1), and huge
embedded covers (some are 1600 px PNGs, half the file) are scaled to 600 px JPEG. Android's
media scanner gives up on oversized tags, which shows up as missing artists and titles cut off
at 30 characters.

Podcasts are re-encoded because the source files are music-grade (112–320 kbps stereo) and
run 1.5–3 hours each. Mono AAC at 48 kbps is clear for speech at a fraction of the size.
Originals on the Mac are never modified.
"""

import argparse
import concurrent.futures as futures
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

MUSIC_ROOT = Path.home() / "Music"
CACHE = MUSIC_ROOT / ".watch-cache"

MUSIC_FOLDERS = ["Juice WRLD"]
PODCAST_FOLDERS = [
    "Huberman Lab",
    "Modern Wisdom",
    "The Ramsey Show",
    "John Delony Show",
    "Financial Audit",
    "Smart Money Happy Hour",
    "Flow State with Harry Mack",
    "ADHD & Autism",
]
AUDIO_EXT = {".mp3", ".m4a", ".aac", ".flac", ".ogg", ".opus", ".wav"}
PODCAST_BITRATE = "48k"
# Leave room on the watch for the OS, apps and updates.
RESERVE_BYTES = 2 * 1024**3

DATE_RE = re.compile(r"(\d{4}-\d{2}-\d{2})")


def audio_files(folder: Path):
    return sorted(p for p in folder.rglob("*") if p.is_file() and p.suffix.lower() in AUDIO_EXT and not p.name.startswith("."))


def episode_sort_key(path: Path):
    """Newest first: by the date in the file name, else by the leading number."""
    m = DATE_RE.search(path.name)
    if m:
        return (0, m.group(1))
    n = re.match(r"(\d+)", path.name)
    return (1, f"{int(n.group(1)):06d}" if n else path.name)


def probe_tags(path: Path) -> dict:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format_tags", "-of", "json", str(path)],
        capture_output=True, text=True,
    )
    try:
        return {k.lower(): v for k, v in json.loads(out.stdout).get("format", {}).get("tags", {}).items()}
    except json.JSONDecodeError:
        return {}


def has_cover(path: Path) -> bool:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=codec_type", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True,
    )
    return "video" in out.stdout


def clean_title(name: str) -> str:
    stem = Path(name).stem
    stem = re.sub(r"^\d+\s*-\s*", "", stem)          # "01 - "
    stem = re.sub(r"^\d{4}-\d{2}-\d{2}\s*-\s*", "", stem)  # "2026-09-14 - "
    return stem.strip()


def duration(path: Path) -> float:
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(path)],
        capture_output=True, text=True,
    )
    try:
        return float(out.stdout.strip())
    except ValueError:
        return 0.0


def convert_episode(src: Path, show: str) -> Path:
    dst = CACHE / "Podcasts" / show / (src.stem + ".m4a")
    src_len = duration(src)
    # Reuse earlier output only if it really holds the whole episode.
    if dst.exists() and duration(dst) >= src_len * 0.98:
        return dst
    dst.parent.mkdir(parents=True, exist_ok=True)
    audio = dst.with_name(dst.stem + ".audio.part.m4a")
    cover = dst.with_name(dst.stem + ".cover.part.jpg")
    final = dst.with_name(dst.stem + ".part.m4a")
    tags = probe_tags(src)
    title = tags.get("title") or clean_title(src.name)
    date = DATE_RE.search(src.name)
    meta = [
        "-map_metadata", "-1",
        "-metadata", f"title={title}",
        "-metadata", f"artist={show}",
        "-metadata", f"album={show}",
        "-metadata", "genre=Podcast",
    ]
    if date:
        meta += ["-metadata", f"date={date.group(1)}"]
    try:
        # 1) Speech-grade audio. 2) Attach the cover in a separate pass: copying an attached
        #    picture while encoding makes the MP4 muxer stop after the picture's single frame.
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(src), "-map", "0:a:0", "-vn",
             "-c:a", "aac", "-b:a", PODCAST_BITRATE, "-ac", "1", "-ar", "44100", *meta, str(audio)],
            check=True,
        )
        if has_cover(src):
            subprocess.run(
                ["ffmpeg", "-v", "error", "-y", "-i", str(src), "-map", "0:v:0", "-frames:v", "1", "-c:v", "copy", str(cover)],
                check=True,
            )
            subprocess.run(
                ["ffmpeg", "-v", "error", "-y", "-i", str(audio), "-i", str(cover), "-map", "0:a", "-map", "1:v",
                 "-c", "copy", "-disposition:v:0", "attached_pic", "-movflags", "+faststart", str(final)],
                check=True,
            )
            audio.unlink()
        else:
            audio.rename(final)
        if duration(final) < src_len * 0.98:
            raise RuntimeError(f"{src.name}: converted file is shorter than the original")
        final.rename(dst)
    finally:
        for p in (audio, cover, final):
            if p.exists():
                p.unlink()
    return dst


def convert_song(src: Path) -> Path:
    rel = src.relative_to(MUSIC_ROOT)
    dst = CACHE / "Music" / rel
    if dst.exists() and dst.stat().st_mtime >= src.stat().st_mtime and dst.stat().st_size > 0:
        return dst
    dst.parent.mkdir(parents=True, exist_ok=True)
    if src.suffix.lower() != ".mp3":
        shutil.copy2(src, dst)
        return dst
    tmp = dst.with_name(dst.stem + ".part.mp3")
    try:
        cmd = ["ffmpeg", "-v", "error", "-y", "-i", str(src), "-map", "0:a:0", "-c:a", "copy"]
        if has_cover(src):
            cmd += ["-map", "0:v:0", "-c:v", "mjpeg", "-q:v", "3",
                    "-vf", "scale=600:600:force_original_aspect_ratio=decrease",
                    "-disposition:v:0", "attached_pic"]
        cmd += ["-id3v2_version", "3", "-write_id3v1", "0", str(tmp)]
        subprocess.run(cmd, check=True)
        if duration(tmp) < duration(src) * 0.98:
            raise RuntimeError(f"{src.name}: cleaned file is shorter than the original")
        tmp.rename(dst)
    except (subprocess.CalledProcessError, RuntimeError):
        # Never lose a song over a tag problem: fall back to the original file.
        shutil.copy2(src, dst)
    finally:
        if tmp.exists():
            tmp.unlink()
    return dst


def prepare(jobs: int):
    if not shutil.which("ffmpeg"):
        sys.exit("ffmpeg is required: brew install ffmpeg")
    songs = [src for folder in MUSIC_FOLDERS for src in audio_files(MUSIC_ROOT / folder)]
    print(f"Cleaning tags on {len(songs)} songs → {CACHE / 'Music'}", flush=True)
    with futures.ThreadPoolExecutor(max_workers=jobs) as pool:
        for i, _ in enumerate(pool.map(convert_song, songs), 1):
            if i % 100 == 0 or i == len(songs):
                print(f"  {i}/{len(songs)}", flush=True)

    work = []
    for show in PODCAST_FOLDERS:
        for src in audio_files(MUSIC_ROOT / show):
            work.append((src, show))
    print(f"Converting {len(work)} episodes with {jobs} workers → {CACHE / 'Podcasts'}", flush=True)
    done = 0
    with futures.ThreadPoolExecutor(max_workers=jobs) as pool:
        for fut in futures.as_completed(pool.submit(convert_episode, s, show) for s, show in work):
            done += 1
            try:
                fut.result()
            except (subprocess.CalledProcessError, RuntimeError) as e:
                print(f"  failed: {e}", flush=True)
            if done % 10 == 0 or done == len(work):
                print(f"  {done}/{len(work)}", flush=True)


# ---------------------------------------------------------------- watch side

def adb(serial, *args, check=True, capture=True, retries=6):
    """Runs an adb command, reconnecting if the watch dropped off Wi-Fi (it does when its screen sleeps)."""
    for attempt in range(retries):
        result = subprocess.run(["adb", "-s", serial, *args], capture_output=capture, text=True)
        if result.returncode == 0 or not check:
            return result
        if attempt < retries - 1:
            time.sleep(3 + attempt * 2)
            if ":" in serial:
                subprocess.run(["adb", "connect", serial], capture_output=True)
            subprocess.run(["adb", "-s", serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP"], capture_output=True)
    raise subprocess.CalledProcessError(result.returncode, result.args, result.stdout, result.stderr)


def watch_free_bytes(serial) -> int:
    out = adb(serial, "shell", "df", "-k", "/sdcard").stdout.strip().splitlines()[-1].split()
    return int(out[3]) * 1024


def remote_files(serial, root) -> dict:
    out = adb(serial, "shell", f"find '{root}' -type f -exec stat -c '%s %n' {{}} + 2>/dev/null", check=False).stdout
    files = {}
    for line in out.splitlines():
        size, _, path = line.partition(" ")
        if size.isdigit():
            files[path] = int(size)
    return files


def build_plan(serial):
    """Music first (all of it), then podcasts round-robin newest episode per show, until full."""
    on_watch = {**remote_files(serial, "/sdcard/Music"), **remote_files(serial, "/sdcard/Podcasts")}
    budget = watch_free_bytes(serial) - RESERVE_BYTES
    items = []  # (local, remote, size)

    for folder in MUSIC_FOLDERS:
        for src in audio_files(CACHE / "Music" / folder):
            if ".part." in src.name:
                continue
            rel = src.relative_to(CACHE / "Music")
            items.append((src, f"/sdcard/Music/{rel.as_posix()}", src.stat().st_size, folder))

    queues = []
    for show in PODCAST_FOLDERS:
        eps = [p for p in (CACHE / "Podcasts" / show).glob("*.m4a") if ".part." not in p.name]
        eps.sort(key=episode_sort_key, reverse=True)
        queues.append([(p, f"/sdcard/Podcasts/{show}/{p.name}", p.stat().st_size, show) for p in eps])
    while any(queues):
        for q in queues:
            if q:
                items.append(q.pop(0))

    chosen, skipped, needed = [], [], 0
    for local, remote, size, group in items:
        if on_watch.get(remote) == size:
            chosen.append((local, remote, size, group, True))
            continue
        if needed + size <= budget:
            needed += size
            chosen.append((local, remote, size, group, False))
        else:
            skipped.append((local, remote, size, group))
    return chosen, skipped, budget, needed


def fmt(n):
    return f"{n / 1024**3:.2f} GB"


def plan(serial):
    chosen, skipped, budget, needed = build_plan(serial)
    print(f"Free on watch (after {fmt(RESERVE_BYTES)} reserve): {fmt(budget)}")
    groups = {}
    for _, _, size, group, present in chosen:
        g = groups.setdefault(group, [0, 0, 0])
        g[0] += 1
        g[1] += size
        g[2] += present
    for group, (n, size, present) in groups.items():
        print(f"  {group:32s} {n:5d} files  {fmt(size):>9s}  ({present} already on watch)")
    if skipped:
        print(f"  Not enough room for {len(skipped)} files ({fmt(sum(s[2] for s in skipped))}): oldest episodes skipped.")
    print(f"To copy now: {fmt(needed)}")
    return chosen


def push(serial):
    chosen = plan(serial)
    todo = [c for c in chosen if not c[4]]
    made = set()
    started, sent = time.time(), 0
    for i, (local, remote, size, group, _) in enumerate(todo, 1):
        parent = str(Path(remote).parent)
        if parent not in made:
            adb(serial, "shell", f"mkdir -p '{parent}'")
            made.add(parent)
        adb(serial, "push", str(local), remote)
        sent += size
        if i % 20 == 0 or i == len(todo):
            rate = sent / max(time.time() - started, 1) / 1024**2
            print(f"  copied {i}/{len(todo)}  {fmt(sent)}  {rate:.1f} MB/s", flush=True)
    # Files written through adb are indexed by the media provider as they land; a volume scan
    # makes sure nothing is missed.
    adb(serial, "shell", "content", "call", "--method", "scan_volume", "--uri", "content://media",
        "--arg", "external_primary", check=False)
    print("Done.")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("command", choices=["prepare", "plan", "push"])
    ap.add_argument("-s", "--serial", help="adb serial of the watch (see `adb devices`)")
    ap.add_argument("-j", "--jobs", type=int, default=max(2, (os.cpu_count() or 4) - 2))
    args = ap.parse_args()
    if args.command == "prepare":
        prepare(args.jobs)
    else:
        if not args.serial:
            sys.exit("--serial is required (the watch's id from `adb devices`)")
        plan(args.serial) if args.command == "plan" else push(args.serial)


if __name__ == "__main__":
    main()
