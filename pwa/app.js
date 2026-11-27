/*
 * Luluco Reef no iPhone.
 *
 * Fala direto com a API REST do Supabase, com fetch e sem biblioteca: o RLS do banco ja garante que
 * cada usuario so ve as proprias linhas, e sem dependencia externa o app abre rapido e continua
 * funcionando offline (ver sw.js). O que ele faz e o que serve para usar fora de casa: ver os
 * parametros, marcar rotina feita e registrar teste, TPA, dose e nota. Grafico, calculadora,
 * quarentena, financas e relatorio ficam no tablet.
 */
'use strict';

const PARAMS = ['kh', 'ca', 'mg', 'salinidade', 'no3', 'po4'];
const ROTULOS = { kh: 'KH', ca: 'Cálcio', mg: 'Magnésio', salinidade: 'Salinidade', no3: 'NO3', po4: 'PO4' };
const UNIDADES = { kh: 'dKH', ca: 'ppm', mg: 'ppm', salinidade: '', no3: 'ppm', po4: 'ppm' };
const CASAS = { kh: 2, ca: 0, mg: 0, salinidade: 3, no3: 1, po4: 2 };
const DIA_MS = 86400000;

// ---------------------------------------------------------------- sessao

const SESSAO = 'luluco_sessao';
let sessao = null;

function lerSessao() {
  try { return JSON.parse(localStorage.getItem(SESSAO) || 'null'); } catch (_) { return null; }
}
function gravarSessao(s) {
  sessao = s;
  if (s) localStorage.setItem(SESSAO, JSON.stringify(s));
  else localStorage.removeItem(SESSAO);
}

async function entrar(email, senha) {
  const r = await fetch(`${CONFIG.url}/auth/v1/token?grant_type=password`, {
    method: 'POST',
    headers: { apikey: CONFIG.key, 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password: senha }),
  });
  const j = await r.json();
  if (!r.ok) throw new Error(j.error_description || j.msg || j.message || 'Não deu para entrar.');
  gravarSessao({ access: j.access_token, refresh: j.refresh_token, expira: Date.now() + (j.expires_in || 3600) * 1000 });
}

/** Troca o refresh_token por um access_token novo. Se o refresh morreu, cai para a tela de login. */
async function renovar() {
  if (!sessao || !sessao.refresh) return false;
  const r = await fetch(`${CONFIG.url}/auth/v1/token?grant_type=refresh_token`, {
    method: 'POST',
    headers: { apikey: CONFIG.key, 'Content-Type': 'application/json' },
    body: JSON.stringify({ refresh_token: sessao.refresh }),
  });
  if (!r.ok) { gravarSessao(null); return false; }
  const j = await r.json();
  gravarSessao({ access: j.access_token, refresh: j.refresh_token, expira: Date.now() + (j.expires_in || 3600) * 1000 });
  return true;
}

// ---------------------------------------------------------------- api

