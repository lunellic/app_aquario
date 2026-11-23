#!/usr/bin/env python3
"""Reconstroi as capturas de tela que o teste de fumaca despeja no logcat.

O SmokeTest grava cada tela como JPEG, parte em pedacos de base64 e manda cada
pedaco para o logcat na tag LULUCO_SHOT, no formato "<tela> <nnn> <base64>".
Com -v raw o logcat nao repete a tag nas linhas de dado, so o conteudo. Aqui
juntamos os pedacos na ordem e devolvemos um .jpg por tela.
"""
import base64
import collections
import pathlib
import re
import sys

PEDACO = re.compile(r"(?:^|\s)([a-z_0-9]+)\s+(\d{3})\s+([A-Za-z0-9+/=]+)\s*$")


def main(entrada: str, saida: str) -> int:
    telas: dict[str, dict[int, str]] = collections.defaultdict(dict)
    with open(entrada, encoding="utf-8", errors="ignore") as f:
        for linha in f:
            m = PEDACO.search(linha.rstrip("\n"))
            if m:
                telas[m.group(1)][int(m.group(2))] = m.group(3)

    destino = pathlib.Path(saida)
    destino.mkdir(parents=True, exist_ok=True)
    if not telas:
        print("nenhuma captura no log", file=sys.stderr)
        return 0

    for nome, pedacos in sorted(telas.items()):
        esperado = range(len(pedacos))
        if sorted(pedacos) != list(esperado):
            # Pedaco perdido significa JPEG truncado. Melhor pular do que gravar lixo.
            print(f"{nome}: pedacos faltando, pulando", file=sys.stderr)
            continue
        dados = base64.b64decode("".join(pedacos[i] for i in esperado))
        (destino / f"{nome}.jpg").write_bytes(dados)
        print(f"{nome}.jpg  {len(dados)} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1], sys.argv[2]))
