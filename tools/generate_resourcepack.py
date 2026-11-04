#!/usr/bin/env python3
"""
Génère les assets du mod FNations (Minecraft Java 1.21.4, Fabric) :
  - textures pixel-art 16x16 (icônes d'items + textures de modèles)
  - modèles 3D en éléments JSON (armes, véhicules, machines, missiles, fusée)
  - définitions d'items (assets/fnations/items/*.json) utilisées via le composant item_model

Usage : python3 tools/generate_resourcepack.py   (écrit dans mod/src/main/resources/assets/fnations)
Les modèles peuvent ensuite être retouchés dans Blockbench.
"""
import json
import math
import os
import random
import shutil

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "fnations")
random.seed(42)


def path(*p):
    full = os.path.join(ASSETS, *p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def write_json(p, data):
    with open(p, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=1, ensure_ascii=False)


# =====================================================================
#  Textures
# =====================================================================
def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def noise_tex(base, var=0.12, size=16):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for x in range(size):
        for y in range(size):
            px[x, y] = shade(base, 1 + random.uniform(-var, var))
    return img


def save_tex(img, kind, name):
    img.save(path("textures", kind, name + ".png"))


def block_textures():
    T = {}

    def add(name, img):
        save_tex(img, "block", name)
        T[name] = "fnations:block/" + name

    steel = hexc("8a9099")
    add("steel", noise_tex(steel, 0.06))
    add("dark_steel", noise_tex(hexc("3b3f45"), 0.08))
    add("gunmetal", noise_tex(hexc("2a2c30"), 0.05))
    add("wood", noise_tex(hexc("7a5230"), 0.15))
    add("rubber", noise_tex(hexc("1c1c1c"), 0.1))
    add("white_paint", noise_tex(hexc("e8e8e8"), 0.03))
    add("red_paint", noise_tex(hexc("b8262b"), 0.05))
    add("yellow_paint", noise_tex(hexc("e0b81c"), 0.05))
    add("green_paint", noise_tex(hexc("3f7a3a"), 0.05))
    add("sky_paint", noise_tex(hexc("8fa7bf"), 0.04))
    add("brass", noise_tex(hexc("c89b3c"), 0.08))
    add("flame", noise_tex(hexc("ff8a1c"), 0.25))

    # Camouflages
    for name, cols in {"camo_green": ["4b5d2e", "3a4a22", "6b7a3f", "2c3519"],
                       "camo_desert": ["c2a26b", "a68955", "d8bd88", "8a6f43"]}.items():
        img = Image.new("RGBA", (16, 16), hexc(cols[0]))
        d = ImageDraw.Draw(img)
        for _ in range(9):
            x, y = random.randint(-2, 15), random.randint(-2, 15)
            d.ellipse([x, y, x + random.randint(3, 6), y + random.randint(2, 5)], fill=hexc(random.choice(cols[1:])))
        add(name, img)

    # Verre de cockpit
    img = Image.new("RGBA", (16, 16), hexc("6fb7e0", 170))
    d = ImageDraw.Draw(img)
    d.line([2, 12, 12, 2], fill=hexc("d8f2ff", 220), width=2)
    d.rectangle([0, 0, 15, 15], outline=hexc("2d3c48", 255))
    add("glass", img)

    # Panneau de machine riveté
    def panel(base, accent=None):
        img = noise_tex(base, 0.05)
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 15, 15], outline=shade(base, 0.6))
        d.rectangle([1, 1, 14, 14], outline=shade(base, 1.2))
        for x, y in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            d.point((x, y), fill=shade(base, 0.5))
        if accent:
            d.rectangle([4, 7, 11, 8], fill=accent)
        return img

    add("machine_side", panel(hexc("7b828b")))
    add("machine_top", panel(hexc("6a7079"), hexc("e0b81c")))
    add("machine_bottom", panel(hexc("4a4f55")))

    # Façades
    img = panel(hexc("7b828b"))
    d = ImageDraw.Draw(img)
    d.ellipse([4, 3, 11, 10], fill=hexc("e8e8e8"), outline=hexc("222222"))
    d.line([7, 7, 10, 4], fill=hexc("c0262b"))
    d.rectangle([3, 12, 12, 13], fill=hexc("333333"))
    add("compressor_front", img)

    img = panel(hexc("3b3f45"))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 6, 12, 12], fill=hexc("1a1a1a"))
    for x in range(4, 12, 2):
        d.line([x, 6, x, 12], fill=hexc("555555"))
    d.rectangle([5, 9, 10, 11], fill=hexc("ff7a18"))
    d.rectangle([4, 2, 11, 3], fill=hexc("e0b81c"))
    add("generator_front", img)

    img = panel(hexc("5d6168"))
    d = ImageDraw.Draw(img)
    d.rectangle([3, 3, 12, 11], fill=hexc("2a0e05"), outline=hexc("1a1a1a"))
    d.rectangle([4, 7, 11, 10], fill=hexc("ff5a10"))
    d.rectangle([4, 5, 11, 6], fill=hexc("ffb030"))
    d.rectangle([3, 13, 6, 13], fill=hexc("3bd13b"))
    add("furnace_front", img)

    img = panel(hexc("6b6f75"))
    d = ImageDraw.Draw(img)
    for i in range(3, 13, 3):
        d.polygon([(i, 4), (i + 1, 11), (i + 2, 4)], fill=hexc("c8c8c8"))
    d.rectangle([2, 2, 13, 13], outline=hexc("e0b81c"))
    add("crusher_top", img)

    img = panel(hexc("7b828b"))
    d = ImageDraw.Draw(img)
    for i in range(-16, 16, 4):
        d.line([i, 15, i + 15, 0], fill=hexc("e0b81c"), width=2)
    d.rectangle([4, 4, 11, 11], fill=hexc("2a2c30"))
    d.rectangle([6, 3, 9, 8], fill=hexc("c0262b"))
    add("switch_front", img)

    img = Image.new("RGBA", (16, 16), hexc("e0b81c"))
    d = ImageDraw.Draw(img)
    for i in range(-16, 16, 6):
        d.polygon([(i, 16), (i + 3, 16), (i + 19, 0), (i + 16, 0)], fill=hexc("1a1a1a"))
    add("hazard", img)

    img = Image.new("RGBA", (16, 16), hexc("1f4f8a"))
    d = ImageDraw.Draw(img)
    for x in range(0, 16, 4):
        d.line([x, 0, x, 15], fill=hexc("bcd6f0"))
    for y in range(0, 16, 4):
        d.line([0, y, 15, y], fill=hexc("bcd6f0"))
    d.rectangle([5, 5, 10, 10], outline=hexc("ffffff"))
    add("blueprint", img)

    for name, base, line in [("solar_t1", "1d3e8a", "7da2e8"), ("solar_t2", "0f1f4a", "5c7fd1"), ("solar_t3", "2a0f4a", "c08cff")]:
        img = Image.new("RGBA", (16, 16), hexc(base))
        d = ImageDraw.Draw(img)
        for x in range(0, 16, 5):
            d.line([x, 0, x, 15], fill=hexc(line))
        for y in range(0, 16, 5):
            d.line([0, y, 15, y], fill=hexc(line))
        d.rectangle([0, 0, 15, 15], outline=hexc("c8c8c8"))
        if name == "solar_t3":
            d.point([(2, 2), (7, 7), (12, 12), (12, 2), (2, 12)], fill=hexc("ffffff"))
        add(name, img)

    img = Image.new("RGBA", (16, 16), hexc("111111"))
    d = ImageDraw.Draw(img)
    d.rectangle([1, 1, 14, 9], fill=hexc("0b3d1a"))
    d.line([2, 7, 5, 4, 8, 6, 13, 2], fill=hexc("3bff6b"))
    d.rectangle([2, 11, 5, 13], fill=hexc("c0262b"))
    d.rectangle([7, 11, 10, 13], fill=hexc("3bd13b"))
    add("screen", img)
    return T


# =====================================================================
#  Icônes plates (item/generated)
# =====================================================================
def icon(draw_fn):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw_fn(ImageDraw.Draw(img), img)
    return img


def outline(img, col=(20, 20, 20, 255)):
    px = img.load()
    src = img.copy().load()
    for x in range(16):
        for y in range(16):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < 16 and 0 <= ny < 16 and src[nx, ny][3] > 0:
                        px[x, y] = col
                        break
    return img


def ingot(c):
    def f(d, img):
        d.polygon([(2, 9), (6, 5), (14, 5), (10, 9)], fill=shade(c, 1.3))
        d.polygon([(2, 9), (10, 9), (10, 12), (2, 12)], fill=c)
        d.polygon([(10, 9), (14, 5), (14, 8), (10, 12)], fill=shade(c, 0.7))
    return f


def plate(c, mark=None):
    def f(d, img):
        d.rectangle([2, 3, 13, 12], fill=c)
        d.rectangle([2, 3, 13, 4], fill=shade(c, 1.3))
        d.rectangle([2, 11, 13, 12], fill=shade(c, 0.7))
        for x, y in [(3, 5), (12, 5), (3, 10), (12, 10)]:
            d.point((x, y), fill=shade(c, 0.5))
        if mark:
            d.rectangle([6, 7, 9, 8], fill=mark)
    return f


def dust(c):
    def f(d, img):
        for _ in range(26):
            x, y = random.randint(3, 12), random.randint(6, 13)
            d.point((x, y), fill=shade(c, random.uniform(0.7, 1.3)))
        d.ellipse([4, 8, 11, 13], fill=c)
    return f


def wire(d, img):
    for i in range(3):
        d.arc([2 + i, 2 + i, 13 - i, 13 - i], 0, 300, fill=hexc("d87a32") if i % 2 == 0 else hexc("b05a1c"), width=1)
    d.line([8, 8, 14, 14], fill=hexc("d87a32"))


def chip(base, pins):
    def f(d, img):
        d.rectangle([3, 3, 12, 12], fill=base)
        for i in range(4, 12, 2):
            d.point([(i, 2), (i, 13), (2, i), (13, i)], fill=pins)
        d.line([5, 6, 10, 6], fill=shade(base, 1.6))
        d.line([5, 9, 8, 9], fill=shade(base, 1.6))
        d.rectangle([9, 8, 10, 10], fill=hexc("e0b81c"))
    return f


