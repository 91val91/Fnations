@echo off
cd /d "%~dp0"
if not exist fabric-server-launch.jar (
  echo Telechargement du lanceur de serveur Fabric...
  powershell -Command "Invoke-WebRequest -Uri 'https://meta.fabricmc.net/v2/versions/loader/1.21.4/0.16.14/1.1.2/server/jar' -OutFile 'fabric-server-launch.jar'"
)
java -Xms2G -Xmx6G -XX:+UseG1GC -jar fabric-server-launch.jar nogui
pause
