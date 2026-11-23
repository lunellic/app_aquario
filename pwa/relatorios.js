/*
 * Aba "Mais": jornal e relatorios.
 *
 * O relatorio e o mesmo texto do tablet (domain/Report.kt), refeito aqui em JavaScript. Como no
 * tablet, estatistica e consumo ignoram leitura marcada como duvidosa, que aparece na lista com a
 * marca. E texto simples de proposito: cabe no compartilhamento do iPhone e no corpo de um e-mail.
 */
'use strict';

const M_ABAS = [['jornal', 'Jornal'], ['relatorio', 'Relatório']];
const SECOES = [
  ['resumo', 'Resumo'], ['testes', 'Testes'], ['consumo', 'Consumo'], ['tpas', 'TPAs'],
  ['produtos', 'Produtos usados'], ['rotinas', 'Rotinas e manutenção'], ['notas', 'Notas e saúde'],
];
const R_PERIODOS = [['7', '7 dias'], ['30', '30 dias'], ['90', '90 dias'], ['365', '1 ano']];

let mAba = 'jornal';
let rPeriodo = '30';
let rSecoes = SECOES.map((s) => s[0]);

function desenharMais() {
  chips($('#m-abas'), M_ABAS, mAba, (v) => { mAba = v; desenharMais(); });
  const caixa = $('#mais');
  caixa.innerHTML = '';
  if (mAba === 'jornal') oJornal(caixa);
  else oRelatorio(caixa);
}

function oJornal(caixa) {
  const itens = [];
  dados.testes.forEach((t) => itens.push({
    em: new Date(t.medido_em).getTime(),
    titulo: `${ROTULOS[t.parametro] || t.parametro}: ${formatar(t.parametro, Number(t.valor))} ${UNIDADES[t.parametro] || ''}`.trim(),
    sub: t.baixa_confianca ? 'teste de água · leitura duvidosa' : 'teste de água',
    cor: 'var(--blue, #4C8DF6)',
  }));
  dados.tpas.forEach((t) => itens.push({ em: new Date(t.feita_em).getTime(), titulo: `TPA de ${t.litros} L`, sub: t.observacao || 'troca de água', cor: '#38BDF8' }));
  dados.doses.forEach((d) => {
    const p = dados.produtos.find((x) => x.id === d.product_id);
    itens.push({ em: new Date(d.dosado_em).getTime(), titulo: `${p ? p.nome : 'Dose'}: ${d.quantidade} ${d.unidade || ''}`.trim(), sub: 'dosagem', cor: '#E5A23C' });
  });
  dados.notas.forEach((n) => itens.push({ em: new Date(n.ocorrido_em).getTime(), titulo: n.texto || 'Nota', sub: 'nota no jornal', cor: '#E06C9F' }));
  dados.financas.forEach((e) => itens.push({
    em: new Date(e.lancado_em).getTime(),
    titulo: `${e.descricao || catRotulo(e.categoria)}: ${dinheiro(e.valor)}`,
    sub: ['finanças', formaTexto(e), parcelaTexto(e)].filter(Boolean).join(' · '),
    cor: '#2CC5A8',
  }));
  dados.casos.forEach((c) => itens.push({ em: new Date(c.aberto_em).getTime(), titulo: c.titulo, sub: `saúde · ${c.tipo}`, cor: '#E5534B' }));

  // o jornal e o que ja aconteceu: parcela de cartao com data futura nao entra
  const agora = Date.now();
  itens.sort((a, b) => b.em - a.em);
  const passado = itens.filter((i) => i.em <= agora);
  const lista = el('div', 'lista');
  if (!passado.length) lista.appendChild(el('div', 'vazio', 'Nada registrado ainda.'));
  passado.slice(0, 200).forEach((i) => {
    const item = el('div', 'item');
    const risco = el('span', 'risco');
    risco.style.background = i.cor;
    item.appendChild(risco);
    item.appendChild(el('div', 'hora', ddmm(i.em)));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', i.titulo));
    c.appendChild(el('div', 'sub', `${i.sub} · ${hhmm(i.em)}`));
    item.appendChild(c);
    lista.appendChild(item);
  });
  caixa.appendChild(lista);
}

