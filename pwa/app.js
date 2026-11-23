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

function statusDe(valor, r) {
  if (valor == null || !r) return 'sem';
  if (valor < r.alerta_min || valor > r.alerta_max) return 'critico';
  if (valor < r.ideal_min || valor > r.ideal_max) return 'aviso';
  return 'ideal';
}
const CORES = { ideal: 'var(--ok)', aviso: 'var(--aviso)', critico: 'var(--critico)', sem: 'var(--fraco)' };

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
 * Ocorrencias de hoje, arrastando as pendencias dos dias anteriores.
 *
 * Mesma regra do app do tablet: rotina por dias da semana que nao foi feita nem pulada continua
 * cobrando nos dias seguintes (uma por rotina, a mais recente) e so quando nao vence hoje tambem.
 * Sem isso a TPA de sexta desaparecia no sabado.
 */
function ocorrenciasDeHoje(rotinas, logs, agora) {
  const hoje = diaDe(agora);
  const porChave = {};
  logs.filter((l) => !l.deleted_at).forEach((l) => {
    const k = `${l.routine_id}|${new Date(l.previsto_para).getTime()}`;
    if (!porChave[k] || new Date(l.updated_at) > new Date(porChave[k].updated_at)) porChave[k] = l;
  });
  const achar = (r, due) => porChave[`${r.id}|${due}`] || null;
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

const estado = (o) => (o.log ? o.log.status : 'pendente');

// ---------------------------------------------------------------- dados

let dados = {
  ranges: {}, testes: [], rotinas: [], logs: [], produtos: [], tanque: null,
  tpas: [], doses: [], notas: [], financas: [], compras: [], casos: [],
};

async function carregar() {
  const desde = iso(new Date(Date.now() - 120 * DIA_MS));
  const [tanque, ranges, testes, rotinas, logs, produtos, tpas, doses, notas, financas, compras, casos] = await Promise.all([
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
    const ponto = el('span', 'ponto');
    ponto.style.background = CORES[st];
    topo.appendChild(ponto);
    topo.appendChild(el('span', 'eyebrow', ROTULOS[p]));
    c.appendChild(topo);

    const v = el('div', 'valor');
    v.textContent = formatar(p, ult ? Number(ult.valor) : null);
    if (UNIDADES[p]) { const u = el('small'); u.textContent = ` ${UNIDADES[p]}`; v.appendChild(u); }
    c.appendChild(v);

    const pe = el('div', 'pe');
    pe.appendChild(el('span', null, ult ? haQuanto(ult.medido_em) : 'sem medição'));
    if (ult && ant) {
      const d = Number(ult.valor) - Number(ant.valor);
      pe.appendChild(el('span', null, (d > 0 ? '▲ ' : d < 0 ? '▼ ' : '= ') + formatar(p, Math.abs(d))));
    }
    c.appendChild(pe);

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
        m.style.background = CORES[st];
        faixa.appendChild(m);
      }
      c.appendChild(faixa);
    }
    caixa.appendChild(c);
  });
}

function desenharHoje() {
  const caixa = $('#hoje');
  caixa.innerHTML = '';
  const agora = Date.now();
  const lista = ocorrenciasDeHoje(dados.rotinas, dados.logs, agora);
  if (!lista.length) { caixa.appendChild(el('div', 'vazio', 'Nada para hoje.')); return; }
  const porId = {};
  dados.produtos.forEach((p) => { porId[p.id] = p; });

  lista.forEach((o) => {
    const st = estado(o);
    const resolvido = st === 'feito' || st === 'pulado';
    const atrasada = !resolvido && agora >= o.due;
    const item = el('div', 'item');

    item.appendChild(el('div', 'hora', o.arrastada ? ddmm(o.due) : hhmm(o.due)));

    const corpo = el('div', 'corpo');
    const t = el('div', `titulo${resolvido ? ' feito' : ''}`, o.rotina.titulo);
    corpo.appendChild(t);
    const partes = [];
    const prod = o.rotina.product_id ? porId[o.rotina.product_id] : null;
    if (o.rotina.tipo === 'dosagem' && prod) {
      const q = doseDe(prod, volume());
      if (q != null) partes.push(`${(Math.round(q * 100) / 100).toString().replace('.', ',')} ${prod.dose_unidade || ''}`.trim());
    }
    if (o.arrastada) partes.push(`vencida em ${ddmm(o.due)}`);
    else if (o.rotina.instrucao) partes.push(o.rotina.instrucao);
    if (partes.length) corpo.appendChild(el('div', 'sub', partes.join(' · ')));
    item.appendChild(corpo);

    if (st === 'feito') {
      const s = el('span', 'selo', 'feito');
      s.style.color = 'var(--ok)';
      item.appendChild(s);
    } else if (st === 'pulado') {
      item.appendChild(el('span', 'selo', 'pulado'));
    } else {
      if (atrasada) {
        const s = el('span', 'selo', 'atrasada');
        s.style.color = 'var(--aviso)';
        item.appendChild(s);
      }
      const b = el('button', 'mini', 'Feito');
      b.onclick = () => marcar(o, 'feito');
      item.appendChild(b);
    }
    caixa.appendChild(item);
  });
}

async function marcar(o, status) {
  ocupado(true);
  try {
    await inserir('routine_logs', {
      routine_id: o.rotina.id,
      previsto_para: new Date(o.due).toISOString(),
      status,
      feito_em: new Date().toISOString(),
    });
    // dosagem marcada como feita tambem lanca a dose, como no tablet
    const prod = dados.produtos.find((p) => p.id === o.rotina.product_id);
    if (status === 'feito' && o.rotina.tipo === 'dosagem' && prod) {
      const q = doseDe(prod, volume());
      if (q != null) {
        await inserir('dosings', {
          dosado_em: new Date().toISOString(),
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
  const linha = (titulo, sub) => {
    const i = el('div', 'item');
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', titulo));
    if (sub) c.appendChild(el('div', 'sub', sub));
    i.appendChild(c);
    caixa.appendChild(i);
  };
  if (tpa) {
    const dias = Math.floor((Date.now() - new Date(tpa.feita_em)) / DIA_MS);
    linha(`Última TPA há ${dias} dia${dias === 1 ? '' : 's'}`, `${tpa.litros} L · ${ddmm(tpa.feita_em)}`);
  } else linha('Nenhuma TPA registrada');
  const d = dados.doses[0];
  if (d) {
    const p = dados.produtos.find((x) => x.id === d.product_id);
    linha(`Última dose: ${p ? p.nome : 'produto'}`, `${d.quantidade} ${d.unidade || ''} · ${haQuanto(d.dosado_em)}`);
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
  window.scrollTo(0, 0);
}

async function iniciar() {
  sessao = lerSessao();
  if (sessao && !(await renovar())) sessao = null;
  if (!sessao) { mostrarApp(false); return; }
  mostrarApp(true);
  ocupado(true);
  try {
    await recarregar();
  } catch (e) {
    toast(`Não deu para carregar: ${e.message}`, true);
    if (String(e.message).includes('sessão')) { gravarSessao(null); mostrarApp(false); }
  } finally {
    ocupado(false);
  }
}

function ligar() {
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

  document.querySelectorAll('#abas button').forEach((b) => { b.onclick = () => abrirAba(b.dataset.aba); });
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

  if ('serviceWorker' in navigator) navigator.serviceWorker.register('sw.js').catch(() => {});
  iniciar();
}

/** Chamado pelo index depois que todos os scripts carregaram. */
function iniciarApp() {
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ligar);
  else ligar();
}
