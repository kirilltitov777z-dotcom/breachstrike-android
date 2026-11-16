#!/usr/bin/env python3
"""
Breach Strike asset pipeline.

Generates the real texture and audio set that ships inside the APK:
  * 7 weapon-skin patterns at 1536x1536
  * 18 world materials at 1024x1024 (concrete, brick, metal, crates, sites, ...)
  * 2 bot camo sets
  * 25 synthesized weapon / UI / round sounds
  * launcher icon

Everything is deterministic: same seed -> same asset pack.
No random filler blobs, every texture is referenced by the engine.
"""
import argparse
import math
import os
import struct
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

SEED = 20261116
rng = np.random.default_rng(SEED)


def fbm(w, h, octaves=6, base=3, seed=0):
    r = np.random.default_rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, total = 1.0, 0.0
    for o in range(octaves):
        gw = max(2, base * (2 ** o))
        gh = max(2, int(gw * h / max(1, w)))
        grid = r.random((gh, gw)).astype(np.float32)
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += np.asarray(img, np.float32) / 255.0 * amp
        total += amp
        amp *= 0.52
    return out / total


def grad(n, colors):
    """colors: [(pos, (r,g,b)), ...]"""
    xs = np.linspace(0, 1, 256)
    cols = np.zeros((256, 3), np.float32)
    for i in range(3):
        pos = [c[0] for c in colors]
        vals = [c[1][i] / 255.0 for c in colors]
        cols[:, i] = np.interp(xs, pos, vals)
    idx = np.clip((n * 255).astype(np.int32), 0, 255)
    return cols[idx]


def grain(img, amount=0.06, seed=0):
    r = np.random.default_rng(seed)
    n = r.normal(0, amount, (img.shape[0], img.shape[1], 1)).astype(np.float32)
    return np.clip(img + n, 0, 1)


def save_png(arr, path, quality=6):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    rgb = (np.clip(arr, 0, 1) * 255).astype(np.uint8)
    Image.fromarray(rgb, "RGB").save(path, "PNG", compress_level=quality, optimize=False)


def save_img(img, path, quality=6):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.convert("RGB").save(path, "PNG", compress_level=quality, optimize=False)


