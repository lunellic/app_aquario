#!/usr/bin/env python3
"""Sobe o APK do build para a pasta do Luluco Reef no Google Drive.

O Lucas instala no tablet pelo Drive, sem depender do computador. A pasta ja tinha um
luluco-reef.apk; este script substitui o CONTEUDO desse mesmo arquivo em vez de criar um
novo a cada build. Assim o link nao muda, o atalho dele continua valendo e a pasta nao
vira um deposito de APK velho. O numero do build vai na descricao do arquivo.

Credencial: conta de servico, no segredo GDRIVE_SA_JSON. A pasta precisa estar
compartilhada como Editor com o e-mail da conta de servico.
"""
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

ESCOPO = ["https://www.googleapis.com/auth/drive"]
NOME = "luluco-reef.apk"
TIPO = "application/vnd.android.package-archive"


def main(caminho: str, pasta: str, descricao: str) -> int:
    bruto = os.environ.get("GDRIVE_SA_JSON", "").strip()
    if not bruto:
        print("GDRIVE_SA_JSON vazio: nada enviado ao Drive.", file=sys.stderr)
        return 0

    import json

    cred = service_account.Credentials.from_service_account_info(json.loads(bruto), scopes=ESCOPO)
    drive = build("drive", "v3", credentials=cred, cache_discovery=False)
    midia = MediaFileUpload(caminho, mimetype=TIPO, resumable=True)

    achados = (
        drive.files()
        .list(
            q=f"'{pasta}' in parents and name = '{NOME}' and trashed = false",
            fields="files(id, name)",
            supportsAllDrives=True,
            includeItemsFromAllDrives=True,
        )
        .execute()
        .get("files", [])
    )

    if achados:
        fid = achados[0]["id"]
        drive.files().update(
            fileId=fid, media_body=midia, body={"description": descricao}, supportsAllDrives=True
        ).execute()
        print(f"Atualizado {NOME} ({fid}): {descricao}")
    else:
        novo = (
            drive.files()
            .create(
                body={"name": NOME, "parents": [pasta], "description": descricao},
                media_body=midia,
                fields="id",
                supportsAllDrives=True,
            )
            .execute()
        )
        print(f"Criado {NOME} ({novo['id']}): {descricao}")
    return 0


if __name__ == "__main__":
    # Nunca derruba o build: o APK ja esta publicado na release e no Storage, o Drive e copia
    # de conveniencia. Mas o motivo sai escrito no log, para nao falhar calado.
    try:
        raise SystemExit(main(sys.argv[1], sys.argv[2], sys.argv[3]))
    except SystemExit:
        raise
    except Exception as e:  # noqa: BLE001
        msg = str(e)
        if "storageQuotaExceeded" in msg or "do not have storage quota" in msg:
            # Conta de servico nao tem espaco proprio no Drive pessoal: ela consegue TROCAR o
            # conteudo de um arquivo que ja e do Lucas, mas nao CRIAR um arquivo novo.
            print("::warning::O Drive recusou criar o arquivo. Deixe um luluco-reef.apk na pasta "
                  "(qualquer versao) e o proximo build passa a atualizar esse arquivo.")
        else:
            print(f"::warning::Nao subiu para o Drive: {msg[:300]}")
        raise SystemExit(0)
