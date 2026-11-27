/*
 * Avisos no iPhone (Web Push), na aba Mais.
 *
 * O que chega: o resumo das 23h30 com o que falta do dia, rotina com horario que passou da hora e
 * alerta critico de parametro. Quem manda e o servidor (Edge Function notifier, de 5 em 5 minutos);
 * aqui so se inscreve. No iPhone so funciona com o app aberto pelo icone da Tela de Inicio (iOS
 * 16.4 ou mais novo), e o pedido de permissao precisa sair direto do toque no botao.
 */
'use strict';

const AVISOS_IOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
const AVISOS_INSTALADO = window.navigator.standalone === true || window.matchMedia('(display-mode: standalone)').matches;

function avisosSuportados() {
  return 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

function chaveBytes(b64) {
  const pad = '='.repeat((4 - (b64.length % 4)) % 4);
  const bin = atob((b64 + pad).replace(/-/g, '+').replace(/_/g, '/'));
  return Uint8Array.from(bin, (c) => c.charCodeAt(0));
}

async function avisosInscricao() {
  if (!avisosSuportados()) return null;
  const reg = await navigator.serviceWorker.ready;
  return reg.pushManager.getSubscription();
}

async function avisosLigar() {
  // primeiro o pedido, ainda dentro do toque: no iOS, qualquer await antes faz o Safari recusar
  const perm = await Notification.requestPermission();
  if (perm !== 'granted') throw new Error(perm === 'denied' ? 'Notificações bloqueadas para o app.' : 'Permissão não concedida.');
  const reg = await navigator.serviceWorker.ready;
  const { key } = await funcao('push_key');
  let sub = await reg.pushManager.getSubscription();
  if (!sub) sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: chaveBytes(key) });
  await funcao('push_subscribe', { subscription: sub.toJSON(), aparelho: navigator.userAgent.slice(0, 160) });
}

async function avisosDesligar() {
  const sub = await avisosInscricao();
  if (!sub) return;
  await funcao('push_unsubscribe', { endpoint: sub.endpoint }).catch(() => {});
  await sub.unsubscribe();
}

/** Ao abrir o app: se ja estava inscrito, reenvia a inscricao (a Apple pode trocar o endereco). */
async function avisosRenovar() {
  try {
    if (!avisosSuportados() || Notification.permission !== 'granted') return;
    const sub = await avisosInscricao();
    if (sub) await funcao('push_subscribe', { subscription: sub.toJSON(), aparelho: navigator.userAgent.slice(0, 160) });
  } catch (_) { /* sem rede: tenta na proxima abertura */ }
}

async function avisosResumo() {
  if (!avisosSuportados()) return 'abrir pela Tela de Início';
  if (Notification.permission === 'denied') return 'bloqueados nos Ajustes';
  try { return (await avisosInscricao()) ? 'ligados neste iPhone' : 'desligados'; } catch (_) { return 'desligados'; }
}

function desenharAvisos(caixa) {
  const card = el('div', 'card');
  caixa.appendChild(card);
  const explica = el('div', 'sub');
  explica.textContent = 'Chega aqui: às 23h30, o que ainda falta do dia (ou, com tudo feito, o que vem amanhã); rotina com horário que passou da hora; e alerta crítico de parâmetro.';
  card.appendChild(explica);

  if (!avisosSuportados() || (AVISOS_IOS && !AVISOS_INSTALADO)) {
    card.appendChild(el('div', null, 'Para receber avisos, abra o Luluco Reef pelo ícone da Tela de Início. Se ainda não tem o ícone: no Safari, Compartilhar → Adicionar à Tela de Início. Precisa do iOS 16.4 ou mais novo.'));
    return;
  }
  if (Notification.permission === 'denied') {
    card.appendChild(el('div', null, 'As notificações do app estão bloqueadas. Libere em Ajustes → Notificações → Luluco Reef e volte aqui.'));
    return;
  }

  const estado = el('div', 'titulo', '...');
  card.appendChild(estado);
  const botoes = el('div', 'botoes-avisos');
  card.appendChild(botoes);

  const desenhar = async () => {
    botoes.innerHTML = '';
    const sub = await avisosInscricao().catch(() => null);
    estado.textContent = sub ? 'Avisos ligados neste iPhone.' : 'Avisos desligados neste iPhone.';
    if (!sub) {
      const ligar = el('button', 'primary', 'Ligar avisos');
      ligar.onclick = async () => {
        ocupado(true);
        try { await avisosLigar(); toast('Avisos ligados.'); } catch (e) { toast(`Não ligou: ${e.message}`, true); } finally { ocupado(false); desenhar(); }
      };
      botoes.appendChild(ligar);
      return;
    }
    const teste = el('button', 'primary', 'Enviar teste');
    teste.onclick = async () => {
      ocupado(true);
      try { const r = await funcao('push_test'); toast(r.message || 'Enviado.'); } catch (e) { toast(e.message, true); } finally { ocupado(false); }
    };
    const desligar = el('button', null, 'Desligar');
    desligar.onclick = async () => {
      ocupado(true);
      try { await avisosDesligar(); toast('Avisos desligados.'); } catch (e) { toast(e.message, true); } finally { ocupado(false); desenhar(); }
    };
    botoes.appendChild(teste);
    botoes.appendChild(desligar);
  };
  desenhar();
}
