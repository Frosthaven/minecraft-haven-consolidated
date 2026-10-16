"""16x16 pixel-art sprites for a metal, from one base color: ingot, nugget, raw chunk, dust, sheet, block, raw block,
ore on stone and on deepslate. Drawn with crisp polygons (no anti-aliasing), the same shapes for every metal so the
set reads as one family. Used by gen.py; never hand-edit the PNGs it writes.
"""
import random

from PIL import Image, ImageDraw

SIZE = 16


def hex_rgb(h: str):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(rgb, k: float):
    """k < 1 darkens, k > 1 lightens toward white."""
    if k <= 1:
        return tuple(max(0, min(255, round(c * k))) for c in rgb)
    return tuple(max(0, min(255, round(c + (255 - c) * (k - 1)))) for c in rgb)


def palette(color: str):
    """outline, dark, mid, base, light, glint"""
    base = hex_rgb(color)
    return [shade(base, 0.32), shade(base, 0.58), shade(base, 0.8), base, shade(base, 1.22), shade(base, 1.5)]


def canvas():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def poly(d, pts, fill, outline=None):
    d.polygon(pts, fill=fill, outline=outline)


def ingot(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    # a bar seen from the front-right: top face, front face, right end
    poly(d, [(3, 8), (10, 3), (14, 5), (7, 10)], lt, o)      # top
    poly(d, [(3, 8), (7, 10), (7, 13), (3, 11)], mid, o)     # front
    poly(d, [(7, 10), (14, 5), (14, 8), (7, 13)], base, o)   # long side
    d.line([(4, 8), (10, 4)], fill=gl)                       # glint along the top edge
    d.point((11, 5), fill=gl)
    return img


def nugget(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    poly(d, [(6, 5), (10, 5), (12, 7), (12, 10), (10, 12), (6, 12), (4, 10), (4, 7)], base, o)
    poly(d, [(6, 6), (9, 6), (10, 7), (7, 9), (5, 8)], lt)
    poly(d, [(8, 11), (11, 10), (11, 8), (10, 10)], dk)
    d.point((7, 6), fill=gl)
    return img


def raw(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    poly(d, [(3, 7), (6, 3), (11, 3), (14, 7), (13, 12), (9, 14), (4, 12)], base, o)
    poly(d, [(6, 4), (10, 4), (12, 7), (8, 8), (5, 7)], lt)              # lit facet
    poly(d, [(8, 8), (12, 7), (13, 11), (9, 13)], mid)                   # right facet
    poly(d, [(4, 8), (8, 8), (9, 13), (5, 11)], dk)                      # shadow facet
    d.line([(8, 8), (8, 13)], fill=o)
    d.line([(8, 8), (12, 7)], fill=o)
    d.point((7, 5), fill=gl)
    return img


def dust(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    poly(d, [(2, 13), (5, 8), (8, 6), (11, 8), (14, 13)], base, o)       # the pile
    poly(d, [(6, 9), (8, 7), (10, 9), (8, 11)], lt)
    poly(d, [(9, 11), (12, 10), (13, 13), (9, 13)], dk)
    rnd = random.Random(7)
    for _ in range(9):                                                    # loose grains
        x, y = rnd.randint(2, 13), rnd.randint(11, 14)
        d.point((x, y), fill=rnd.choice([dk, mid, lt]))
    return img


def sheet(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    poly(d, [(2, 9), (10, 3), (14, 5), (6, 11)], base, o)                # flat plate
    poly(d, [(2, 9), (6, 11), (6, 12), (2, 10)], dk, o)                  # thickness
    poly(d, [(6, 11), (14, 5), (14, 6), (6, 12)], mid, o)
    d.line([(3, 9), (10, 4)], fill=lt)
    d.point((11, 5), fill=gl)
    return img


def block(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    d.rectangle([0, 0, 15, 15], fill=base)
    d.rectangle([1, 1, 14, 14], outline=lt)
    d.line([(14, 1), (14, 14)], fill=dk)
    d.line([(1, 14), (14, 14)], fill=dk)
    d.rectangle([0, 0, 15, 15], outline=o)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):                    # rivets
        d.point((x, y), fill=o)
        d.point((x, y - 1), fill=gl)
    d.rectangle([5, 5, 10, 10], outline=mid)
    return img


def raw_block(p):
    img, d = canvas()
    o, dk, mid, base, lt, gl = p
    d.rectangle([0, 0, 15, 15], fill=mid)
    rnd = random.Random(11)
    for _ in range(14):                                                   # packed chunks
        x, y = rnd.randint(-2, 14), rnd.randint(-2, 14)
        w, h = rnd.randint(3, 6), rnd.randint(3, 5)
        d.rectangle([x, y, x + w, y + h], fill=rnd.choice([base, base, lt, dk]), outline=o)
    for _ in range(10):
        d.point((rnd.randint(0, 15), rnd.randint(0, 15)), fill=gl)
    return img


def stone(deepslate: bool):
    img, d = canvas()
    rnd = random.Random(31 if deepslate else 17)
    tones = [(0x3c, 0x3c, 0x40), (0x4a, 0x4a, 0x4f), (0x55, 0x55, 0x5a), (0x62, 0x62, 0x68)] if deepslate else \
            [(0x70, 0x70, 0x70), (0x7e, 0x7e, 0x7e), (0x8a, 0x8a, 0x8a), (0x96, 0x96, 0x96)]
    for y in range(SIZE):
        for x in range(SIZE):
            k = rnd.choice([1, 1, 2, 2, 2, 3])
            if deepslate and (x // 2 + y // 5) % 3 == 0:                 # vertical grain
                k = max(0, k - 1)
            d.point((x, y), fill=tones[k])
    for _ in range(6):                                                    # cracks
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        d.line([(x, y), (x + rnd.randint(-3, 3), y + rnd.randint(-2, 2))], fill=tones[0])
    return img


def ore(p, deepslate: bool):
    img = stone(deepslate)
    d = ImageDraw.Draw(img)
    o, dk, mid, base, lt, gl = p
    for cx, cy in ((3, 3), (11, 2), (7, 8), (13, 10), (2, 12), (9, 13)):   # six nuggets in the rock
        poly(d, [(cx, cy), (cx + 2, cy), (cx + 3, cy + 1), (cx + 2, cy + 3), (cx, cy + 3), (cx - 1, cy + 1)], base, o)
        d.point((cx + 1, cy + 1), fill=lt)
        d.point((cx + 2, cy + 2), fill=dk)
        d.point((cx, cy), fill=gl)
    return img


SPRITES = {
    "ingot": lambda p: ingot(p), "nugget": lambda p: nugget(p), "raw": lambda p: raw(p), "dust": lambda p: dust(p),
    "sheet": lambda p: sheet(p), "block": lambda p: block(p), "raw_block": lambda p: raw_block(p),
    "ore": lambda p: ore(p, False), "deepslate_ore": lambda p: ore(p, True),
}