def battery(d, img):
    d.rectangle([4, 3, 11, 13], fill=hexc("2a2c30"))
    d.rectangle([6, 2, 9, 2], fill=hexc("c8c8c8"))
    d.rectangle([4, 9, 11, 13], fill=hexc("e0b81c"))
    d.line([7, 5, 7, 7], fill=hexc("3bd13b"))
    d.line([6, 6, 8, 6], fill=hexc("3bd13b"))


def solar_cell(d, img):
    d.polygon([(1, 10), (6, 4), (15, 4), (10, 10)], fill=hexc("1d3e8a"))
    for i in range(3):
        d.line([3 + i * 3, 9 - 0, 7 + i * 3, 4], fill=hexc("7da2e8"))
    d.line([1, 10, 10, 10], fill=hexc("c8c8c8"))
    d.rectangle([4, 11, 7, 13], fill=hexc("555555"))


def motor(d, img):
    d.rectangle([3, 5, 12, 12], fill=hexc("5d6168"))
    for x in range(4, 12, 2):
        d.line([x, 5, x, 12], fill=hexc("3b3f45"))
    d.rectangle([12, 7, 14, 9], fill=hexc("c8c8c8"))
    d.rectangle([5, 3, 9, 4], fill=hexc("c0262b"))


def jet(d, img):
    d.polygon([(2, 5), (11, 4), (14, 6), (14, 10), (11, 12), (2, 11)], fill=hexc("8a9099"))
    d.ellipse([1, 5, 5, 11], fill=hexc("2a2c30"))
    d.ellipse([2, 6, 4, 10], fill=hexc("555555"))
    d.rectangle([13, 6, 15, 10], fill=hexc("ff8a1c"))


def rotor(d, img):
    d.line([1, 1, 14, 14], fill=hexc("3b3f45"), width=2)
    d.line([1, 14, 14, 1], fill=hexc("3b3f45"), width=2)
    d.ellipse([6, 6, 9, 9], fill=hexc("c8c8c8"))


def tire(d, img):
    d.ellipse([2, 2, 13, 13], fill=hexc("1c1c1c"))
    d.ellipse([5, 5, 10, 10], fill=hexc("8a9099"))
    d.ellipse([7, 7, 8, 8], fill=hexc("3b3f45"))


def track(d, img):
    d.rounded_rectangle([1, 4, 14, 11], radius=3, fill=hexc("2a2c30"))
    for x in range(2, 14, 2):
        d.line([x, 4, x, 11], fill=hexc("555555"))
    for x in (4, 8, 12):
        d.ellipse([x - 2, 6, x + 1, 9], fill=hexc("8a9099"))


def cockpit(d, img):
    d.pieslice([2, 3, 13, 18], 180, 360, fill=hexc("6fb7e0"))
    d.line([5, 6, 8, 4], fill=hexc("e8f8ff"))
    d.rectangle([1, 10, 14, 12], fill=hexc("8a9099"))


def barrel(d, img):
    d.line([2, 13, 13, 2], fill=hexc("2a2c30"), width=3)
    d.line([3, 13, 13, 3], fill=hexc("5d6168"), width=1)


def parts(d, img):
    d.ellipse([2, 2, 9, 9], fill=hexc("8a9099"))
    d.ellipse([4, 4, 7, 7], fill=hexc("3b3f45"))
    d.rectangle([8, 9, 14, 11], fill=hexc("5d6168"))
    d.rectangle([10, 7, 12, 13], fill=hexc("5d6168"))


def explosive(d, img):
    for x in (3, 6, 9):
        d.rectangle([x, 4, x + 2, 13], fill=hexc("b8262b"))
        d.line([x, 4, x, 13], fill=hexc("e05555"))
    d.rectangle([3, 8, 11, 9], fill=hexc("1a1a1a"))
    d.line([7, 4, 9, 1, 12, 2], fill=hexc("c8c8c8"))


def warhead(d, img):
    d.polygon([(8, 1), (12, 7), (12, 14), (4, 14), (4, 7)], fill=hexc("3b3f45"))
    d.polygon([(8, 1), (12, 7), (4, 7)], fill=hexc("b8262b"))
    d.rectangle([4, 10, 12, 11], fill=hexc("e0b81c"))


def guidance(d, img):
    chip(hexc("1f4f8a"), hexc("c8c8c8"))(d, img)
    d.line([12, 3, 15, 0], fill=hexc("c8c8c8"))
    d.point((15, 0), fill=hexc("ff3030"))


def nuclear(d, img):
    d.ellipse([2, 2, 13, 13], fill=hexc("1d5c22"))
    d.ellipse([4, 4, 11, 11], fill=hexc("5cff5c"))
    d.ellipse([6, 6, 9, 9], fill=hexc("e0ffe0"))
    d.pieslice([2, 2, 13, 13], 0, 60, fill=hexc("e0b81c"))
    d.pieslice([2, 2, 13, 13], 120, 180, fill=hexc("e0b81c"))
    d.pieslice([2, 2, 13, 13], 240, 300, fill=hexc("e0b81c"))
    d.ellipse([6, 6, 9, 9], fill=hexc("1a1a1a"))


def heat_shield(d, img):
    for x in range(2, 14, 4):
        for y in range(2, 14, 4):
            d.rectangle([x, y, x + 3, y + 3], fill=hexc("2a2c30") if (x + y) % 8 else hexc("3b3f45"))
    d.rectangle([2, 12, 13, 13], fill=hexc("c89b3c"))


def rocket_engine(d, img):
    d.rectangle([5, 1, 10, 5], fill=hexc("8a9099"))
    d.polygon([(5, 5), (10, 5), (13, 13), (2, 13)], fill=hexc("3b3f45"))
    d.polygon([(4, 13), (11, 13), (8, 15)], fill=hexc("ff8a1c"))


def canister(c):
    def f(d, img):
        d.rectangle([3, 4, 12, 14], fill=c)
        d.rectangle([4, 2, 7, 4], fill=shade(c, 0.6))
        d.rectangle([9, 2, 11, 3], fill=hexc("2a2c30"))
        d.line([4, 6, 11, 13], fill=shade(c, 1.3))
        d.line([11, 6, 4, 13], fill=shade(c, 1.3))
    return f


def bullets(c, n=3, long=False):
    def f(d, img):
        for i in range(n):
            x = 3 + i * (10 // n)
            top = 3 if long else 6
            d.rectangle([x, top + 2, x + 2, 13], fill=c)
            d.polygon([(x, top + 2), (x + 2, top + 2), (x + 1, top)], fill=hexc("c87a32"))
            d.line([x, 12, x + 2, 12], fill=shade(c, 0.6))
    return f


def shells(d, img):
    for i, x in enumerate((3, 8)):
        d.rectangle([x, 4, x + 4, 11], fill=hexc("b8262b"))
        d.rectangle([x, 11, x + 4, 13], fill=hexc("c89b3c"))


def rpg_rocket(d, img):
    d.line([3, 12, 11, 4], fill=hexc("3b5a2e"), width=3)
    d.polygon([(11, 1), (14, 4), (11, 7), (8, 4)], fill=hexc("5c7a3a"))
    d.line([2, 13, 4, 15], fill=hexc("2a2c30"), width=2)


def tank_shell(d, img):
    d.rectangle([5, 6, 10, 14], fill=hexc("c89b3c"))
    d.polygon([(5, 6), (10, 6), (8, 1)], fill=hexc("3b3f45"))
    d.line([5, 12, 10, 12], fill=hexc("8a6f43"))


def banknote(d, img):
    d.rectangle([1, 4, 14, 11], fill=hexc("6fb36a"))
    d.rectangle([2, 5, 13, 10], outline=hexc("3d7a3a"))
    d.ellipse([6, 5, 9, 10], fill=hexc("a8e0a0"))
    d.text((6, 4), "$", fill=hexc("1d4a1a"))


def flask(d, img):
    d.polygon([(6, 2), (9, 2), (9, 6), (13, 13), (2, 13), (6, 6)], fill=hexc("ff8a1c"))
    d.rectangle([6, 1, 9, 2], fill=hexc("8a9099"))
    d.polygon([(4, 10), (11, 10), (13, 13), (2, 13)], fill=hexc("ffcf5c"))


FLAT = {
    "steel_ingot": ingot(hexc("8f969e")),
    "steel_plate": plate(hexc("8f969e")),
    "compressed_iron": plate(hexc("d8d8d8"), hexc("8a9099")),
    "carbon_plate": plate(hexc("2a2c30"), hexc("555555")),
    "iron_dust": dust(hexc("b8b8b8")),
    "gold_dust": dust(hexc("e8c03c")),
    "copper_wire": wire,
    "circuit": chip(hexc("1d6b2c"), hexc("c8c8c8")),
    "advanced_circuit": chip(hexc("1f4f8a"), hexc("e0b81c")),
    "battery": battery,
    "solar_cell": solar_cell,
    "motor": motor,
    "jet_engine": jet,
    "rotor": rotor,
    "tire": tire,
    "track": track,
    "cockpit": cockpit,
    "gun_barrel": barrel,
    "gun_parts": parts,
    "explosive_charge": explosive,
    "warhead": warhead,
    "guidance_system": guidance,
    "nuclear_core": nuclear,
    "heat_shield": heat_shield,
    "rocket_engine": rocket_engine,
    "fuel_canister": canister(hexc("c0262b")),
    "rocket_fuel": flask,
    "ammo_9mm": bullets(hexc("c89b3c"), 3),
    "ammo_762": bullets(hexc("c89b3c"), 3, True),
    "ammo_sniper": bullets(hexc("a07020"), 2, True),
    "ammo_shotgun": shells,
    "rpg_rocket": rpg_rocket,
    "tank_shell": tank_shell,
    "banknote": banknote,
}


# =====================================================================
#  Modèles 3D
# =====================================================================
def _auto_uv(face, frm, to):
    """UV calculées par Minecraft quand le modèle n'en donne pas (BlockElement.uvsByFace)."""
    fx, fy, fz = frm
    tx, ty, tz = to
    return {
        "down": [fx, 16 - tz, tx, 16 - fz],
        "up": [fx, fz, tx, tz],
        "north": [16 - tx, 16 - ty, 16 - fx, 16 - fy],
        "south": [fx, 16 - ty, tx, 16 - fy],
        "west": [fz, 16 - ty, tz, 16 - fy],
        "east": [16 - tz, 16 - ty, 16 - fz, 16 - fy],
    }[face]


def _fit_uv(uv):
    """Minecraft ne borne pas ces UV : au-delà de 0..16 la face lit les textures voisines de l'atlas.
    Retourne des UV ramenées dans la texture (ou None si elles y sont déjà)."""
    if all(0 <= v <= 16 for v in uv):
        return None

    def fit(a, b):
        size = b - a
        if size >= 16:
            return 0, 16
        if a < 0 or b > 16:
            a = a % 16
            if a + size > 16:
                a = 16 - size
        return a, a + size

    u0, u1 = fit(uv[0], uv[2])
    v0, v1 = fit(uv[1], uv[3])
    return [round(x, 4) for x in (u0, v0, u1, v1)]


class Model:
    def __init__(self, textures):
        self.textures = dict(textures)
        self.elements = []

    def box(self, frm, to, tex, rot=None, faces=None, name=None, full=None, uv=None, only=None, light=0):
        """Ajoute un cuboïde. tex = clé de texture, faces = dict face->clé optionnel,
        full = faces (ou "all") qui affichent la texture entière (UV 0..16), uv = dict face->UV explicites,
        only = faces à garder (les autres sont omises), light = émission de lumière de l'élément (0..15)."""
        frm = [round(v, 4) for v in frm]
        to = [round(v, 4) for v in to]
        for v in frm + to:
            assert -16 <= v <= 32, f"coordonnée hors limites {frm} {to}"
        f = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            if only and face not in only:
                continue
            t = (faces or {}).get(face, tex)
            f[face] = {"texture": "#" + t}
            if uv and face in uv:
                f[face]["uv"] = list(uv[face])
            elif full == "all" or (full and face in full):
                f[face]["uv"] = [0, 0, 16, 16]
            else:
                fitted = _fit_uv(_auto_uv(face, frm, to))
                if fitted:
                    f[face]["uv"] = fitted
        e = {"from": list(frm), "to": list(to), "faces": f}
        if name:
            e["name"] = name
        if rot:
            e["rotation"] = rot
        if light:
            e["light_emission"] = light
        self.elements.append(e)
        return self

    def json(self, display=None, parent=None):
        out = {}
        if parent:
            out["parent"] = parent
        out["textures"] = {k: v for k, v in self.textures.items()}
        out["textures"]["particle"] = list(self.textures.values())[0]
        out["elements"] = self.elements
        if display:
            out["display"] = display
        return out


def rot(axis, angle, origin):
    return {"axis": axis, "angle": angle, "origin": list(origin)}


def gun_display(length, gui_scale=None):
    gs = gui_scale or round(min(1.25, 21.0 / length), 2)
    return {
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [-3, 3.5, -2], "scale": [0.7, 0.7, 0.7]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [3, 3.5, -2], "scale": [0.7, 0.7, 0.7]},
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [0, 90, -35], "translation": [0, 0, 0], "scale": [gs, gs, gs]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 90, 0], "scale": [gs, gs, gs]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
    }


