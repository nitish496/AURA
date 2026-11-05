#!/usr/bin/env python3
"""Generate a fictional demo music library for emulator screenshots.

Creates MP3 files (silent audio, real ID3v2.4 tags, embedded cover art and, for a
few songs, embedded LRC-timestamped lyrics written for this project) so the app
can be exercised and photographed without any copyrighted material.

Usage: demo_media.py OUT_DIR [--sheet contact.png]
Requires: ffmpeg on PATH, Pillow, numpy, mutagen, Inter fonts (fonts-inter).
"""
import argparse
import math
import os
import random
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from mutagen.id3 import APIC, ID3, TALB, TCON, TDRC, TIT2, TPE1, TPE2, TRCK, USLT

SIZE = 1200
FONT_DIRS = ["/usr/share/fonts/opentype/inter", "/usr/share/fonts/truetype/inter"]


def font(name, size):
    for d in FONT_DIRS:
        for ext in (".otf", ".ttf"):
            p = os.path.join(d, name + ext)
            if os.path.exists(p):
                return ImageFont.truetype(p, size)
    return ImageFont.load_default(size)


def hexrgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def grain(img, amount=10, seed=0):
    rng = np.random.default_rng(seed)
    a = np.asarray(img).astype(np.int16)
    noise = rng.normal(0, amount, a.shape[:2])[..., None]
    return Image.fromarray(np.clip(a + noise, 0, 255).astype(np.uint8))


def vgrad(top, bottom, size=SIZE, curve=1.0):
    t = np.linspace(0, 1, size)[:, None] ** curve
    a, b = np.array(hexrgb(top)), np.array(hexrgb(bottom))
    rows = (a * (1 - t) + b * t)
    return Image.fromarray(np.repeat(rows[:, None, :], size, axis=1).astype(np.uint8))


def label(d, text, xy, size, fill, weight="Inter-Medium", tracking=0):
    f = font(weight, size)
    x, y = xy
    if tracking == 0:
        d.text((x, y), text, font=f, fill=fill)
        return
    for ch in text:
        d.text((x, y), ch, font=f, fill=fill)
        x += d.textlength(ch, font=f) + tracking


# ---------------------------------------------------------------- cover styles
def cover_low_tide(a):
    img = vgrad("#E9DEC8", "#D7C6A6")
    sea = vgrad("#2C6170", "#15363F")
    img.paste(sea.crop((0, 0, SIZE, 560)), (0, 640))
    d = ImageDraw.Draw(img)
    d.ellipse((470, 470, 730, 730), fill=hexrgb("#D9785B"))
    img.paste(sea.crop((0, 0, SIZE, 560)), (0, 640))
    for i, y in enumerate(range(660, 1200, 46)):
        d.line((0, y, SIZE, y), fill=(255, 255, 255, 0) if i % 2 else hexrgb("#3B7686"), width=2)
    label(d, a["artist"].upper(), (72, 80), 34, hexrgb("#15363F"), "Inter-SemiBold", 6)
    label(d, a["title"], (72, 126), 34, hexrgb("#15363F"), "Inter-Regular")
    return img