function oRelatorio(caixa) {
  const periodo = el('div', 'chips');
  chips(periodo, R_PERIODOS, rPeriodo, (v) => { rPeriodo = v; desenharMais(); });
  caixa.appendChild(el('h3', null, 'Período'));
  caixa.appendChild(periodo);

  caixa.appendChild(el('h3', null, 'O que entra'));
  const secoes = el('div', 'chips');
  SECOES.forEach(([id, rotulo]) => {
    const b = el('button', `chip${rSecoes.includes(id) ? ' ativo' : ''}`, rotulo);
    b.onclick = () => {
      rSecoes = rSecoes.includes(id) ? rSecoes.filter((x) => x !== id) : [...rSecoes, id];
      desenharMais();
    };
    secoes.appendChild(b);
  });
  caixa.appendChild(secoes);

  const texto = montarRelatorio(Number(rPeriodo), rSecoes);
  const bot = el('div', 'linha-botoes');
  const compartilhar = el('button', 'primary', 'Compartilhar');
  compartilhar.onclick = async () => {
    try {
      // navigator.share abre a folha do iPhone: Mail, WhatsApp, Notas, o que ele quiser
      if (navigator.share) await navigator.share({ title: 'Luluco Reef - relatório', text: texto });
      else { await navigator.clipboard.writeText(texto); toast('Copiado.'); }
    } catch (_) { /* cancelou */ }
  };
  const copiar = el('button', null, 'Copiar');
  copiar.onclick = async () => {
    try { await navigator.clipboard.writeText(texto); toast('Copiado.'); }
    catch (_) { toast('Não deu para copiar.', true); }
  };
  bot.appendChild(compartilhar);
  bot.appendChild(copiar);
  caixa.appendChild(bot);

  caixa.appendChild(el('h3', null, 'Prévia'));
  const pre = el('pre', 'relatorio', texto);
  caixa.appendChild(pre);
}

const arred = (v, casas) => Math.round(v * 10 ** casas) / 10 ** casas;
const numCurto = (v) => String(arred(v, 2)).replace('.', ',');

function dataHora(ms) {
  const d = new Date(ms);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')} ${hhmm(ms)}`;
}
function dataSo(ms) {
  const d = new Date(ms);
  return `${String(d.getDate()).padStart(2, '0')}/${String(d.getMonth() + 1).padStart(2, '0')}/${d.getFullYear()}`;
}

