#!/usr/bin/env python3
"""Convert raw emulator PNG captures into compact JPEGs for docs/screenshots."""
import os, shutil, sys
from PIL import Image

out = sys.argv[1]
raw = os.path.join(out, "raw")
for f in sorted(os.listdir(raw)):
    src = os.path.join(raw, f)
    if f.endswith(".png"):
        im = Image.open(src).convert("RGB")
        w = 720
        im = im.resize((w, round(im.height * w / im.width)), Image.LANCZOS)
        im.save(os.path.join(out, f[:-4] + ".jpg"), quality=86, optimize=True, progressive=True)
    elif f in ("capture-log.txt", "logcat.txt", "scroll.mp4"):
        shutil.copy(src, os.path.join(out, f))
shutil.rmtree(raw)
