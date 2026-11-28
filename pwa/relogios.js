/*
 * Alarmes e timers do tablet, pelo iPhone (aba Mais).
 *
 * Quem toca e sempre o tablet, ao lado do aquario. Aqui da para ver, criar, editar e cancelar;
 * o tablet confere a cada 20 segundos o que mudou. Com "avisar no iPhone" ligado, o aviso chega
 * aqui tambem na hora do toque (timer ja vem ligado, alarme vem desligado, como no tablet).
 */
'use strict';

const REL_PRESETS = [5, 10, 15, 20, 30];
const REL_DIAS = ['seg', 'ter', 'qua', 'qui', 'sex', 'sáb', 'dom'];
let relCaixa = null;
let relTique = null;

const hm = (h, m) => `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;

function relRestante(ms) {
  const s = Math.max(0, Math.round(ms / 1000));
  const m = Math.floor(s / 60);
  return m >= 60 ? `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}` : `${m}:${String(s % 60).padStart(2, '0')}`;
}

function relRecorrencia(a) {
  if (a.recorrencia === 'diaria') return 'todo dia';
  if (a.recorrencia === 'dias_da_semana') return (a.dias_semana || []).map((d) => REL_DIAS[d - 1]).join(', ');
  return 'uma vez';
}

async function desenharRelogios(caixa) {
  relCaixa = caixa;
  if (relTique) { clearInterval(relTique); relTique = null; }
  caixa.innerHTML = '';
  const parar = el('button', 'parar-toque', 'Parar o toque no tablet');
  parar.onclick = () => pararToque();
  caixa.appendChild(parar);
  const aviso = el('div', 'sub');
  aviso.textContent = 'Tudo aqui toca no tablet. O tablet confere o que mudou a cada 20 segundos (e a cada 3 enquanto toca).';
  caixa.appendChild(aviso);

  let timers = [];
  let alarmes = [];
  try {
    [timers, alarmes] = await Promise.all([
      buscar('timers', `select=*&deleted_at=is.null&encerrado_em=is.null&termina_em=gt.${new Date().toISOString()}&order=termina_em.asc`),
      buscar('alarms', 'select=*&deleted_at=is.null&order=hora.asc,minuto.asc'),
    ]);
  } catch (e) {
    caixa.appendChild(el('div', 'vazio', `Não deu para carregar: ${e.message}`));
    return;
  }

  // ---- timers
  caixa.appendChild(el('h3', 'sec', 'Timers'));
  const ct = el('div', 'card');
  caixa.appendChild(ct);
  const nome = document.createElement('input');
  nome.type = 'text';
  nome.placeholder = 'Nome (opcional), ex.: religar a bomba';
  ct.appendChild(nome);
  const pre = el('div', 'chips');
  const iniciar = async (min) => {
    ocupado(true);
    try {
      await inserir('timers', { label: nome.value.trim(), termina_em: new Date(Date.now() + min * 60000).toISOString(), iphone: true });
      toast(`Timer de ${min} min no tablet.`);
      await desenharRelogios(caixa);
    } catch (e) { toast(e.message, true); } finally { ocupado(false); }
  };
  REL_PRESETS.forEach((m) => { const b = el('button', 'chip', `${m} min`); b.onclick = () => iniciar(m); pre.appendChild(b); });
  const outro = el('button', 'chip', 'Outro');
  outro.onclick = () => {
    const v = Number(prompt('Quantos minutos?', '45'));
    if (v > 0 && v <= 24 * 60) iniciar(Math.round(v));
  };
  pre.appendChild(outro);
  ct.appendChild(pre);
  ct.appendChild(el('div', 'sub', 'Quando acabar, toca no tablet e aqui no iPhone ao mesmo tempo.'));

  const lt = el('div', 'lista');
  caixa.appendChild(lt);
  if (!timers.length) lt.appendChild(el('div', 'vazio', 'Nenhum timer contando.'));
  const conta = [];
  timers.forEach((t) => {
    const it = el('div', 'item');
    const corpo = el('div', 'corpo');
    corpo.appendChild(el('div', 'titulo', t.label || 'Timer'));
    const sub = el('div', 'sub', '');
    corpo.appendChild(sub);
    conta.push([sub, new Date(t.termina_em).getTime()]);
    it.appendChild(corpo);
    const x = el('button', 'mini', 'Cancelar');
    x.onclick = async () => {
      ocupado(true);
      try { await atualizar('timers', t.id, { encerrado_em: new Date().toISOString() }); toast('Timer cancelado.'); await desenharRelogios(caixa); }
      catch (e) { toast(e.message, true); } finally { ocupado(false); }
    };
    it.appendChild(x);
    lt.appendChild(it);
  });
  const pinta = () => {
    const agora = Date.now();
    conta.forEach(([sub, fim]) => {
      sub.textContent = fim > agora ? `faltam ${relRestante(fim - agora)} · acaba às ${hm(new Date(fim).getHours(), new Date(fim).getMinutes())}` : 'tocando no tablet';
    });
    if (!document.body.contains(lt) && relTique) { clearInterval(relTique); relTique = null; }
  };
  pinta();
  if (conta.length) relTique = setInterval(pinta, 1000);

  // ---- alarmes
  const cab = el('div', 'linha-sec');
  cab.appendChild(el('h3', 'sec', 'Alarmes'));
  const novo = el('button', 'mini', 'Novo alarme');
  novo.onclick = () => folhaAlarme(null);
  cab.appendChild(novo);
  caixa.appendChild(cab);
  const la = el('div', 'lista');
  caixa.appendChild(la);
  if (!alarmes.length) la.appendChild(el('div', 'vazio', 'Nenhum alarme.'));
  alarmes.forEach((a) => {
    const it = el('div', 'item');
    it.appendChild(el('div', 'hora', hm(a.hora, a.minuto)));
    const corpo = el('div', 'corpo');
    corpo.appendChild(el('div', `titulo${a.ativo ? '' : ' feito'}`, a.titulo || 'Alarme'));
    corpo.appendChild(el('div', 'sub', `${relRecorrencia(a)}${a.alto ? '' : ' · som normal'}${a.avisar_iphone ? ' · avisa no iPhone' : ''}${a.ativo ? '' : ' · desligado'}`));
    corpo.onclick = () => folhaAlarme(a);
    it.appendChild(corpo);
    const liga = el('button', 'mini', a.ativo ? 'Desligar' : 'Ligar');
    liga.onclick = async () => {
      ocupado(true);
      try {
        const campos = { ativo: !a.ativo };
        // alarme de uma vez so religado vale para o proximo horario
        if (!a.ativo && a.recorrencia === 'unica') campos.dia_unico = new Date().toISOString();
        await atualizar('alarms', a.id, campos);
        await desenharRelogios(caixa);
      } catch (e) { toast(e.message, true); } finally { ocupado(false); }
    };
    it.appendChild(liga);
    la.appendChild(it);
  });
}

function folhaAlarme(a) {
  abrirFolha(a ? 'Editar alarme' : 'Novo alarme', (c) => {
    let rec = a ? a.recorrencia : 'unica';
    let dias = a && a.dias_semana && a.dias_semana.length ? [...a.dias_semana] : [3];
    let alto = a ? a.alto : true;
    let ip = a ? a.avisar_iphone : false;

    const titulo = document.createElement('input');
    titulo.type = 'text';
    titulo.placeholder = 'Nome, ex.: comida dos peixes';
    titulo.value = a ? a.titulo : '';
    const hora = document.createElement('input');
    hora.type = 'time';
    hora.value = a ? hm(a.hora, a.minuto) : '07:00';
    const lh = el('label');
    lh.appendChild(el('span', null, 'Horário'));
    lh.appendChild(hora);

    const lr = el('div', 'chips');
    const ld = el('div', 'chips');
    const pintaDias = () => {
      ld.innerHTML = '';
      if (rec !== 'dias_da_semana') return;
      REL_DIAS.forEach((n, i) => {
        const d = i + 1;
        const b = el('button', `chip${dias.includes(d) ? ' ativo' : ''}`, n);
        b.onclick = () => { dias = dias.includes(d) ? dias.filter((x) => x !== d) : [...dias, d].sort(); pintaDias(); };
        ld.appendChild(b);
      });
    };
    const pintaRec = () => {
      chips(lr, [['unica', 'Uma vez'], ['diaria', 'Todo dia'], ['dias_da_semana', 'Dias da semana']], rec, (v) => { rec = v; pintaRec(); pintaDias(); });
    };
    pintaRec();
    pintaDias();

    const ls = el('div', 'chips');
    const pintaSom = () => chips(ls, [['alto', 'Som alto'], ['normal', 'Som normal']], alto ? 'alto' : 'normal', (v) => { alto = v === 'alto'; pintaSom(); });
    pintaSom();

    const li = el('label', 'linha-chave');
    const ci = document.createElement('input');
    ci.type = 'checkbox';
    ci.checked = ip;
    ci.onchange = () => { ip = ci.checked; };
    li.appendChild(ci);
    li.appendChild(el('span', null, 'Avisar também no iPhone'));

    [titulo, lh, lr, ld, ls, li].forEach((x) => c.appendChild(x));

    const salvar = el('button', 'primary', 'Salvar');
    salvar.onclick = async () => {
      if (rec === 'dias_da_semana' && !dias.length) { toast('Escolha pelo menos um dia.', true); return; }
      const [hh, mm] = (hora.value || '07:00').split(':').map(Number);
      const linha = {
        titulo: titulo.value.trim(), hora: hh || 0, minuto: mm || 0, recorrencia: rec,
        dias_semana: rec === 'dias_da_semana' ? dias : null, alto, avisar_iphone: ip, ativo: true,
        dia_unico: rec === 'unica' ? new Date().toISOString() : null,
      };
      ocupado(true);
      try {
        if (a) await atualizar('alarms', a.id, linha); else await inserir('alarms', linha);
        fecharFolha();
        toast('Alarme salvo. O tablet pega em até 20 s.');
        if (relCaixa) await desenharRelogios(relCaixa);
      } catch (e) { toast(e.message, true); } finally { ocupado(false); }
    };
    c.appendChild(salvar);
    if (a) {
      const apaga = el('button', null, 'Apagar');
      apaga.onclick = async () => {
        if (!confirm('Apagar este alarme?')) return;
        ocupado(true);
        try { await apagar('alarms', a.id); fecharFolha(); if (relCaixa) await desenharRelogios(relCaixa); }
        catch (e) { toast(e.message, true); } finally { ocupado(false); }
      };
      c.appendChild(apaga);
    }
  });
}

/** Pede ao tablet para parar o que estiver tocando (ele confere a cada 3 s enquanto toca). */
async function pararToque() {
  relSilenciar();
  if (!dados.tanque) { toast('Configuração do aquário não carregada.', true); return; }
  ocupado(true);
  try {
    await atualizar('tank_config', dados.tanque.id, { parar_toque_em: new Date().toISOString() });
    toast('Pedido enviado. O tablet para em alguns segundos.');
  } catch (e) { toast(e.message, true); } finally { ocupado(false); }
}

// ---- toque aqui no iPhone com o app aberto (com o app fechado, quem toca e a notificacao)
let relSom = null;
function relTocar(titulo, texto) {
  let tela = document.getElementById('tocando');
  if (!tela) {
    tela = el('div', null);
    tela.id = 'tocando';
    document.body.appendChild(tela);
  }
  tela.innerHTML = '';
  tela.appendChild(el('div', 'tocando-titulo', titulo.replace(/^Luluco Reef: /, '')));
  tela.appendChild(el('div', 'tocando-texto', texto));
  const b = el('button', 'primary', 'Parar aqui e no tablet');
  b.onclick = () => pararToque();
  tela.appendChild(b);
  const so = el('button', null, 'Parar só aqui');
  so.onclick = () => relSilenciar();
  tela.appendChild(so);
  tela.classList.remove('hidden');
  try {
    const Ctx = window.AudioContext || window.webkitAudioContext;
    if (!relSom && Ctx) {
      const ctx = new Ctx();
      const bipe = () => {
        [0, 0.25, 0.5].forEach((t) => {
          const o = ctx.createOscillator(); const g = ctx.createGain();
          o.frequency.value = 880; g.gain.value = 0.25;
          o.connect(g); g.connect(ctx.destination);
          o.start(ctx.currentTime + t); o.stop(ctx.currentTime + t + 0.15);
        });
      };
      if (ctx.resume) ctx.resume();
      bipe();
      relSom = { ctx, timer: setInterval(bipe, 1500) };
    }
  } catch (_) { /* sem som: fica a tela */ }
  if (navigator.vibrate) navigator.vibrate([400, 200, 400]);
}

function relSilenciar() {
  if (relSom) { clearInterval(relSom.timer); try { relSom.ctx.close(); } catch (_) { /* ok */ } relSom = null; }
  const tela = document.getElementById('tocando');
  if (tela) tela.classList.add('hidden');
}

function abrirRelogios() {
  abrirAba('mais');
  mAba = 'relogios';
  desenharMais();
}

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.addEventListener('message', (e) => {
    const d = e.data || {};
    if (d.tocar) relTocar(d.tocar, d.texto || '');
    if (d.abrir === 'relogios') abrirRelogios();
  });
}
