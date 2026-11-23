/*
 * Graficos. Desenhados em SVG, nao em canvas: no iPhone o SVG acompanha a resolucao da tela sem
 * conta de pixel, nao borra no zoom e nao precisa redesenhar quando a tela gira.
 *
 * Mostra so ponto medido, nunca ponto inventado entre duas medicoes. Leitura marcada como duvidosa
 * aparece vazada e fica fora da media e da projecao, igual ao app do tablet.
 */
'use strict';

const G_PERIODOS = [['7', '7 dias'], ['30', '30 dias'], ['90', '90 dias'], ['365', '1 ano']];
let gPeriodo = '30';
let gComparar = ['kh', 'ca', 'mg'];

function serieDe(p, dias) {
  const desde = Date.now() - dias * DIA_MS;
  return dados.testes
    .filter((t) => t.parametro === p && new Date(t.medido_em).getTime() >= desde)
    .map((t) => ({ em: new Date(t.medido_em).getTime(), v: Number(t.valor), duvida: !!t.baixa_confianca }))
    .sort((a, b) => a.em - b.em);
}

const svgEl = (tag, attrs) => {
  const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
  Object.entries(attrs || {}).forEach(([k, v]) => e.setAttribute(k, v));
  return e;
};

/**
 * Um grafico. `series` e uma lista de { p, pontos, cor }. Com mais de uma serie cada uma ganha a
 * propria escala, senao o calcio (400) esmagaria o KH (8) contra o chao.
 */
function desenharSvg(series, dias, mostrarFaixa) {
  const L = 300;
  const A = 110;
  const pad = { t: 8, b: 16, l: 4, r: 4 };
  const svg = svgEl('svg', { viewBox: `0 0 ${L} ${A}`, class: 'gsvg', preserveAspectRatio: 'none' });
  const ate = Date.now();
  const de = ate - dias * DIA_MS;
  const x = (t) => pad.l + ((t - de) / (ate - de)) * (L - pad.l - pad.r);

  series.forEach((s) => {
    if (!s.pontos.length) return;
    const r = dados.ranges[s.p];
    let lo = Math.min(...s.pontos.map((q) => q.v));
    let hi = Math.max(...s.pontos.map((q) => q.v));
    if (mostrarFaixa && r) { lo = Math.min(lo, Number(r.ideal_min)); hi = Math.max(hi, Number(r.ideal_max)); }
    const folga = (hi - lo || Math.max(1e-3, hi * 0.05)) * 0.18;
    lo -= folga; hi += folga;
    const y = (v) => pad.t + (1 - (v - lo) / (hi - lo)) * (A - pad.t - pad.b);

    // faixa ideal ao fundo, so no grafico de um parametro so
    if (mostrarFaixa && r) {
      const y1 = y(Number(r.ideal_max));
      const y2 = y(Number(r.ideal_min));
      const larg = L - pad.l - pad.r;
      svg.appendChild(svgEl('rect', { x: pad.l, y: y1, width: larg, height: Math.max(1, y2 - y1), fill: 'var(--ok)', opacity: '0.07' }));
      // as bordas da faixa marcadas: sem elas o bloco verde se confunde com o fundo do card
      [y1, y2].forEach((yy) => svg.appendChild(svgEl('line', {
        x1: pad.l, x2: pad.l + larg, y1: yy, y2: yy,
        stroke: 'var(--ok)', 'stroke-width': '1', 'stroke-dasharray': '3 4', opacity: '0.45', 'vector-effect': 'non-scaling-stroke',
      })));
    }

    const d = s.pontos.map((q, i) => `${i ? 'L' : 'M'}${x(q.em).toFixed(1)},${y(q.v).toFixed(1)}`).join(' ');
    svg.appendChild(svgEl('path', { d, fill: 'none', stroke: s.cor, 'stroke-width': '1.6', 'stroke-linejoin': 'round', 'stroke-linecap': 'round', 'vector-effect': 'non-scaling-stroke' }));

    s.pontos.forEach((q) => {
      const cor = mostrarFaixa && r ? corDaZona(q.v, r) : s.cor;
      svg.appendChild(svgEl('circle', {
        cx: x(q.em).toFixed(1), cy: y(q.v).toFixed(1), r: '2.6',
        fill: q.duvida ? 'var(--fundo)' : cor, stroke: cor, 'stroke-width': '1.4',
      }));
    });
  });
  return svg;
}

