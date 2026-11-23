/*
 * Vocabulario visual, o mesmo do tablet.
 *
 * O que faltava no PWA nao era cor solta: era icone, anel de saude e minigrafico. Sem eles toda
 * tela virava lista de texto, e lista de texto parece planilha. A paleta continua sobria (grafite,
 * um acento so), e a cor entra onde ela informa: na area a que o item pertence e na zona em que o
 * parametro caiu.
 *
 * Os icones sao desenhados aqui em SVG, um por um. Nao ha biblioteca de icone: dependencia externa
 * quebraria o funcionamento sem internet e ainda cobraria um download no primeiro acesso.
 */
'use strict';

/** Cor de cada area, igual ao Theme.kt do tablet. */
const AREA = {
  painel: '#2CC5A8', teste: '#4C8DF6', tpa: '#38BDF8', dose: '#E5A23C', jornal: '#E06C9F',
  animal: '#34C77B', saude: '#E5534B', equip: '#94A3B8', grafico: '#8B7CF6',
  relatorio: '#6E7FF3', calc: '#EAB308', financas: '#10B981', ajustes: '#8B95A3',
};

/** Traco de cada icone, numa caixa de 24 por 24. */
const ICONES = {
  painel: 'M3 11.2 12 3.5l9 7.7M5.4 9.6V20h13.2V9.6',
  registrar: 'M12 4.5v15M4.5 12h15',
  graficos: 'M4 4.5v15h15M7.5 15.5l3.5-4.5 3 3 4-6',
  financas: 'M3.5 7.5h17v11h-17zM3.5 7.5 15 4l3 3.5M16.5 13h2',
  mais: 'M5 7h14M5 12h14M5 17h9',
  tpa: 'M12 3.5s-6.5 7.3-6.5 11a6.5 6.5 0 0 0 13 0c0-3.7-6.5-11-6.5-11z',
  teste: 'M9.5 3.5h5M10.5 3.5v5.5L5.8 18.6a1 1 0 0 0 .9 1.4h10.6a1 1 0 0 0 .9-1.4L13.5 9V3.5M7.8 14h8.4',
  dose: 'M12 3.5s-4.5 5.4-4.5 8.3a4.5 4.5 0 0 0 9 0c0-2.9-4.5-8.3-4.5-8.3zM16 17.5l4 3M19 16.5l-2 4',
  manutencao: 'M14.5 4a4.5 4.5 0 0 0-5.6 5.6L4 14.5 6 19l4.9-4.9A4.5 4.5 0 0 0 16.5 8.5',
  nota: 'M6 3.5h8l4 4v13H6zM14 3.5v4h4M9 12h6M9 16h4',
  saude: 'M12 20s-7-4.4-7-9.3A3.9 3.9 0 0 1 12 8a3.9 3.9 0 0 1 7-1.3c0 4.9-7 13.3-7 13.3z',
  animal: 'M12 4l2.2 4.8 5.3.6-4 3.6 1.1 5.2L12 15.6 7.4 18.2l1.1-5.2-4-3.6 5.3-.6z',
  timer: 'M12 7.5v5l3 2M12 4.5a7.5 7.5 0 1 0 0 15 7.5 7.5 0 0 0 0-15zM9.5 2.5h5',
  alerta: 'M12 4.5 3 19.5h18zM12 10v4M12 16.8v.2',
  recarregar: 'M20 12a8 8 0 1 1-2.6-5.9M20 4.5V10h-5.5',
};

/** Um icone, em SVG. `tamanho` em pixels. */
function icone(nome, cor, tamanho) {
  const s = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  s.setAttribute('viewBox', '0 0 24 24');
  s.setAttribute('width', tamanho || 20);
  s.setAttribute('height', tamanho || 20);
  s.setAttribute('fill', 'none');
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', ICONES[nome] || ICONES.mais);
  p.setAttribute('stroke', cor || 'currentColor');
  p.setAttribute('stroke-width', '1.7');
  p.setAttribute('stroke-linecap', 'round');
  p.setAttribute('stroke-linejoin', 'round');
  s.appendChild(p);
  return s;
}

/**
 * Quadradinho com o icone na cor da area e um brilho leve atras, como o GlowIcon do tablet.
 * E o que da identidade a cada linha: dose e laranja, TPA e azul, saude e vermelho.
 */
