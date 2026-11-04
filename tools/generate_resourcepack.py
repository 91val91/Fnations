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
class Model:
    def __init__(self, textures):
        self.textures = dict(textures)
        self.elements = []

    def box(self, frm, to, tex, rot=None, faces=None, name=None):
        """Ajoute un cuboïde. tex = clé de texture, faces = dict face->clé optionnel."""
        for v in list(frm) + list(to):
            assert -16 <= v <= 32, f"coordonnée hors limites {frm} {to}"
        f = {}
        for face in ("north", "south", "east", "west", "up", "down"):
            t = (faces or {}).get(face, tex)
            f[face] = {"texture": "#" + t}
        e = {"from": list(frm), "to": list(to), "faces": f}
        if name:
            e["name"] = name
        if rot:
            e["rotation"] = rot
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

    m = Model({"b": T["blueprint"], "s": T["machine_side"], "d": T["dark_steel"], "w": T["wood"], "c": T["screen"]})
    m.box([0, 0, 0], [16, 12, 16], "s", name="établi")
    m.box([-1, 12, -1], [17, 14, 17], "w", faces={"up": "b"}, name="plateau")
    m.box([2, 14, 11], [14, 22, 13], "d", faces={"north": "c"}, name="écran")
    m.box([2, 14, 2], [5, 16, 5], "d", name="étau")
    out["crafting_station"] = m.json(parent="minecraft:block/block")

    for tier in (1, 2, 3):
        m = Model({"p": T["solar_t%d" % tier], "s": T["machine_side"], "d": T["dark_steel"], "t": T["machine_top"]})
        m.box([0, 0, 0], [16, 6, 16], "s", faces={"up": "t"}, name="boîtier")
        m.box([7, 6, 7], [9, 10, 9], "d", name="mât")
        m.box([-2, 10, -2], [18, 11, 18], "d", name="cadre")
        m.box([-1.5, 11, -1.5], [17.5, 11.5, 17.5], "p", faces={"down": "d"}, name="panneau")
        if tier >= 2:
            m.box([-2, 11.5, 7.5], [18, 12, 8.5], "d", name="séparateur")
        if tier == 3:
            m.box([7.5, 11.5, -2], [8.5, 12, 18], "d", name="séparateur 2")
        out["solar_panel_t%d" % tier] = m.json(parent="minecraft:block/block")
    return out


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
    """Textures des menus : panneau (style coffre, thème sombre doré) et machine."""
    base, light, dark, gold = hexc("2b2f36"), hexc("40464f"), hexc("15171b"), hexc("c9a13c")

    def bevel(d, x0, y0, x1, y1):
        d.rectangle([x0, y0, x1, y1], fill=base)
        d.line([x0, y0, x1, y0], fill=light)
        d.line([x0, y0, x0, y1], fill=light)
        d.line([x0, y1, x1, y1], fill=dark)
        d.line([x1, y0, x1, y1], fill=dark)

    def slot(d, x, y):
        d.rectangle([x, y, x + 17, y + 17], fill=hexc("1d2025"))
        d.line([x, y, x + 17, y], fill=dark)
        d.line([x, y, x, y + 17], fill=dark)
        d.line([x, y + 17, x + 17, y + 17], fill=hexc("4a515b"))
        d.line([x + 17, y, x + 17, y + 17], fill=hexc("4a515b"))

    # Panneau : 6 rangées en haut (y 0..124), inventaire du joueur en bas (y 126..221)
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bevel(d, 0, 0, 175, 124)
    d.line([3, 13, 172, 13], fill=gold)
    for r in range(6):
        for c in range(9):
            slot(d, 7 + c * 18, 17 + r * 18)
    bevel(d, 0, 126, 175, 221)
    d.rectangle([0, 126, 175, 128], fill=base)
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 139 + r * 18)
    for c in range(9):
        slot(d, 7 + c * 18, 197)
    img.save(path("textures", "gui", "panel.png"))

    # Machine : 176x166, cases en (52,35) (112,35) (80,35) (80,22), jauges à gauche/droite
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    bevel(d, 0, 0, 175, 165)
    d.line([3, 16, 172, 16], fill=gold)
    d.rectangle([8, 17, 20, 69], outline=gold)
    d.rectangle([156, 17, 168, 69], outline=hexc("b8262b"))
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 83 + r * 18)
    for c in range(9):
        slot(d, 7 + c * 18, 141)
    img.save(path("textures", "gui", "machine.png"))


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
    # Machines : modèle de bloc + blockstate orienté + item
    for name, data in blocks.items():
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
