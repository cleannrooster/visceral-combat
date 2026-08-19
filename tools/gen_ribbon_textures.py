"""Generate continuous white-alpha masks for the textured melee ribbons."""

from pathlib import Path
from PIL import Image
import math


OUT = Path(__file__).parents[1] / "common/src/main/resources/assets/visceral_combat/textures/effect"


def smoothstep(value: float) -> float:
    value = max(0.0, min(1.0, value))
    return value * value * (3.0 - 2.0 * value)


def make_ribbon(name: str, width: int, height: int, root_taper: float, tip_taper: float,
                radial_taper: bool = False) -> None:
    image = Image.new("RGBA", (width, height), (255, 255, 255, 0))
    pixels = image.load()
    for y in range(height):
        v = (y + 0.5) / height
        if radial_taper:
            # Arc UV V runs from the inner radius to the outer tip. Fade the inner third in so the
            # crescent narrows toward its centre instead of ending in a broad rectangular root.
            inner = smoothstep(v / 0.34)
            outer = smoothstep((1.0 - v) / 0.08)
            edge = inner * outer
        else:
            # Lanes/thrusts are symmetric across their width.
            vertical = abs(v * 2.0 - 1.0)
            edge = smoothstep((1.0 - vertical) / 0.18)
        for x in range(width):
            u = (x + 0.5) / width
            root = min(1.0, u / root_taper)
            tip = min(1.0, (1.0 - u) / tip_taper)
            longitudinal = math.sqrt(max(0.0, root * tip))
            # A slightly harder luminous spine keeps the surface readable through translucent sorting.
            spine = 0.82 + 0.18 * (v if radial_taper else 1.0 - vertical) ** 2
            alpha = round(255 * edge * longitudinal * spine)
            pixels[x, y] = (255, 255, 255, alpha)
    image.save(OUT / name, optimize=True)


make_ribbon("slash.png", 256, 64, root_taper=0.10, tip_taper=0.055, radial_taper=True)
make_ribbon("thrust.png", 256, 32, root_taper=0.18, tip_taper=0.035)