function selo(nome, cor, tamanho) {
  const t = tamanho || 34;
  const b = el('span', 'selo-icone');
  b.style.width = `${t}px`;
  b.style.height = `${t}px`;
  b.style.background = `${cor}1A`;
  b.style.borderColor = `${cor}33`;
  b.appendChild(icone(nome, cor, Math.round(t * 0.55)));
  return b;
}

/** A area de uma rotina, pelo tipo. Mesma divisao do menu do tablet. */
function areaDaRotina(tipo) {
  if (tipo === 'tpa') return ['tpa', AREA.tpa];
  if (tipo === 'teste') return ['teste', AREA.teste];
  if (tipo === 'dosagem') return ['dose', AREA.dose];
  if (tipo === 'manutencao') return ['manutencao', AREA.equip];
  return ['nota', AREA.jornal];
}

/**
 * Anel da nota de saude, como no Painel do tablet: o arco enche ate a nota e a cor diz em que pe
 * o aquario esta. Sem nota (falta medir algo), fica o tracinho e o anel vazio.
 */
function anelSaude(nota, tamanho, temCritico) {
  const t = tamanho || 86;
  const r = t / 2 - 5;
  const volta = 2 * Math.PI * r;
  const cor = corDaNota(nota, temCritico);
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', `0 0 ${t} ${t}`);
  svg.setAttribute('width', t);
  svg.setAttribute('height', t);
  svg.classList.add('anel');
  const base = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
  Object.entries({ cx: t / 2, cy: t / 2, r, fill: 'none', stroke: 'rgba(255,255,255,0.07)', 'stroke-width': 5 })
    .forEach(([k, v]) => base.setAttribute(k, v));
  svg.appendChild(base);
  if (nota != null) {
    const arco = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    Object.entries({
      cx: t / 2, cy: t / 2, r, fill: 'none', stroke: cor, 'stroke-width': 5, 'stroke-linecap': 'round',
      'stroke-dasharray': `${(volta * nota) / 100} ${volta}`, transform: `rotate(-90 ${t / 2} ${t / 2})`,
    }).forEach(([k, v]) => arco.setAttribute(k, v));
    svg.appendChild(arco);
  }
  const caixa = el('div', 'anel-caixa');
  caixa.appendChild(svg);
  const dentro = el('div', 'anel-dentro');
  dentro.appendChild(el('div', 'anel-nota', nota == null ? '-' : String(nota)));
  dentro.appendChild(el('div', 'anel-rotulo', 'SAÚDE'));
  caixa.appendChild(dentro);
  return caixa;
}

/**
 * Cor do anel. A nota e um total: 85 com um unico parametro critico ainda da 85, e pela regra do
 * numero o anel ficaria verde. Verde se le como "esta tudo bem", e nao esta: com calcio critico
 * nada esta bem. Por isso, havendo parametro critico, o anel nao passa de laranja.
 */
function corDaNota(n, temCritico) {
  if (n == null) return 'var(--fraco)';
  if (temCritico) return n >= 65 ? 'var(--aviso)' : 'var(--critico)';
  if (n >= 85) return 'var(--ok)';
  if (n >= 65) return 'var(--aviso)';
  return 'var(--critico)';
}

/**
 * Minigrafico de um parametro: linha fina com os pontos na cor da zona em que caiu.
 * E o detalhe que mostra sem ler numero que o KH passou dois dias fora da faixa.
 */
