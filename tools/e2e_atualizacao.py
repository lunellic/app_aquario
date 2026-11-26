#!/usr/bin/env python3
"""Atualizacao de ponta a ponta no emulador, tocando na tela como o Lucas toca no tablet.

Instalado no emulador esta um APK de versao 1, assinado com a mesma chave da release, e o
emulador tem cartao de memoria, como o Fire (o APK baixado vai para a pasta externa do app, igual
la). O script:

  1. abre o app e espera o aviso "Versao X disponivel" no Painel;
  2. toca no botao "Atualizar" (achado pela cor na tela, porque o dump do uiautomator nao
     enxerga o Compose no Android 5.1);
  3. espera o download, a conferencia e o instalador do sistema abrir;
  4. toca em "Instalar" no instalador;
  5. confere: a versao instalada virou a publicada, o app nao caiu em nenhum momento, e ele voltou
     sozinho para a frente depois da troca.

Cada etapa sai fotografada no log. Diferente do teste instrumentado, aqui o processo do teste nao
e o do app, entao o Android pode matar e trocar o app de verdade, como no tablet.
"""
import base64
import io
import json
import os
import re
import subprocess
import sys
import time
import urllib.request

from PIL import Image

PKG = "br.com.lunelli.lulucoreef"


def adb(*args, binario=False, timeout=120):
    r = subprocess.run(["adb", *args], capture_output=True, timeout=timeout)
    return r.stdout if binario else r.stdout.decode("utf-8", "replace")


def tela():
    return Image.open(io.BytesIO(adb("exec-out", "screencap", "-p", binario=True))).convert("RGB")


def foto(nome, img=None):
    img = img if img is not None else tela()
    menor = img.resize((800, img.height * 800 // img.width))
    buf = io.BytesIO()
    menor.save(buf, "JPEG", quality=70)
    b64 = base64.b64encode(buf.getvalue()).decode()
    for i in range(0, len(b64), 3000):
        print(f"{nome} {i // 3000:03d} {b64[i:i + 3000]}", flush=True)


def achar_cor(img, alvo, tol, caixa):
    """Centro dos pixels perto da cor `alvo` dentro de `caixa` (fracoes da tela). None se poucos."""
    w, h = img.size
    x0, y0, x1, y1 = int(caixa[0] * w), int(caixa[1] * h), int(caixa[2] * w), int(caixa[3] * h)
    px = img.load()
    xs, ys = [], []
    for y in range(y0, y1, 2):
        for x in range(x0, x1, 2):
            r, g, b = px[x, y]
            if abs(r - alvo[0]) <= tol and abs(g - alvo[1]) <= tol and abs(b - alvo[2]) <= tol:
                xs.append(x)
                ys.append(y)
    if len(xs) < 30:
        return None
    return sum(xs) // len(xs), sum(ys) // len(ys)


def tocar(xy, rotulo):
    print(f"E2E tocando em {rotulo} em {xy}", flush=True)
    adb("shell", "input", "tap", str(xy[0]), str(xy[1]))


def log_upd():
    return adb("logcat", "-d", "-v", "time", "-s", "LULUCO_UPD:I")


def quedas():
    t = adb("logcat", "-d", "-v", "time", "AndroidRuntime:E", "*:S")
    return [l for l in t.splitlines() if "FATAL" in l or PKG in l]


def versao():
    m = re.search(r"versionCode=(\d+)", adb("shell", "dumpsys", "package", PKG))
    return int(m.group(1)) if m else None


def foco():
    m = re.search(r"mCurrentFocus=Window\{\S+ \S+ ([^}\s]+)", adb("shell", "dumpsys", "window", "windows"))
    return m.group(1) if m else "?"


def esperar(cond, segundos, passo=2):
    fim = time.time() + segundos
    while time.time() < fim:
        v = cond()
        if v:
            return v
        time.sleep(passo)
    return None


def falhou(msg):
    foto("e2e_falha")
    print(f"E2E FALHOU: {msg}", flush=True)
    print("E2E foco agora: " + foco(), flush=True)
    for l in quedas()[-20:]:
        print("E2E queda: " + l, flush=True)
    return 1


def main():
    base = os.environ["SUPABASE_URL"].rstrip("/") + "/storage/v1/object/public/app"
    v = json.load(urllib.request.urlopen(base + "/versao.json"))
    publicada = int(v["build"])
    print(f"E2E publicada: {v.get('versao')} ({v.get('apk')})", flush=True)
    antes = versao()
    print(f"E2E versao instalada antes: {antes}", flush=True)
    print("E2E pasta externa: " + adb("shell", "ls", "-d", "/sdcard/Android/data").strip(), flush=True)

    # 1. abre e espera o aviso (botao lilas na metade direita, faixa de cima)
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")
    lilas = (167, 150, 255)
    botao = esperar(lambda: achar_cor(tela(), lilas, 40, (0.70, 0.15, 1.0, 0.65)), 90, 3)
    foto("e2e_1_aviso")
    if not botao:
        return falhou("o aviso de versao nova nao apareceu no Painel")

    # 2. toca em Atualizar
    tocar(botao, "Atualizar")
    if not esperar(lambda: "Baixando" in log_upd(), 15, 1):
        return falhou("tocou em Atualizar e o download nao comecou")
    time.sleep(1)
    foto("e2e_2_baixando")

    # 3. download, conferencia e instalador
    if not esperar(lambda: "abrindo o instalador" in log_upd() or "Falhou" in log_upd(), 240, 2):
        return falhou("o download nao terminou em 4 minutos")
    if "Falhou" in log_upd() and "abrindo o instalador" not in log_upd():
        return falhou("o download falhou: " + log_upd()[-400:])
    for l in log_upd().splitlines():
        if "abrindo" in l:
            print("E2E " + l.strip(), flush=True)
    if not esperar(lambda: "packageinstaller" in foco().lower(), 30, 1):
        return falhou("o instalador do sistema nao abriu")
    time.sleep(3)
    img = tela()
    foto("e2e_3_instalador", img)
    if quedas():
        return falhou("o app caiu antes de chegar ao instalador")

    # 4. toca em Instalar (texto verde-azulado na metade direita do rodape do dialogo)
    instalar = achar_cor(img, (0, 150, 136), 45, (0.5, 0.55, 1.0, 1.0))
    if not instalar:
        return falhou("nao achei o botao Instalar no instalador")
    tocar(instalar, "Instalar")

    # 5. troca de versao e volta sozinho
    depois = esperar(lambda: (versao() if versao() != antes else None), 120, 2)
    time.sleep(15)
    frente = foco()
    foto("e2e_4_depois")
    print(f"E2E versao depois: {depois} | foco: {frente}", flush=True)
    for l in quedas():
        print("E2E linha de queda: " + l, flush=True)
    if depois != publicada:
        return falhou(f"a versao nao virou a publicada ({depois} em vez de {publicada})")
    # Queda depois da troca nao conta: e o Android matando o app velho para trocar os arquivos.
    # O que conta e o app novo estar na frente sozinho.
    if PKG not in frente:
        return falhou("atualizou, mas o app nao voltou sozinho para a frente")
    print("E2E OK: tocou em Atualizar, baixou, tocou em Instalar, atualizou e o app voltou sozinho", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
