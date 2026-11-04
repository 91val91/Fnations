#!/usr/bin/env bash
# Construit tout : assets du mod, mod Fabric, dossier dist/ (mods + manifeste du launcher) et prépare server/.
# Usage : ./tools/build.sh [url-de-base-des-mods] [adresse-serveur] [port]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BASE_URL="${1:-https://example.com/fnations/mods}"
ADDRESS="${2:-play.example.com}"
PORT="${3:-25565}"
FABRIC_API="0.119.4+1.21.4"
WORLDEDIT_URL="https://cdn.modrinth.com/data/1u6JkXh5/versions/bxlboAan/worldedit-mod-7.3.11.jar"

echo "==> Assets du mod (modèles, textures, langues)"
python3 "$ROOT/tools/generate_resourcepack.py"

echo "==> Mod Fabric"
(cd "$ROOT/mod" && ./gradlew build -q)

echo "==> dist/mods"
mkdir -p "$ROOT/dist/mods"
rm -f "$ROOT/dist/mods/"fnations-*.jar
cp "$ROOT/mod/build/libs/fnations-"*[0-9].jar "$ROOT/dist/mods/"
API_JAR="$ROOT/dist/mods/fabric-api-$FABRIC_API.jar"
[ -f "$API_JAR" ] || curl -fsSL -o "$API_JAR" "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/${FABRIC_API/+/%2B}/fabric-api-${FABRIC_API/+/%2B}.jar"

echo "==> Manifeste du launcher"
node "$ROOT/launcher/tools/make-manifest.mjs" "$ROOT/dist/mods" "$BASE_URL" "$ADDRESS" "$PORT" > "$ROOT/dist/manifest.json"

echo "==> Serveur"
mkdir -p "$ROOT/server/mods"
cp "$ROOT/dist/mods/"*.jar "$ROOT/server/mods/"
echo "    (optionnel) WorldEdit pour les chambres : téléchargez-le depuis Modrinth dans server/mods :"
echo "    $WORLDEDIT_URL"
echo "Terminé. Hébergez dist/mods/* à l'adresse $BASE_URL et dist/manifest.json à l'URL configurée dans le launcher."
