@echo off
REM Reactiva todo lo desactivado hasta ahora. Ejecutar dentro de platform-tools.
set TV=192.168.0.48:5555
adb connect %TV%
adb -s %TV% shell pm enable com.miui.tv.analytics
adb -s %TV% shell pm enable com.xiaomi.statistic
adb -s %TV% shell pm enable com.google.android.feedback
adb -s %TV% shell pm enable com.google.android.tv.bugreportsender
adb -s %TV% shell pm enable com.google.android.videos
adb -s %TV% shell pm enable com.google.android.music
adb -s %TV% shell pm enable com.google.android.play.games
adb -s %TV% shell pm enable com.xm.webcontent
adb -s %TV% shell pm enable com.mitv.tvhome.mitvplus
adb -s %TV% shell pm enable com.mitv.tvhome.atv
echo.
echo Paquetes aun desactivados:
adb -s %TV% shell pm list packages -d
pause