function minigrafico(pontos, range, largura, altura) {
  const L = largura || 120;
  const A = altura || 30;
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', `0 0 ${L} ${A}`);
  svg.setAttribute('class', 'mini');
  svg.setAttribute('preserveAspectRatio', 'none');
  if (pontos.length < 2) return svg;
  let lo = Math.min(...pontos.map((p) => p.v));
  let hi = Math.max(...pontos.map((p) => p.v));
  if (range) { lo = Math.min(lo, Number(range.ideal_min)); hi = Math.max(hi, Number(range.ideal_max)); }
  const folga = (hi - lo || Math.max(1e-3, hi * 0.05)) * 0.25;
  lo -= folga; hi += folga;
  const t0 = pontos[0].em;
  const t1 = pontos[pontos.length - 1].em;
  const x = (t) => 3 + ((t - t0) / Math.max(1, t1 - t0)) * (L - 6);
  const y = (v) => 4 + (1 - (v - lo) / (hi - lo)) * (A - 8);

  if (range) {
    const faixa = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    const y1 = y(Number(range.ideal_max));
    const y2 = y(Number(range.ideal_min));
    Object.entries({ x: 0, y: y1, width: L, height: Math.max(1, y2 - y1), fill: 'var(--ok)', opacity: 0.09 })
      .forEach(([k, v]) => faixa.setAttribute(k, v));
    svg.appendChild(faixa);
  }
  const linha = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  linha.setAttribute('d', pontos.map((p, i) => `${i ? 'L' : 'M'}${x(p.em).toFixed(1)},${y(p.v).toFixed(1)}`).join(' '));
  linha.setAttribute('fill', 'none');
  linha.setAttribute('stroke', 'var(--fraco)');
  linha.setAttribute('stroke-width', '1.3');
  linha.setAttribute('vector-effect', 'non-scaling-stroke');
  svg.appendChild(linha);
  pontos.forEach((p) => {
    const c = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    const cor = range ? corDaZona(p.v, range) : 'var(--suave)';
    Object.entries({ cx: x(p.em).toFixed(1), cy: y(p.v).toFixed(1), r: 2.1, fill: p.duvida ? 'var(--fundo)' : cor, stroke: cor, 'stroke-width': 1.1 })
      .forEach(([k, v]) => c.setAttribute(k, v));
    svg.appendChild(c);
  });
  return svg;
}

/**
 * Nota de saude, pelas mesmas regras do tablet (domain/Health.kt): desconta parametro fora da
 * faixa, TPA atrasada, rotina nao cumprida e caso de saude aberto. Sem nenhuma medicao de um
 * parametro principal nao ha nota, porque a conta seria sobre o que nao se sabe.
 */
function notaDeSaude() {
  const agora = Date.now();
  const itens = [];
  const falta = [];
  let critico = false;
  const principais = ['kh', 'ca', 'mg', 'salinidade'];

  const avaliar = (p, peso, contaFalta) => {
    const u = ultimas(p)[0];
    if (!u) { if (contaFalta) falta.push(`medir ${ROTULOS[p]}`); return; }
    const idade = agora - new Date(u.medido_em).getTime();
    if (idade > 7 * DIA_MS) { itens.push({ txt: `${ROTULOS[p]} sem medição há mais de 7 dias`, pts: -5 * peso }); return; }
    const r = dados.ranges[p];
    if (!r) return;
    const st = statusDe(Number(u.valor), r);
    if (st === 'aviso') itens.push({ txt: `${ROTULOS[p]} em atenção`, pts: -8 * peso });
    if (st === 'critico') { itens.push({ txt: `${ROTULOS[p]} crítico`, pts: -15 * peso }); critico = true; }
  };
  principais.forEach((p) => avaliar(p, 1, true));
  ['no3', 'po4'].forEach((p) => {
    const u = ultimas(p)[0];
    if (u && agora - new Date(u.medido_em).getTime() <= 14 * DIA_MS) avaliar(p, 0.5, false);
  });

  const tpa = dados.tpas[0];
  if (!tpa) falta.push('registrar uma TPA');
  else {
    const dias = Math.floor((agora - new Date(tpa.feita_em).getTime()) / DIA_MS);
    if (dias > 16) itens.push({ txt: `TPA há ${dias} dias`, pts: -20 });
    else if (dias > 9) itens.push({ txt: `TPA há ${dias} dias`, pts: -10 });
  }

  const abertos = dados.casos.filter((c) => !c.fechado_em).length;
  if (abertos > 0) itens.push({ txt: `${abertos} caso${abertos > 1 ? 's' : ''} de saúde aberto${abertos > 1 ? 's' : ''}`, pts: -Math.min(15, 5 * abertos) });

  const arredondados = itens.map((i) => ({ ...i, pts: -Math.round(-i.pts) }));
  if (falta.length) return { nota: null, itens: arredondados, falta, critico };
  const nota = Math.max(0, Math.min(100, Math.round(100 + arredondados.reduce((a, i) => a + i.pts, 0))));
  return { nota, itens: arredondados, falta, critico };
}
