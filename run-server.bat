@echo off
chcp 65001 >nul
cd /d "%~dp0"
title Personal Worlds - тестовый сервер

echo Запускаю тестовый сервер Minecraft 1.21.1 с модом Personal Worlds (порт 25565).
echo Когда появится "Done", запускайте run-client.bat.
echo Чтобы выдать себе права оператора, введите здесь: op Dev
echo Остановить сервер: stop
echo.

call gradlew.bat runServer
pause
