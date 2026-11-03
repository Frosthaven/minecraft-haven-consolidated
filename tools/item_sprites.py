"""16x16 sprites for the mod's own items (not metals), drawn as pixels. Used by gen.py; never hand-edit the PNGs it
writes. The tooth colors are Living Things' shark tooth greys, so the necklace reads as made from it.
"""
from PIL import Image

SIZE = 16
CORD_DARK, CORD, CORD_LIGHT = (59, 38, 24), (122, 82, 50), (156, 107, 67)
TOOTH_LIGHT, TOOTH, TOOTH_SHADE, TOOTH_EDGE = (236, 233, 222), (211, 211, 211), (157, 157, 157), (89, 89, 89)


def tooth(img, left: int, top: int, width: int, height: int):
    """A fang pointing down: full width, then one pixel narrower per row from the right; lit from the top left."""
    for row in range(height):
        span = max(1, width - row * width // height)
        for col in range(span):
            last = col == span - 1
            color = TOOTH_LIGHT if col == 0 and row < height - 1 else TOOTH_SHADE if last else TOOTH
            img.putpixel((left + col, top + row), color + (255,))
        img.putpixel((left + span, top + row), TOOTH_EDGE + (255,))   # shadow edge on the right
    img.putpixel((left, top + height), TOOTH_EDGE + (255,))           # the tip's shadow


def shark_tooth_necklace() -> Image.Image:
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    # The cord: two strands from the top corners down to a loop, a dark outer pixel and a lighter twist every other row.
    strand = [(2, 0), (2, 1), (2, 2), (3, 3), (3, 4), (4, 5), (5, 6), (6, 7), (7, 7)]
    for i, (x, y) in enumerate(strand):
        for px in (x, SIZE - 1 - x):
            img.putpixel((px, y), (CORD_LIGHT if i % 2 else CORD) + (255,))
        if y < 6:   # the outer edge of each strand; the loop below has its edge underneath
            img.putpixel((x - 1, y), CORD_DARK + (255,))
            img.putpixel((SIZE - x, y), CORD_DARK + (255,))
    for x in range(6, 10):
        img.putpixel((x, 8), CORD_DARK + (255,))
    tooth(img, 3, 6, 2, 4)    # left, on the strand
    tooth(img, 10, 6, 2, 4)   # right
    tooth(img, 6, 8, 3, 6)    # the middle one, longest
    return img


KELP_DARK, KELP = (38, 56, 26), (66, 92, 38)
PUFF_LIGHT, PUFF, PUFF_DARK = (250, 222, 110), (230, 190, 60), (176, 132, 36)
SPINE, SPINE_TIP = (176, 140, 62), (255, 244, 196)


def pufferfish_spine_bracelet() -> Image.Image:
    """A dried kelp cord seen at an angle (an ellipse) with four pufferfish-yellow beads on it and a spine pointing out
    between each pair of beads."""
    import math
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    cx, cy, rx, ry = 7.5, 8.0, 5.6, 3.9

    def put(x, y, color):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            img.putpixel((x, y), color + (255,))

    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2
            if 0.55 <= d <= 1.0:
                put(x, y, KELP_DARK if d > 0.85 else KELP)
    for i in range(8):
        ux, uy = math.cos(math.tau * i / 8), math.sin(math.tau * i / 8)
        if i % 2 == 0:   # a 2x2 bead on the cord, lit from the top left
            bx, by = round(cx + rx * 0.82 * ux - 0.5), round(cy + ry * 0.82 * uy - 0.5)
            for dx, dy, color in ((0, 0, PUFF_LIGHT), (1, 0, PUFF), (0, 1, PUFF), (1, 1, PUFF_DARK)):
                put(bx + dx, by + dy, color)
        else:            # a spine: dark base on the cord's edge, light tip outward
            put(round(cx + (rx + 0.6) * ux), round(cy + (ry + 0.6) * uy), SPINE)
            put(round(cx + (rx + 1.6) * ux), round(cy + (ry + 1.6) * uy), SPINE_TIP)
    return img


SPRITES = {"shark_tooth_necklace": shark_tooth_necklace, "pufferfish_spine_bracelet": pufferfish_spine_bracelet}
