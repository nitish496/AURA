#!/usr/bin/env python3
"""Regenerates tools/stubs/media3 from the real androidx/media 1.5.1 sources.

Fetches the root API classes listed below from raw.githubusercontent.com (the only GitHub
endpoint reachable from the agent sandbox), turns them into signature-only stubs with
StubGen.java, compiles the result and keeps fetching whatever the stub signatures still
reference until everything resolves. Run from anywhere:

    python3 -I tools/stubgen/regen-media3-stubs.py

Needs: JDK 17+, network access to raw.githubusercontent.com, tools/cache/ jars (run
scripts/compile-check.sh once first so android-all + guava are downloaded).
"""
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request

TAG = "1.5.1"
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CACHE = os.path.join(ROOT, "tools", "cache")
SRC = os.path.join(CACHE, "media3-src-" + TAG)
OUT = os.path.join(ROOT, "tools", "stubs", "media3")
ANNOT = os.path.join(ROOT, "tools", "stubs", "annotation-experimental")
PLACEHOLDERS = os.path.join(ROOT, "tools", "stubs", "placeholders")
STUBGEN = os.path.join(ROOT, "tools", "stubgen", "StubGen.java")
WORK = os.path.join(ROOT, "build", "stubgen")
BASE = "https://raw.githubusercontent.com/androidx/media/%s/libraries/%s/src/main/java/%s"

