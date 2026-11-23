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

/**
 * Curva suave por entre os pontos (Catmull-Rom virada em Bezier).
 *
 * O desenho passa exatamente por cada medicao, so chega nela em curva em vez de em bico. Isso
 * importa: uma curva que "corrigisse" o caminho para ficar bonita estaria inventando medicao.
 */
function curvaSuave(pts) {
  if (pts.length < 2) return pts.length ? `M${pts[0][0]},${pts[0][1]}` : '';
  let d = `M${pts[0][0].toFixed(1)},${pts[0][1].toFixed(1)}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] || p2;
    const t = 0.2; // tensao baixa: suaviza sem estufar entre dois pontos distantes
    const c1 = [p1[0] + (p2[0] - p0[0]) * t, p1[1] + (p2[1] - p0[1]) * t];
    const c2 = [p2[0] - (p3[0] - p1[0]) * t, p2[1] - (p3[1] - p1[1]) * t];
    d += ` C${c1[0].toFixed(1)},${c1[1].toFixed(1)} ${c2[0].toFixed(1)},${c2[1].toFixed(1)} ${p2[0].toFixed(1)},${p2[1].toFixed(1)}`;
  }
  return d;
}

const svgEl = (tag, attrs) => {
  const e = document.createElementNS('http://www.w3.org/2000/svg', tag);
  Object.entries(attrs || {}).forEach(([k, v]) => e.setAttribute(k, v));
  return e;
};

/**
 * Um grafico, dentro de uma caixa posicionada.
 *
 * O desenho vai em SVG esticado (preserveAspectRatio none), o que serve para a linha mas distorce
 * texto. Por isso todo numero e HTML posicionado por cima, em porcentagem: escala na esquerda,
 * valor atual na ponta da linha e a bolha que segue o dedo.
 *
 * `series` e uma lista de { p, pontos, cor }. Com mais de uma serie cada uma ganha a propria
 * escala, senao o calcio (400) esmagaria o KH (8) contra o chao.
 */
function desenharGrafico(series, dias, mostrarFaixa) {
  const L = 300;
  const A = 110;
  const pad = { t: 8, b: 10, l: 4, r: 4 };
  const caixa = el('div', 'graf');
  const svg = svgEl('svg', { viewBox: `0 0 ${L} ${A}`, class: 'gsvg', preserveAspectRatio: 'none' });
  const ate = Date.now();
  const de = ate - dias * DIA_MS;
  const x = (t) => pad.l + ((t - de) / (ate - de)) * (L - pad.l - pad.r);
  const pct = (v, total) => `${(v / total) * 100}%`;
  // tudo que foi desenhado, para a leitura por toque achar o ponto mais proximo
  const marcados = [];

  series.forEach((s) => {
    if (!s.pontos.length) return;
    const r = dados.ranges[s.p];
    let lo = Math.min(...s.pontos.map((q) => q.v));
    let hi = Math.max(...s.pontos.map((q) => q.v));
    if (mostrarFaixa && r) { lo = Math.min(lo, Number(r.ideal_min)); hi = Math.max(hi, Number(r.ideal_max)); }
    const folga = (hi - lo || Math.max(1e-3, hi * 0.05)) * 0.18;
    lo -= folga; hi += folga;
    const y = (v) => pad.t + (1 - (v - lo) / (hi - lo)) * (A - pad.t - pad.b);

    if (mostrarFaixa && r) {
      const y1 = y(Number(r.ideal_max));
      const y2 = y(Number(r.ideal_min));
      const larg = L - pad.l - pad.r;
      svg.appendChild(svgEl('rect', { x: pad.l, y: y1, width: larg, height: Math.max(1, y2 - y1), fill: 'var(--ok)', opacity: '0.07' }));
      [y1, y2].forEach((yy) => svg.appendChild(svgEl('line', {
        x1: pad.l, x2: pad.l + larg, y1: yy, y2: yy,
        stroke: 'var(--ok)', 'stroke-width': '1', 'stroke-dasharray': '3 4', opacity: '0.45', 'vector-effect': 'non-scaling-stroke',
      })));
    }

    const xy = s.pontos.map((q) => [x(q.em), y(q.v)]);
    const d = curvaSuave(xy);

    // preenchimento em degrade embaixo da linha, como o Financia. Nao acrescenta informacao, mas e
    // o que separa um grafico de planilha de um grafico de aplicativo.
    if (mostrarFaixa) {
      const idg = `g${Math.random().toString(36).slice(2, 8)}`;
      const defs = svgEl('defs', {});
      const lg = svgEl('linearGradient', { id: idg, x1: '0', y1: '0', x2: '0', y2: '1' });
      lg.appendChild(svgEl('stop', { offset: '0', 'stop-color': s.cor, 'stop-opacity': '0.28' }));
      lg.appendChild(svgEl('stop', { offset: '1', 'stop-color': s.cor, 'stop-opacity': '0' }));
      defs.appendChild(lg);
      svg.appendChild(defs);
      const base = A - pad.b;
      svg.appendChild(svgEl('path', { d: `${d} L${xy[xy.length - 1][0].toFixed(1)},${base} L${xy[0][0].toFixed(1)},${base} Z`, fill: `url(#${idg})`, stroke: 'none' }));
    }

    svgEl('path', {});
    svg.appendChild(svgEl('path', { d, fill: 'none', stroke: s.cor, 'stroke-width': '1.8', 'stroke-linejoin': 'round', 'stroke-linecap': 'round', 'vector-effect': 'non-scaling-stroke' }));

    s.pontos.forEach((q) => {
      const cor = mostrarFaixa && r ? corDaZona(q.v, r) : s.cor;
      svg.appendChild(svgEl('circle', {
        cx: x(q.em).toFixed(1), cy: y(q.v).toFixed(1), r: '2.6',
        fill: q.duvida ? 'var(--fundo)' : cor, stroke: cor, 'stroke-width': '1.4',
      }));
      marcados.push({ ...q, p: s.p, cor, xp: x(q.em) / L, yp: y(q.v) / A });
    });

    // escala: so faz sentido com uma serie so, porque cada serie tem a sua
    if (series.length === 1) {
      [[hi, pad.t], [lo, A - pad.b]].forEach(([valor, yy]) => {
        const t = el('span', 'y-marca', formatar(s.p, valor));
        t.style.top = pct(yy, A);
        caixa.appendChild(t);
      });
      if (mostrarFaixa && r) {
        const faixa = el('span', 'y-faixa', `ideal ${formatar(s.p, Number(r.ideal_min))} a ${formatar(s.p, Number(r.ideal_max))}`);
        faixa.style.top = pct((y(Number(r.ideal_max)) + y(Number(r.ideal_min))) / 2, A);
        caixa.appendChild(faixa);
      }
    }

    // valor de agora, colado na ponta da linha: e a pergunta que ele faz olhando o grafico
    const ultimo = s.pontos[s.pontos.length - 1];
    const marca = el('span', 'agora', formatar(s.p, ultimo.v) + (series.length > 1 ? '' : ` ${UNIDADES[s.p] || ''}`));
    marca.style.top = pct(y(ultimo.v), A);
    marca.style.color = series.length > 1 ? s.cor : 'var(--creme)';
    caixa.appendChild(marca);
  });

  caixa.insertBefore(svg, caixa.firstChild);
  ligarLeitura(caixa, svg, marcados);
  return caixa;
}

