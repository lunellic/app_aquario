#!/usr/bin/env bash
# Roda dentro do emulador do CI (reactivecircus/android-emulator-runner).
#
# Num arquivo, e nao direto no "script:" do workflow: aquele campo roda CADA LINHA num "sh -c"
# separado, entao "status=$?" numa linha e "if [ $status ... ]" na seguinte nao enxergavam a
# mesma variavel, e teste instrumentado que falhava deixava o build verde.
set -u
adb logcat -G 16M
adb shell settings put secure immersive_mode_confirmations confirmed
gradle --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=br.com.lunelli.lulucoreef.SmokeTest#capturasDeTela; status=$?
# A captura vem antes do exit: build que quebrou e justamente o que a gente
# quer ver na tela, nao o que a gente quer ficar sem print.
# tee e nao redirecionamento: o artefato e o jeito bom de pegar as telas, mas o
# log continua sendo o unico jeito de ver um print de onde nao da para baixar zip.
adb logcat -d -v raw -s LULUCO_SHOT:I | tee capturas.log
echo "=== atualizacao e quedas ==="
adb logcat -d -v time -s LULUCO_UPD:I AndroidRuntime:E PackageInstaller:* ActivityManager:I | grep -E "LULUCO_UPD|FATAL|AndroidRuntime|Exception|at br\.com|PackageInstaller|install" | tail -80 || true
if [ $status -ne 0 ]; then adb logcat -d -t 400 '*:E'; fi
# Atualizacao de ponta a ponta, tocando na tela: um APK "versao 1" com a assinatura
# da release; o script toca em Atualizar no Painel e em Instalar no instalador do
# sistema, e confere que a versao trocou e que o app voltou sozinho.
echo "PROVA: sem ponta a ponta neste ramo"; exit 0
echo "=== atualizacao de ponta a ponta ==="
GITHUB_RUN_NUMBER=1 gradle --no-daemon -q assembleDebug
adb uninstall br.com.lunelli.lulucoreef >/dev/null 2>&1 || true
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell run-as br.com.lunelli.lulucoreef mkdir -p files
adb shell run-as br.com.lunelli.lulucoreef touch files/e2e
python3 -m pip install --quiet --disable-pip-version-check pillow >/dev/null 2>&1 || pip3 install --quiet pillow >/dev/null 2>&1 || true
adb logcat -c
SUPABASE_URL=$(grep "^SUPABASE_URL=" gradle.properties | cut -d= -f2-) python3 tools/e2e_atualizacao.py | tee e2e.log; e2e=${PIPESTATUS[0]}
adb logcat -d -v time | grep -E "LULUCO_UPD|FATAL|AndroidRuntime|PackageManager.*lulucoreef|INSTALL_FAILED|BootReceiver|MY_PACKAGE_REPLACED" | tail -60 || true
cat e2e.log >> capturas.log
echo "=== resultado: testes instrumentados $status, atualizacao de ponta a ponta $e2e ==="
# os dois contam: qualquer um falhando deixa o build vermelho
[ $status -eq 0 ] && [ $e2e -eq 0 ]