def big_display(size_units):
    s = round(14.0 / size_units, 3)
    return {
        "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [s, s, s]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [s * 0.6] * 3},
        "fixed": {"rotation": [0, 0, 0], "scale": [s, s, s]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [s * 0.6] * 3},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [s * 0.7] * 3},
        "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 2, 0], "scale": [s * 0.7] * 3},
        "head": {"rotation": [0, 0, 0], "scale": [s, s, s]},
    }


def guns(T):
    out = {}
    tx = {"g": T["gunmetal"], "s": T["dark_steel"], "w": T["wood"], "m": T["steel"], "c": T["camo_green"], "b": T["brass"]}
    # Le canon pointe vers le nord (-Z). Centre de la poignée vers z=10.
    m = Model(tx)
    m.box([7, 8, 2], [9, 10, 12], "g", name="glissière")
    m.box([7, 4, 9], [9, 8, 11], "s", rot=rot("x", -22.5, [8, 6, 10]), name="poignée")
    m.box([7.25, 6.5, 6], [8.75, 8, 9], "s", name="pontet")
    m.box([7.5, 8.5, 1.5], [8.5, 9.5, 2], "m", name="bouche")
    out["pistol"] = m.json(gun_display(11))

    m = Model(tx)
    m.box([7, 8, -1], [9, 10.5, 11], "g", name="corps")
    m.box([7.5, 8.75, -5], [8.5, 9.75, -1], "m", name="canon")
    m.box([7.25, 4, 3], [8.75, 8, 5], "s", name="chargeur")
    m.box([7, 4.5, 8], [9, 8, 10], "s", rot=rot("x", -22.5, [8, 6, 9]), name="poignée")
    m.box([7.5, 9, 11], [8.5, 9.5, 16], "m", name="crosse pliante")
    out["smg"] = m.json(gun_display(21))

    m = Model(tx)
    m.box([7, 8, -4], [9, 10.5, 10], "g", name="corps")
    m.box([7.5, 8.75, -12], [8.5, 9.75, -4], "m", name="canon")
    m.box([7.25, 8.5, -8], [8.75, 10, -4], "w", name="garde-main")
    m.box([7.25, 3.5, -1], [8.75, 8, 2], "s", rot=rot("x", 22.5, [8, 8, 0]), name="chargeur")
    m.box([7.25, 4.5, 5], [8.75, 8, 7], "s", rot=rot("x", -22.5, [8, 6, 6]), name="poignée")
    m.box([7, 6.5, 10], [9, 10, 18], "w", name="crosse")
    m.box([7.5, 10.5, -1], [8.5, 11.5, 4], "s", name="rail")
    out["assault_rifle"] = m.json(gun_display(30))

    m = Model(tx)
    m.box([7, 8.5, -10], [9, 10, 8], "g", name="canon")
    m.box([7.1, 7, -6], [8.9, 8.5, 0], "w", name="pompe")
    m.box([7, 7.5, 0], [9, 10.5, 7], "s", name="carcasse")
    m.box([7, 5, 7], [9, 10, 16], "w", rot=rot("x", -22.5, [8, 9, 7]), name="crosse")
    out["shotgun"] = m.json(gun_display(26))

    m = Model(tx)
    m.box([7.5, 8.75, -16], [8.5, 9.75, -2], "g", name="canon long")
    m.box([7, 8, -2], [9, 10.5, 9], "c", name="corps")
    m.box([7.25, 11, -3], [8.75, 12.5, 6], "s", name="lunette")
    m.box([7, 10.5, 0], [9, 11, 1], "s")
    m.box([7, 10.5, 4], [9, 11, 5], "s")
    m.box([7.25, 4, 4], [8.75, 8, 6], "s", rot=rot("x", -22.5, [8, 6, 5]), name="poignée")
    m.box([7, 6, 9], [9, 10, 18], "c", name="crosse")
    m.box([7.75, 5, -9], [8.25, 8.75, -8.5], "s", rot=rot("x", 22.5, [8, 8.75, -8.75]), name="bipied")
    out["sniper"] = m.json(gun_display(34))

    m = Model(tx)
    m.box([6.5, 9, -10], [9.5, 12, 14], "c", name="tube")
    m.box([6, 8.5, -14], [10, 12.5, -10], "s", name="ogive")
    m.box([7.25, 4.5, 2], [8.75, 9, 4], "s", name="poignée")
    m.box([7.25, 5, -3], [8.75, 9, -1], "s", name="poignée avant")
    m.box([9.5, 10, 0], [10.5, 12, 3], "s", name="viseur")
    out["rpg"] = m.json(gun_display(28))
    return out


def vehicles(T):
    out = {}
    # ---------- Char (avant vers -Z) ----------
    tx = {"c": T["camo_green"], "d": T["dark_steel"], "r": T["rubber"], "g": T["gunmetal"], "s": T["screen"]}
    m = Model(tx)
    m.box([-6, 0, -14], [0, 7, 30], "r", faces={"up": "d"}, name="chenille gauche")
    m.box([16, 0, -14], [22, 7, 30], "r", faces={"up": "d"}, name="chenille droite")
    m.box([-4, 5, -16], [20, 12, 30], "c", name="caisse")
    m.box([-4, 5, -16], [20, 9, -12], "c", rot=rot("x", -22.5, [8, 9, -14]), name="glacis")
    m.box([0, 12, 0], [16, 19, 18], "c", name="tourelle")
    m.box([2, 19, 6], [6, 21, 10], "d", name="trappe")
    m.box([6.5, 14, -16], [9.5, 17, 0], "g", name="canon")
    m.box([6, 13.5, -16], [10, 17.5, -13], "d", name="frein de bouche")
    m.box([12, 19, 12], [13, 25, 13], "d", name="antenne")
    for z in (-10, -2, 6, 14, 22):
        m.box([-6.5, 1, z], [-5.5, 5, z + 4], "d")
        m.box([21.5, 1, z], [22.5, 5, z + 4], "d")
    out["tank"] = m.json(big_display(48))

    # ---------- Avion (nez vers -Z) ----------
    tx = {"p": T["sky_paint"], "g": T["glass"], "d": T["dark_steel"], "w": T["white_paint"], "r": T["red_paint"], "f": T["flame"]}
    m = Model(tx)
    m.box([5, 4, -12], [11, 10, 26], "p", name="fuselage")
    m.box([6, 5, -16], [10, 9, -12], "d", name="nez")
    m.box([6, 9.5, -8], [10, 12.5, 2], "g", name="cockpit")
    m.box([-16, 6, 2], [32, 7, 12], "p", name="ailes")
    m.box([-16, 6, 2], [-13, 7.5, 12], "r", name="saumon g")
    m.box([29, 6, 2], [32, 7.5, 12], "r", name="saumon d")
    m.box([-2, 7, 20], [18, 8, 26], "p", name="empennage horizontal")
    m.box([7.5, 10, 18], [8.5, 18, 26], "p", name="dérive")
    m.box([6, 5, 26], [10, 9, 28], "d", name="tuyère")
    m.box([6.5, 5.5, 28], [9.5, 8.5, 29], "f", name="flamme")
    m.box([0, 4.5, 4], [3, 6, 10], "w", name="missile g")
    m.box([13, 4.5, 4], [16, 6, 10], "w", name="missile d")
    m.box([7.5, 0, -8], [8.5, 4, -7], "d", name="train avant")
    m.box([3.5, 0, 8], [4.5, 4, 9], "d", name="train g")
    m.box([11.5, 0, 8], [12.5, 4, 9], "d", name="train d")
    out["plane"] = m.json(big_display(48))

    # ---------- Hélicoptère (nez vers -Z), rotor séparé ----------
    tx = {"c": T["green_paint"], "g": T["glass"], "d": T["dark_steel"], "y": T["yellow_paint"]}
    m = Model(tx)
    m.box([2, 4, -10], [14, 15, 10], "c", name="cabine")
    m.box([3, 6, -14], [13, 14, -10], "g", name="verrière")
    m.box([6, 9, 10], [10, 13, 30], "c", name="poutre de queue")
    m.box([7, 13, 26], [9, 20, 30], "c", name="dérive")
    m.box([9, 14, 27], [10, 20, 28], "d", name="rotor anticouple")
    m.box([6.5, 15, -2], [9.5, 18, 2], "d", name="mât")
    m.box([0, 0, -8], [1, 1, 8], "d", name="patin g")
    m.box([15, 0, -8], [16, 1, 8], "d", name="patin d")
    m.box([1, 0, -4], [2, 4, -3], "d")
    m.box([1, 0, 3], [2, 4, 4], "d")
    m.box([14, 0, -4], [15, 4, -3], "d")
    m.box([14, 0, 3], [15, 4, 4], "d")
    m.box([0, 6, -4], [2, 8, 4], "y", name="lance-roquettes g")
    m.box([14, 6, -4], [16, 8, 4], "y", name="lance-roquettes d")
    out["helicopter"] = m.json(big_display(44))

    m = Model({"d": T["dark_steel"]})
    m.box([7, 18, -16], [9, 18.5, 32], "d", name="pale 1")
    m.box([-16, 18, 7], [32, 18.5, 9], "d", name="pale 2")
    m.box([7, 18, 7], [9, 19.5, 9], "d", name="moyeu")
    out["helicopter_rotor"] = m.json(big_display(48))

    # ---------- Tracteur (avant vers -Z) ----------
    tx = {"r": T["red_paint"], "t": T["rubber"], "d": T["dark_steel"], "g": T["glass"], "y": T["yellow_paint"]}
    m = Model(tx)
    m.box([3, 5, -10], [13, 11, 8], "r", name="capot")
    m.box([2, 5, 6], [14, 17, 16], "r", name="cabine")
    m.box([3, 11, 6.5], [13, 16, 7], "g", name="pare-brise")
    m.box([1.5, 17, 5], [14.5, 18, 17], "d", name="toit")
    m.box([-3, 0, 8], [2, 12, 18], "t", faces={"west": "y", "east": "y"}, name="roue arrière g")
    m.box([14, 0, 8], [19, 12, 18], "t", faces={"west": "y", "east": "y"}, name="roue arrière d")
    m.box([0, 0, -9], [3, 6, -3], "t", faces={"west": "y", "east": "y"}, name="roue avant g")
    m.box([13, 0, -9], [16, 6, -3], "t", faces={"west": "y", "east": "y"}, name="roue avant d")
    m.box([10, 11, -6], [11, 17, -5], "d", name="échappement")
    m.box([4, 6, -11], [12, 10, -10], "d", name="calandre")
    m.box([1, 2, 18], [15, 4, 22], "d", name="attelage / charrue")
    out["tractor"] = m.json(big_display(34))
    return out


