@echo off
REM Ejecutar dentro de platform-tools, con app-debug.apk copiado en la misma carpeta.
REM Cambia la IP si la TV ha cambiado.
set TV=192.168.0.48:5555
adb connect %TV%
adb -s %TV% push app-debug.apk /data/local/tmp/tvlauncher.apk
adb -s %TV% shell pm install -r /data/local/tmp/tvlauncher.apk
adb -s %TV% shell cmd package query-activities -a android.intent.action.MAIN -c android.intent.category.HOME
echo.
echo Si aparece com.rafa.tvlauncher en la lista, el APK se ha instalado.
echo Para dejarlo como Home, ejecuta a mano:
echo   adb shell cmd package set-home-activity com.rafa.tvlauncher/.MainActivity
pause
