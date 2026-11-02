#!/bin/bash
# Abre o app num emulador e guarda telas e logcat para diagnóstico.
mkdir -p diag
adb install -r app/build/outputs/apk/release/app-release.apk > diag/install.txt 2>&1
adb shell pm grant com.dmwnezes.radar android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W -n com.dmwnezes.radar/.MainActivity > diag/start.txt 2>&1
sleep 6;  adb exec-out screencap -p > diag/t06.png
sleep 14; adb exec-out screencap -p > diag/t20.png
adb shell dumpsys activity activities | grep -E 'mResumedActivity|topResumedActivity' > diag/activity.txt
adb shell pidof com.dmwnezes.radar > diag/pid.txt
sleep 15; adb exec-out screencap -p > diag/t35.png
TAM=$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1); L=${TAM%x*}; A=${TAM#*x}; echo "$TAM" > diag/tam.txt
Y=$((A * 94 / 100))
adb shell input tap $((L * 18 / 100)) $Y; sleep 3; adb exec-out screencap -p > diag/radar.png
adb shell input tap $((L * 82 / 100)) $Y; sleep 3; adb exec-out screencap -p > diag/jogos.png
adb shell input tap $((L / 2)) $Y; sleep 2
adb shell input tap $((L / 2)) $((A * 30 / 100)); sleep 8; adb exec-out screencap -p > diag/detalhe.png
adb shell pidof com.dmwnezes.radar >> diag/pid.txt
adb logcat -d > diag/logcat.txt
adb logcat -b crash -d > diag/crash.txt
ls -la /data/anr 2>/dev/null > /dev/null
true