ROOTS = """
androidx.media3.common.AdPlaybackState androidx.media3.common.AudioAttributes
androidx.media3.common.AuxEffectInfo androidx.media3.common.BasePlayer androidx.media3.common.C
androidx.media3.common.DeviceInfo androidx.media3.common.ForwardingPlayer
androidx.media3.common.ForwardingSimpleBasePlayer androidx.media3.common.Format
androidx.media3.common.HeartRating androidx.media3.common.IllegalSeekPositionException
androidx.media3.common.MediaItem androidx.media3.common.MediaMetadata
androidx.media3.common.Metadata androidx.media3.common.MimeTypes
androidx.media3.common.ParserException androidx.media3.common.PercentageRating
androidx.media3.common.PlaybackException androidx.media3.common.PlaybackParameters
androidx.media3.common.Player androidx.media3.common.Rating androidx.media3.common.SimpleBasePlayer
androidx.media3.common.StarRating androidx.media3.common.ThumbRating
androidx.media3.common.Timeline androidx.media3.common.TrackGroup
androidx.media3.common.TrackSelectionOverride androidx.media3.common.TrackSelectionParameters
androidx.media3.common.Tracks androidx.media3.common.VideoSize
androidx.media3.common.audio.AudioProcessor androidx.media3.common.audio.BaseAudioProcessor
androidx.media3.common.audio.ChannelMixingAudioProcessor
androidx.media3.common.audio.ChannelMixingMatrix androidx.media3.common.audio.SonicAudioProcessor
androidx.media3.common.audio.AudioManagerCompat androidx.media3.common.audio.AudioFocusRequestCompat
androidx.media3.common.text.Cue androidx.media3.common.text.CueGroup
androidx.media3.common.util.Assertions androidx.media3.common.util.BitmapLoader
androidx.media3.common.util.Clock androidx.media3.common.util.Consumer
androidx.media3.common.util.Log androidx.media3.common.util.NullableType
androidx.media3.common.util.Size androidx.media3.common.util.UnstableApi
androidx.media3.common.util.Util
androidx.media3.datasource.BaseDataSource androidx.media3.datasource.ByteArrayDataSource
androidx.media3.datasource.ContentDataSource androidx.media3.datasource.DataSink
androidx.media3.datasource.DataSource androidx.media3.datasource.DataSourceBitmapLoader
androidx.media3.datasource.DataSourceException androidx.media3.datasource.DataSpec
androidx.media3.datasource.DefaultDataSource androidx.media3.datasource.DefaultHttpDataSource
androidx.media3.datasource.FileDataSource androidx.media3.datasource.HttpDataSource
androidx.media3.datasource.HttpUtil androidx.media3.datasource.ResolvingDataSource
androidx.media3.datasource.TransferListener
androidx.media3.datasource.cache.Cache androidx.media3.datasource.cache.CacheDataSink
androidx.media3.datasource.cache.CacheDataSource androidx.media3.datasource.cache.CacheEvictor
androidx.media3.datasource.cache.CacheKeyFactory androidx.media3.datasource.cache.CacheSpan
androidx.media3.datasource.cache.CacheWriter androidx.media3.datasource.cache.ContentMetadata
androidx.media3.datasource.cache.ContentMetadataMutations
androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
androidx.media3.datasource.cache.NoOpCacheEvictor androidx.media3.datasource.cache.SimpleCache
androidx.media3.database.DatabaseProvider androidx.media3.database.StandaloneDatabaseProvider
androidx.media3.exoplayer.DefaultLoadControl androidx.media3.exoplayer.DefaultRenderersFactory
androidx.media3.exoplayer.ExoPlaybackException androidx.media3.exoplayer.ExoPlayer
androidx.media3.exoplayer.LoadControl androidx.media3.exoplayer.Renderer
androidx.media3.exoplayer.RenderersFactory androidx.media3.exoplayer.SeekParameters
androidx.media3.exoplayer.analytics.AnalyticsListener
androidx.media3.exoplayer.audio.AudioCapabilities androidx.media3.exoplayer.audio.AudioSink
androidx.media3.exoplayer.audio.DefaultAudioSink androidx.media3.exoplayer.audio.ForwardingAudioSink
androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
androidx.media3.exoplayer.audio.TeeAudioProcessor
androidx.media3.exoplayer.source.ClippingMediaSource
androidx.media3.exoplayer.source.ConcatenatingMediaSource2
androidx.media3.exoplayer.source.DefaultMediaSourceFactory
androidx.media3.exoplayer.source.MediaSource androidx.media3.exoplayer.source.ProgressiveMediaSource
androidx.media3.exoplayer.source.ShuffleOrder
androidx.media3.exoplayer.trackselection.DefaultTrackSelector
androidx.media3.exoplayer.upstream.DefaultBandwidthMeter androidx.media3.exoplayer.util.EventLogger
androidx.media3.session.CacheBitmapLoader androidx.media3.session.CommandButton
androidx.media3.session.DefaultMediaNotificationProvider androidx.media3.session.LibraryResult
androidx.media3.session.MediaBrowser androidx.media3.session.MediaController
androidx.media3.session.MediaLibraryService androidx.media3.session.MediaNotification
androidx.media3.session.MediaSession androidx.media3.session.MediaSessionService
androidx.media3.session.SessionCommand androidx.media3.session.SessionCommands
androidx.media3.session.SessionError androidx.media3.session.SessionResult
androidx.media3.session.SessionToken
""".split()

MODULES = [
    ("androidx.media3.common", "common"), ("androidx.media3.datasource", "datasource"),
    ("androidx.media3.database", "database"), ("androidx.media3.decoder", "decoder"),
    ("androidx.media3.extractor", "extractor"), ("androidx.media3.container", "container"),
    ("androidx.media3.exoplayer", "exoplayer"), ("androidx.media3.session", "session"),
]


def top_level(fqn):
    parts = fqn.split(".")
    for i, p in enumerate(parts):
        if p[:1].isupper():
            return ".".join(parts[: i + 1])
    return None


def rel_path(top):
    return top.replace(".", "/") + ".java"


def fetch(top, missing):
    rel = rel_path(top)
    dest = os.path.join(SRC, rel)
    if os.path.exists(dest):
        return True
    if not top.startswith("androidx.media3."):
        missing.add(top)
        return False
    pkg = top.rsplit(".", 1)[0]
    mods = [m for prefix, m in MODULES if pkg == prefix or pkg.startswith(prefix + ".")]
    mods += [m for _, m in MODULES if m not in mods]
    for m in mods:
        url = BASE % (TAG, m, rel)
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                data = r.read()
        except urllib.error.HTTPError as e:
            if e.code == 404:
                continue
            raise
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        with open(dest, "wb") as f:
            f.write(data)
        print("fetched", m, rel)
        return True
    missing.add(top)
    return False


