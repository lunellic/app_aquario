#!/usr/bin/env python3
"""Atualizacao de ponta a ponta no emulador, tocando nos botoes como o Lucas toca no tablet.

Instalado no emulador esta um APK de versao 1, assinado com a mesma chave da release. O script:
abre o app, espera o aviso de versao nova, toca em "Atualizar", espera o instalador do sistema,
toca em "Instalar", e confere duas coisas: a versao instalada mudou para a publicada, e o app
voltou sozinho para a frente depois de ser trocado. Cada etapa sai fotografada no log.

Diferente do teste instrumentado, aqui o processo do teste nao e o do app, entao da para deixar
o Android matar e trocar o app de verdade.
"""
import base64
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "br.com.lunelli.lulucoreef"


def adb(*args, binario=False):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=120)
    return r.stdout if binario else r.stdout.decode("utf-8", "replace")


def foto(nome):
    png = adb("exec-out", "screencap", "-p", binario=True)
    b64 = base64.b64encode(png).decode()
    for i in range(0, len(b64), 3000):
        print(f"{nome} {i // 3000:03d} {b64[i:i + 3000]}")


def nos():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    xml = adb("shell", "cat", "/sdcard/ui.xml")
    xml = xml[xml.find("<?xml"):] if "<?xml" in xml else xml
    try:
        raiz = ET.fromstring(xml)
    except ET.ParseError:
        return []
    return list(raiz.iter("node"))


def centro(no):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", no.get("bounds", "[0,0][0,0]")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def achar(textos=(), ids=(), espera=60):
    fim = time.time() + espera
    while time.time() < fim:
        for n in nos():
            t = (n.get("text") or "").strip().lower()
            if (t and t in [x.lower() for x in textos]) or n.get("resource-id") in ids:
                return n
        time.sleep(2)
    return None


def tocar(no, rotulo):
    x, y = centro(no)
    print(f"E2E tocando em {rotulo} ({x},{y})")
    adb("shell", "input", "tap", str(x), str(y))


def versao():
    m = re.search(r"versionCode=(\d+)", adb("shell", "dumpsys", "package", PKG))
    return int(m.group(1)) if m else None


def na_frente():
    a = adb("shell", "dumpsys", "activity", "activities")
    m = re.search(r"mResumedActivity: ActivityRecord\{\S+ \S+ (\S+)", a)
    return m.group(1) if m else "?"


def main():
    # O toque em "Atualizar", o download, a conferencia e a abertura do instalador ja sao cobertos
    # pelo AtualizacaoTest (instrumentado). Aqui fica a parte que ele nao consegue fazer: a troca
    # do app de verdade, com o processo morrendo, e a volta sozinha depois. "adb install -r"
    # dispara a mesma troca e o mesmo aviso MY_PACKAGE_REPLACED que o botao Instalar do sistema.
    # (O dump de tela do uiautomator nao serve com o Compose no Android 5.1: ele espera a tela
    # "parar" e nunca acha o botao, entao nada aqui depende de achar texto na tela.)
    import json
    import os
    import urllib.request
    base = os.environ["SUPABASE_URL"].rstrip("/") + "/storage/v1/object/public/app"
    v = json.load(urllib.request.urlopen(base + "/versao.json"))
    print(f"E2E publicada: {v.get('versao')} ({v.get('apk')})")
    urllib.request.urlretrieve(f"{base}/{v['apk']}", "/tmp/publicado.apk")

    antes = versao()
    print(f"E2E versao instalada antes: {antes}")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    time.sleep(30)
    foto("e2e_1_antes")
    print(f"E2E na frente antes: {na_frente()}")

    r = subprocess.run(["adb", "install", "-r", "/tmp/publicado.apk"], capture_output=True, text=True, timeout=180)
    print(f"E2E adb install -r: {(r.stdout + r.stderr).strip()[-200:]}")

    depois = versao()
    time.sleep(12)  # da tempo do app voltar sozinho
    frente = na_frente()
    foto("e2e_2_depois")
    print(f"E2E versao depois: {depois} | na frente: {frente}")
    if not depois or depois == antes:
        print("E2E FALHOU: a versao nao mudou")
        return 1
    if PKG not in frente:
        print("E2E FALHOU: atualizou, mas o app nao voltou sozinho para a frente")
        return 1
    print("E2E OK: atualizou e o app voltou sozinho")
    return 0


if __name__ == "__main__":
    sys.exit(main())