/** Mesmo texto do relatorio do tablet, no mesmo formato e na mesma ordem. */
function montarRelatorio(dias, secoes) {
  const agora = Date.now();
  const de = agora - dias * DIA_MS;
  const dentro = (ms) => ms >= de && ms <= agora;

  const testes = dados.testes.filter((t) => dentro(new Date(t.medido_em).getTime())).sort((a, b) => new Date(a.medido_em) - new Date(b.medido_em));
  const tpas = dados.tpas.filter((t) => dentro(new Date(t.feita_em).getTime())).sort((a, b) => new Date(a.feita_em) - new Date(b.feita_em));
  const doses = dados.doses.filter((d) => dentro(new Date(d.dosado_em).getTime())).sort((a, b) => new Date(a.dosado_em) - new Date(b.dosado_em));
  const notas = dados.notas.filter((n) => dentro(new Date(n.ocorrido_em).getTime())).sort((a, b) => new Date(a.ocorrido_em) - new Date(b.ocorrido_em));
  const nomes = {};
  dados.produtos.forEach((p) => { nomes[p.id] = p.nome; });

  const L = [];
  const titulo = (t) => { L.push('', t.toUpperCase(), '-'.repeat(t.length)); };

  L.push('LULUCO REEF - RELATÓRIO');
  L.push(`Período: ${dataSo(de)} a ${dataSo(agora)}`);
  if (dados.tanque) L.push(`${dados.tanque.nome}, ${numCurto(dados.tanque.volume_dosagem_l)} L, sal ${dados.tanque.sal_atual || 'não informado'}`);

  if (secoes.includes('resumo')) {
    titulo('Resumo');
    const ultTpa = dados.tpas[0];
    L.push(ultTpa
      ? `Última TPA: ${dataHora(new Date(ultTpa.feita_em).getTime())}, ${numCurto(ultTpa.litros)} L (há ${Math.floor((agora - new Date(ultTpa.feita_em)) / DIA_MS)} dias)`
      : 'Última TPA: nenhuma registrada');
    L.push(`Testes no período: ${testes.length}`);
    L.push(`TPAs no período: ${tpas.length} (${numCurto(tpas.reduce((a, t) => a + Number(t.litros), 0))} L no total)`);
    L.push(`Doses registradas no período: ${doses.length}`);
    PARAMS.forEach((p) => {
      const u = ultimas(p)[0];
      if (u) L.push(`Último ${ROTULOS[p]}: ${formatar(p, Number(u.valor))} ${u.unidade || ''} em ${dataHora(new Date(u.medido_em).getTime())}`);
    });
  }

  if (secoes.includes('testes')) {
    titulo('Testes');
    if (!testes.length) L.push('Nenhum teste no período.');
    PARAMS.forEach((p) => {
      const lista = testes.filter((t) => t.parametro === p);
      if (!lista.length) return;
      const bons = lista.filter((t) => !t.baixa_confianca).map((t) => Number(t.valor));
      let cab = `\n${ROTULOS[p]} (${lista[0].unidade || ''}): ${lista.length} medições`;
      if (bons.length) {
        const media = bons.reduce((a, b) => a + b, 0) / bons.length;
        cab += `, mín. ${formatar(p, Math.min(...bons))}, máx. ${formatar(p, Math.max(...bons))}, média ${formatar(p, media)}`;
      }
      L.push(cab);
      lista.forEach((t) => {
        let linha = `  ${dataHora(new Date(t.medido_em).getTime())}  ${formatar(p, Number(t.valor))}`;
        if (t.baixa_confianca) linha += '  (baixa confiança)';
        if (t.observacao) linha += `  - ${t.observacao}`;
        L.push(linha);
      });
    });
  }

  if (secoes.includes('consumo')) {
    titulo('Consumo e tendência');
    let achou = false;
    ['kh', 'ca', 'mg'].forEach((p) => {
      const bons = testes.filter((t) => t.parametro === p && !t.baixa_confianca);
      if (bons.length < 2) return;
      const a = bons[0];
      const b = bons[bons.length - 1];
      const d = (new Date(b.medido_em) - new Date(a.medido_em)) / DIA_MS;
      if (d < 0.5) return;
      achou = true;
      const delta = Number(b.valor) - Number(a.valor);
      const porDia = delta / d;
      const sinal = delta >= 0 ? '+' : '-';
      L.push(`${ROTULOS[p]}: de ${formatar(p, Number(a.valor))} para ${formatar(p, Number(b.valor))} em ${numCurto(d)} dias (${sinal}${numCurto(Math.abs(porDia))} por dia, ${sinal}${numCurto(Math.abs(porDia) * 7)} por semana)`);
    });
    L.push(achou
      ? 'Variação líquida entre a primeira e a última leitura confiável do período. Já inclui o que as TPAs e as doses repuseram, então não é o consumo puro dos corais.'
      : 'Faltam leituras confiáveis no período para estimar (pelo menos 2 por parâmetro).');
  }

  if (secoes.includes('tpas')) {
    titulo('TPAs');
    if (!tpas.length) L.push('Nenhuma TPA no período.');
    tpas.forEach((c) => {
      const extra = [c.sal, c.salinidade_nova_agua ? `água nova a ${formatar('salinidade', Number(c.salinidade_nova_agua))}` : null, c.metodo, c.observacao].filter(Boolean);
      L.push(`  ${dataHora(new Date(c.feita_em).getTime())}  ${numCurto(c.litros)} L${extra.length ? ', ' + extra.join(', ') : ''}`);
    });
  }

  if (secoes.includes('produtos')) {
    titulo('Produtos usados');
    if (!doses.length) L.push('Nenhuma dose registrada no período.');
    const porProduto = {};
    doses.forEach((d) => { (porProduto[d.product_id] = porProduto[d.product_id] || []).push(d); });
    Object.entries(porProduto).sort((a, b) => b[1].length - a[1].length).forEach(([id, lista]) => {
      const total = lista.reduce((a, d) => a + Number(d.quantidade || 0), 0);
      const unidade = (lista.find((d) => d.unidade) || {}).unidade || '';
      let linha = `  ${nomes[id] || 'Produto'}: ${lista.length} vez${lista.length === 1 ? '' : 'es'}`;
      if (total > 0) linha += `, total ${numCurto(total)} ${unidade}`.trimEnd();
      linha += `, última em ${dataHora(new Date(lista[lista.length - 1].dosado_em).getTime())}`;
      L.push(linha);
    });
  }

  if (secoes.includes('rotinas')) {
    titulo('Rotinas e manutenção');
    const porId = {};
    dados.rotinas.forEach((r) => { porId[r.id] = r; });
    const feitas = dados.logs.filter((l) => {
      const r = porId[l.routine_id];
      const quando = new Date(l.feito_em || l.previsto_para).getTime();
      return !l.deleted_at && l.status === 'feito' && dentro(quando) && r && r.tipo !== 'dosagem';
    }).sort((a, b) => new Date(a.feito_em || a.previsto_para) - new Date(b.feito_em || b.previsto_para));
    if (!feitas.length) L.push('Nenhuma manutenção marcada como feita no período.');
    feitas.forEach((l) => L.push(`  ${dataHora(new Date(l.feito_em || l.previsto_para).getTime())}  ${(porId[l.routine_id] || {}).titulo || ''}`));
    const puladas = dados.logs.filter((l) => !l.deleted_at && l.status === 'pulado' && dentro(new Date(l.previsto_para).getTime())).length;
    if (puladas) L.push(`Puladas: ${puladas}`);
  }

  if (secoes.includes('notas')) {
    titulo('Notas e saúde');
    if (!notas.length) L.push('Nenhuma nota no período.');
    notas.forEach((n) => L.push(`  ${dataHora(new Date(n.ocorrido_em).getTime())}  ${n.texto}`));
    const abertos = dados.casos.filter((c) => !c.fechado_em);
    if (abertos.length) {
      L.push('Casos de saúde abertos:');
      abertos.forEach((c) => L.push(`  ${c.titulo} (${c.tipo}), aberto em ${dataSo(new Date(c.aberto_em).getTime())}`));
    }
  }

  L.push('', `Gerado no iPhone pelo Luluco Reef em ${dataHora(agora)}.`);
  return L.join('\n');
}