/** Datas embaixo do grafico: comeco, meio e hoje. Sem isso um grafico de 1 ano nao diz quando. */
function eixoDeTempo(dias) {
  const ate = Date.now();
  const de = ate - dias * DIA_MS;
  const marcas = [de, (de + ate) / 2, ate];
  const linha = el('div', 'eixo-x');
  marcas.forEach((t, i) => linha.appendChild(el('span', null, i === 2 ? 'hoje' : ddmm(t))));
  return linha;
}

function corDaZona(v, r) {
  if (v < Number(r.alerta_min) || v > Number(r.alerta_max)) return 'var(--critico)';
  if (v < Number(r.ideal_min) || v > Number(r.ideal_max)) return 'var(--aviso)';
  return 'var(--ok)';
}

const CORES_SERIE = ['#2CC5A8', '#4C8DF6', '#E5A23C', '#E06C9F', '#8B7CF6', '#38BDF8'];

function desenharGraficos() {
  const dias = Number(gPeriodo);

  chips($('#g-periodo'), G_PERIODOS, gPeriodo, (v) => { gPeriodo = v; desenharGraficos(); });

  // comparacao: ate 6 parametros no mesmo desenho, cada um na propria escala
  const disponiveis = PARAMS.filter((p) => dados.testes.some((t) => t.parametro === p));
  chips($('#g-escolha'), disponiveis.map((p) => [p, ROTULOS[p]]), null, (p) => {
    if (gComparar.includes(p)) gComparar = gComparar.filter((x) => x !== p);
    else if (gComparar.length < 6) gComparar = [...gComparar, p];
    desenharGraficos();
  });
  Array.from($('#g-escolha').children).forEach((b, i) => {
    if (gComparar.includes(disponiveis[i])) b.classList.add('ativo');
  });

  const multi = $('#g-multi');
  multi.innerHTML = '';
  const escolhidos = gComparar.filter((p) => disponiveis.includes(p));
  if (!escolhidos.length) {
    multi.appendChild(el('div', 'muted', 'Escolha os parâmetros acima.'));
  } else {
    const series = escolhidos.map((p, i) => ({ p, pontos: serieDe(p, dias), cor: CORES_SERIE[i % CORES_SERIE.length] }));
    multi.appendChild(desenharSvg(series, dias, false));
    multi.appendChild(eixoDeTempo(dias));
    const leg = el('div', 'legenda');
    series.forEach((s) => {
      const i = el('span', 'leg');
      const pt = el('span', 'ponto');
      pt.style.background = s.cor;
      i.appendChild(pt);
      i.appendChild(el('span', null, ROTULOS[s.p]));
      leg.appendChild(i);
    });
    multi.appendChild(leg);
    multi.appendChild(el('div', 'muted', 'Cada parâmetro na própria escala: serve para ver se sobem e descem juntos, não para comparar valor.'));
  }

  // um grafico por parametro, com a faixa ideal ao fundo
  const caixa = $('#graficos');
  caixa.innerHTML = '';
  if (!disponiveis.length) { caixa.appendChild(el('div', 'vazio', 'Nenhuma medição ainda.')); return; }
  disponiveis.forEach((p) => {
    const pontos = serieDe(p, dias);
    const c = el('div', 'card');
    const topo = el('div', 'topo-graf');
    topo.appendChild(el('div', 'eyebrow', `${ROTULOS[p]}${UNIDADES[p] ? ` · ${UNIDADES[p]}` : ''}`));
    const bons = pontos.filter((q) => !q.duvida);
    if (bons.length) {
      const med = bons.reduce((a, b) => a + b.v, 0) / bons.length;
      topo.appendChild(el('div', 'muted', `mín ${formatar(p, Math.min(...bons.map((q) => q.v)))} · máx ${formatar(p, Math.max(...bons.map((q) => q.v)))} · média ${formatar(p, med)}`));
    }
    c.appendChild(topo);
    if (pontos.length < 2) c.appendChild(el('div', 'muted', 'Menos de duas medições no período.'));
    else {
      c.appendChild(desenharSvg([{ p, pontos, cor: 'var(--suave)' }], dias, true));
      c.appendChild(eixoDeTempo(dias));
      if (dados.ranges[p]) c.appendChild(el('div', 'muted', `Faixa ideal tracejada: ${formatar(p, Number(dados.ranges[p].ideal_min))} a ${formatar(p, Number(dados.ranges[p].ideal_max))}. Ponto vazado é leitura duvidosa.`));
    }
    caixa.appendChild(c);
  });
}
