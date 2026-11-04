#!/usr/bin/env python3
"""
Données de la carte du monde FNations (Terre au 1/1000 : 1 bloc = 1 km).

Télécharge (une fois, dans tools/.earth-cache/) :
  - NOAA ETOPO5 : altitude et bathymétrie mondiales, grille de 5 minutes d'arc (4320 x 2160) ;
  - Natural Earth II (couverture du sol, 16200 x 8100) : forêts, prairies, déserts, toundra, glace ;
  - Natural Earth admin 0 (1:50m) : frontières des pays, noms français et continents.

Produit dans mod/src/main/resources/fnations-earth/ :
  - elevation.png : altitude encodée sur 8 bits (0..99 océan, 100..255 terre, échelle racine carrée) ;
  - cover.png     : classe de couverture du sol par pixel (voir CLASSES) ;
  - countries.png : indice du pays par pixel (0 = aucun) + countries.json (noms, continents).
Les trois images font 4320 x 2160 pixels en projection équirectangulaire (1 pixel = 9,28 km).
"""
import colorsys
import json
import math
import os
import sys
import urllib.request
import zipfile

import numpy as np
from PIL import Image, ImageDraw

Image.MAX_IMAGE_PIXELS = None
ROOT = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(ROOT, ".earth-cache")
OUT = os.path.join(ROOT, "..", "mod", "src", "main", "resources", "fnations-earth")
W, H = 4320, 2160

SOURCES = {
    "ETOPO5.DAT": "https://www.ngdc.noaa.gov/mgg/global/relief/ETOPO5/TOPO/ETOPO5/ETOPO5.DAT",
    "NE2_LR_LC.zip": "https://naciscdn.org/naturalearth/10m/raster/NE2_LR_LC.zip",
    "countries50.geojson": "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson",
}

# Classes de couverture du sol (doivent correspondre à fr.fnations.earth.EarthData)
WATER, ICE, TUNDRA, FOREST, MIXED, GRASS, STEPPE, DESERT, BARREN, LAKE = range(10)
CLASS_COLORS = {WATER: (40, 70, 140), ICE: (235, 240, 250), TUNDRA: (170, 175, 150), FOREST: (40, 110, 50), MIXED: (110, 160, 70),
                GRASS: (170, 190, 90), STEPPE: (200, 180, 110), DESERT: (235, 215, 160), BARREN: (140, 135, 130), LAKE: (70, 120, 200)}
CONTINENTS = {"Europe": "Europe", "Asia": "Asie", "Africa": "Afrique", "North America": "Amérique du Nord", "South America": "Amérique du Sud",
              "Oceania": "Océanie", "Antarctica": "Antarctique", "Seven seas (open ocean)": "Océans"}


def fetch(name):
    os.makedirs(CACHE, exist_ok=True)
    p = os.path.join(CACHE, name)
    if not os.path.exists(p):
        print("Téléchargement de", name, "...")
        urllib.request.urlretrieve(SOURCES[name], p)
    return p


def encode_elevation(e):
    """Mètres -> code 8 bits (racine carrée : plus de précision près du niveau de la mer)."""
    e = e.astype(np.float64)
    out = np.empty(e.shape, dtype=np.uint8)
    sea = e < 0
    d = np.clip(-e[sea], 0, 11000)
    out[sea] = np.clip(99 - np.round(np.sqrt(d / 11000.0) * 99), 0, 99).astype(np.uint8)
    land = np.clip(e[~sea], 0, 9000)
    out[~sea] = np.clip(100 + np.round(np.sqrt(land / 9000.0) * 155), 100, 255).astype(np.uint8)
    return out


def elevation():
    raw = np.fromfile(fetch("ETOPO5.DAT"), dtype=">i2").reshape(2160, 4320)
    # ETOPO5 commence à 0° de longitude : on recentre sur -180°
    return np.roll(raw, 2160, axis=1).astype(np.int32)


