# Pixel-art mod icon on a 32x32 grid: a chat bubble with three typing dots above a player's head,
# and a "Z" of an AFK player. Usage (needs Pillow):
#   python3 docs/make-icon.py icon.png common/src/main/resources/assets/playerstatuses/icon.png
# Writes the 512px store icon and the 128px icon packed into the mod.
import sys
from PIL import Image

N = 32
BG, BORDER = (23, 26, 43), (52, 58, 92)
BUBBLE, BUBBLE_EDGE = (244, 245, 252), (178, 184, 222)
DOT = (124, 140, 255)
SKIN, SKIN_DARK, HAIR = (222, 170, 128), (186, 132, 96), (74, 52, 38)
SHIRT, EYE = (64, 168, 160), (40, 44, 70)
ZZZ = (255, 200, 87)

img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
px = img.load()


def inside(x, y, inset):
    r = 5 - inset
    lo, hi = inset, N - 1 - inset
    if not (lo <= x <= hi and lo <= y <= hi):
        return False
    cx = min(max(x, lo + r), hi - r)
    cy = min(max(y, lo + r), hi - r)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 1


for y in range(N):
    for x in range(N):
        if inside(x, y, 0):
            px[x, y] = BORDER + (255,)
        if inside(x, y, 1):
            px[x, y] = BG + (255,)


def put(x, y, c):
    px[x, y] = c + (255,)


def rect(x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(x, y, c)


# Player's head at the bottom: hair, face, eyes, shoulders
rect(11, 20, 20, 29, SKIN)
rect(11, 20, 20, 22, HAIR)
put(11, 23, HAIR)
put(20, 23, HAIR)
rect(13, 24, 14, 25, EYE)
rect(17, 24, 18, 25, EYE)
rect(14, 27, 17, 27, SKIN_DARK)
rect(7, 30, 24, 30, SHIRT)

# Chat bubble above the head, with a tail pointing down at it
BX0, BX1, BY0, BY1 = 3, 22, 5, 14
rect(BX0 + 1, BY0 + 1, BX1 - 1, BY1 - 1, BUBBLE)
for x in range(BX0 + 1, BX1):
    put(x, BY0, BUBBLE_EDGE)
    put(x, BY1, BUBBLE_EDGE)
for y in range(BY0 + 1, BY1):
    put(BX0, y, BUBBLE_EDGE)
    put(BX1, y, BUBBLE_EDGE)
for x, y in ((BX0 + 1, BY0 + 1), (BX1 - 1, BY0 + 1), (BX0 + 1, BY1 - 1), (BX1 - 1, BY1 - 1)):
    put(x, y, BUBBLE_EDGE)
# Tail: from the bubble's bottom edge down towards the head
rect(14, BY1, 16, BY1, BUBBLE)
put(13, BY1 + 1, BUBBLE_EDGE)
rect(14, BY1 + 1, 15, BY1 + 1, BUBBLE)
put(16, BY1 + 1, BUBBLE_EDGE)
put(14, BY1 + 2, BUBBLE_EDGE)
put(15, BY1 + 2, BUBBLE_EDGE)

# Three typing dots, 3x3 each
for x0 in (6, 11, 16):
    rect(x0, 8, x0 + 2, 10, DOT)

# An AFK "Z" to the right of the bubble: 5x5 with a clear diagonal
for x in range(24, 29):
    put(x, 3, ZZZ)
    put(x, 7, ZZZ)
put(27, 4, ZZZ)
put(26, 5, ZZZ)
put(25, 6, ZZZ)

for size, out in ((512, sys.argv[1]), (128, sys.argv[2])):
    img.resize((size, size), Image.NEAREST).save(out, "PNG", optimize=True)
