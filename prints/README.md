# Prints

Capturas de tela do app ao longo das versões, para comparar lado a lado e para ilustrar
os posts do LinkedIn.

Uma pasta por build que mudou a interface de verdade: `build-NNN`, com o número da
execução do workflow "App Android" que gerou aquele APK. Build que só mexeu em lógica,
em sync ou no backend não ganha pasta. Pasta nova não substitui a anterior: o valor
disso é justamente poder ver a diferença entre uma versão e outra.

Dentro de cada pasta ficam só as telas que importam: painel, standby, standby noturno,
e as telas de funcionalidade nova daquela versão. Tela que não mudou desde a versão
anterior não precisa ser copiada de novo.

## Checkpoints de post

| Pasta | Quando | Post |
|---|---|---|
| `build-037` | 17/11/2026 | Primeiro post do LinkedIn |
| `build-075` | 19/11/2026 | Visual v0.3, entre os dois posts |
| `build-094` | 23/11/2026 | Base do segundo post |

Toda vez que o Lucas pedir o prompt de levantamento para o post, o estado atual do app
vira um checkpoint aqui antes de qualquer outra mudança.

## De onde vêm

O teste de fumaça do CI roda no emulador API 22, na resolução do Fire HD 8, contra os
dados reais do Supabase, e grava cada tela no logcat. O build monta os JPEGs e sobe como
artefato `capturas-<número do build>`, que fica 90 dias na página da execução. Para
guardar aqui, basta baixar o artefato e copiar as telas que valem a pena.

Os prints do `build-037` foram recuperados depois: a execução original falhou na subida
do emulador, então o mesmo commit foi rodado de novo em 23/11 pelo `workflow_dispatch`.
A interface é a daquela versão, mas o relógio e os dados são os de hoje.

Para remontar as capturas de um log antigo, quando não houver artefato:

    gh run view <id> --log > run.log
    python3 tools/extrair_prints.py run.log capturas