/** Chamada a Edge Function notifier com o token do usuario (avisos no iPhone). */
async function funcao(mode, extra = {}, repetir = true) {
  if (sessao && sessao.expira && Date.now() > sessao.expira - 60000) await renovar();
  if (!sessao) throw new Error('sem sessão');
  const r = await fetch(`${CONFIG.url}/functions/v1/notifier`, {
    method: 'POST',
    headers: { apikey: CONFIG.key, Authorization: `Bearer ${sessao.access}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ mode, ...extra }),
  });
  if (r.status === 401 && repetir && await renovar()) return funcao(mode, extra, false);
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || `erro ${r.status}`);
  return j;
}

/** Chamada ao REST com o token. Em 401 renova uma vez e repete; nunca repete duas. */
async function api(caminho, opcoes = {}, repetir = true) {
  if (sessao && sessao.expira && Date.now() > sessao.expira - 60000) await renovar();
  if (!sessao) throw new Error('sem sessão');
  const r = await fetch(`${CONFIG.url}/rest/v1/${caminho}`, {
    ...opcoes,
    headers: {
      apikey: CONFIG.key,
      Authorization: `Bearer ${sessao.access}`,
      'Content-Type': 'application/json',
      Prefer: 'return=representation',
      ...(opcoes.headers || {}),
    },
  });
  if (r.status === 401 && repetir) {
    if (await renovar()) return api(caminho, opcoes, false);
    throw new Error('sessão expirada');
  }
  if (!r.ok) throw new Error(`${r.status}: ${(await r.text()).slice(0, 140)}`);
  return r.status === 204 ? null : r.json();
}

const buscar = (tabela, query) => api(`${tabela}?${query}`);
const inserir = (tabela, linha) => api(tabela, { method: 'POST', body: JSON.stringify(linha) });
/** PATCH por id. `updated_at` vai junto porque a sincronizacao do app resolve conflito por ele. */
const atualizar = (tabela, id, campos) =>
  api(`${tabela}?id=eq.${id}`, { method: 'PATCH', body: JSON.stringify({ ...campos, updated_at: new Date().toISOString() }) });
const apagar = (tabela, id) => atualizar(tabela, id, { deleted_at: new Date().toISOString() });

// ---------------------------------------------------------------- regras (espelho do app do tablet)

// a regra de zona mora em ui.js (zonaDe); aqui so o apelido antigo, para nao reescrever a tela toda
const statusDe = (valor, r) => zonaDe(valor, r);

function formatar(p, v) {
  if (v == null) return '-';
  return Number(v).toFixed(CASAS[p] ?? 2).replace('.', ',');
}

/** Dose da bula recalculada para o volume do aquario (igual a doseFor do app). */
function doseDe(produto, litros) {
  if (!produto || produto.dose_quantidade == null) return null;
  const base = produto.dose_base_litros;
  return base && base > 0 ? (produto.dose_quantidade * litros) / base : produto.dose_quantidade;
}

const diaDe = (ms) => { const d = new Date(ms); d.setHours(0, 0, 0, 0); return d; };
// O dia da AGENDA vira as 6h, e nao a meia-noite: de madrugada a lista ainda e a do dia anterior,
// porque as tarefas da noite muitas vezes sao feitas depois da meia-noite. Igual ao tablet.
const VIRADA_H = 6;
const diaDaAgenda = (ms) => diaDe(ms - VIRADA_H * 3600 * 1000);
const iso = (d) => d.toISOString();

/** 1 = segunda ... 7 = domingo (o getDay do JS e 0 = domingo). */
const isoDia = (d) => ((d.getDay() + 6) % 7) + 1;

function vencimento(rotina, dia) {
  if (!rotina.ativa || rotina.deleted_at) return null;
  const [h, m] = (rotina.horario || '20:00').split(':').map(Number);
  const at = (d) => { const x = new Date(d); x.setHours(h || 0, m || 0, 0, 0); return x.getTime(); };
  if (rotina.recorrencia === 'diaria') return at(dia);
  if (rotina.recorrencia === 'dias_da_semana') return (rotina.dias_semana || []).includes(isoDia(dia)) ? at(dia) : null;
  // intervalo e uma vez so: usam proxima_em como ancora e seguem cobrando depois dela
  if (!rotina.proxima_em) return null;
  const ancora = new Date(rotina.proxima_em);
  return diaDe(ancora.getTime()) <= diaDe(dia.getTime()) ? ancora.getTime() : null;
}

/**
 * Registro que vale para a ocorrencia (mesma regra do tablet, domain/Routines.kt registroDe): o feito
 * ou pulado desde o dia seguinte a vez anterior da rotina ate o dia dela, nao so o da hora exata.
 * Mudar horario ou dia da rotina nao faz voltar o que ja foi feito. Sem resolvido, o do proprio dia.
 */
function registroDe(r, due, logsDaRotina) {
  if (!logsDaRotina || !logsDaRotina.length) return null;
  const dia = diaDe(due).getTime();
  let desde = dia - DIA_MS;
  if (r.recorrencia === 'dias_da_semana') {
    for (let i = 1; i <= 7; i++) {
      const d = new Date(dia); d.setDate(d.getDate() - i);
      if (vencimento(r, d) != null) { desde = diaDe(d.getTime()).getTime(); break; }
    }
  }
  let resolvido = null;
  let doDia = null;
  logsDaRotina.forEach((l) => {
    const d = diaDe(new Date(l.previsto_para).getTime()).getTime();
    if (d > dia || d <= desde) return;
    const novo = (x) => !x || new Date(l.updated_at) > new Date(x.updated_at);
    if ((l.status === 'feito' || l.status === 'pulado') && novo(resolvido)) resolvido = l;
    if (d === dia && novo(doDia)) doDia = l;
  });
  return resolvido || doDia;
}

function logsPorRotina(logs) {
  const m = {};
  logs.filter((l) => !l.deleted_at).forEach((l) => { (m[l.routine_id] = m[l.routine_id] || []).push(l); });
  return m;
}

/**
 * Quantas rotinas venceram nos ultimos `dias` dias e quantas foram feitas. Mesma conta do
 * compliance() do tablet, linha por linha, para a nota de saude dar o mesmo numero nos dois:
 * cada (rotina, horario) conta uma vez so, nada do futuro entra, e atividade de uma vez so que ja
 * foi resolvida conta apenas no dia em que foi resolvida.
 */
function cumprimento(rotinas, logs, agora, dias = 7) {
  const porRotina = logsPorRotina(logs);
  let feito = 0;
  let esperado = 0;
  const visto = new Set();
  for (let i = 0; i < dias; i++) {
    const dia = diaDe(agora);
    dia.setDate(dia.getDate() - i);
    rotinas.forEach((r) => {
      const due = vencimento(r, dia);
      if (due == null || due > agora) return;
      const k = `${r.id}|${due}`;
      const log = registroDe(r, due, porRotina[r.id]);
      const aberto = !log || log.status === 'pendente' || log.status === 'adiado';
      if (r.recorrencia === 'unica' && !aberto) {
        const resolvido = new Date(log.feito_em || log.updated_at).getTime();
        if (diaDe(resolvido).getTime() !== dia.getTime()) return;
      }
      if (visto.has(k)) return;
      visto.add(k);
      esperado++;
      if (log && log.status === 'feito') feito++;
    });
  }
  return { feito, esperado };
}

/**
 * Ocorrencias de hoje, arrastando as pendencias dos dias anteriores.
 *
 * Mesma regra do app do tablet: rotina por dias da semana que nao foi feita nem pulada continua
 * cobrando nos dias seguintes (uma por rotina, a mais recente) e so quando nao vence hoje tambem.
 * Sem isso a TPA de sexta desaparecia no sabado.
 */
function ocorrenciasDeHoje(rotinas, logs, agora) {
  const hoje = diaDaAgenda(agora);
  const porRotina = logsPorRotina(logs);
  const achar = (r, due) => registroDe(r, due, porRotina[r.id]);
  const aberto = (log) => !log || log.status === 'adiado';

  const lista = [];
  const noDia = new Set();
  rotinas.forEach((r) => {
    const due = vencimento(r, hoje);
    if (due == null) return;
    noDia.add(r.id);
    lista.push({ rotina: r, due, log: achar(r, due), arrastada: false });
  });
  rotinas.forEach((r) => {
    if (r.recorrencia !== 'dias_da_semana' || noDia.has(r.id) || !r.ativa || r.deleted_at) return;
    // dosagem nao arrasta: dose tem janela, e dobrar suplemento depois faz mal ao aquario
    if (r.tipo === 'dosagem') return;
    for (let i = 1; i <= 30; i++) {
      const d = new Date(hoje); d.setDate(d.getDate() - i);
      const due = vencimento(r, d);
      if (due == null) continue;
      // para na mais recente: se ela foi resolvida, as anteriores sao historico
      const log = achar(r, due);
      if (aberto(log)) lista.push({ rotina: r, due, log, arrastada: true });
      return;
    }
  });
  return lista.sort((a, b) => a.due - b.due);
}

/**
 * Ocorrencias de um dia qualquer, para navegar pelas setas do Painel.
 *
 * Hoje continua usando a regra de cima, com as atrasadas arrastadas. Os outros dias mostram so
 * o que venceu NAQUELE dia, sem arrastar nada: no passado a pergunta e "o que eu fiz e o que
 * deixei de fazer em tal dia", e repetir a mesma TPA atrasada em todos os dias seguintes
 * esconderia a resposta; no futuro nada esta atrasado ainda.
 *
 * Rotina por intervalo ou de uma vez so tem uma data unica (proxima_em). Fora de hoje ela so
 * aparece nesse dia exato; senao, uma troca de carvao marcada para quinta apareceria em todos os
 * dias depois de quinta. No passado entram tambem os registros que caem no dia, para que uma
 * rotina por intervalo ja feita continue aparecendo no dia em que foi feita.
 */
function ocorrenciasDoDia(rotinas, logs, dia, agora) {
  if (diaDe(dia.getTime()).getTime() === diaDaAgenda(agora).getTime()) return ocorrenciasDeHoje(rotinas, logs, agora);
  const alvo = diaDe(dia.getTime());
  const fimDoDia = alvo.getTime() + DIA_MS;
  const porRotina = logsPorRotina(logs);
  const porChave = {};
  logs.filter((l) => !l.deleted_at).forEach((l) => {
    const k = `${l.routine_id}|${new Date(l.previsto_para).getTime()}`;
    if (!porChave[k] || new Date(l.updated_at) > new Date(porChave[k].updated_at)) porChave[k] = l;
  });
  const lista = [];
  const visto = new Set();
  rotinas.forEach((r) => {
    if (!r.ativa || r.deleted_at) return;
    // rotina criada depois desse dia nao tinha como ter sido feita nele
    if (r.created_at && new Date(r.created_at).getTime() >= fimDoDia) return;
    let due = null;
    if (r.recorrencia === 'diaria' || r.recorrencia === 'dias_da_semana') due = vencimento(r, alvo);
    else if (r.proxima_em) {
      const t = new Date(r.proxima_em).getTime();
      if (t >= alvo.getTime() && t < fimDoDia) due = t;
    }
    if (due == null) return;
    const log = registroDe(r, due, porRotina[r.id]);
    visto.add(`${r.id}|${due}`);
    if (log) visto.add(`${r.id}|${new Date(log.previsto_para).getTime()}`);
    lista.push({ rotina: r, due, log, arrastada: false });
  });
  const porId = {};
  rotinas.forEach((r) => { porId[r.id] = r; });
  Object.values(porChave).forEach((l) => {
    const due = new Date(l.previsto_para).getTime();
    if (due < alvo.getTime() || due >= fimDoDia || visto.has(`${l.routine_id}|${due}`)) return;
    const r = porId[l.routine_id];
    if (r) lista.push({ rotina: r, due, log: l, arrastada: false });
  });
  return lista.sort((a, b) => a.due - b.due);
}

const estado = (o) => (o.log ? o.log.status : 'pendente');

// Dia mostrado no Painel, em dias a partir de hoje (0 = hoje, -1 = ontem, 1 = amanha).
// O historico carregado cobre 45 dias, entao a navegacao para em 30 para tras.
let deslocDia = 0;
const DIAS_ATRAS = 30;
const DIAS_FRENTE = 30;

// ---------------------------------------------------------------- dados

let dados = {
  ranges: {}, testes: [], rotinas: [], logs: [], produtos: [], tanque: null, habitantes: [], equipamentos: [],
  tpas: [], doses: [], notas: [], financas: [], compras: [], casos: [],
};

async function carregar() {
  const desde = iso(new Date(Date.now() - 120 * DIA_MS));
  const [tanque, ranges, testes, rotinas, logs, produtos, tpas, doses, notas, financas, compras, casos, habitantes, equipamentos] = await Promise.all([
    buscar('tank_config', 'deleted_at=is.null&limit=1'),
    buscar('param_ranges', 'deleted_at=is.null'),
    buscar('water_tests', `deleted_at=is.null&medido_em=gte.${desde}&order=medido_em.desc&limit=400`),
    buscar('routines', 'deleted_at=is.null&ativa=is.true'),
    buscar('routine_logs', `deleted_at=is.null&previsto_para=gte.${iso(new Date(Date.now() - 45 * DIA_MS))}&limit=600`),
    buscar('products', 'deleted_at=is.null&ativo=is.true&order=nome'),
    buscar('water_changes', `deleted_at=is.null&order=feita_em.desc&limit=20`),
    buscar('dosings', `deleted_at=is.null&order=dosado_em.desc&limit=40`),
    buscar('events', `deleted_at=is.null&order=ocorrido_em.desc&limit=200`),
    buscar('finance_entries', 'deleted_at=is.null&order=lancado_em.desc&limit=500'),
    buscar('shopping_items', 'deleted_at=is.null&order=created_at.desc&limit=200'),
    buscar('health_cases', 'deleted_at=is.null&order=aberto_em.desc&limit=50'),
    buscar('livestock', 'deleted_at=is.null&order=nome_popular&limit=500'),
    buscar('equipment', 'deleted_at=is.null&order=tipo&limit=200'),
  ]);
  dados.tanque = (tanque || [])[0] || null;
  dados.ranges = {};
  (ranges || []).forEach((r) => { dados.ranges[r.parametro] = r; });
  dados.testes = testes || [];
  dados.rotinas = rotinas || [];
  dados.logs = logs || [];
  dados.produtos = produtos || [];
  dados.tpas = tpas || [];
  dados.doses = doses || [];
  dados.notas = notas || [];
  dados.financas = financas || [];
  dados.compras = compras || [];
  dados.casos = casos || [];
  dados.habitantes = habitantes || [];
  dados.equipamentos = equipamentos || [];
}

const volume = () => (dados.tanque && dados.tanque.volume_dosagem_l) || 100;

/** Ultima leitura confiavel de um parametro, e a anterior, para a variacao. */
function ultimas(p) {
  const l = dados.testes
    .filter((t) => t.parametro === p && !t.baixa_confianca)
    .sort((a, b) => new Date(b.medido_em) - new Date(a.medido_em));
  return [l[0] || null, l[1] || null];
}

// ---------------------------------------------------------------- tela

const $ = (s) => document.querySelector(s);
const el = (tag, cls, texto) => { const e = document.createElement(tag); if (cls) e.className = cls; if (texto != null) e.textContent = texto; return e; };

/** Linha de chips de escolha unica. Devolve o container, ja preenchido. */
function chips(alvo, opcoes, selecionado, aoEscolher) {
  alvo.innerHTML = '';
  opcoes.forEach(([valor, rotulo]) => {
    const b = el('button', `chip${valor === selecionado ? ' ativo' : ''}`, rotulo);
    b.onclick = () => aoEscolher(valor);
    alvo.appendChild(b);
  });
  return alvo;
}

/**
 * Folha que sobe de baixo, para formularios. No celular isso funciona melhor que uma tela nova:
 * o fundo continua visivel, e fechar e um toque fora ou no X.
 */
function abrirFolha(titulo, construir) {
  $('#folha-titulo').textContent = titulo;
  const c = $('#folha-conteudo');
  c.innerHTML = '';
  construir(c);
  $('#folha').classList.remove('hidden');
}
function fecharFolha() { $('#folha').classList.add('hidden'); }

function toast(msg, erro) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = erro ? 'erro-toast' : '';
  setTimeout(() => { t.className = 'hidden'; }, erro ? 5000 : 2200);
}
const ocupado = (on) => { $('#carregando').classList.toggle('hidden', !on); };

function hhmm(ms) {
  const d = new Date(ms);
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}
function ddmm(ms) {
  const d = new Date(ms);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}`;
}
function haQuanto(ms) {
  const d = Date.now() - new Date(ms).getTime();
  if (d < 3600000) return 'agora';
  if (d < DIA_MS) return `${Math.floor(d / 3600000)} h`;
  if (d < 60 * DIA_MS) return `${Math.floor(d / DIA_MS)} d`;
  return ddmm(ms);
}

/** Cabecalho do Painel: anel da nota, saudacao e o que mais pesa contra a nota agora. */
function desenharCabecalho() {
  const caixa = $('#saude');
  caixa.innerHTML = '';
  const { nota, itens, falta, critico } = notaDeSaude();
  const card = el('div', 'card-saude');
  card.appendChild(anelSaude(nota, null, critico));
  const lado = el('div', 'saude-lado');
  const h = new Date().getHours();
  lado.appendChild(el('div', 'saude-ola', h < 12 ? 'Bom dia, Lucas' : h < 18 ? 'Boa tarde, Lucas' : 'Boa noite, Lucas'));
  const piores = itens.slice().sort((a, b) => a.pts - b.pts).slice(0, 2);
  if (falta.length) lado.appendChild(el('div', 'saude-sub', `Sem nota enquanto faltar: ${falta.join(', ')}.`));
  else if (!piores.length) lado.appendChild(el('div', 'saude-sub', 'Nada descontando a nota agora.'));
  else piores.forEach((i) => {
    const l = el('div', 'saude-linha');
    const pt = el('span', 'ponto');
    pt.style.background = i.pts <= -12 ? 'var(--critico)' : 'var(--aviso)';
    l.appendChild(pt);
    l.appendChild(el('span', null, `${i.txt} (${i.pts})`));
    lado.appendChild(l);
  });
  card.appendChild(lado);
  caixa.appendChild(card);
}

function desenharParams() {
  const caixa = $('#params');
  caixa.innerHTML = '';
  const mostrar = PARAMS.filter((p) => dados.ranges[p] || ultimas(p)[0]);
  if (!mostrar.length) { caixa.appendChild(el('div', 'vazio', 'Nenhuma medição ainda.')); return; }
  mostrar.forEach((p) => {
    const [ult, ant] = ultimas(p);
    const r = dados.ranges[p];
    const st = statusDe(ult ? Number(ult.valor) : null, r);
    const c = el('div', 'param');

    const topo = el('div', 'topo');
    topo.appendChild(el('span', 'eyebrow', ROTULOS[p]));
    topo.appendChild(el('span', 'espaco'));
    // a zona vira pilula tingida, no lugar do pontinho que so quem sabia entendia
    topo.appendChild(pilula(ROTULO_ZONA[st], ZONA[st]));
    c.appendChild(topo);

    const v = el('div', 'valor');
    v.textContent = formatar(p, ult ? Number(ult.valor) : null);
    if (UNIDADES[p]) { const u = el('small'); u.textContent = ` ${UNIDADES[p]}`; v.appendChild(u); }
    c.appendChild(v);

    const pe = el('div', 'pe');
    pe.appendChild(el('span', null, ult ? haQuanto(ult.medido_em) : 'sem medição'));
    if (ult && ant) {
      const d = Number(ult.valor) - Number(ant.valor);
      pe.appendChild(pilulaDelta(formatar(p, Math.abs(d)), d));
    }
    c.appendChild(pe);

    // minigrafico: o detalhe que mostra sem ler numero que o parametro andou fora da faixa
    const serie = dados.testes
      .filter((t) => t.parametro === p)
      .map((t) => ({ em: new Date(t.medido_em).getTime(), v: Number(t.valor), duvida: !!t.baixa_confianca }))
      .sort((a, b) => a.em - b.em)
      .slice(-12);
    if (serie.length >= 2) c.appendChild(minigrafico(serie, r));

    if (r) {
      const pad = (Number(r.alerta_max) - Number(r.alerta_min)) * 0.25;
      const lo = Number(r.alerta_min) - pad;
      const hi = Number(r.alerta_max) + pad;
      const fr = (x) => Math.max(0, Math.min(1, (x - lo) / (hi - lo))) * 100;
      const faixa = el('div', 'faixa');
      const za = el('div', 'aviso-zona');
      za.style.left = `${fr(Number(r.alerta_min))}%`;
      za.style.width = `${fr(Number(r.alerta_max)) - fr(Number(r.alerta_min))}%`;
      faixa.appendChild(za);
      const zi = el('div', 'ideal-zona');
      zi.style.left = `${fr(Number(r.ideal_min))}%`;
      zi.style.width = `${fr(Number(r.ideal_max)) - fr(Number(r.ideal_min))}%`;
      faixa.appendChild(zi);
      if (ult) {
        const m = el('div', 'marca');
        m.style.left = `${fr(Number(ult.valor))}%`;
        m.style.background = ZONA[st];
        faixa.appendChild(m);
      }
      c.appendChild(faixa);
    }
    caixa.appendChild(c);
  });
}

function rotuloDoDia(desloc, dia) {
  if (desloc === 0) {
    // de madrugada, "Hoje" com as tarefas do dia anterior confundiria: o relogio ja virou
    if (diaDaAgenda(Date.now()).getTime() !== diaDe(Date.now()).getTime()) {
      return 'Ainda ' + dia.toLocaleDateString('pt-BR', { weekday: 'long' }).split('-')[0];
    }
    return 'Hoje';
  }
  if (desloc === -1) return 'Ontem';
  if (desloc === 1) return 'Amanhã';
  if (desloc === -2) return 'Anteontem';
  return dia.toLocaleDateString('pt-BR', { weekday: 'short', day: 'numeric', month: 'short' }).replace(/\./g, '');
}

function desenharHoje() {
  const caixa = $('#hoje');
  caixa.innerHTML = '';
  const agora = Date.now();
  const dia = diaDaAgenda(agora); dia.setDate(dia.getDate() + deslocDia);
  const passado = deslocDia < 0;
  const futuro = deslocDia > 0;

  const rot = $('#dia-rotulo');
  rot.textContent = rotuloDoDia(deslocDia, dia);
  rot.classList.toggle('fora', deslocDia !== 0);
  $('#dia-ant').disabled = deslocDia <= -DIAS_ATRAS;
  $('#dia-prox').disabled = deslocDia >= DIAS_FRENTE;

  const lista = ocorrenciasDoDia(dados.rotinas, dados.logs, dia, agora);
  if (!lista.length) {
    caixa.appendChild(el('div', 'vazio', passado ? 'Nada estava marcado para esse dia.' : futuro ? 'Nada marcado para esse dia.' : 'Nada para hoje.'));
    return;
  }
  // No passado, um resumo de uma linha em cima: e a pergunta que trouxe a pessoa ate aqui.
  if (passado) {
    const feitos = lista.filter((o) => estado(o) === 'feito').length;
    const faltou = lista.filter((o) => estado(o) === 'pendente' || estado(o) === 'adiado').length;
    caixa.appendChild(el('div', 'sub resumo-dia',
      faltou ? `${feitos} de ${lista.length} feitas · ${faltou} ficaram sem fazer` : `Tudo feito: ${feitos} de ${lista.length}`));
  }
  const porId = {};
  dados.produtos.forEach((p) => { porId[p.id] = p; });

  lista.forEach((o) => {
    const st = estado(o);
    const resolvido = st === 'feito' || st === 'pulado';
    // sem horario ("faco quando der") so atrasa quando o dia vira, a meia-noite
    const semHora = o.rotina.com_horario === false;
    const atrasada = !resolvido && agora >= (semHora ? diaDe(o.due).getTime() + DIA_MS : o.due);
    const item = el('div', 'item');
    const [ic, cor] = areaDaRotina(o.rotina.tipo);
    item.appendChild(selo(ic, resolvido ? '#5B6573' : cor));

    // Numa tela de 390 px nao cabe icone, coluna de hora, titulo, selo e botao: o titulo quebrava
    // em duas linhas. A hora e o estado descem para a linha de baixo, que ja existe.
    const corpo = el('div', 'corpo');
    corpo.appendChild(el('div', `titulo${resolvido ? ' feito' : ''}`, o.rotina.titulo));
    const partes = semHora ? [] : [hhmm(o.due)];
    if (o.rotina.tipo === 'tpa' && dados.tpas && dados.tpas.length) {
      const d = Math.round((diaDe(agora).getTime() - diaDe(new Date(dados.tpas[0].feita_em).getTime()).getTime()) / DIA_MS);
      partes.push(`última TPA ${d === 0 ? 'hoje' : d === 1 ? 'ontem' : `há ${d} dias`}`);
    }
    const prod = o.rotina.product_id ? porId[o.rotina.product_id] : null;
    if (o.rotina.tipo === 'dosagem' && prod) {
      const q = doseDe(prod, volume());
      if (q != null) partes.push(`${(Math.round(q * 100) / 100).toString().replace('.', ',')} ${prod.dose_unidade || ''}`.trim());
    } else if (o.rotina.instrucao) partes.push(o.rotina.instrucao);
    corpo.appendChild(el('div', 'sub', partes.join(' · ')));
    // o estado sai do texto e vira pilula
    if (st === 'feito') corpo.appendChild(pilula(passado && o.log && o.log.feito_em ? `feito ${ddmm(new Date(o.log.feito_em).getTime())} ${hhmm(new Date(o.log.feito_em).getTime())}` : 'feito', ZONA.ideal));
    else if (st === 'pulado') corpo.appendChild(pilula('pulado', '#8B95A3'));
    else if (passado) corpo.appendChild(pilula('não foi feito', ZONA.atencao));
    else if (futuro) corpo.appendChild(pilula('previsto', '#8B95A3'));
    else if (o.arrastada) corpo.appendChild(pilula(`atrasada desde ${ddmm(o.due)}`, ZONA.atencao));
    else if (atrasada) corpo.appendChild(pilula('atrasada', ZONA.atencao));
    item.appendChild(corpo);

    // Dia passado tambem tem botao: o caso comum e ter feito e esquecido de marcar. No futuro
    // nao, porque marcar como feito algo que ainda nao aconteceu e inventar registro.
    if (!resolvido && !futuro) {
      const b = el('button', 'mini', passado ? 'Fiz' : 'Feito');
      b.onclick = () => marcar(o, 'feito', passado);
      item.appendChild(b);
    }
    caixa.appendChild(item);
  });
}

async function marcar(o, status, retroativo = false) {
  // Marcando um dia passado, o feito e a dose ficam com o horario previsto, e nao com o de
  // agora. Uma dose de anteontem gravada como de hoje bagunca o historico e o grafico de
  // consumo, que e justamente onde se olha para entender um parametro que mexeu.
  const quando = retroativo ? new Date(o.due).toISOString() : new Date().toISOString();
  ocupado(true);
  try {
    await inserir('routine_logs', {
      routine_id: o.rotina.id,
      previsto_para: new Date(o.due).toISOString(),
      status,
      feito_em: quando,
    });
    // dosagem marcada como feita tambem lanca a dose, como no tablet
    const prod = dados.produtos.find((p) => p.id === o.rotina.product_id);
    if (status === 'feito' && o.rotina.tipo === 'dosagem' && prod) {
      const q = doseDe(prod, volume());
      if (q != null) {
        await inserir('dosings', {
          dosado_em: quando,
          product_id: prod.id,
          quantidade: q,
          unidade: prod.dose_unidade || '',
        });
      }
    }
    await recarregar();
    toast('Marcado.');
  } catch (e) {
    toast(`Não salvou: ${e.message}`, true);
  } finally {
    ocupado(false);
  }
}

function desenharResumo() {
  const caixa = $('#resumo');
  caixa.innerHTML = '';
  const tpa = dados.tpas[0];
  const linha = (titulo, sub, ic, cor) => {
    const i = el('div', 'item');
    if (ic) i.appendChild(selo(ic, cor, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', titulo));
    if (sub) c.appendChild(el('div', 'sub', sub));
    i.appendChild(c);
    caixa.appendChild(i);
  };
  if (tpa) {
    const dias = Math.floor((Date.now() - new Date(tpa.feita_em)) / DIA_MS);
    linha(`Última TPA há ${dias} dia${dias === 1 ? '' : 's'}`, `${tpa.litros} L · ${ddmm(tpa.feita_em)}`, 'tpa', AREA.tpa);
  } else linha('Nenhuma TPA registrada', null, 'tpa', AREA.tpa);
  const d = dados.doses[0];
  if (d) {
    const p = dados.produtos.find((x) => x.id === d.product_id);
    linha(`Última dose: ${p ? p.nome : 'produto'}`, `${d.quantidade} ${d.unidade || ''} · ${haQuanto(d.dosado_em)}`, 'dose', AREA.dose);
  }
}

function desenharFormularios() {
  const caixa = $('#campos-teste');
  if (!caixa.childElementCount) {
    PARAMS.forEach((p) => {
      const l = el('label');
      l.appendChild(el('span', null, `${ROTULOS[p]}${UNIDADES[p] ? ` (${UNIDADES[p]})` : ''}`));
      const i = document.createElement('input');
      i.type = 'number';
      i.inputMode = 'decimal';
      i.step = p === 'salinidade' ? '0.001' : p === 'kh' ? '0.05' : '1';
      i.id = `teste-${p}`;
      i.placeholder = formatar(p, ultimas(p)[0] ? Number(ultimas(p)[0].valor) : null);
      l.appendChild(i);
      caixa.appendChild(l);
    });
  } else {
    PARAMS.forEach((p) => {
      const i = $(`#teste-${p}`);
      if (i) i.placeholder = formatar(p, ultimas(p)[0] ? Number(ultimas(p)[0].valor) : null);
    });
  }

  const sel = $('#dose-produto');
  const antes = sel.value;
  sel.innerHTML = '';
  dados.produtos.forEach((p) => {
    const o = document.createElement('option');
    o.value = p.id;
    o.textContent = p.nome;
    sel.appendChild(o);
  });
  if (antes) sel.value = antes;
  atualizarDosePadrao();
}

function atualizarDosePadrao() {
  const p = dados.produtos.find((x) => x.id === $('#dose-produto').value);
  const q = doseDe(p, volume());
  $('#dose-qtd').placeholder = q == null ? 'quantidade' : String(Math.round(q * 100) / 100).replace('.', ',');
}

function desenharTudo() {
  const d = new Date();
  $('#hoje-data').textContent = d.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  $('#tanque').textContent = (dados.tanque && dados.tanque.nome) || 'Luluco Reef';
  desenharCabecalho();
  desenharParams();
  desenharHoje();
  desenharResumo();
  desenharFormularios();
  // as outras abas vivem nos proprios arquivos (graficos.js, financas.js, relatorios.js)
  desenharGraficos();
  desenharFinancas();
  desenharMais();
}

async function recarregar() {
  await carregar();
  desenharTudo();
}

// ---------------------------------------------------------------- salvar

const numero = (s) => {
  const v = parseFloat(String(s).replace(',', '.'));
  return Number.isFinite(v) ? v : null;
};

/** Salinidade abreviada, como no tablet: 25 ou 1025 viram 1,025. */
function salinidade(v) {
  if (v == null) return null;
  if (v > 1.5 && v < 100) return 1 + v / 1000;
  if (v >= 100) return v / 1000;
  return v;
}

async function salvarTeste() {
  const linhas = [];
  const baixa = $('#baixa').checked;
  const agora = new Date().toISOString();
  PARAMS.forEach((p) => {
    let v = numero($(`#teste-${p}`).value);
    if (v == null) return;
    if (p === 'salinidade') v = salinidade(v);
    linhas.push({ medido_em: agora, parametro: p, valor: v, unidade: UNIDADES[p] || '', baixa_confianca: baixa });
  });
  if (!linhas.length) { toast('Preencha pelo menos um parâmetro.', true); return; }
  ocupado(true);
  try {
    await inserir('water_tests', linhas);
    PARAMS.forEach((p) => { $(`#teste-${p}`).value = ''; });
    $('#baixa').checked = false;
    await recarregar();
    toast(`${linhas.length} medição${linhas.length > 1 ? 'ões' : ''} salva${linhas.length > 1 ? 's' : ''}.`);
  } catch (e) {
    toast(`Não salvou: ${e.message}`, true);
  } finally {
    ocupado(false);
  }
}

async function salvarTpa() {
  const litros = numero($('#tpa-litros').value);
  if (litros == null || litros <= 0) { toast('Quantos litros?', true); return; }
  ocupado(true);
  try {
    await inserir('water_changes', {
      feita_em: new Date().toISOString(),
      litros,
      sal: (dados.tanque && dados.tanque.sal_atual) || null,
      observacao: $('#tpa-obs').value.trim() || null,
    });
    // fecha a TPA que estiver cobrando (do dia ou arrastada), como no tablet
    const aberta = ocorrenciasDeHoje(dados.rotinas, dados.logs, Date.now())
      .find((o) => o.rotina.tipo === 'tpa' && !/baiac/i.test(o.rotina.titulo || '') && (!o.log || o.log.status === 'adiado'));
    if (aberta) {
      await inserir('routine_logs', { routine_id: aberta.rotina.id, previsto_para: new Date(aberta.due).toISOString(), status: 'feito', feito_em: new Date().toISOString() });
    }
    $('#tpa-litros').value = '';
    $('#tpa-obs').value = '';
    await recarregar();
    toast('TPA salva.');
  } catch (e) {
    toast(`Não salvou: ${e.message}`, true);
  } finally {
    ocupado(false);
  }
}

async function salvarDose() {
  const p = dados.produtos.find((x) => x.id === $('#dose-produto').value);
  if (!p) { toast('Escolha o produto.', true); return; }
  const q = numero($('#dose-qtd').value) ?? doseDe(p, volume());
  if (q == null) { toast('Qual a quantidade?', true); return; }
  ocupado(true);
  try {
    await inserir('dosings', { dosado_em: new Date().toISOString(), product_id: p.id, quantidade: q, unidade: p.dose_unidade || '' });
    $('#dose-qtd').value = '';
    await recarregar();
    toast('Dose salva.');
  } catch (e) {
    toast(`Não salvou: ${e.message}`, true);
  } finally {
    ocupado(false);
  }
}

async function salvarNota() {
  const texto = $('#nota-texto').value.trim();
  if (!texto) { toast('Escreva a nota.', true); return; }
  ocupado(true);
  try {
    // events.tipo tem check no banco: observacao, problema, equipamento, alimentacao ou outro
    await inserir('events', { ocorrido_em: new Date().toISOString(), tipo: 'observacao', texto });
    $('#nota-texto').value = '';
    await recarregar();
    toast('Nota salva.');
  } catch (e) {
    toast(`Não salvou: ${e.message}`, true);
  } finally {
    ocupado(false);
  }
}

// ---------------------------------------------------------------- ligacao

function mostrarApp(logado) {
  $('#login').classList.toggle('hidden', logado);
  $('#app').classList.toggle('hidden', !logado);
}

const ABAS = ['painel', 'registrar', 'graficos', 'financas', 'mais'];

function abrirAba(nome) {
  ABAS.forEach((a) => {
    $(`#aba-${a}`).classList.toggle('hidden', a !== nome);
  });
  document.querySelectorAll('#abas button').forEach((b) => b.classList.toggle('ativo', b.dataset.aba === nome));
  moverPilula();
  window.scrollTo(0, 0);
}

/**
 * A pilula de vidro desliza ate a aba escolhida, em vez de so acender o texto.
 * Anima transform, que o navegador resolve na GPU, entao nao custa quadro nenhum de layout.
 */
function moverPilula() {
  const b = document.querySelector('#abas button.ativo');
  const pil = $('#pilula');
  if (!b || !pil) return;
  pil.style.width = `${b.offsetWidth - 10}px`;
  pil.style.transform = `translateX(${b.offsetLeft + 5}px)`;
}

async function iniciar() {
  sessao = lerSessao();
  if (sessao && !(await renovar())) sessao = null;
  if (!sessao) { mostrarApp(false); return; }
  mostrarApp(true);
  ocupado(true);
  try {
    await recarregar();
    avisosRenovar();
  } catch (e) {
    toast(`Não deu para carregar: ${e.message}`, true);
    if (String(e.message).includes('sessão')) { gravarSessao(null); mostrarApp(false); }
  } finally {
    ocupado(false);
  }
}

function ligarDias() {
  const mudar = (n) => { deslocDia = Math.max(-DIAS_ATRAS, Math.min(DIAS_FRENTE, n)); desenharHoje(); };
  $('#dia-ant').onclick = () => mudar(deslocDia - 1);
  $('#dia-prox').onclick = () => mudar(deslocDia + 1);
  $('#dia-rotulo').onclick = () => mudar(0);
}

function ligar() {
  ligarDias();
  $('#login-form').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    $('#login-erro').textContent = '';
    $('#login-btn').disabled = true;
    try {
      await entrar($('#email').value.trim(), $('#senha').value);
      $('#senha').value = '';
      await iniciar();
    } catch (e) {
      $('#login-erro').textContent = e.message;
    } finally {
      $('#login-btn').disabled = false;
    }
  });

  document.querySelectorAll('#abas button').forEach((b) => {
    const nome = b.dataset.aba;
    const rotulo = b.textContent;
    b.textContent = '';
    b.appendChild(icone(nome, 'currentColor', 21));
    b.appendChild(el('span', null, rotulo));
    b.onclick = () => abrirAba(nome);
  });
  $('#recarregar').appendChild(icone('recarregar', 'currentColor', 20));
  $('#recarregar').textContent = '';
  $('#recarregar').onclick = async () => { ocupado(true); try { await recarregar(); toast('Atualizado.'); } catch (e) { toast(e.message, true); } finally { ocupado(false); } };
  $('#salvar-teste').onclick = salvarTeste;
  $('#salvar-tpa').onclick = salvarTpa;
  $('#salvar-dose').onclick = salvarDose;
  $('#salvar-nota').onclick = salvarNota;
  $('#dose-produto').onchange = atualizarDosePadrao;
  $('#folha-fechar').onclick = fecharFolha;
  $('#folha-fundo').onclick = fecharFolha;

  // volta do bolso: atualiza para nao mostrar dado de horas atras
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && sessao) recarregar().catch(() => {});
  });

  // a pilula so sabe onde parar depois que a barra tem largura
  requestAnimationFrame(moverPilula);
  window.addEventListener('resize', moverPilula);
  window.addEventListener('orientationchange', () => setTimeout(moverPilula, 120));

  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  iniciar();
}

/** Chamado pelo index depois que todos os scripts carregaram. */
function iniciarApp() {
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ligar);
  else ligar();
}
