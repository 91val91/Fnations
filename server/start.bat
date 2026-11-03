@echo off
cd /d "%~dp0"
java -Xms2G -Xmx6G -XX:+UseG1GC -jar paper.jar --nogui
pause
