#!/usr/bin/env python3
"""Generates festival-themed PNG app icons using pure Python standard library."""
import math
import os
import struct
import sys
import zlib


def write_png(path: str, size: int) -> None:
    raw = bytearray()
    cx = size * 0.5
    cy = size * 0.5
    inv = 1.0 / max(size, 1)

    for y in range(size):
        raw.append(0)  # filter type 0
        ny = y * inv
        for x in range(size):
            nx = x * inv
            dx = (x - cx) * inv
            dy = (y - cy) * inv
            dist = math.sqrt(dx * dx + dy * dy)

            # Festive sunset-carnival radial background
            t = min(1.0, dist * 1.45)
            r = int(255 * (1.0 - t) + 118 * t)
            g = int(105 * (1.0 - t) + 28 * t)
            b = int(65 * (1.0 - t) + 165 * t)

            # Sunburst festival rays
            angle = math.atan2(dy, dx)
            stripe = int((angle + math.pi) / (math.pi / 8.0)) % 2
            if stripe == 0 and dist > 0.12:
                r = min(255, r + 18)
                g = min(255, g + 14)

            # Cannon barrel (tilted 35 degrees toward upper-right)
            bx = nx - 0.34
            by = ny - 0.66
            rot_x = bx * 0.80 - by * 0.60
            rot_y = bx * 0.60 + by * 0.80
            if -0.09 <= rot_x <= 0.09 and -0.34 <= rot_y <= 0.06:
                r, g, b = 46, 52, 68
                if abs(rot_x) < 0.035:
                    r, g, b = 82, 92, 118

            # Golden cannon wheel
            wdx = nx - 0.31
            wdy = ny - 0.70
            wdist = math.sqrt(wdx * wdx + wdy * wdy)
            if wdist <= 0.115:
                r, g, b = 250, 196, 48
                if wdist <= 0.05:
                    r, g, b = 180, 110, 24

            # Flying glowing cannonball
            cdx = nx - 0.63
            cdy = ny - 0.35
            cdist = math.sqrt(cdx * cdx + cdy * cdy)
            if cdist <= 0.11:
                shade = max(0.35, 1.0 - cdist * 5.5)
                r = int(55 * shade + 40)
                g = int(60 * shade + 45)
                b = int(75 * shade + 55)

            # Shattering turquoise & gold ceramic shards around impact
            for sx, sy, sr, col in (
                (0.76, 0.24, 0.045, (64, 235, 215)),
                (0.81, 0.39, 0.040, (255, 220, 75)),
                (0.55, 0.20, 0.038, (255, 95, 165)),
            ):
                if abs(nx - sx) + abs(ny - sy) < sr:
                    r, g, b = col

            raw.extend((r, g, b, 255))

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack("!I", len(data))
            + tag
            + data
            + struct.pack("!I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack("!IIBBBBB", size, size, 8, 6, 0, 0, 0)
    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw), level=6))
        + chunk(b"IEND", b"")
    )
    with open(path, "wb") as f:
        f.write(png)


def main() -> None:
    out_dir = sys.argv[1]
    os.makedirs(out_dir, exist_ok=True)
    icons = {
        "AppIcon60x60@2x.png": 120,
        "AppIcon60x60@3x.png": 180,
        "AppIcon76x76@2x~ipad.png": 152,
        "AppIcon1024x1024.png": 1024,
    }
    for filename, px in icons.items():
        write_png(os.path.join(out_dir, filename), px)
    print(f"Generated {len(icons)} festival PNG icons in {out_dir}")


if __name__ == "__main__":
    main()
