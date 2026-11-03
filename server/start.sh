#!/usr/bin/env bash
# Lance le serveur Paper (Java 21 requis)
cd "$(dirname "$0")"
exec java -Xms2G -Xmx6G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 \
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 \
  -jar paper.jar --nogui