def cover(elev):
    z = fetch("NE2_LR_LC.zip")
    tif = os.path.join(CACHE, "NE2_LR_LC.tif")
    if not os.path.exists(tif):
        with zipfile.ZipFile(z) as zf:
            for n in zf.namelist():
                if n.endswith(".tif"):
                    with zf.open(n) as src, open(tif, "wb") as dst:
                        dst.write(src.read())
    img = Image.open(tif).convert("RGB").resize((W, H), Image.BOX)
    a = np.asarray(img).astype(np.float64) / 255.0
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    mx, mn = a.max(axis=2), a.min(axis=2)
    v = mx
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    hue = np.zeros_like(v)
    d = np.maximum(mx - mn, 1e-6)
    hue = np.where(mx == r, ((g - b) / d) % 6, hue)
    hue = np.where(mx == g, (b - r) / d + 2, hue)
    hue = np.where(mx == b, (r - g) / d + 4, hue)
    hue = hue * 60.0
    lat = 90 - (np.arange(H) + 0.5) / H * 180
    lat = np.repeat(lat[:, None], W, axis=1)
    c = np.full((H, W), MIXED, dtype=np.uint8)
    white = (r > 0.985) & (g > 0.985) & (b > 0.985)
    alat = np.abs(lat)
    c[(hue >= 105) & (hue < 170)] = FOREST
    c[(hue >= 75) & (hue < 105)] = MIXED
    c[(hue >= 50) & (hue < 75)] = GRASS
    c[(hue < 50) & (v < 0.915)] = STEPPE
    c[(hue < 50) & (v >= 0.915)] = DESERT
    bluish = (hue >= 170) & (hue <= 270)
    c[bluish] = np.where(alat[bluish] > 55, TUNDRA, BARREN)
    grey = s < 0.065
    c[grey & (alat >= 55)] = TUNDRA
    c[grey & (alat < 55)] = BARREN
    c[(grey | bluish) & (v > 0.94) & (hue >= 170) & (hue <= 270)] = ICE
    c[(alat > 60) & (v > 0.95) & (s < 0.09) & (hue >= 170)] = ICE
    # pas de forêt ni de prairie au-delà de la limite des arbres
    polar = (alat > 72) & ((c == FOREST) | (c == MIXED) | (c == GRASS) | (c == STEPPE) | (c == DESERT))
    c[polar] = TUNDRA
    # l'Antarctique et l'intérieur du Groenland sont des calottes de glace
    c[(lat < -60)] = ICE
    # eau : l'altitude décide de la mer, le blanc pur des terres (hors pôles) = lac
    c[white & (elev >= 0) & (alat < 66)] = LAKE
    c[white & (elev >= 0) & (alat >= 66)] = ICE
    c[elev < 0] = WATER
    return c


def countries():
    gj = json.load(open(fetch("countries50.geojson"), encoding="utf-8"))
    img = Image.new("L", (W, H), 0)
    dr = ImageDraw.Draw(img)
    meta = [{"n": "", "c": ""}]
    feats = sorted(gj["features"], key=lambda f: -(f["properties"].get("POP_EST") or 0))
    for f in feats:
        p = f["properties"]
        if len(meta) >= 255:
            break
        name = p.get("NAME_FR") or p.get("NAME") or "?"
        meta.append({"n": name, "c": CONTINENTS.get(p.get("CONTINENT", ""), p.get("CONTINENT", "")), "iso": p.get("ISO_A2", "")})
        idx = len(meta) - 1
        geom = f["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        for poly in polys:
            for k, ring in enumerate(poly):
                pts = [((lon + 180) / 360 * W, (90 - lat) / 180 * H) for lon, lat in ring]
                if len(pts) >= 3:
                    dr.polygon(pts, fill=idx if k == 0 else 0)
    return np.asarray(img), meta


def main():
    os.makedirs(OUT, exist_ok=True)
    e = elevation()
    print("Altitude : min", e.min(), "max", e.max())
    code = encode_elevation(e)
    Image.fromarray(code, "L").save(os.path.join(OUT, "elevation.png"), optimize=True)
    c = cover(e)
    Image.fromarray(c, "L").save(os.path.join(OUT, "cover.png"), optimize=True)
    cmap, meta = countries()
    Image.fromarray(cmap.astype(np.uint8), "L").save(os.path.join(OUT, "countries.png"), optimize=True)
    with open(os.path.join(OUT, "countries.json"), "w", encoding="utf-8") as fp:
        json.dump(meta, fp, ensure_ascii=False, separators=(",", ":"))
    # aperçu (classes + relief) pour contrôle visuel
    rgb = np.zeros((H, W, 3), dtype=np.uint8)
    for k, col in CLASS_COLORS.items():
        rgb[c == k] = col
    shade = np.clip(1 + (np.roll(e, 1, axis=1) - e) / 3000.0, 0.6, 1.3)
    rgb = np.clip(rgb * shade[..., None], 0, 255).astype(np.uint8)
    prev = sys.argv[1] if len(sys.argv) > 1 else os.path.join(CACHE, "preview.png")
    Image.fromarray(rgb).resize((2160, 1080), Image.BOX).save(prev)
    sizes = {f: os.path.getsize(os.path.join(OUT, f)) // 1024 for f in os.listdir(OUT)}
    print("Fichiers (Ko) :", sizes, "| pays :", len(meta) - 1, "| aperçu :", prev)


if __name__ == "__main__":
    main()
