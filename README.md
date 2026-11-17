# Luluco Reef

App Android (tablet de parede) para acompanhar um aquário marinho: medições, TPAs, dosagens, rotinas, inventário e alertas. Especificação em `claude/spec-app-aquario-v1.md` do projeto.

## Estado

- [x] Bloco A (essencial): login com sessão persistente, Room, sincronização offline, Registrar, Histórico, Meu Aquário, Produtos e Rotinas, Painel com alertas, lembretes locais com som, vitrine com bolhas, modo noite
- [x] Bloco B (acompanhamento): gráficos com projeção, Saúde (casos e entradas com foto), Equipamentos, nota de saúde explicada
- [x] Bloco C (nuvem e IA): código pronto (Edge Functions `assistant` e `notifier`, cron, telas). **Falta publicar**: depende dos secrets abaixo. Ainda não foi executado contra o projeto real.

## Modo quiosque (v0.3)

O tablet é usado só para o app:
- tela cheia (immersive sticky) em todas as telas; as barras somem de novo sozinhas depois de arrastar da borda, do teclado ou de um diálogo;
- aparece por cima da tela de bloqueio e liga a tela (`FLAG_SHOW_WHEN_LOCKED`, `FLAG_DISMISS_KEYGUARD`, `FLAG_TURN_SCREEN_ON`). Só funciona sem PIN/senha no aparelho;
- abre sozinho ao ligar o tablet e vem para a frente na hora de uma rotina (fora do modo noite);
- para sair: voltar duas vezes no Painel em até 2 s.

Tela inicial (launcher): em Ajustes > "Oferecer como tela inicial do tablet" o app passa a responder ao botão Home (um `activity-alias` com categoria HOME, desligado por padrão). O Fire OS 5 costuma bloquear launcher de terceiros; se o botão Home continuar abrindo a tela da Amazon, deixe desligado. Não foi testado em Fire real.

## Visual (v0.3)

Tema água sem preto: fundo em degradê azul-marinho da marca com reflexos (cáusticas) de um bitmap pré-renderizado, cards translúcidos, ondas no cabeçalho, anel da nota de saúde, números contando, botões com mola e transição entre telas. Tudo em Canvas a ~20 quadros por segundo, sem shader (o Fire é API 22), pausando fora da tela. Ajustes > "Animações de água" desliga os efeitos se o tablet ficar lento.

Fontes embutidas (licença OFL, textos em `docs/fonts`): Fredoka SemiBold (títulos e números) e Manrope (textos).

## Relatórios

Menu > Relatórios: escolhe o período (7, 30, 90 dias, este mês, mês passado, tudo) e as seções (resumo com a última TPA, testes, consumo, TPAs, produtos usados, rotinas e manutenção, notas). O texto é montado no app (`domain/Report.kt`). "Enviar para meu e-mail" usa a Edge Function `notifier` (modo `report`, Resend) e por isso depende do backend publicado; "Compartilhar" abre os apps do aparelho.

## Testes

- Unitários (`app/src/test`): regras de faixa, projeção, alertas, rotinas, doses, nota de saúde, JSON do Supabase.
- Fumaça em emulador **Android 5.1 (API 22)** no CI (`app/src/androidTest`): handshake TLS real com o Supabase usando as raízes embutidas, fluxo principal das telas offline e migração do banco local (v1 para v2).
- O mesmo job grava capturas de tela no log (tag `LULUCO_SHOT`, JPEG em base64) para conferir o visual sem precisar do aparelho.

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
| Room | 2.7.2 | 2.8+ exige API 23 |
| WorkManager | 2.10.3 | 2.11+ exige API 23 |
| KSP | 2.1.20-1.0.32 | par do Kotlin 2.1.20 |
| kotlinx-serialization-json | 1.8.1 | |
| OkHttp | 4.12.0 | suporta Android 5.0+ |
| desugar_jdk_libs | 2.1.5 | java.time no Android 5.1 |

Decisão de UI: **Jetpack Compose (Material 3)**. Compila e empacota com minSdk 22 nas versões acima. Decisão fechada. Navegação própria (pilha simples), sem a biblioteca Navigation, para ficar leve no Fire.

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
- Migrações em `supabase/migrations`, na ordem do nome. Enquanto o deploy automático não existe: painel do Supabase > SQL Editor > New query, colar o conteúdo de cada arquivo e Run, na ordem: `..._schema.sql`, `..._seed.sql`, `..._sync_cursor.sql`, `..._avisos_e_ia.sql`, `..._historico_lucas.sql`, `..._rotina_unica.sql`. Todas podem ser rodadas de novo sem duplicar nada.
- O seed grava os dados no primeiro usuário de `auth.users`.

## Bloco C: publicar o backend (uma vez)

1. No GitHub: Settings > Secrets and variables > Actions > New repository secret. Criar:
   - `SUPABASE_ACCESS_TOKEN`: gerar em https://supabase.com/dashboard/account/tokens
   - `ANTHROPIC_API_KEY`: gerar no Console da Anthropic (conta com crédito)
   - `RESEND_API_KEY`: gerar no Resend. A conta do Resend precisa usar o mesmo e-mail do login do app, porque sem domínio próprio o Resend só entrega para o dono da conta.
   - `NTFY_TOPIC`: um nome longo e aleatório, por exemplo `luluco-` seguido de 32 letras e números. Quem souber o nome consegue ler os avisos, então não divulgue.
2. Actions > "Backend Supabase" > Run workflow. Ele aplica as migrações, publica as funções, grava os secrets delas e agenda o cron (a cada 5 min e domingo 10h).
3. No iPhone: instalar o app **ntfy**, tocar em "+" e assinar o mesmo nome usado em `NTFY_TOPIC`.
4. No app: Ajustes > Avisos no iPhone > "Enviar teste" (push e e-mail).

Limites da IA: no máximo 60 chamadas por dia por usuário e saída limitada a 2000 tokens. Modelo em `ANTHROPIC_MODEL` (padrão `claude-haiku-5-5`).

As regras do servidor (`supabase/functions/_shared/rules.ts`) espelham as do app e têm testes com os mesmos casos (`deno test supabase/functions/_shared/`).

## Instalar no Fire

1. Configurações > Segurança > ativar "Apps de fontes desconhecidas".
2. Baixar o APK da página de Releases (o repositório é privado: precisa estar logado no GitHub no navegador Silk) ou copiar por USB.
3. Abrir o arquivo e instalar.
