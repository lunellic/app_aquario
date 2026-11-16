# Luluco Reef

App Android (tablet de parede) para acompanhar um aquário marinho: medições, TPAs, dosagens, rotinas, inventário e alertas. Especificação em `claude/spec-app-aquario-v1.md` do projeto.

## Estado

- [x] Bloco A.1: projeto Android, tema, ícone, CI gerando APK
- [x] Bloco A.2: prova de conexão com o Supabase (TLS com raízes embutidas, login, consulta)
- [x] Bloco A.3: migrações SQL com RLS e seeds (`supabase/migrations`)
- [ ] Bloco A.4 em diante

## Dispositivo alvo e versões fixadas

Alvo principal: Amazon Fire HD 8 (2017), Fire OS 5.7 = Android 5.1 (API 22). Por isso `minSdk 22`.

O AndroidX passou a exigir minSdk 23 nas versões novas. Ficam fixadas as últimas linhas estáveis que ainda aceitam API 21/22. **Não atualizar sem conferir o minSdk da nova versão** (o build falha no manifest merger se passar de 22).

| Biblioteca | Versão | Observação |
|---|---|---|
| Android Gradle Plugin / Gradle | 8.9.1 / 8.11.1 | compileSdk e targetSdk 35 |
| Kotlin + plugin Compose | 2.1.20 | |
| Compose BOM | 2025.05.01 (Compose 1.8, Material 3 1.3) | Compose 1.9+ exige API 23 |
| activity-compose | 1.10.1 | 1.11+ exige API 23 |
| core-ktx | 1.16.0 | 1.17+ exige API 23 |
| lifecycle | 2.9.1 | 2.10+ exige API 23 |
| OkHttp | 4.12.0 | suporta Android 5.0+ |
| desugar_jdk_libs | 2.1.5 | java.time no Android 5.1 |

Decisão de UI: **Jetpack Compose (Material 3)**. Compila e empacota com minSdk 22 nas versões acima. Decisão fechada.

Rede: OkHttp chamando REST e Auth do Supabase direto (sem supabase-kt).

## Certificados (Android 5.1)

Cadeia real de `*.supabase.co` (conferida no CI a cada build): `supabase.co` -> `WE1` -> `GTS Root R4` (com assinatura cruzada da GlobalSign Root CA). O Android 5.1 não tem as raízes GTS nem ISRG. Em `app/src/main/res/raw` ficam embutidas GTS Root R1, R3, R4 e ISRG Root X1, X2. `TrustedHttp` monta um TrustManager com raízes do sistema + embutidas. A validação nunca é desligada. TLS 1.2.

## Build

GitHub Actions (`.github/workflows/android.yml`): cada push na `main` roda os testes e publica o APK como artifact; tag `v*` cria um Release com o APK.

### Assinatura fixa (pendente)

Sem os secrets abaixo o APK sai com chave temporária e cada atualização exige desinstalar o app antes. Para fixar, gere uma keystore e grave em Settings > Secrets and variables > Actions:

```
keytool -genkeypair -keystore luluco.jks -storetype JKS -alias luluco -keyalg RSA -keysize 2048 -validity 10950
base64 -w0 luluco.jks   # conteúdo vai em KEYSTORE_BASE64
```

Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Guarde o arquivo `.jks` fora do repositório.

## Supabase

- URL e publishable key ficam em `gradle.properties` (são públicas por natureza). Nenhum segredo vai no APK ou no repositório.
- Migrações em `supabase/migrations`, na ordem do nome. Enquanto o deploy automático não existe: painel do Supabase > SQL Editor > New query, colar o conteúdo de cada arquivo e Run, primeiro `..._schema.sql`, depois `..._seed.sql`. As duas podem ser rodadas de novo sem duplicar nada.
- O seed grava os dados no primeiro usuário de `auth.users`.

## Instalar no Fire

1. Configurações > Segurança > ativar "Apps de fontes desconhecidas".
2. Baixar o APK da página de Releases (o repositório é privado: precisa estar logado no GitHub no navegador Silk) ou copiar por USB.
3. Abrir o arquivo e instalar.