def classpath():
    jars = [f for f in os.listdir(CACHE) if f.endswith(".jar")]
    pick = lambda pre: os.path.join(CACHE, sorted(j for j in jars if j.startswith(pre))[-1])
    return os.pathsep.join([pick("android-all-"), pick("guava-"), pick("failureaccess-")])


def java_files(d):
    out = []
    for base, _, files in os.walk(d):
        out += [os.path.join(base, f) for f in files if f.endswith(".java")]
    return sorted(out)


def run_stubgen(cp):
    rels = [os.path.relpath(p, SRC) for p in java_files(SRC)]
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    os.makedirs(OUT)
    cmd = ["java", "--add-exports", "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
           "--add-exports", "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
           STUBGEN, SRC, OUT, cp] + rels
    subprocess.run(cmd, check=True)


def compile_stubs(cp):
    if os.path.isdir(WORK):
        shutil.rmtree(WORK)
    os.makedirs(WORK)
    srcs = java_files(OUT) + java_files(ANNOT) + java_files(PLACEHOLDERS)
    argfile = os.path.join(WORK, "sources.txt")
    with open(argfile, "w") as f:
        f.write("\n".join(srcs))
    cmd = ["javac", "--release", "17", "-proc:none", "-nowarn", "-Xmaxerrs", "100000",
           "-encoding", "UTF-8", "-cp", cp, "-d", os.path.join(WORK, "classes"), "@" + argfile]
    p = subprocess.run(cmd, capture_output=True, text=True)
    return p.returncode, p.stderr


ERR = re.compile(r"^(?P<file>[^\n]+?\.java):(?P<line>\d+): error: (?P<msg>.*)$", re.M)


def wanted_from_errors(stderr):
    want = set()
    blocks = re.split(r"(?m)^(?=[^\s][^\n]*\.java:\d+: error:)", stderr)
    for b in blocks:
        m = ERR.match(b)
        if not m:
            continue
        msg = m.group("msg")
        sym = re.search(r"symbol:\s+(?:class|interface|enum)\s+([\w$]+)", b)
        loc = re.search(r"location:\s+(package|class|interface|enum|@interface)\s+([\w.$]+)", b)
        imp = re.search(r"^\s*import\s+(static\s+)?([\w.]+)\s*;", b, re.M)
        if "does not exist" in msg and imp:
            t = top_level(imp.group(2))
            if t:
                want.add(t)
            continue
        if sym and loc:
            name = sym.group(1)
            kind, where = loc.groups()
            if kind == "package":
                want.add(where + "." + name)
            else:
                pkg = ".".join(p for p in where.split(".") if not p[:1].isupper())
                want.add(pkg + "." + name)
        elif imp:
            t = top_level(imp.group(2))
            if t:
                want.add(t)
    return want


def main():
    cp = classpath()
    os.makedirs(SRC, exist_ok=True)
    missing = set()
    for fqn in ROOTS:
        fetch(top_level(fqn), missing)
    for rnd in range(1, 40):
        run_stubgen(cp)
        rc, err = compile_stubs(cp)
        if rc == 0:
            print("stubs compile cleanly after round", rnd, "-", len(java_files(OUT)), "files")
            break
        want = wanted_from_errors(err)
        new = [w for w in sorted(want) if not os.path.exists(os.path.join(SRC, rel_path(w)))
               and w not in missing]
        fetched = [w for w in new if fetch(w, missing)]
        print("round", rnd, ": errors reference", len(want), "types, fetched", len(fetched))
        if not fetched:
            sys.stderr.write(err[-20000:])
            print("\nunresolved (need placeholders or StubGen fixes):", sorted(missing))
            sys.exit(1)
    shutil.rmtree(WORK, ignore_errors=True)


if __name__ == "__main__":
    main()
