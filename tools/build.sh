#!/usr/bin/env bash
# Construit tout : plugin, resource pack (zip + SHA-1), datapack (zip), et prépare server/
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist"
mkdir -p "$OUT"

echo "==> Plugin"
(cd "$ROOT/plugin" && gradle build -q)
cp "$ROOT/plugin/build/libs/"FNations-*.jar "$OUT/"

echo "==> Resource pack"
python3 "$ROOT/tools/generate_resourcepack.py"
rm -f "$OUT/FNations-RP.zip"
(cd "$ROOT/resourcepack/FNations-RP" && zip -qr "$OUT/FNations-RP.zip" .)
SHA=$(sha1sum "$OUT/FNations-RP.zip" | cut -d' ' -f1)
echo "    SHA-1 : $SHA  (à mettre dans resource-pack-sha1)"

echo "==> Datapack"
rm -f "$OUT/FNations-datapack.zip"
(cd "$ROOT/datapack/fnations" && zip -qr "$OUT/FNations-datapack.zip" .)

echo "==> Serveur"
mkdir -p "$ROOT/server/plugins" "$ROOT/server/world/datapacks"
cp "$OUT/"FNations-*.jar "$ROOT/server/plugins/"
cp "$OUT/FNations-datapack.zip" "$ROOT/server/world/datapacks/"
echo "Terminé. Fichiers dans $OUT"
echo "Pensez à placer paper.jar et WorldEdit dans server/ (voir README)."
