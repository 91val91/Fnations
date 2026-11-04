#!/usr/bin/env bash
# Serveur Fabric 1.21.4 + FNations (Java 21 requis).
set -e
cd "$(dirname "$0")"
if [ ! -f fabric-server-launch.jar ]; then
  echo "Téléchargement du lanceur de serveur Fabric..."
  curl -fsSL -o fabric-server-launch.jar "https://meta.fabricmc.net/v2/versions/loader/1.21.4/0.16.14/1.1.2/server/jar"
fi
exec java -Xms2G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -jar fabric-server-launch.jar nogui