def scratch(img, count, alpha=0.10, seed=0, color=(255, 255, 255)):
    h, w, _ = img.shape
    r = np.random.default_rng(seed)
    # Draw every scratch on one grayscale mask, then blend once. Blending per
    # scratch over a 2.3 MP float image was ~500x slower for no visual gain.
    ov = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(ov)
    for _ in range(count):
        y = int(r.integers(0, h))
        x = int(r.integers(0, w))
        ln = int(r.integers(20, max(40, w // 5)))
        ang = float(r.normal(0, 0.25))
        dx, dy = int(math.cos(ang) * ln), int(math.sin(ang) * ln)
        d.line([x, y, x + dx, y + dy], fill=int(r.integers(70, 160)), width=int(r.integers(1, 3)))
    mask = (np.asarray(ov, np.float32) / 255.0 * alpha)[..., None]
    return img * (1.0 - mask) + np.array(color, np.float32) / 255.0 * mask


# ---------------------------------------------------------------- skins
def pattern_fade(w, h):
    n = fbm(w, h, 5, 4, 11)
    xx = np.linspace(0, 1, w)[None, :, None]
    yy = np.linspace(0, 1, h)[:, None, None]
    t = np.clip(xx * 0.7 + (1 - yy) * 0.45 + (n[..., None] - 0.5) * 0.35, 0, 1)
    stops = [(0.0, (255, 214, 92)), (0.22, (255, 120, 57)), (0.42, (236, 66, 140)),
             (0.62, (128, 72, 214)), (0.8, (58, 128, 255)), (1.0, (120, 245, 220))]
    xs = np.linspace(0, 1, 256)
    out = np.zeros((h, w, 3), np.float32)
    for c in range(3):
        out[..., c] = np.interp(t[..., 0], xs, np.interp(xs, [s[0] for s in stops], [s[1][c] / 255 for s in stops]))
    out *= (0.72 + 0.55 * n[..., None])
    out = scratch(out, 500, 0.12, 31)
    return grain(out, 0.035, 3)


def pattern_case(w, h):
    n = fbm(w, h, 6, 4, 22)
    n2 = fbm(w, h, 5, 6, 23)
    base = grad(n * 0.65 + n2 * 0.35, [(0.0, (28, 34, 68)), (0.35, (46, 84, 160)),
                                        (0.6, (118, 126, 148)), (0.78, (186, 150, 74)),
                                        (1.0, (236, 206, 120))])
    mask = (n2 > 0.55)[..., None]
    blue = grad(n, [(0.0, (18, 26, 52)), (1.0, (78, 122, 210))])
    base = np.where(mask, base * 0.6 + blue * 0.6, base)
    base = scratch(base, 900, 0.16, 33)
    return grain(base, 0.04, 4)


def pattern_doppler(w, h):
    n = fbm(w, h, 7, 3, 44)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    sw = np.sin((xx / w) * 9.0 + n * 8.0 + (yy / h) * 4.0)
    t = np.clip((sw + 1) * 0.5 * 0.7 + n * 0.5, 0, 1)
    base = grad(t, [(0.0, (8, 6, 20)), (0.35, (45, 20, 92)), (0.62, (105, 40, 180)),
                    (0.82, (200, 70, 220)), (1.0, (255, 190, 245))])
    base *= (0.65 + 0.55 * n[..., None])
    return grain(scratch(base, 300, 0.08, 45), 0.03, 5)


def pattern_slaughter(w, h):
    n = fbm(w, h, 6, 3, 55)
    n2 = fbm(w, h, 5, 5, 56)
    base = grad(n, [(0.0, (70, 6, 10)), (0.45, (168, 18, 24)), (0.8, (226, 60, 52)), (1.0, (255, 150, 130))])
    white = (n2 > 0.62)[..., None]
    base = np.where(white, np.array([232, 226, 214], np.float32) / 255.0, base)
    base = scratch(base, 700, 0.14, 57)
    return grain(base, 0.045, 6)


def pattern_asiimov(w, h):
    n = fbm(w, h, 5, 4, 66)
    base = grad(n, [(0.0, (206, 204, 198)), (0.55, (238, 238, 232)), (1.0, (255, 255, 250))])
    img = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(img)
    r = np.random.default_rng(66)
    for i in range(26):
        x0 = int(r.integers(0, w)); y0 = int(r.integers(0, h))
        ww = int(r.integers(w // 14, w // 4)); hh = int(r.integers(h // 20, h // 6))
        if i % 3 == 0:
            d.rectangle([x0, y0, x0 + ww, y0 + hh], fill=(235, 110, 26))
        elif i % 3 == 1:
            d.rectangle([x0, y0, x0 + ww, y0 + hh], outline=(28, 30, 34), width=max(3, w // 200))
        else:
            d.rectangle([x0, y0, x0 + ww, y0 + hh], fill=(24, 26, 30))
    arr = np.asarray(img, np.float32) / 255.0
    arr = grain(scratch(arr, 400, 0.1, 67), 0.02, 7)
    return arr


def pattern_hyper(w, h):
    n = fbm(w, h, 6, 4, 77)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    scales = np.sin(xx / w * 60 + np.sin(yy / h * 18) * 3 + n * 10)
    t = np.clip(scales * 0.3 + n * 0.7, 0, 1)
    base = grad(t, [(0.0, (12, 60, 66)), (0.35, (20, 130, 128)), (0.6, (222, 86, 30)),
                    (0.82, (255, 170, 60)), (1.0, (60, 20, 16))])
    base = scratch(base, 500, 0.12, 78)
    return grain(base, 0.04, 8)


def pattern_dragon(w, h):
    n = fbm(w, h, 6, 3, 88)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    swirl = np.sin(xx / w * 7 + np.sin(yy / h * 5 + n * 9) * 2.2)
    t = np.clip(swirl * 0.35 + n * 0.8, 0, 1)
    base = grad(t, [(0.0, (10, 26, 18)), (0.4, (28, 66, 40)), (0.65, (110, 92, 40)),
                    (0.85, (224, 186, 88)), (1.0, (255, 236, 168))])
    base = scratch(base, 350, 0.08, 89)
    return grain(base, 0.03, 9)


# ---------------------------------------------------------------- world
def tex_concrete(w, h, dark=False):
    n = fbm(w, h, 7, 3, 101)
    base = np.ones((h, w, 3), np.float32) * (0.42 if dark else 0.58)
    base *= (0.55 + 0.8 * n[..., None])
    base = scratch(base, 240, 0.18, 102, (30, 30, 32))
    base = scratch(base, 90, 0.12, 103, (210, 210, 205))
    yy = np.linspace(0, 1, h)[:, None, None]
    base *= (0.85 + 0.25 * yy)
    return grain(base, 0.035, 104)


def tex_brick(w, h):
    img = Image.new("RGB", (w, h), (128, 52, 40))
    d = ImageDraw.Draw(img)
    bh = h // 16
    bw = w // 8
    for row in range(17):
        off = (bw // 2) if row % 2 else 0
        y0 = row * bh
        for col in range(-1, 10):
            x0 = col * bw + off
            shade = 90 + int((math.sin(row * 3 + col * 7) + 1) * 30)
            d.rectangle([x0 + 3, y0 + 3, x0 + bw - 3, y0 + bh - 3],
                        fill=(shade + 50, shade // 2 + 20, shade // 3 + 14))
    arr = np.asarray(img, np.float32) / 255.0
    n = fbm(w, h, 6, 4, 111)
    arr *= (0.7 + 0.55 * n[..., None])
    arr = scratch(arr, 500, 0.12, 112, (40, 30, 26))
    return grain(arr, 0.04, 113)


def tex_metal(w, h, tint=(0.55, 0.58, 0.62)):
    n = fbm(w, h, 6, 4, 121)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    brushed = 0.5 + 0.5 * np.sin(xx / w * 800 + n * 30)
    base = np.array(tint, np.float32)[None, None, :] * (0.6 + 0.4 * brushed[..., None])
    base *= (0.7 + 0.5 * n[..., None])
    r = np.random.default_rng(122)
    img = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for _ in range(60):
        x = int(r.integers(0, w)); y = int(r.integers(0, h))
        rr = int(r.integers(w // 120, w // 55))
        d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=(150, 152, 156), outline=(40, 42, 46))
    arr = np.asarray(img, np.float32) / 255.0
    arr = scratch(arr, 220, 0.15, 123, (200, 190, 170))
    return grain(arr, 0.03, 124)


def tex_wood(w, h):
    n = fbm(w, h, 6, 4, 131)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    grain_lines = 0.5 + 0.5 * np.sin(xx / w * 40 + np.sin(yy / h * 9 + n * 6) * 4)
    base = grad(n * 0.6 + grain_lines * 0.4, [(0.0, (92, 58, 30)), (0.5, (150, 100, 56)), (1.0, (214, 164, 100))])
    img = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for i in range(1, 6):
        y = i * h / 6
        d.line([0, y, w, y], fill=(60, 36, 18), width=max(3, h // 220))
    arr = np.asarray(img, np.float32) / 255.0
    arr = scratch(arr, 260, 0.12, 132, (40, 24, 12))
    return grain(arr, 0.035, 133)


def tex_tile(w, h):
    n = fbm(w, h, 6, 4, 141)
    base = np.zeros((h, w, 3), np.float32)
    t = 16
    yy, xx = np.mgrid[0:h, 0:w]
    checker = ((xx // (w // t)) + (yy // (h // t))) % 2
    base[checker == 0] = (0.52, 0.54, 0.56)
    base[checker == 1] = (0.30, 0.32, 0.35)
    base *= (0.6 + 0.6 * n[..., None])
    for i in range(1, t):
        base[:, i * w // t:i * w // t + 3, :] *= 0.45
        base[i * h // t:i * h // t + 3, :, :] *= 0.45
    return grain(scratch(base, 260, 0.16, 142), 0.03, 143)


def tex_rust(w, h):
    n = fbm(w, h, 7, 3, 151)
    n2 = fbm(w, h, 5, 6, 152)
    base = grad(n * 0.7 + n2 * 0.3, [(0.0, (52, 32, 20)), (0.3, (120, 62, 26)),
                                      (0.65, (172, 94, 38)), (1.0, (98, 62, 48))])
    return grain(scratch(base, 500, 0.2, 153), 0.05, 154)


def tex_container(w, h, blue=False):
    n = fbm(w, h, 6, 4, 161 if not blue else 171)
    c = (0.20, 0.36, 0.52) if blue else (0.62, 0.26, 0.12)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    ribs = 0.5 + 0.5 * np.sin(xx / w * 90)
    base = np.array(c, np.float32)[None, None, :] * (0.55 + 0.45 * ribs[..., None])
    base *= (0.6 + 0.7 * n[..., None])
    return grain(scratch(base, 600, 0.22, 162 if not blue else 172, (120, 80, 40)), 0.04, 163)


def tex_grate(w, h):
    base = np.ones((h, w, 3), np.float32) * 0.16
    n = fbm(w, h, 5, 5, 181)
    base *= (0.6 + 0.8 * n[..., None])
    bar = w // 10
    for i in range(0, w, bar):
        base[:, i:i + max(2, bar // 3), :] = 0.42
    for j in range(0, h, bar):
        base[j:j + max(2, bar // 3), :, :] = 0.42
    base = scratch(base, 180, 0.2, 182, (140, 120, 90))
    return grain(base, 0.03, 183)


def tex_site(w, h, letter):
    n = fbm(w, h, 6, 4, 191)
    base = np.ones((h, w, 3), np.float32) * 0.22
    base *= (0.55 + 0.75 * n[..., None])
    base = scratch(base, 400, 0.2, 192)
    img = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    # stencil letter
    tmp = Image.new("L", (128, 128), 0)
    td = ImageDraw.Draw(tmp)
    td.text((26, 6), letter, fill=255, font=ImageFont.load_default())
    tmp = tmp.resize((h, w), Image.BICUBIC).filter(ImageFilter.GaussianBlur(1))
    mask = np.asarray(tmp, np.float32) / 255.0
    arr = np.asarray(img, np.float32) / 255.0
    yellow = np.array([0.95, 0.72, 0.12], np.float32)
    arr = arr * (1 - mask[..., None]) + yellow * mask[..., None]
    return grain(arr, 0.03, 193)


def tex_barrel(w, h):
    n = fbm(w, h, 6, 4, 201)
    base = grad(n, [(0.0, (72, 18, 12)), (0.5, (166, 44, 22)), (1.0, (226, 96, 36))])
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    base *= (0.6 + 0.6 * n[..., None])
    for y in (int(h * 0.2), int(h * 0.8)):
        base[y:y + max(3, h // 90), :, :] *= 0.45
    hazard = np.sin(xx / w * 30 + yy / h * 2) > 0.4
    base[hazard] = base[hazard] * 0.4 + np.array([0.9, 0.75, 0.1]) * 0.6
    return grain(scratch(base, 300, 0.2, 202), 0.04, 203)


def tex_bomb(w, h):
    base = np.ones((h, w, 3), np.float32) * 0.13
    n = fbm(w, h, 5, 5, 211)
    base *= (0.6 + 0.7 * n[..., None])
    img = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    d.rectangle([w * 0.18, h * 0.3, w * 0.82, h * 0.7], outline=(220, 60, 40), width=max(3, w // 40))
    d.text((w * 0.42, h * 0.42), "C4", fill=(230, 220, 200), font=ImageFont.load_default())
    arr = np.asarray(img, np.float32) / 255.0
    return grain(arr, 0.03, 212)


def tex_gunmetal(w, h):
    n = fbm(w, h, 6, 4, 221)
    base = grad(n, [(0.0, (32, 34, 38)), (0.55, (74, 78, 84)), (1.0, (130, 134, 140))])
    return grain(scratch(base, 200, 0.08, 222), 0.02, 223)


def tex_blade(w, h):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    base = np.ones((h, w, 3), np.float32) * 0.75
    base *= (0.5 + 0.7 * (1 - np.abs(xx / w - 0.5))[..., None])
    n = fbm(w, h, 5, 5, 231)
    base *= (0.8 + 0.3 * n[..., None])
    return grain(base, 0.02, 232)


def tex_flash(w, h):
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx / w - 0.5) ** 2 + (yy / h - 0.5) ** 2) * 2
    v = np.clip(1 - d, 0, 1) ** 0.4
    arr = np.zeros((h, w, 3), np.float32)
    arr[..., 0] = v
    arr[..., 1] = v * 0.8
    arr[..., 2] = v * 0.35
    return arr


def tex_camo(w, h, warm=True):
    n = fbm(w, h, 6, 4, 241 if warm else 251)
    n2 = fbm(w, h, 5, 7, 242 if warm else 252)
    if warm:
        c1, c2, c3 = (0.36, 0.26, 0.16), (0.62, 0.47, 0.28), (0.22, 0.18, 0.12)
    else:
        c1, c2, c3 = (0.16, 0.22, 0.32), (0.34, 0.44, 0.56), (0.10, 0.14, 0.2)
    out = np.zeros((h, w, 3), np.float32)
    out[:] = c1
    out[n > 0.52] = c2
    out[n2 > 0.62] = c3
    out *= (0.8 + 0.35 * n[..., None])
    return grain(out, 0.03, 243 if warm else 253)


# ---------------------------------------------------------------- audio
def wav_write(path, data, sr=22050):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = np.clip(data, -1, 1)
    pcm = (data * 32760).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())


def noise(dur, sr=22050, seed=0):
    r = np.random.default_rng(seed)
    return r.normal(0, 1, int(dur * sr)).astype(np.float32)


def lowpass(x, k=8):
    if k <= 1:
        return x
    w = np.ones(k, np.float32) / k
    return np.convolve(x, w, mode="same")


def env(n, a=0.002, d=0.2, sr=22050):
    t = np.linspace(0, 1, n, endpoint=False)
    at = np.clip(t / max(1e-4, a), 0, 1)
    dc = np.exp(-t / max(1e-4, d))
    return at * dc


def tone(freq, dur, sr=22050, amp=0.5, shape="sine"):
    t = np.arange(int(dur * sr)) / sr
    if shape == "square":
        s = np.sign(np.sin(2 * math.pi * freq * t))
    elif shape == "saw":
        s = 2 * ((t * freq) % 1) - 1
    else:
        s = np.sin(2 * math.pi * freq * t)
    return s * amp


def gunshot(dur, cutoff, boom, seed, sr=22050):
    n = noise(dur, sr, seed)
    n = lowpass(n, cutoff)
    e = env(len(n), 0.001, dur * 0.35, sr)
    body = n * e * 0.8
    body += tone(boom, dur, sr, 0.5) * env(len(n), 0.001, dur * 0.3, sr)
    click = noise(0.02, sr, seed + 1) * env(int(0.02 * sr), 0.0005, 0.008, sr) * 0.9
    body[:len(click)] += click
    return np.tanh(body * 1.4) * 0.9


def make_sounds(outdir):
    sr = 22050
    s = {}
    s["shot_pistol"] = gunshot(0.22, 5, 190, 301)
    s["shot_rifle"] = gunshot(0.34, 9, 120, 302)
    s["shot_smg"] = gunshot(0.18, 4, 240, 303)
    s["shot_shotgun"] = gunshot(0.5, 18, 78, 304)
    s["shot_lmg"] = gunshot(0.4, 12, 100, 305)
    s["shot_sniper"] = gunshot(0.95, 22, 58, 306)

    def click_series(times, amp=0.5, seed=401):
        total = int((times[-1] + 0.08) * sr)
        buf = np.zeros(total, np.float32)
        for i, t in enumerate(times):
            c = noise(0.05, sr, seed + i)
            c = lowpass(c, 3) * env(len(c), 0.0005, 0.02, sr) * amp
            start = int(t * sr)
            end = min(total, start + len(c))
            buf[start:end] += c[:end - start]
        return buf

    s["reload"] = click_series([0.02, 0.18, 0.42, 0.62], 0.55, 401)
    s["switch"] = click_series([0.02, 0.14], 0.45, 411)
    s["inspect"] = click_series([0.03, 0.16, 0.3], 0.35, 421)
    s["scope"] = click_series([0.01], 0.5, 431)
    s["empty"] = click_series([0.01], 0.45, 441)

    t = np.arange(int(0.16 * sr)) / sr
    s["step"] = lowpass(noise(0.16, sr, 451), 30) * np.exp(-t / 0.05) * 0.5
    t = np.arange(int(0.3 * sr)) / sr
    s["jump"] = lowpass(noise(0.3, sr, 452), 20) * np.sin(np.pi * t / 0.3) * 0.4
    t = np.arange(int(0.09 * sr)) / sr
    s["hitmarker"] = (np.sin(2 * math.pi * 2200 * t) * np.exp(-t / 0.02) * 0.5)
    t = np.arange(int(0.22 * sr)) / sr
    s["headshot"] = (np.sin(2 * math.pi * 1500 * t) + 0.5 * np.sin(2 * math.pi * 2400 * t)) * np.exp(-t / 0.06) * 0.45
    t = np.arange(int(0.3 * sr)) / sr
    s["kill"] = (np.sin(2 * math.pi * 880 * t) * 0.4 + np.sin(2 * math.pi * 1320 * t) * 0.3) * np.exp(-t / 0.12)
    t = np.arange(int(0.28 * sr)) / sr
    s["pain"] = lowpass(noise(0.28, sr, 461), 25) * np.exp(-t / 0.07) * 0.6 + np.sin(2 * math.pi * 110 * t) * np.exp(-t / 0.1) * 0.3
    t = np.arange(int(0.28 * sr)) / sr
    s["knife_swing"] = lowpass(noise(0.28, sr, 471), 10) * np.sin(np.pi * t / 0.28) * 0.5
    s["knife_hit"] = lowpass(noise(0.2, sr, 472), 4) * np.exp(-t[:int(0.2 * sr)] / 0.05) * 0.7

    t = np.arange(int(1.1 * sr)) / sr
    beeps = np.zeros(len(t), np.float32)
    for i, bt in enumerate([0.0, 0.35, 0.7]):
        start = int(bt * sr)
        n = min(len(t) - start, int(0.12 * sr))
        beeps[start:start + n] += np.sin(2 * math.pi * 1400 * (np.arange(n) / sr)) * 0.4
    s["plant"] = beeps

    t = np.arange(int(2.0 * sr)) / sr
    boom = lowpass(noise(2.0, sr, 481), 40) * np.exp(-t / 0.4) * 0.9
    boom += np.sin(2 * math.pi * 45 * t) * np.exp(-t / 0.5) * 0.6
    s["explosion"] = np.tanh(boom * 1.5) * 0.95

    def chord(freqs, dur, amp=0.3):
        t = np.arange(int(dur * sr)) / sr
        out = np.zeros(len(t), np.float32)
        for f in freqs:
            out += np.sin(2 * math.pi * f * t)
        out *= amp / len(freqs)
        return out * env(len(t), 0.01, dur * 0.6, sr)

    s["round_win"] = np.concatenate([chord([523, 659, 784], 0.14), chord([659, 784, 988], 0.3)])
    s["round_lose"] = np.concatenate([chord([330, 392, 466], 0.18), chord([262, 311, 392], 0.34)])
    s["match_win"] = np.concatenate([chord([523, 659, 784], 0.18), chord([659, 784, 1046], 0.18),
                                     chord([784, 988, 1318], 0.5)])
    s["match_lose"] = np.concatenate([chord([392, 466, 587], 0.2), chord([349, 415, 523], 0.2),
                                      chord([262, 311, 392], 0.55)])

    for name, data in s.items():
        wav_write(os.path.join(outdir, "sfx", name + ".wav"), data, sr)
    return len(s)


def make_icon(path):
    size = 512
    img = Image.new("RGB", (size, size), (11, 14, 20))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([16, 16, size - 16, size - 16], radius=96, fill=(20, 26, 36), outline=(255, 138, 30), width=10)
    # rifle silhouette
    d.rectangle([90, 250, 360, 290], fill=(255, 138, 30))
    d.rectangle([330, 225, 420, 265], fill=(255, 138, 30))
    d.rectangle([120, 290, 190, 360], fill=(255, 138, 30))
    d.rectangle([200, 285, 250, 315], fill=(255, 138, 30))
    d.rectangle([80, 215, 140, 250], fill=(255, 138, 30))
    d.line([90, 380, 422, 380], fill=(255, 200, 120), width=6)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, "PNG", compress_level=6)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="app/src/main/assets")
    ap.add_argument("--res", default="app/src/main/res")
    ap.add_argument("--skin", type=int, default=1536)
    ap.add_argument("--world", type=int, default=1024)
    args = ap.parse_args()

    texdir = os.path.join(args.out, "tex")
    os.makedirs(texdir, exist_ok=True)

    skins = {
        "fade": pattern_fade, "casehardened": pattern_case, "doppler": pattern_doppler,
        "slaughter": pattern_slaughter, "asiimov": pattern_asiimov,
        "hyperbeast": pattern_hyper, "dragonlore": pattern_dragon,
    }
    for name, fn in skins.items():
        arr = fn(args.skin, args.skin)
        save_png(arr, os.path.join(texdir, "skin_" + name + ".png"))
        print("skin", name, arr.shape)

    w = args.world
    worlds = {
        "concrete": lambda: tex_concrete(w, w, False),
        "concrete2": lambda: tex_concrete(w, w, True),
        "brick": lambda: tex_brick(w, w),
        "metal": lambda: tex_metal(w, w),
        "crate": lambda: tex_wood(w, w),
        "barrel": lambda: tex_barrel(w, w),
        "site_a": lambda: tex_site(w, w, "A"),
        "site_b": lambda: tex_site(w, w, "B"),
        "tile": lambda: tex_tile(w, w),
        "rust": lambda: tex_rust(w, w),
        "container": lambda: tex_container(w, w, False),
        "container2": lambda: tex_container(w, w, True),
        "grate": lambda: tex_grate(w, w),
        "gunmetal": lambda: tex_gunmetal(w, w),
        "blade": lambda: tex_blade(w, w),
        "bomb": lambda: tex_bomb(w, w),
        "flash": lambda: tex_flash(w, w),
        "bot_t": lambda: tex_camo(w, w, True),
        "bot_ct": lambda: tex_camo(w, w, False),
    }
    for name, fn in worlds.items():
        arr = fn()
        save_png(arr, os.path.join(texdir, "mat_" + name + ".png") if name != "bot_t" and name != "bot_ct"
                 else os.path.join(texdir, name + ".png"))
        print("world", name)

    n_sounds = make_sounds(args.out)
    make_icon(os.path.join(args.res, "drawable", "ic_launcher.png"))

    total = 0
    count = 0
    for root, _, files in os.walk(args.out):
        for f in files:
            total += os.path.getsize(os.path.join(root, f))
            count += 1
    print("sounds:", n_sounds)
    print("assets: %d files, %.1f MB" % (count, total / 1048576))


if __name__ == "__main__":
    main()
