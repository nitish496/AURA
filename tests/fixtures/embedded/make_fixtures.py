"""Writes tag-only audio fixtures with mutagen (the same library the CI demo library uses).
Lyrics are original placeholder lines written for these tests. Regenerate (needs `pip install mutagen`): python3 tests/fixtures/embedded/make_fixtures.py tests/fixtures/embedded"""
import os, struct, sys
from mutagen.id3 import ID3, TIT2, TPE1, TALB, USLT, SYLT, APIC, Encoding
from mutagen.flac import FLAC, Picture

out = sys.argv[1]
LRC = "\n".join([
    "[00:00.80]Salt on the window, morning on the line",
    "[00:05.20]Counting the ferries as they cross the bay",
    "[00:09.70]",
    "[00:11.30]Every lantern answers in its own time",
    "[00:16.05]We keep the small light burning anyway",
])
PLAIN = "Salt on the window, morning on the line\nCounting the ferries as they cross the bay\n\nEvery lantern answers in its own time"
# Two fake MPEG-1 Layer III frame headers + padding stand in for audio; parsers must stop at the tag.
AUDIO = (b"\xff\xfb\x90\x64" + b"\x00" * 413) * 2
# A tiny but valid 1x1 PNG so APIC/PICTURE frames sit in front of or after the lyrics like real files.
PNG = bytes.fromhex("89504e470d0a1a0a0000000d4948445200000001000000010806000000"
                    "1f15c4890000000d49444154789c6360000002000154a24f5d0000000049454e44ae426082")

def mp3(name, frames, v2_version):
    path = os.path.join(out, name)
    with open(path, "wb") as f:
        f.write(AUDIO)
    tags = ID3()
    for frame in frames:
        tags.add(frame)
    tags.save(path, v2_version=v2_version)

# Exactly the CI demo shape: ID3v2.4, UTF-8 USLT, lang "eng", empty descriptor, LRC-stamped text.
mp3("mutagen_uslt_v24.mp3", [
    TIT2(encoding=Encoding.UTF8, text="Paper Lanterns"),
    TPE1(encoding=Encoding.UTF8, text="Juniper Static"),
    TALB(encoding=Encoding.UTF8, text="Night Ferries"),
    APIC(encoding=Encoding.UTF8, mime="image/png", type=3, desc="", data=PNG * 40),
    USLT(encoding=Encoding.UTF8, lang="eng", desc="", text=LRC),
], 4)
# ID3v2.3 with UTF-16 (BOM) plain lyrics.
mp3("mutagen_uslt_v23_utf16.mp3", [
    TIT2(encoding=Encoding.UTF16, text="Glasshouse"),
    USLT(encoding=Encoding.UTF16, lang="eng", desc="", text=PLAIN + "\nÉté — déjà vu"),
], 3)
# ID3v2.4 SYLT with millisecond timestamps (format 2), content type 1 (lyrics).
mp3("mutagen_sylt_v24.mp3", [
    TIT2(encoding=Encoding.UTF8, text="Overpass Bloom"),
    SYLT(encoding=Encoding.UTF8, lang="eng", format=2, type=1, desc="",
         text=[("First light on the overpass", 1200), ("Engines hum a lower key", 4800), ("Hold the bloom until it lasts", 9100)]),
], 4)

# FLAC: STREAMINFO (44.1 kHz, stereo, 16-bit), then mutagen adds a PICTURE and Vorbis comments.
flac_path = os.path.join(out, "mutagen_vorbis.flac")
streaminfo = struct.pack(">HH", 4096, 4096) + b"\x00\x00\x00" + b"\x00\x00\x00"
packed = (44100 << 44) | ((2 - 1) << 41) | ((16 - 1) << 36) | 0
streaminfo += packed.to_bytes(8, "big") + b"\x00" * 16
with open(flac_path, "wb") as f:
    f.write(b"fLaC" + bytes([0x80]) + len(streaminfo).to_bytes(3, "big") + streaminfo)
flac = FLAC(flac_path)
picture = Picture()
picture.type = 3
picture.mime = "image/png"
picture.width = picture.height = 1
picture.depth = 32
picture.data = PNG * 400
flac.add_picture(picture)
flac["TITLE"] = "Low Tide Signals"
flac["ARTIST"] = "Marlow Fields"
flac["LYRICS"] = LRC
flac["UNSYNCEDLYRICS"] = PLAIN
flac.save()
for name in sorted(os.listdir(out)):
    print(name, os.path.getsize(os.path.join(out, name)))