def cover_paper_moons(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#F1E9DA"))
    d = ImageDraw.Draw(img)
    d.ellipse((330, 250, 1010, 930), fill=hexrgb("#C4573A"))
    d.ellipse((190, 360, 760, 930), outline=hexrgb("#2B2622"), width=5)
    label(d, a["title"].upper(), (72, 1060), 30, hexrgb("#2B2622"), "Inter-SemiBold", 8)
    label(d, a["artist"], (72, 1104), 30, hexrgb("#2B2622"), "Inter-Regular")
    return img


def cover_concrete(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#BDB9B1"))
    d = ImageDraw.Draw(img)
    cols = ["#5E6B3A", "#D3A23C", "#2E2D2A", "#5E6B3A", "#E7E3DA", "#D3A23C"]
    rng = random.Random(3)
    cell = 300
    for gy in range(4):
        for gx in range(4):
            if rng.random() < 0.55:
                c = cols[rng.randrange(len(cols))]
                x0, y0 = gx * cell, gy * cell
                if rng.random() < 0.5:
                    d.rectangle((x0, y0, x0 + cell, y0 + cell), fill=hexrgb(c))
                else:
                    d.pieslice((x0 - cell, y0, x0 + cell, y0 + 2 * cell), 270, 360, fill=hexrgb(c))
    d.rectangle((0, 1060, SIZE, SIZE), fill=hexrgb("#1E1D1B"))
    label(d, a["artist"].upper(), (60, 1100), 34, hexrgb("#E7E3DA"), "Inter-Bold", 4)
    label(d, a["title"], (700, 1100), 34, hexrgb("#BDB9B1"), "Inter-Regular")
    return img


def cover_night_bus(a):
    img = vgrad("#0E1726", "#1B2A3F", curve=1.6)
    glow = Image.new("RGB", (SIZE, SIZE), (0, 0, 0))
    g = ImageDraw.Draw(glow)
    rng = random.Random(11)
    for _ in range(70):
        x, y = rng.randrange(SIZE), rng.randrange(380, 900)
        r = rng.randrange(12, 70)
        c = hexrgb(rng.choice(["#F2A541", "#F6E7C8", "#E86A33", "#F2A541"]))
        g.ellipse((x - r, y - r, x + r, y + r), fill=c)
    glow = glow.filter(ImageFilter.GaussianBlur(18))
    img = Image.fromarray(np.clip(np.asarray(img).astype(np.int16) + np.asarray(glow).astype(np.int16) * 0.8, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    d.line((0, 960, SIZE, 960), fill=hexrgb("#F2A541"), width=3)
    label(d, a["title"], (72, 1010), 64, hexrgb("#F6E7C8"), "InterDisplay-SemiBold")
    label(d, a["artist"], (72, 1090), 34, hexrgb("#A9B4C4"), "Inter-Regular")
    return img


def cover_field(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#1E3B2F"))
    d = ImageDraw.Draw(img)
    rng = random.Random(5)
    for i, x in enumerate(range(80, 1120, 13)):
        h = 140 + 360 * abs(math.sin(i * 0.17)) * (0.6 + 0.4 * rng.random())
        d.line((x, 760 - h, x, 760), fill=hexrgb("#CFE3D4"), width=3)
    label(d, a["artist"].upper(), (80, 880), 30, hexrgb("#CFE3D4"), "Inter-SemiBold", 6)
    label(d, a["title"], (80, 926), 52, hexrgb("#F0F5EE"), "InterDisplay-Medium")
    return img


def cover_kinetic(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#0C0C0C"))
    d = ImageDraw.Draw(img)
    d.polygon([(0, 820), (SIZE, 260), (SIZE, 560), (0, 1120)], fill=hexrgb("#E0362C"))
    label(d, a["title"].upper(), (72, 90), 120, hexrgb("#F4F1EC"), "InterDisplay-Black", 2)
    label(d, a["artist"].upper(), (76, 230), 30, hexrgb("#9C9893"), "Inter-SemiBold", 8)
    return img


def cover_bloom(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#EAD1C5"))
    layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for k in range(7):
        petal = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        p = ImageDraw.Draw(petal)
        p.ellipse((520, 230, 680, 600), fill=hexrgb("#A8472F") + (120,))
        layer = Image.alpha_composite(layer, petal.rotate(k * 360 / 7, center=(600, 600)))
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    d = ImageDraw.Draw(img)
    d.ellipse((560, 560, 640, 640), fill=hexrgb("#5B2316"))
    label(d, a["artist"], (72, 1080), 32, hexrgb("#5B2316"), "Inter-Medium")
    label(d, a["title"].upper(), (72, 80), 32, hexrgb("#5B2316"), "Inter-SemiBold", 8)
    return img


def cover_mono(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#EDEDEA"))
    d = ImageDraw.Draw(img)
    f = font("InterDisplay-Black", 420)
    d.text((-30, 330), "SUM", font=f, fill=hexrgb("#111111"))
    d.text((-30, 690), "MER", font=f, fill=hexrgb("#111111"))
    label(d, a["artist"].upper(), (72, 90), 30, hexrgb("#111111"), "Inter-SemiBold", 6)
    label(d, "Monochrome", (72, 136), 30, hexrgb("#6B6B66"), "Inter-Regular")
    return img


def cover_static(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#1F4FB8"))
    rng = np.random.default_rng(9)
    band = (rng.random((240, SIZE)) > 0.5).astype(np.uint8)
    band = np.repeat(np.repeat(band[::6, ::6], 6, axis=0), 6, axis=1)[:240, :SIZE]
    arr = np.asarray(img).copy()
    orange = np.array(hexrgb("#F07A2B"))
    arr[600:840][band == 1] = orange
    img = Image.fromarray(arr)
    d = ImageDraw.Draw(img)
    label(d, a["title"], (72, 900), 70, hexrgb("#F4F1EC"), "InterDisplay-Bold")
    label(d, a["artist"], (72, 990), 34, hexrgb("#C9D6F2"), "Inter-Regular")
    return img


def cover_glasshouse(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#212528"))
    base = img.convert("RGBA")
    for i, (x, y) in enumerate([(180, 220), (430, 380), (300, 600), (620, 260), (560, 560)]):
        pane = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        ImageDraw.Draw(pane).rectangle((x, y, x + 380, y + 380), fill=hexrgb("#9ED9C4") + (60 + i * 18,))
        base = Image.alpha_composite(base, pane)
    img = base.convert("RGB")
    d = ImageDraw.Draw(img)
    label(d, a["title"].upper(), (72, 1070), 30, hexrgb("#9ED9C4"), "Inter-SemiBold", 8)
    label(d, a["artist"], (72, 1112), 30, hexrgb("#C9CED1"), "Inter-Regular")
    return img


def cover_dust(a):
    img = vgrad("#D2A24A", "#5A3A1E", curve=0.8)
    d = ImageDraw.Draw(img)
    for i in range(14):
        x = -400 + i * 140
        d.line((600, 520, x, SIZE), fill=hexrgb("#F2D9A6"), width=2)
    d.rectangle((0, 0, SIZE, 520), fill=hexrgb("#E9C77E"))
    d.ellipse((520, 380, 680, 540), fill=hexrgb("#F6E9C9"))
    d.rectangle((0, 520, SIZE, 524), fill=hexrgb("#5A3A1E"))
    label(d, a["artist"].upper(), (72, 80), 32, hexrgb("#5A3A1E"), "Inter-Bold", 6)
    label(d, a["title"], (72, 126), 32, hexrgb("#5A3A1E"), "Inter-Regular")
    return img


def cover_afterlight(a):
    img = Image.new("RGB", (SIZE, SIZE), hexrgb("#3E0D10"))
    yy, xx = np.mgrid[0:SIZE, 0:SIZE]
    dist = np.sqrt((xx - 600) ** 2 + (yy - 1250) ** 2)
    t = np.clip(1 - dist / 900, 0, 1) ** 1.8
    amber, red = np.array(hexrgb("#F2A33A")), np.array(hexrgb("#3E0D10"))
    arr = (red * (1 - t[..., None]) + amber * t[..., None]).astype(np.uint8)
    img = Image.fromarray(arr)
    d = ImageDraw.Draw(img)
    label(d, a["title"], (72, 90), 88, hexrgb("#F7E6CF"), "InterDisplay-SemiBold")
    label(d, a["artist"], (76, 196), 34, hexrgb("#E2B48E"), "Inter-Regular")
    return img


# ---------------------------------------------------------------- catalogue
PAPER_MOONS_LRC = """[00:12.40]I folded the evening in half
[00:17.90]and kept it under my coat
[00:23.60]the streetlights were humming a song
[00:29.10]that nobody wrote
[00:34.80]Paper moons over the river
[00:40.30]hanging on threads of light
[00:45.90]you said they would never last
[00:51.40]but they lasted the night
[00:58.20]
[01:02.70]We counted the trains going east
[01:08.10]and named every one that we missed
[01:13.80]the city was quiet enough
[01:19.20]to hear what it kissed
[01:24.90]Paper moons over the river
[01:30.40]hanging on threads of light
[01:36.00]you said they would never last
[01:41.50]but they lasted the night
[01:48.30]
[01:52.60]And if the morning comes too soon
[01:58.10]I'll cut another moon
[02:03.70]and hang it where you'll see
[02:09.20]the shape of you and me
[02:16.80]Paper moons over the river
[02:22.30]hanging on threads of light
[02:27.90]you said they would never last
[02:33.40]but they lasted the night
"""

NIGHT_BUS_LRC = """[00:09.00]Window seat, the glass is cold
[00:14.20]every stop a story told
[00:19.60]orange light on empty streets
[00:24.80]the engine keeps a steady beat
[00:31.00]Night bus home, night bus home
[00:36.40]all the city on its own
[00:41.70]I don't mind the long way round
[00:47.00]when the world is this quiet now
[00:54.50]
[00:58.20]Driver hums a song I know
[01:03.40]radio is turned down low
[01:08.80]someone sleeping in the back
[01:14.00]dreaming of the railway track
[01:20.20]Night bus home, night bus home
[01:25.60]all the city on its own
[01:30.90]I don't mind the long way round
[01:36.20]when the world is this quiet now
"""

ALBUMS = [
    dict(title="Paper Moons", artist="Mira Vale", year=2025, genre="Indie Pop", style=cover_paper_moons,
         tracks=[("Paper Moons", 214, PAPER_MOONS_LRC), ("Ferris Wheel", 198, None), ("Letters to June", 241, None),
                 ("Kite Season", 187, None), ("Small Hours", 263, None)]),
    dict(title="Night Bus Home", artist="Juno Park", year=2024, genre="Electronic", style=cover_night_bus,
         tracks=[("Night Bus Home", 226, NIGHT_BUS_LRC), ("Sodium", 251, None), ("Last Stop", 207, None),
                 ("Underpass", 239, None)]),
    dict(title="Low Tide", artist="The Saltwater Choir", year=2023, genre="Folk", style=cover_low_tide,
         tracks=[("Low Tide", 232, None), ("Harbour Lights", 205, None), ("Driftwood", 249, None),
                 ("The Long Swim", 301, None)]),
    dict(title="Concrete Gardens", artist="Okafor & Lind", year=2025, genre="Jazz", style=cover_concrete,
         tracks=[("Concrete Gardens", 318, None), ("Blue Hour Walk", 274, None), ("Fifth Floor", 256, None),
                 ("Ochre", 289, None)]),
    dict(title="Field Recordings", artist="Hollis Arden", year=2022, genre="Ambient", style=cover_field,
         tracks=[("Morning Field", 364, None), ("Hedgerow", 297, None), ("Rain on Tin", 341, None)]),
    dict(title="Kinetic", artist="Sable Coast", year=2025, genre="Rock", style=cover_kinetic,
         tracks=[("Kinetic", 203, None), ("Red Line", 191, None), ("Overdrive Heart", 222, None),
                 ("Static Bloom", 238, None), ("Engines", 210, None)]),
    dict(title="Slow Bloom", artist="Ama Mensah", year=2024, genre="R&B", style=cover_bloom,
         tracks=[("Slow Bloom", 229, None), ("Honey Light", 214, None), ("Stay a While", 247, None),
                 ("Petals", 198, None)]),
    dict(title="Monochrome Summer", artist="The Quiet Hours", year=2023, genre="Alternative", style=cover_mono,
         tracks=[("Monochrome Summer", 236, None), ("Grey Skies, Warm Hands", 219, None), ("Shutter", 201, None)]),
    dict(title="Salt & Static", artist="Rhea Moreau", year=2025, genre="Synthpop", style=cover_static,
         tracks=[("Salt & Static", 212, None), ("Signal Lost", 227, None), ("Coastline FM", 243, None),
                 ("Blue Noise", 205, None)]),
    dict(title="Glasshouse", artist="Teo Lindqvist", year=2024, genre="Electronic", style=cover_glasshouse,
         tracks=[("Glasshouse", 268, None), ("Mint", 231, None), ("Panes", 252, None)]),
    dict(title="Dust Roads", artist="Calloway Brothers", year=2022, genre="Country", style=cover_dust,
         tracks=[("Dust Roads", 224, None), ("Porch Light", 208, None), ("Mile Marker 9", 237, None),
                 ("Wide Open", 215, None)]),
    dict(title="Afterlight", artist="Noor Haddad", year=2025, genre="Soul", style=cover_afterlight,
         tracks=[("Afterlight", 245, None), ("Amber", 221, None), ("Golden Thread", 259, None)]),
]


def make_mp3(path, seconds):
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "lavfi", "-i",
                    f"anullsrc=r=22050:cl=mono", "-t", str(seconds), "-c:a", "libmp3lame",
                    "-b:a", "32k", path], check=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--sheet")
    ap.add_argument("--covers-only", action="store_true")
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)
    thumbs = []
    for n, album in enumerate(ALBUMS):
        cover = grain(album["style"](album), 7, seed=n)
        cover_path = os.path.join(args.out, f"cover_{n:02d}.jpg")
        cover.save(cover_path, quality=90)
        thumbs.append(cover.resize((300, 300), Image.LANCZOS))
        if args.covers_only:
            continue
        art = open(cover_path, "rb").read()
        folder = os.path.join(args.out, album["artist"], album["title"])
        os.makedirs(folder, exist_ok=True)
        for i, (title, secs, lyrics) in enumerate(album["tracks"], 1):
            path = os.path.join(folder, f"{i:02d} {title}.mp3".replace("/", "-"))
            make_mp3(path, secs)
            tags = ID3()
            tags.add(TIT2(encoding=3, text=title))
            tags.add(TPE1(encoding=3, text=album["artist"]))
            tags.add(TPE2(encoding=3, text=album["artist"]))
            tags.add(TALB(encoding=3, text=album["title"]))
            tags.add(TRCK(encoding=3, text=f"{i}/{len(album['tracks'])}"))
            tags.add(TDRC(encoding=3, text=str(album["year"])))
            tags.add(TCON(encoding=3, text=album["genre"]))
            tags.add(APIC(encoding=3, mime="image/jpeg", type=3, desc="Cover", data=art))
            if lyrics:
                tags.add(USLT(encoding=3, lang="eng", desc="", text=lyrics))
            tags.save(path, v2_version=4)
        os.remove(cover_path)
    if args.sheet:
        sheet = Image.new("RGB", (4 * 310 + 10, 3 * 310 + 10), (20, 20, 20))
        for i, t in enumerate(thumbs):
            sheet.paste(t, (10 + (i % 4) * 310, 10 + (i // 4) * 310))
        sheet.save(args.sheet)
    print(f"generated {len(ALBUMS)} albums, {sum(len(a['tracks']) for a in ALBUMS)} tracks in {args.out}")


if __name__ == "__main__":
    sys.exit(main())
