@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Personal Worlds - клиент

echo Запускаю Minecraft 1.21.1 с NeoForge и сразу захожу на localhost:25565.
echo Сначала должен быть запущен run-server.bat.
echo.

call gradlew.bat runClient -Pquick_play=localhost:25565
pause