def rocket_model(T):
    tx = {"w": T["white_paint"], "r": T["red_paint"], "d": T["dark_steel"], "g": T["glass"], "f": T["flame"], "h": T["hazard"]}
    m = Model(tx)
    # Fusée verticale de -16 à 32 (3 blocs), centrée sur x=z=8
    m.box([4, -10, 4], [12, 22, 12], "w", name="corps")
    m.box([4.5, 22, 4.5], [11.5, 26, 11.5], "w", name="coiffe 1")
    m.box([5.5, 26, 5.5], [10.5, 29, 10.5], "r", name="coiffe 2")
    m.box([6.5, 29, 6.5], [9.5, 31, 9.5], "r", name="coiffe 3")
    m.box([7.5, 31, 7.5], [8.5, 32, 8.5], "d", name="pointe")
    m.box([4, 12, 3.5], [12, 14, 4], "h", name="bande")
    m.box([6, 16, 3.5], [10, 19, 4], "g", name="hublot")
    m.box([4, 0, 4], [12, 2, 12], "r", name="anneau")
    m.box([1, -16, 7], [4, -6, 9], "r", name="aileron 1")
    m.box([12, -16, 7], [15, -6, 9], "r", name="aileron 2")
    m.box([7, -16, 1], [9, -6, 4], "r", name="aileron 3")
    m.box([7, -16, 12], [9, -6, 15], "r", name="aileron 4")
    m.box([5, -14, 5], [11, -10, 11], "d", name="tuyère")
    m.box([6, -16, 6], [10, -14, 10], "f", name="flamme")
    return m.json(big_display(48))


def missiles(T):
    out = {}
    specs = {
        "missile_tactical": ("white_paint", "red_paint", 12, 1.0),
        "missile_cruise": ("sky_paint", "dark_steel", 16, 1.2),
        "missile_ballistic": ("white_paint", "dark_steel", 20, 1.6),
        "missile_nuclear": ("yellow_paint", "green_paint", 22, 1.8),
    }
    for name, (body, tip, half, w) in specs.items():
        m = Model({"b": T[body], "t": T[tip], "d": T["dark_steel"], "f": T["flame"], "h": T["hazard"]})
        lo, hi = 8 - w * 1.5, 8 + w * 1.5
        # nez vers -Z
        m.box([lo, lo, 8 - half + 3], [hi, hi, 8 + half], "b", name="corps")
        m.box([lo + 0.5, lo + 0.5, 8 - half], [hi - 0.5, hi - 0.5, 8 - half + 3], "t", name="ogive")
        m.box([7.5, 7.5, 8 - half - 1], [8.5, 8.5, 8 - half], "t", name="pointe")
        m.box([lo - 2.5, 7.75, 8 + half - 4], [hi + 2.5, 8.25, 8 + half], "d", name="ailettes h")
        m.box([7.75, lo - 2.5, 8 + half - 4], [8.25, hi + 2.5, 8 + half], "d", name="ailettes v")
        if name == "missile_nuclear":
            m.box([lo - 0.01, lo - 0.01, 2], [hi + 0.01, hi + 0.01, 4], "h", name="bande")
        if name == "missile_cruise":
            m.box([lo - 5, 7.6, 6], [hi + 5, 8.4, 10], "b", name="ailes")
        out[name] = m.json(big_display(half * 2 + 2))
    return out


def machines(T):
    out = {}
    base = {"side": T["machine_side"], "top": T["machine_top"], "bottom": T["machine_bottom"]}

    def cube(front, top=None, extra=None):
        t = dict(base)
        t["front"] = T[front]
        if top:
            t["top"] = T[top]
        if extra:
            t.update(extra)
        m = Model(t)
        m.box([0, 0, 0], [16, 16, 16], "side", faces={"north": "front", "up": "top", "down": "bottom"})
        return m

    m = cube("compressor_front")
    m.box([3, 16, 3], [7, 20, 7], "side", name="vérin")
    m.box([9, 16, 9], [13, 19, 13], "side", name="réservoir")
    out["compressor"] = m.json(parent="minecraft:block/block")

    m = cube("generator_front")
    m.box([10, 16, 10], [14, 24, 14], "bottom", name="cheminée")
    out["coal_generator"] = m.json(parent="minecraft:block/block")

    m = cube("furnace_front")
    m.box([2, 16, 2], [14, 17, 14], "bottom", name="grille")
    out["electric_furnace"] = m.json(parent="minecraft:block/block")

    m = cube("machine_side", top="crusher_top", extra={"hazard": T["hazard"]})
    m.box([-0.01, 0, -0.01], [16.01, 3, 16.01], "hazard", name="bande danger")
    m.box([1, 16, 1], [15, 18, 15], "side", faces={"up": "top"}, name="trémie")
    out["crusher"] = m.json(parent="minecraft:block/block")

    m = cube("switch_front", extra={"red": T["red_paint"]})
    m.box([7, 8, -4], [9, 10, 0], "red", rot=rot("x", 22.5, [8, 9, 0]), name="levier")
    out["switch"] = m.json(parent="minecraft:block/block")

    m = Model({"h": T["hazard"], "d": T["dark_steel"], "s": T["steel"], "c": T["screen"]})
    m.box([0, 0, 0], [16, 6, 16], "d", faces={"north": "h", "south": "h", "east": "h", "west": "h"}, name="socle")
    m.box([2, 6, 2], [14, 8, 14], "s", name="plateau")
    m.box([3, 8, 6], [5, 24, 10], "s", rot=rot("x", 22.5, [4, 8, 8]), name="rail g")
    m.box([11, 8, 6], [13, 24, 10], "s", rot=rot("x", 22.5, [12, 8, 8]), name="rail d")
    m.box([5, 6, -2], [11, 12, 1], "d", faces={"north": "c"}, name="console")
    out["missile_launcher"] = m.json(parent="minecraft:block/block")


    return out