/**
 * Leitura por toque: arrastar o dedo sobre o grafico mostra o valor e a data do ponto mais proximo.
 * Num celular nao da para acertar um ponto de 3 px com o dedo, entao a busca e so pelo eixo do
 * tempo e o ponto mais perto vence.
 */
function ligarLeitura(caixa, svg, pontos) {
  if (!pontos.length) return;
  const bolha = el('div', 'bolha hidden');
  const bico = el('div', 'bolha-bico hidden');
  caixa.appendChild(bico);
  const risca = el('div', 'risca hidden');
  caixa.appendChild(risca);
  caixa.appendChild(bolha);

  const mostrar = (clienteX) => {
    const cx = caixa.getBoundingClientRect();
    const frac = Math.max(0, Math.min(1, (clienteX - cx.left) / cx.width));
    let melhor = pontos[0];
    pontos.forEach((q) => { if (Math.abs(q.xp - frac) < Math.abs(melhor.xp - frac)) melhor = q; });
    bolha.textContent = `${formatar(melhor.p, melhor.v)} ${UNIDADES[melhor.p] || ''} · ${ddmm(melhor.em)}`.trim();
    // perto das bordas a bolha sairia da tela: ali ela encosta no canto em vez de centralizar
    bolha.style.left = `${melhor.xp * 100}%`;
    bolha.style.transform = melhor.xp < 0.2 ? 'translateX(0)' : melhor.xp > 0.8 ? 'translateX(-100%)' : 'translateX(-50%)';
    bolha.style.color = melhor.cor;
    risca.style.left = `${melhor.xp * 100}%`;
    bico.style.left = `${melhor.xp * 100}%`;
    bico.style.borderTopColor = 'var(--solido)';
    bolha.classList.remove('hidden');
    bico.classList.remove('hidden');
    risca.classList.remove('hidden');
  };
  const esconder = () => { [bolha, bico, risca].forEach((x) => x.classList.add('hidden')); };

  caixa.addEventListener('touchstart', (e) => { mostrar(e.touches[0].clientX); }, { passive: true });
  caixa.addEventListener('touchmove', (e) => { mostrar(e.touches[0].clientX); }, { passive: true });
  caixa.addEventListener('touchend', esconder, { passive: true });
  caixa.addEventListener('mousemove', (e) => mostrar(e.clientX));
  caixa.addEventListener('mouseleave', esconder);
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

const CORES_SERIE = ['#2CC5A8', '#4C8DF6', '#E5A23C', '#E06C9F', '#8B7CF6', '#38BDF8'];

function desenharGraficos() {
  const dias = Number(gPeriodo);

  segmentado($('#g-periodo'), G_PERIODOS, gPeriodo, (v) => { gPeriodo = v; desenharGraficos(); });

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
    multi.appendChild(desenharGrafico(series, dias, false));
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
      c.appendChild(desenharGrafico([{ p, pontos, cor: 'var(--suave)' }], dias, true));
      c.appendChild(eixoDeTempo(dias));
      c.appendChild(el('div', 'muted', 'Arraste o dedo sobre o gráfico para ler cada medição. Ponto vazado é leitura duvidosa.'));
      c.appendChild(el('div', 'eyebrow', 'Leituras por faixa'));
      c.appendChild(barraDeFaixas(p, dias));
    }
    caixa.appendChild(c);
  });
}
