"""Generates src/main/resources/assets/redscope/textures/block/oscilloscope.png (16x16).

Standard library only. Run from the project root: python3 -I tools/make_texture.py
"""
import struct
import zlib

SIZE = 16
FRAME = (0x3A, 0x3F, 0x45, 0xFF)
FRAME_LIGHT = (0x5A, 0x61, 0x69, 0xFF)
SCREEN = (0x0B, 0x1A, 0x10, 0xFF)
GRID = (0x17, 0x30, 0x22, 0xFF)
TRACE = (0x4C, 0xFF, 0x7A, 0xFF)

pixels = [[FRAME] * SIZE for _ in range(SIZE)]

# bevel: lighter top-left edge
for i in range(SIZE):
    pixels[0][i] = FRAME_LIGHT
    pixels[i][0] = FRAME_LIGHT

# screen area
for y in range(2, 14):
    for x in range(2, 14):
        pixels[y][x] = SCREEN
for i in range(2, 14):
    pixels[8][i] = GRID
    pixels[i][8] = GRID

# square wave: high (y=5) for x 3..6, falls at x=7, low (y=10) for x 7..10, rises at x=11, high for x 11..12
for x in range(3, 7):
    pixels[5][x] = TRACE
for y in range(5, 11):
    pixels[y][7] = TRACE
for x in range(7, 11):
    pixels[10][x] = TRACE
for y in range(5, 11):
    pixels[y][11] = TRACE
for x in range(11, 13):
    pixels[5][x] = TRACE


def chunk(kind, data):
    body = kind + data
    return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


raw = b"".join(b"\x00" + b"".join(bytes(p) for p in row) for row in pixels)
png = (
    b"\x89PNG\r\n\x1a\n"
    + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
    + chunk(b"IDAT", zlib.compress(raw, 9))
    + chunk(b"IEND", b"")
)

with open("src/main/resources/assets/redscope/textures/block/oscilloscope.png", "wb") as out:
    out.write(png)
print("wrote oscilloscope.png", len(png), "bytes")