# =====================================================================
#  Panneaux solaires et Station de Craft 3D animés
#  (parties fixes = modèles de bloc, parties mobiles = modèles rendus par MachineRenderer)
# =====================================================================
def save_anim(frames, name, frametime=2, timing=None, kind="block"):
    """Texture animée : bande verticale de frames 16x16 + fichier .mcmeta."""
    strip = Image.new("RGBA", (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        strip.paste(f, (0, 16 * i))
    p = path("textures", kind, name + ".png")
    strip.save(p)
    anim = {"frametime": frametime, "interpolate": False}
    if timing:
        anim["frames"] = timing
    write_json(p + ".mcmeta", {"animation": anim})
    return "fnations:%s/%s" % (kind, name)


def bevel_tex(base, light=1.35, dark=0.55, inner=None):
    img = noise_tex(base, 0.04)
    d = ImageDraw.Draw(img)
    d.line([0, 0, 15, 0], fill=shade(base, light))
    d.line([0, 0, 0, 15], fill=shade(base, light))
    d.line([0, 15, 15, 15], fill=shade(base, dark))
    d.line([15, 0, 15, 15], fill=shade(base, dark))
    if inner:
        d.rectangle([2, 2, 13, 13], outline=shade(base, inner))
    return img


def solar_textures():
    T = {}
    metal = hexc("2c3138")
    side = bevel_tex(metal, inner=0.8)
    save_tex(side, "block", "solar_side")
    T["side"] = "fnations:block/solar_side"

    # Face avant : circuit (éteint / allumé et animé)
    def circuit(level, pulse=None, led=(255, 70, 60)):
        img = bevel_tex(metal)
        d = ImageDraw.Draw(img)
        d.rectangle([2, 2, 13, 13], fill=hexc("121418"))
        d.rectangle([2, 2, 13, 13], outline=hexc("454b53"))
        copper = shade(hexc("b86a2c"), level)
        path_pts = [(4, 4), (4, 9), (7, 9), (7, 11), (11, 11), (11, 6), (9, 6)]
        for a, b in zip(path_pts, path_pts[1:]):
            d.line([a, b], fill=copper)
        d.rectangle([8, 3, 11, 4], fill=shade(copper, 0.9))
        d.point((5, 12), fill=copper)
        d.rectangle([3, 12, 4, 12], fill=copper)
        if pulse is not None:
            pts = []
            for a, b in zip(path_pts, path_pts[1:]):
                n = max(abs(b[0] - a[0]), abs(b[1] - a[1]))
                for k in range(n):
                    pts.append((a[0] + (b[0] - a[0]) * k // n, a[1] + (b[1] - a[1]) * k // n))
            for j in range(3):
                q = pts[(pulse + j) % len(pts)]
                d.point(q, fill=(255, 230, 150, 255) if j == 1 else (255, 170, 80, 255))
        d.point((12, 3), fill=led + (255,))
        return img

    save_tex(circuit(0.55, led=(150, 40, 40)), "block", "solar_front_off")
    T["front_off"] = "fnations:block/solar_front_off"
    frames = [circuit(1.0, pulse=i * 3, led=(80, 255, 120) if i % 4 < 2 else (40, 160, 70)) for i in range(8)]
    T["front_on"] = save_anim(frames, "solar_front_on", frametime=2)

    # Côté : grille d'aération encadrée
    img = bevel_tex(metal)
    d = ImageDraw.Draw(img)
    d.rectangle([3, 3, 12, 12], fill=hexc("17191d"), outline=hexc("5a616b"))
    for y in range(5, 12, 2):
        d.line([5, y, 10, y], fill=hexc("3a4048"))
    d.rectangle([7, 7, 8, 8], fill=hexc("2f6fd8"))
    save_tex(img, "block", "solar_vent")
    T["vent"] = "fnations:block/solar_vent"

    # Dessus du boîtier : 2x2 cellules bleues
    img = Image.new("RGBA", (16, 16), hexc("dfe6ee"))
    d = ImageDraw.Draw(img)
    for (x, y) in [(1, 1), (9, 1), (1, 9), (9, 9)]:
        d.rectangle([x, y, x + 5, y + 5], fill=hexc("1d4fd8"))
        d.line([x, y, x + 5, y], fill=hexc("4f7ff0"))
        d.line([x, y + 5, x + 5, y + 5], fill=hexc("173fae"))
    d.rectangle([6, 6, 9, 9], fill=hexc("8a9099"), outline=hexc("5d636b"))
    save_tex(img, "block", "solar_top")
    T["top"] = "fnations:block/solar_top"

    img = noise_tex(hexc("c9ced6"), 0.05)
    ImageDraw.Draw(img).line([5, 0, 5, 15], fill=hexc("eef2f6"))
    save_tex(img, "block", "solar_pole")
    T["pole"] = "fnations:block/solar_pole"
    save_tex(noise_tex(hexc("e6ebf1"), 0.03), "block", "solar_frame")
    T["frame"] = "fnations:block/solar_frame"
    img = Image.new("RGBA", (16, 16), hexc("10225a"))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=hexc("3b5bb8"))
    d.rectangle([6, 6, 9, 9], fill=hexc("e6ebf1"))
    save_tex(img, "block", "solar_junction")
    T["junction"] = "fnations:block/solar_junction"

    # Cellules des panneaux (par tier) avec reflet balayant la surface
    for tier, (base, line, glow) in {1: ("1d4fd8", "5a86f2", (210, 230, 255)), 2: ("0f2a8a", "6f8fe6", (200, 220, 255)),
                                     3: ("4b1d8f", "c08cff", (255, 220, 255))}.items():
        def cell(glint):
            img = Image.new("RGBA", (16, 16), hexc(base))
            px = img.load()
            for x in range(16):
                for y in range(16):
                    f = 0.85 + 0.25 * (1 - (x + y) / 30)
                    px[x, y] = shade(hexc(base), f)
            d = ImageDraw.Draw(img)
            d.line([0, 7, 15, 7], fill=hexc(line))
            d.line([7, 0, 7, 15], fill=hexc(line))
            if tier == 3:
                d.point([(3, 3), (11, 3), (3, 11), (11, 11)], fill=(255, 230, 120, 255))
            if glint is not None:
                for x in range(16):
                    for y in range(16):
                        dd = abs((x + y) - glint)
                        if dd <= 2:
                            a = (3 - dd) / 3 * 0.6
                            r, g, b, al = px[x, y]
                            px[x, y] = (int(r + (glow[0] - r) * a), int(g + (glow[1] - g) * a), int(b + (glow[2] - b) * a), 255)
            return img
        frames = [cell(None)] + [cell(g) for g in range(-2, 34, 4)]
        timing = [{"index": 0, "time": 70}] + list(range(1, len(frames)))
        T["cell%d" % tier] = save_anim(frames, "solar_cell_t%d" % tier, frametime=2, timing=timing)
    return T


def station_textures():
    T = {}
    dark = hexc("23272e")
    img = bevel_tex(dark, inner=0.75)
    d = ImageDraw.Draw(img)
    for x in (2, 13):
        for y in (2, 13):
            d.point((x, y), fill=hexc("9aa0a8"))
    save_tex(img, "block", "station_base")
    T["base"] = "fnations:block/station_base"
    save_tex(bevel_tex(hexc("1b1e23"), inner=1.25), "block", "station_top")
    T["top"] = "fnations:block/station_top"

    # LED clignotantes du socle
    def leds(i):
        img = bevel_tex(hexc("2a2f37"))
        d = ImageDraw.Draw(img)
        d.rectangle([1, 6, 14, 9], fill=hexc("121418"))
        cols = [(70, 240, 120), (60, 210, 255), (255, 190, 60), (255, 80, 70)]
        for k, x in enumerate((3, 7, 11)):
            on = (i + k) % 4 != 3
            c = cols[(i + k) % 4] if on else (40, 44, 50)
            d.rectangle([x, 7, x + 1, 8], fill=c + (255,))
        return img
    T["leds"] = save_anim([leds(i) for i in range(4)], "station_leds", frametime=6)

    # Piliers avec bande lumineuse montante
    def pillar(i):
        img = noise_tex(hexc("2c3138"), 0.04)
        d = ImageDraw.Draw(img)
        d.line([0, 0, 0, 15], fill=hexc("454b53"))
        d.line([15, 0, 15, 15], fill=hexc("17191d"))
        d.rectangle([6, 0, 9, 15], fill=hexc("0b2a33"))
        for y in range(16):
            dy = (y - (15 - (i * 2) % 16)) % 16
            a = max(0.0, 1 - dy / 6)
            c = (int(30 + 60 * a), int(140 + 110 * a), int(170 + 85 * a), 255)
            d.line([7, y, 8, y], fill=c)
        return img
    T["pillar"] = save_anim([pillar(i) for i in range(8)], "station_pillar", frametime=2)

    # Écran supérieur : lignes de code défilantes
    rng = random.Random(7)
    lines = [[rng.randint(0, 1) for _ in range(12)] for _ in range(16)]
    def screen(i):
        img = Image.new("RGBA", (16, 16), hexc("06090c"))
        d = ImageDraw.Draw(img)
        d.rectangle([0, 0, 15, 15], outline=hexc("2b3138"))
        for row in range(6):
            src = lines[(row + i) % 16]
            y = 2 + row * 2
            for k, bit in enumerate(src):
                if bit:
                    d.point((2 + k, y), fill=(60, 220, 230, 255) if row != 2 else (150, 255, 200, 255))
        d.rectangle([2, 14, 2 + (i % 8) + 3, 14], fill=(60, 220, 230, 255))
        return img
    T["screen"] = save_anim([screen(i) for i in range(8)], "station_screen", frametime=4)

    # Console : plan technique avec curseur
    def console(i):
        img = Image.new("RGBA", (16, 16), hexc("0d2340"))
        d = ImageDraw.Draw(img)
        for x in range(0, 16, 4):
            d.line([x, 0, x, 15], fill=hexc("1c4570"))
        for y in range(0, 16, 4):
            d.line([0, y, 15, y], fill=hexc("1c4570"))
        d.rectangle([4, 5, 11, 10], outline=hexc("9fd8ff"))
        d.line([4, 5, 11, 10], fill=hexc("6fb7e0"))
        if i % 2 == 0:
            d.rectangle([12, 12, 13, 13], fill=(255, 255, 255, 255))
        d.rectangle([0, 0, 15, 15], outline=hexc("2b3138"))
        return img
    T["console"] = save_anim([console(i) for i in range(4)], "station_console", frametime=10)

    img = noise_tex(hexc("8a9099"), 0.05)
    d = ImageDraw.Draw(img)
    d.ellipse([4, 4, 11, 11], fill=hexc("3fdcff"))
    d.ellipse([6, 6, 9, 9], fill=hexc("d8fbff"))
    save_tex(img, "block", "station_emitter")
    T["emitter"] = "fnations:block/station_emitter"
    img = Image.new("RGBA", (16, 16), (90, 225, 255, 70))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=(150, 240, 255, 150))
    save_tex(img, "block", "station_glass")
    T["glass"] = "fnations:block/station_glass"

    # Hologramme : cyan translucide avec lignes de balayage
    def holo(i):
        img = Image.new("RGBA", (16, 16), (70, 215, 255, 70))
        d = ImageDraw.Draw(img)
        for y in range(16):
            if (y + i * 2) % 6 == 0:
                d.line([0, y, 15, y], fill=(170, 245, 255, 150))
        return img
    T["holo"] = save_anim([holo(i) for i in range(6)], "holo", frametime=2)
    img = Image.new("RGBA", (16, 16), (120, 240, 255, 175))
    save_tex(img, "block", "holo_ring")
    T["ring"] = "fnations:block/holo_ring"
    return T


def solar_models():
    """Bloc (boîtier + mât + pivot), panneau mobile 2x3 cellules, et icône d'inventaire."""
    S = solar_textures()
    blocks, parts, items = {}, {}, {}
    for state in ("off", "on"):
        m = Model({"side": S["side"], "front": S["front_" + state], "vent": S["vent"], "top": S["top"],
                   "pole": S["pole"], "dark": "fnations:block/dark_steel"})
        m.box([0, 0, 0], [16, 16, 16], "side", faces={"north": "front", "east": "vent", "west": "vent", "up": "top"}, full="all", name="boîtier")
        m.box([7, 16, 7], [9, 25, 9], "pole", name="mât")
        m.box([6.5, 25, 6.5], [9.5, 26, 9.5], "dark", name="rotule")
        blocks[state] = m
    for tier in (1, 2, 3):
        a = Model({"cell": S["cell%d" % tier], "frame": S["frame"], "j": S["junction"], "dark": "fnations:block/dark_steel"})
        # cadre extérieur (centré sur 8,8,8 : le pivot du rendu)
        a.box([-8, 8, -8], [24, 9.25, -7], "frame", name="cadre N")
        a.box([-8, 8, 23], [24, 9.25, 24], "frame", name="cadre S")
        a.box([-8, 8, -7], [-7, 9.25, 23], "frame", name="cadre O")
        a.box([23, 8, -7], [24, 9.25, 23], "frame", name="cadre E")
        a.box([7.5, 8, -7], [8.5, 9.1, 23], "frame", name="séparateur central")
        a.box([-7, 8, 2.5], [23, 9.1, 3.5], "frame", name="séparateur 1")
        a.box([-7, 8, 12.5], [23, 9.1, 13.5], "frame", name="séparateur 2")
        for (x0, x1) in ((-7, 7.5), (8.5, 23)):
            for (z0, z1) in ((-7, 2.5), (3.5, 12.5), (13.5, 23)):
                a.box([x0, 8.1, z0], [x1, 9, z1], "cell", faces={"down": "dark"}, full=["up"], name="cellule")
        a.box([6, 9, 6], [10, 9.6, 10], "j", full="all", name="boîte de jonction")
        a.box([7, 7, 3], [9, 8, 13], "dark", name="charnière")
        a.box([7.5, 7.2, -6], [8.5, 8, 22], "dark", name="longeron")
        parts["solar_array_t%d" % tier] = a
        # Icône d'inventaire : boîtier + mât + panneau (statique)
        it = Model({"side": S["side"], "front": S["front_on"], "vent": S["vent"], "top": S["top"], "pole": S["pole"],
                    "cell": S["cell%d" % tier], "frame": S["frame"], "dark": "fnations:block/dark_steel"})
        it.box([0, 0, 0], [16, 16, 16], "side", faces={"north": "front", "east": "vent", "west": "vent", "up": "top"}, full="all")
        it.box([7, 16, 7], [9, 25, 9], "pole")
        it.box([-8, 25, -8], [24, 26, 24], "frame", faces={"up": "cell"}, full=["up"])
        items["solar_panel_t%d" % tier] = it
    return blocks, parts, items


def station_models():
    S = station_textures()
    m = Model({"base": S["base"], "top": S["top"], "leds": S["leds"], "pillar": S["pillar"], "screen": S["screen"],
               "console": S["console"], "emitter": S["emitter"], "glass": S["glass"], "dark": "fnations:block/dark_steel"})
    m.box([0, 0, 0], [16, 2, 16], "base", faces={"up": "top"}, full="all", name="socle")
    m.box([1, 2, 1], [15, 4, 15], "leds", faces={"up": "top", "down": "top"}, light=9, name="socle LED")
    m.box([4, 4, 0.5], [12, 9, 1.5], "dark", faces={"north": "console"}, full=["north"],
          rot=rot("x", 22.5, [8, 4, 1]), light=11, name="console")
    m.box([5, 4, 5], [11, 6, 11], "dark", faces={"up": "emitter"}, full=["up"], light=13, name="émetteur")
    # piliers : canal sombre au centre de chaque face, bande lumineuse animée sur les faces extérieures
    pil = {f: [6, 0, 10, 16] for f in ("north", "south", "east", "west")}
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box([x, 4, z], [x + 2, 20, z + 2], "pillar", uv=pil, faces={"up": "dark", "down": "dark"}, name="pilier")
        ns = "north" if z < 8 else "south"
        ew = "west" if x < 8 else "east"
        zf = z - 0.02 if ns == "north" else z + 2
        m.box([x + 0.6, 4.5, zf], [x + 1.4, 19.5, zf + 0.02], "pillar", only=[ns], uv={ns: [7, 0, 9, 16]}, light=12,
              name="bande " + ns)
        xf = x - 0.02 if ew == "west" else x + 2
        m.box([xf, 4.5, z + 0.6], [xf + 0.02, 19.5, z + 1.4], "pillar", only=[ew], uv={ew: [7, 0, 9, 16]}, light=12,
              name="bande " + ew)
    m.box([0, 20, 0], [16, 22, 16], "base", faces={"up": "top"}, full="all", name="auvent")
    m.box([2, 22, 2], [14, 22.5, 14], "dark", faces={"up": "screen"}, full=["up"], light=14, name="écran")
    m.box([5, 19, 5], [11, 20, 11], "dark", faces={"down": "emitter"}, full=["down"], light=13, name="projecteur")
    m.box([1.75, 4, 3.5], [2.25, 20, 12.5], "glass", full="all", name="vitre O")
    m.box([13.75, 4, 3.5], [14.25, 20, 12.5], "glass", full="all", name="vitre E")
    m.box([3.5, 4, 13.75], [12.5, 20, 14.25], "glass", full="all", name="vitre arrière")
    for (x, z) in ((0.5, 0.5), (13.5, 0.5), (0.5, 13.5), (13.5, 13.5)):
        m.box([x, 22, z], [x + 2, 23.5, z + 2], "base", name="borne")
    beam = Model({"holo": S["holo"]})
    for i, (a, b) in enumerate(((7, 9), (6.5, 9.5), (6, 10), (5.25, 10.75))):
        beam.box([a, 8 + i * 2, a], [b, 10 + i * 2, b], "holo", full="all", name="faisceau %d" % i)
    # anneau octogonal : 4 segments droits + 4 segments tournés de 45° autour du centre
    ring = Model({"r": S["ring"]})
    R, w = 6.0, 0.75
    half = round(R * math.tan(math.pi / 8) + 0.2, 3)
    for k in range(2):
        r45 = rot("y", 45, [8, 8, 8]) if k else None
        ring.box([8 - half, 7.75, 8 - R - w / 2], [8 + half, 8.25, 8 - R + w / 2], "r", full="all", rot=r45)
        ring.box([8 - half, 7.75, 8 + R - w / 2], [8 + half, 8.25, 8 + R + w / 2], "r", full="all", rot=r45)
        ring.box([8 - R - w / 2, 7.75, 8 - half], [8 - R + w / 2, 8.25, 8 + half], "r", full="all", rot=r45)
        ring.box([8 + R - w / 2, 7.75, 8 - half], [8 + R + w / 2, 8.25, 8 + half], "r", full="all", rot=r45)
    return m, {"holo_beam": beam, "holo_ring": ring}

# =====================================================================
#  Assemblage
# =====================================================================
MACHINES = ["crafting_station", "compressor", "coal_generator", "solar_panel_t1", "solar_panel_t2", "solar_panel_t3",
            "electric_furnace", "crusher", "switch", "missile_launcher"]

# Noms (fr_fr, en_us)
NAMES = {
    "steel_ingot": ("Lingot d'acier", "Steel Ingot"), "steel_plate": ("Plaque d'acier", "Steel Plate"),
    "compressed_iron": ("Fer compressé", "Compressed Iron"), "carbon_plate": ("Plaque de carbone", "Carbon Plate"),
    "iron_dust": ("Poudre de fer", "Iron Dust"), "gold_dust": ("Poudre d'or", "Gold Dust"), "copper_wire": ("Fil de cuivre", "Copper Wire"),
    "circuit": ("Circuit électronique", "Circuit"), "advanced_circuit": ("Circuit avancé", "Advanced Circuit"), "battery": ("Batterie", "Battery"),
    "solar_cell": ("Cellule photovoltaïque", "Solar Cell"), "motor": ("Moteur", "Motor"), "jet_engine": ("Réacteur", "Jet Engine"),
    "rotor": ("Rotor d'hélicoptère", "Helicopter Rotor"), "tire": ("Pneu", "Tire"), "track": ("Chenille", "Track"), "cockpit": ("Cockpit", "Cockpit"),
    "gun_barrel": ("Canon d'arme", "Gun Barrel"), "gun_parts": ("Pièces d'arme", "Gun Parts"), "explosive_charge": ("Charge explosive", "Explosive Charge"),
    "warhead": ("Ogive", "Warhead"), "guidance_system": ("Système de guidage", "Guidance System"), "nuclear_core": ("Cœur nucléaire", "Nuclear Core"),
    "heat_shield": ("Bouclier thermique", "Heat Shield"), "rocket_engine": ("Moteur-fusée", "Rocket Engine"),
    "fuel_canister": ("Jerrican d'essence", "Fuel Canister"), "rocket_fuel": ("Ergol de fusée", "Rocket Propellant"),
    "ammo_9mm": ("Munitions 9mm", "9mm Ammo"), "ammo_762": ("Munitions 7.62mm", "7.62mm Ammo"), "ammo_sniper": ("Munitions .50 BMG", ".50 BMG Ammo"),
    "ammo_shotgun": ("Cartouches calibre 12", "12 Gauge Shells"), "rpg_rocket": ("Roquette RPG", "RPG Rocket"), "tank_shell": ("Obus de char", "Tank Shell"),
    "pistol": ("Pistolet M9", "M9 Pistol"), "smg": ("Pistolet-mitrailleur", "Submachine Gun"), "assault_rifle": ("Fusil d'assaut", "Assault Rifle"),
    "shotgun": ("Fusil à pompe", "Shotgun"), "sniper": ("Fusil de précision", "Sniper Rifle"), "rpg": ("Lance-roquettes RPG", "RPG Launcher"),
    "tank": ("Char d'assaut", "Tank"), "plane": ("Avion de chasse", "Fighter Jet"), "helicopter": ("Hélicoptère", "Helicopter"), "tractor": ("Tracteur", "Tractor"),
    "rocket": ("Fusée spatiale", "Space Rocket"),
    "missile_tactical": ("Missile tactique", "Tactical Missile"), "missile_cruise": ("Missile de croisière", "Cruise Missile"),
    "missile_ballistic": ("Missile balistique", "Ballistic Missile"), "missile_nuclear": ("Missile nucléaire", "Nuclear Missile"),
    "banknote": ("Billet de banque", "Banknote"),
    "crafting_station": ("Station de Craft", "Crafting Station"), "compressor": ("Compresseur", "Compressor"),
    "coal_generator": ("Générateur à charbon", "Coal Generator"), "solar_panel_t1": ("Panneau solaire Tier 1", "Solar Panel Tier 1"),
    "solar_panel_t2": ("Panneau solaire Tier 2", "Solar Panel Tier 2"), "solar_panel_t3": ("Panneau solaire Tier 3", "Solar Panel Tier 3"),
    "electric_furnace": ("Four électrique", "Electric Furnace"), "crusher": ("Concasseur", "Crusher"),
    "switch": ("Interrupteur de sécurité", "Safety Switch"), "missile_launcher": ("Lanceur de missiles", "Missile Launcher"),
}
EXTRA_LANG = {
    "itemGroup.fnations": ("FNations", "FNations"),
    "key.fnations.reload": ("Recharger l'arme", "Reload weapon"),
    "key.fnations.tablet": ("Ouvrir la tablette", "Open tablet"),
    "category.fnations": ("FNations", "FNations"),
    "entity.fnations.shell": ("Obus", "Shell"),
    "entity.fnations.missile": ("Missile", "Missile"),
    "entity.fnations.rocket": ("Fusée spatiale", "Space Rocket"),
    "entity.fnations.tank": ("Char d'assaut", "Tank"),
    "entity.fnations.plane": ("Avion de chasse", "Fighter Jet"),
    "entity.fnations.helicopter": ("Hélicoptère", "Helicopter"),
    "entity.fnations.tractor": ("Tracteur", "Tractor"),
    "container.fnations.panel": ("FNations", "FNations"),
}


def gui_textures():
    """Textures des menus (thème bleu nuit de la tablette) : panneau et machine."""
    base, edge, dark, accent = hexc("1e1f2a"), hexc("2e3040"), hexc("111219"), hexc("5865f2")

    def panel(d, x0, y0, x1, y1):
        d.rounded_rectangle([x0, y0, x1, y1], radius=4, fill=base, outline=edge)

    def slot(d, x, y):
        d.rounded_rectangle([x, y, x + 17, y + 17], radius=2, fill=hexc("262836"))
        d.rounded_rectangle([x + 1, y + 1, x + 16, y + 16], radius=2, fill=hexc("16171f"))

    # Panneau : 6 rangées en haut (y 0..124), inventaire du joueur en bas (y 126..221)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, 175, 124)
    d.line([6, 13, 169, 13], fill=accent)
    for r in range(6):
        for c in range(9):
            slot(d, 7 + c * 18, 17 + r * 18)
    panel(d, 0, 126, 175, 221)
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 139 + r * 18)
    for c in range(9):
        slot(d, 7 + c * 18, 197)
    img.save(path("textures", "gui", "panel.png"))

    # Machine : 176x166, jauges à gauche (énergie) et à droite (chaleur)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, 175, 165)
    d.line([6, 16, 169, 16], fill=accent)
    d.rounded_rectangle([8, 17, 20, 69], radius=2, outline=accent)
    d.rounded_rectangle([156, 17, 168, 69], radius=2, outline=hexc("f04747"))
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 83 + r * 18)
    for c in range(9):
        slot(d, 7 + c * 18, 141)
    img.save(path("textures", "gui", "machine.png"))


def ui_icons():
    """Icônes blanches de la tablette (teintées à l'affichage), dans l'ordre de fr.fnations.ui.UiIcons."""
    java = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "java", "fr", "fnations", "ui", "UiIcons.java")
    src = open(java, encoding="utf-8").read()
    block = src[src.index("List.of(") + 8: src.index(");", src.index("List.of("))]
    names = [n.strip().strip('"') for n in block.replace("\n", " ").split(",") if n.strip()]
    W = (255, 255, 255, 255)
    S = 64  # dessin en 64x64 puis réduction lissée en 16x16

    def person(d, cx, top, r):
        d.ellipse([cx - r, top, cx + r, top + 2 * r], fill=W)
        d.pieslice([cx - 2 * r, top + 2 * r + 2, cx + 2 * r, top + 6 * r + 2], 180, 360, fill=W)

    def gear(d, teeth, outer, inner, hole):
        c = S / 2
        pts = []
        for i in range(teeth * 2):
            a = math.pi * 2 * i / (teeth * 2)
            rr = outer if i % 2 == 0 else inner
            for da in (-0.18, 0.18):
                pts.append((c + rr * math.cos(a + da), c + rr * math.sin(a + da)))
        d.polygon(pts, fill=W)
        d.ellipse([c - hole, c - hole, c + hole, c + hole], fill=(0, 0, 0, 0))

    def draw(name, d):
        L = 6  # épaisseur de trait
        if name == "home":
            d.polygon([(32, 6), (58, 30), (50, 30), (50, 56), (14, 56), (14, 30), (6, 30)], fill=W)
            d.rectangle([27, 38, 37, 56], fill=(0, 0, 0, 0))
        elif name == "flag":
            d.rectangle([12, 6, 17, 60], fill=W)
            d.polygon([(17, 8), (54, 8), (46, 20), (54, 32), (17, 32)], fill=W)
        elif name == "bank":
            d.polygon([(32, 4), (60, 20), (4, 20)], fill=W)
            for x in (10, 22, 36, 48):
                d.rectangle([x, 24, x + 6, 48], fill=W)
            d.rectangle([4, 50, 60, 58], fill=W)
        elif name == "briefcase":
            d.rounded_rectangle([6, 20, 58, 56], radius=6, fill=W)
            d.rounded_rectangle([22, 8, 42, 24], radius=4, outline=W, width=L)
            d.rectangle([6, 34, 58, 38], fill=(0, 0, 0, 0))
        elif name == "pickaxe":
            d.line([(16, 54), (44, 22)], fill=W, width=L + 2)
            d.arc([6, 2, 62, 50], 200, 340, fill=W, width=L + 2)
        elif name == "shop":
            d.polygon([(6, 8), (58, 8), (62, 24), (2, 24)], fill=W)
            d.rectangle([8, 28, 56, 58], fill=W)
            d.rectangle([24, 38, 40, 58], fill=(0, 0, 0, 0))
        elif name == "flask":
            d.rectangle([24, 4, 40, 10], fill=W)
            d.polygon([(26, 10), (38, 10), (38, 26), (58, 58), (6, 58), (26, 26)], fill=W)
            d.polygon([(30, 14), (34, 14), (34, 28), (40, 38), (24, 38), (30, 28)], fill=(0, 0, 0, 0))
        elif name == "swords":
            d.line([(8, 8), (52, 52)], fill=W, width=L)
            d.line([(56, 8), (12, 52)], fill=W, width=L)
            d.line([(40, 54), (54, 40)], fill=W, width=L)
            d.line([(10, 40), (24, 54)], fill=W, width=L)
        elif name == "bed":
            d.rectangle([4, 14, 10, 54], fill=W)
            d.rectangle([4, 34, 60, 46], fill=W)
            d.rectangle([54, 34, 60, 54], fill=W)
            d.rounded_rectangle([12, 24, 26, 34], radius=3, fill=W)
            d.rounded_rectangle([28, 26, 60, 34], radius=3, fill=W)
        elif name == "trophy":
            d.pieslice([14, -10, 50, 40], 0, 180, fill=W)
            d.rectangle([14, 6, 50, 15], fill=W)
            d.arc([2, 8, 22, 30], 90, 270, fill=W, width=5)
            d.arc([42, 8, 62, 30], 270, 90, fill=W, width=5)
            d.rectangle([28, 38, 36, 48], fill=W)
            d.rounded_rectangle([16, 48, 48, 58], radius=3, fill=W)
        elif name == "gear":
            gear(d, 8, 30, 22, 9)
        elif name == "cog":
            gear(d, 6, 28, 18, 8)
        elif name == "close":
            d.line([(12, 12), (52, 52)], fill=W, width=L + 2)
            d.line([(52, 12), (12, 52)], fill=W, width=L + 2)
        elif name == "search":
            d.ellipse([6, 6, 42, 42], outline=W, width=L + 1)
            d.line([(38, 38), (58, 58)], fill=W, width=L + 3)
        elif name == "plus":
            d.rectangle([28, 8, 36, 56], fill=W)
            d.rectangle([8, 28, 56, 36], fill=W)
        elif name == "minus":
            d.rectangle([8, 28, 56, 36], fill=W)
        elif name.startswith("chevron_"):
            pts = {"down": [(10, 22), (32, 44), (54, 22)], "up": [(10, 42), (32, 20), (54, 42)],
                   "right": [(22, 10), (44, 32), (22, 54)], "left": [(42, 10), (20, 32), (42, 54)]}[name[8:]]
            d.line(pts, fill=W, width=L + 2, joint="curve")
        elif name == "check":
            d.line([(8, 34), (24, 50), (56, 14)], fill=W, width=L + 3, joint="curve")
        elif name == "lock":
            d.arc([16, 4, 48, 40], 180, 360, fill=W, width=L + 1)
            d.rectangle([16, 20, 22, 30], fill=W)
            d.rectangle([42, 20, 48, 30], fill=W)
            d.rounded_rectangle([8, 28, 56, 60], radius=6, fill=W)
            d.rectangle([29, 38, 35, 50], fill=(0, 0, 0, 0))
        elif name == "star":
            pts = []
            for i in range(10):
                a = -math.pi / 2 + i * math.pi / 5
                rr = 30 if i % 2 == 0 else 12
                pts.append((32 + rr * math.cos(a), 34 + rr * math.sin(a)))
            d.polygon(pts, fill=W)
        elif name == "coin":
            d.ellipse([4, 4, 60, 60], fill=W)
            d.ellipse([12, 12, 52, 52], outline=(0, 0, 0, 0), width=4)
            d.rectangle([29, 18, 35, 46], fill=(0, 0, 0, 0))
        elif name == "clock":
            d.ellipse([4, 4, 60, 60], outline=W, width=L)
            d.line([(32, 14), (32, 32), (46, 40)], fill=W, width=L)
        elif name == "teleport":
            d.ellipse([6, 36, 58, 58], outline=W, width=L)
            d.line([(32, 4), (32, 34)], fill=W, width=L + 1)
            d.polygon([(20, 22), (44, 22), (32, 38)], fill=W)
        elif name == "users":
            person(d, 22, 8, 9)
            person(d, 44, 14, 8)
        elif name == "user":
            person(d, 32, 6, 12)
        elif name == "map":
            d.polygon([(4, 12), (22, 4), (42, 12), (60, 4), (60, 52), (42, 60), (22, 52), (4, 60)], fill=W)
            d.line([(22, 8), (22, 52)], fill=(0, 0, 0, 0), width=3)
            d.line([(42, 14), (42, 58)], fill=(0, 0, 0, 0), width=3)
        elif name == "crown":
            d.polygon([(4, 18), (18, 32), (32, 8), (46, 32), (60, 18), (54, 52), (10, 52)], fill=W)
            d.rectangle([10, 54, 54, 60], fill=W)
        elif name == "shield":
            d.polygon([(32, 4), (58, 12), (56, 36), (32, 60), (8, 36), (6, 12)], fill=W)
        elif name == "info":
            d.ellipse([4, 4, 60, 60], fill=W)
            d.rectangle([28, 26, 36, 48], fill=(0, 0, 0, 0))
            d.ellipse([27, 12, 37, 21], fill=(0, 0, 0, 0))
        elif name == "hammer":
            d.line([(14, 56), (40, 26)], fill=W, width=L + 1)
            d.polygon([(26, 14), (44, 2), (62, 22), (46, 34)], fill=W)
        elif name == "wheat":
            d.line([(32, 60), (32, 10)], fill=W, width=4)
            for y in (12, 24, 36):
                d.ellipse([18, y, 30, y + 14], fill=W)
                d.ellipse([34, y, 46, y + 14], fill=W)
            d.ellipse([26, 2, 38, 16], fill=W)
        elif name == "sword":
            d.line([(14, 50), (54, 10)], fill=W, width=L + 1)
            d.line([(8, 36), (28, 56)], fill=W, width=L)
            d.line([(6, 58), (14, 50)], fill=W, width=L + 2)
        elif name == "axe":
            d.line([(18, 58), (42, 12)], fill=W, width=L)
            d.pieslice([30, 2, 62, 40], 250, 80, fill=W)
        elif name == "chart":
            d.rectangle([6, 36, 18, 58], fill=W)
            d.rectangle([26, 22, 38, 58], fill=W)
            d.rectangle([46, 8, 58, 58], fill=W)
        elif name == "mail":
            d.rounded_rectangle([4, 12, 60, 52], radius=5, fill=W)
            d.line([(6, 14), (32, 36), (58, 14)], fill=(0, 0, 0, 0), width=5)
        elif name == "tag":
            d.polygon([(4, 30), (30, 4), (60, 4), (60, 34), (34, 60)], fill=W)
            d.ellipse([44, 12, 54, 22], fill=(0, 0, 0, 0))
        elif name == "factory":
            d.polygon([(4, 58), (4, 28), (20, 38), (20, 28), (36, 38), (36, 28), (52, 38), (52, 6), (60, 6), (60, 58)], fill=W)
        elif name == "refresh":
            d.arc([8, 8, 56, 56], 40, 320, fill=W, width=L + 1)
            d.polygon([(44, 2), (60, 18), (40, 22)], fill=W)
        elif name == "trash":
            d.rectangle([8, 10, 56, 16], fill=W)
            d.rectangle([24, 4, 40, 10], fill=W)
            d.polygon([(12, 18), (52, 18), (48, 60), (16, 60)], fill=W)
        elif name == "edit":
            d.polygon([(10, 46), (44, 12), (52, 20), (18, 54), (8, 56)], fill=W)
            d.polygon([(48, 8), (52, 4), (60, 12), (56, 16)], fill=W)
        elif name == "heart":
            d.ellipse([4, 8, 34, 38], fill=W)
            d.ellipse([30, 8, 60, 38], fill=W)
            d.polygon([(6, 28), (58, 28), (32, 58)], fill=W)
        elif name == "globe":
            d.ellipse([4, 4, 60, 60], outline=W, width=L)
            d.ellipse([20, 4, 44, 60], outline=W, width=4)
            d.line([(6, 32), (58, 32)], fill=W, width=4)
        elif name == "cart":
            d.line([(2, 8), (12, 8), (20, 42), (54, 42), (60, 16), (14, 16)], fill=W, width=L, joint="curve")
            d.ellipse([16, 48, 28, 60], fill=W)
            d.ellipse([44, 48, 56, 60], fill=W)
        elif name == "box":
            d.polygon([(32, 4), (60, 18), (32, 32), (4, 18)], fill=W)
            d.polygon([(4, 22), (30, 36), (30, 62), (4, 48)], fill=W)
            d.polygon([(34, 36), (60, 22), (60, 48), (34, 62)], fill=W)
        elif name == "medal":
            d.polygon([(14, 2), (28, 2), (34, 22), (22, 26)], fill=W)
            d.polygon([(50, 2), (36, 2), (30, 22), (42, 26)], fill=W)
            d.ellipse([12, 22, 52, 62], fill=W)
            d.ellipse([22, 32, 42, 52], outline=(0, 0, 0, 0), width=4)
        elif name == "bell":
            d.pieslice([10, 6, 54, 50], 180, 360, fill=W)
            d.rectangle([10, 28, 54, 46], fill=W)
            d.rectangle([4, 46, 60, 52], fill=W)
            d.ellipse([26, 52, 38, 62], fill=W)
        elif name == "arrow_up":
            d.polygon([(32, 4), (58, 32), (42, 32), (42, 60), (22, 60), (22, 32), (6, 32)], fill=W)
        elif name == "arrow_down":
            d.polygon([(32, 60), (58, 32), (42, 32), (42, 4), (22, 4), (22, 32), (6, 32)], fill=W)
        elif name == "handshake":
            d.rounded_rectangle([4, 20, 38, 44], radius=12, outline=W, width=L)
            d.rounded_rectangle([26, 20, 60, 44], radius=12, outline=W, width=L)
        elif name == "send":
            d.polygon([(4, 30), (60, 4), (42, 60), (30, 36)], fill=W)
            d.line([(30, 36), (60, 4)], fill=(0, 0, 0, 0), width=3)
        elif name == "dot":
            d.ellipse([16, 16, 48, 48], fill=W)
        else:
            raise SystemExit("Icône sans dessin : " + name)

    atlas = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    for i, name in enumerate(names):
        big = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        draw(name, ImageDraw.Draw(big))
        small = big.resize((16, 16), Image.LANCZOS)
        atlas.alpha_composite(small, ((i % 16) * 16, (i // 16) * 16))
    atlas.save(path("textures", "gui", "ui_icons.png"))
    return len(names)


def mod_icon():
    ic = Image.new("RGBA", (128, 128), hexc("1a1a1a"))
    d = ImageDraw.Draw(ic)
    d.rectangle([6, 6, 121, 121], outline=hexc("e0b81c"), width=5)
    d.polygon([(64, 16), (88, 60), (80, 112), (48, 112), (40, 60)], fill=hexc("e8e8e8"))
    d.polygon([(64, 16), (80, 44), (48, 44)], fill=hexc("b8262b"))
    d.polygon([(52, 112), (76, 112), (64, 124)], fill=hexc("ff8a1c"))
    ic.save(path("icon.png"))


def main():
    if os.path.exists(ASSETS):
        shutil.rmtree(ASSETS)
    os.makedirs(ASSETS)
    T = block_textures()
    models = {}
    for name, fn in FLAT.items():
        img = outline(icon(fn))
        save_tex(img, "item", name)
        models[name] = {"parent": "minecraft:item/generated", "textures": {"layer0": "fnations:item/" + name}}
    models.update(guns(T))
    models.update(vehicles(T))
    models["rocket"] = rocket_model(T)
    models.update(missiles(T))
    blocks = machines(T)

    for name, data in models.items():
        write_json(path("models", "item", name + ".json"), data)
        write_json(path("items", name + ".json"), {"model": {"type": "minecraft:model", "model": "fnations:item/" + name}})
    # Panneaux solaires et station (animés)
    sblocks, sparts, sitems = solar_models()
    station, hparts = station_models()
    disp_solar = {"gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.36, 0.36, 0.36]},
                  "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.2, 0.2, 0.2]},
                  "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
                  "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
                  "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]},
                  "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]}}
    disp_station = {"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
                    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                    "fixed": {"rotation": [0, 0, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
                    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
                    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]},
                    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.35, 0.35, 0.35]}}
    for tier in (1, 2, 3):
        name = "solar_panel_t%d" % tier
        blocks[name] = None
        for state, m in sblocks.items():
            write_json(path("models", "block", "%s_%s.json" % (name, state)), m.json(parent="minecraft:block/block"))
        write_json(path("models", "item", name + ".json"), sitems[name].json(display=disp_solar))
        write_json(path("items", name + ".json"), {"model": {"type": "minecraft:model", "model": "fnations:item/" + name}})
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for active in ("false", "true"):
                v = {"model": "fnations:block/%s_%s" % (name, "on" if active == "true" else "off")}
                if y:
                    v["y"] = y
                variants["active=%s,facing=%s" % (active, facing)] = v
        write_json(path("blockstates", name + ".json"), {"variants": variants})
    blocks["crafting_station"] = station.json(parent="minecraft:block/block", display=disp_station)
    for name, m in {**sparts, **hparts}.items():
        write_json(path("models", "item", name + ".json"), m.json())
        write_json(path("items", name + ".json"), {"model": {"type": "minecraft:model", "model": "fnations:item/" + name}})

    # Machines : modèle de bloc + blockstate orienté + item
    for name, data in blocks.items():
        if data is None:
            continue
        write_json(path("models", "block", name + ".json"), data)
        write_json(path("items", name + ".json"), {"model": {"type": "minecraft:model", "model": "fnations:block/" + name}})
        variants = {}
        for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for active in ("false", "true"):
                v = {"model": "fnations:block/" + name}
                if y:
                    v["y"] = y
                variants["active=%s,facing=%s" % (active, facing)] = v
        write_json(path("blockstates", name + ".json"), {"variants": variants})
    gui_textures()
    print("Icônes de la tablette :", ui_icons())
    mod_icon()
    # Langues
    fr, en = {}, {}
    for k, (f, e) in NAMES.items():
        key = ("block.fnations." if k in MACHINES else "item.fnations.") + k
        fr[key], en[key] = f, e
    for k, (f, e) in EXTRA_LANG.items():
        fr[k], en[k] = f, e
    write_json(path("lang", "fr_fr.json"), fr)
    write_json(path("lang", "en_us.json"), en)
    print(f"Assets générés : {len(models)} modèles d'items, {len(blocks)} machines -> {os.path.normpath(ASSETS)}")


if __name__ == "__main__":
    main()
