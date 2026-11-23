/*
 * Financas no iPhone: mesmas regras do tablet.
 *
 * Caixinha: entram deposito e venda; saem gasto no Pix e retirada. Cartao nao mexe na caixinha,
 * por isso ele tem a propria conta de "a pagar".
 *
 * Ciclo da fatura: do dia escolhido (padrao 16) ate a vespera do mesmo dia do mes seguinte, e o
 * ciclo leva o nome do mes em que termina.
 */
'use strict';

const CAT_DESPESA = [
  ['corais_peixes', 'Corais e peixes'], ['sal_suplementos', 'Sal e suplementos'], ['equipamentos', 'Equipamentos'],
  ['manutencao', 'Manutenção'], ['alimentacao', 'Alimentação'], ['outros', 'Outros'],
];
const CAT_RECEITA = [['venda_corais', 'Venda de corais e mudas'], ['venda_outros', 'Venda de outros itens'], ['outros', 'Outros']];
const AQUARIOS = [['marinho', 'Marinho'], ['doce', 'Água doce'], ['ambos', 'Ambos']];
const F_ABAS = [['resumo', 'Resumo do ciclo'], ['caixinha', 'Caixinha'], ['compras', 'Lista de compras']];

let fAba = 'resumo';
let fMesOffset = 0;
/** Dia em que o ciclo vira. No tablet fica em Ajustes; aqui e local, com o mesmo padrao. */
const CICLO_DIA = Number(localStorage.getItem('luluco_ciclo_dia') || 16);

const catRotulo = (c) => (CAT_DESPESA.concat(CAT_RECEITA).find((x) => x[0] === c) || [c, c])[1];
const aquarioRotulo = (a) => (AQUARIOS.find((x) => x[0] === a) || [a, a])[1];
const dinheiro = (v) => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

/** Em que ciclo cai uma data: antes do dia de virada ainda e o ciclo deste mes. */
function cicloDe(ms) {
  const d = new Date(ms);
  let ano = d.getFullYear();
  let mes = d.getMonth();
  if (d.getDate() >= CICLO_DIA) mes += 1;
  if (mes > 11) { mes -= 12; ano += 1; }
  return `${ano}-${String(mes + 1).padStart(2, '0')}`;
}
function cicloRotulo(chave) {
  const [a, m] = chave.split('-').map(Number);
  const t = new Date(a, m - 1, 1).toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  return t.charAt(0).toUpperCase() + t.slice(1);
}
function cicloAtual(offset) {
  const hoje = new Date();
  const base = cicloDe(hoje.getTime()).split('-').map(Number);
  const d = new Date(base[0], base[1] - 1 + offset, 1);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function caixaDelta(e) {
  if (e.tipo === 'aporte' || e.tipo === 'receita') return Number(e.valor);
  if (e.tipo === 'retirada') return -Number(e.valor);
  if (e.tipo === 'despesa' && e.forma === 'pix') return -Number(e.valor);
  return 0;
}
const emAberto = (e) => e.tipo === 'despesa' && e.forma === 'cartao' && !e.pago;
const podeQuitar = (e) => e.tipo === 'despesa' && e.forma === 'cartao';
const parcelaTexto = (e) => (e.parcela && e.parcelas > 1 ? `parcela ${e.parcela}/${e.parcelas}` : null);
const formaTexto = (e) => (e.tipo === 'aporte' ? 'Depósito' : e.tipo === 'retirada' ? 'Retirada' : e.forma === 'cartao' ? 'Cartão' : 'Pix');

function desenharFinancas() {
  chips($('#f-abas'), F_ABAS, fAba, (v) => { fAba = v; desenharFinancas(); });
  const caixa = $('#financas');
  caixa.innerHTML = '';
  if (fAba === 'resumo') resumoDoCiclo(caixa);
  else if (fAba === 'caixinha') aCaixinha(caixa);
  else aListaDeCompras(caixa);
}

function numeroGrande(alvo, rotulo, valor, cor, nota) {
  const c = el('div', 'num');
  c.appendChild(el('div', 'eyebrow', rotulo));
  const v = el('div', 'num-valor', dinheiro(valor));
  if (cor) v.style.color = cor;
  c.appendChild(v);
  if (nota) c.appendChild(el('div', 'num-nota', nota));
  alvo.appendChild(c);
  return c;
}

function resumoDoCiclo(caixa) {
  const chave = cicloAtual(fMesOffset);
  const doCiclo = dados.financas.filter((e) => cicloDe(new Date(e.lancado_em).getTime()) === chave);
  const gastos = doCiclo.filter((e) => e.tipo === 'despesa').reduce((a, e) => a + Number(e.valor), 0);
  const vendas = doCiclo.filter((e) => e.tipo === 'receita').reduce((a, e) => a + Number(e.valor), 0);
  const cartao = doCiclo.filter((e) => e.tipo === 'despesa' && e.forma === 'cartao').reduce((a, e) => a + Number(e.valor), 0);
  const aPagarTudo = dados.financas.filter(emAberto).reduce((a, e) => a + Number(e.valor), 0);
  const caixinha = dados.financas.reduce((a, e) => a + caixaDelta(e), 0);

  // navegacao de ciclo
  const nav = el('div', 'nav-mes');
  const antes = el('button', 'mini', '‹');
  antes.onclick = () => { fMesOffset -= 1; desenharFinancas(); };
  const dep = el('button', 'mini', '›');
  dep.onclick = () => { fMesOffset += 1; desenharFinancas(); };
  nav.appendChild(antes);
  const t = el('div', 'nav-mes-txt');
  t.appendChild(el('strong', null, 'Fatura de ' + cicloRotulo(chave)));
  t.appendChild(el('div', 'muted', `ciclo do dia ${CICLO_DIA}`));
  nav.appendChild(t);
  nav.appendChild(dep);
  caixa.appendChild(nav);

  const g = el('div', 'grid');
  numeroGrande(g, 'Caixinha', caixinha, caixinha < 0 ? 'var(--critico)' : null,
    dados.financas.every((e) => caixaDelta(e) === 0) ? 'nenhum depósito ainda' : null);
  numeroGrande(g, 'A pagar', aPagarTudo, aPagarTudo > 0 ? 'var(--aviso)' : null, 'inclui parcelas futuras');
  numeroGrande(g, 'Gastos do ciclo', gastos);
  numeroGrande(g, 'No cartão', cartao);
  caixa.appendChild(g);
  if (vendas > 0) {
    const g2 = el('div', 'grid');
    numeroGrande(g2, 'Vendas', vendas, 'var(--ok)');
    numeroGrande(g2, 'Saldo do ciclo', vendas - gastos, vendas - gastos >= 0 ? 'var(--ok)' : 'var(--critico)');
    caixa.appendChild(g2);
  }

  const novo = el('button', 'primary cheio', 'Novo lançamento');
  novo.onclick = () => folhaLancamento(null);
  caixa.appendChild(novo);

  // para onde foi o dinheiro
  const porCat = {};
  doCiclo.filter((e) => e.tipo === 'despesa').forEach((e) => { porCat[e.categoria] = (porCat[e.categoria] || 0) + Number(e.valor); });
  const cats = Object.entries(porCat).sort((a, b) => b[1] - a[1]);
  if (cats.length) {
    caixa.appendChild(el('h3', null, 'Para onde foi'));
    const lista = el('div', 'lista');
    cats.forEach(([c, v]) => {
      const i = el('div', 'item coluna');
      const linha = el('div', 'linha-cheia');
      linha.appendChild(el('span', null, catRotulo(c)));
      linha.appendChild(el('strong', null, dinheiro(v)));
      i.appendChild(linha);
      const barra = el('div', 'barra');
      const dentro = el('div', 'barra-dentro');
      dentro.style.width = `${Math.max(2, (v / gastos) * 100)}%`;
      barra.appendChild(dentro);
      i.appendChild(barra);
      lista.appendChild(i);
    });
    caixa.appendChild(lista);
  }

  caixa.appendChild(el('h3', null, 'Lançamentos'));
  const lista = el('div', 'lista');
  if (!doCiclo.length) lista.appendChild(el('div', 'vazio', 'Nada lançado neste ciclo.'));
  doCiclo.sort((a, b) => new Date(b.lancado_em) - new Date(a.lancado_em)).forEach((e) => lista.appendChild(linhaLancamento(e)));
  caixa.appendChild(lista);
}

function linhaLancamento(e) {
  const quitado = podeQuitar(e) && e.pago;
  const i = el('div', 'item');
  const entrada = e.tipo === 'receita' || e.tipo === 'aporte';
  i.appendChild(selo(entrada ? 'financas' : 'dose', quitado ? '#5B6573' : entrada ? AREA.financas : AREA.calc, 30));
  // data, estado e forma vao todos para a linha de baixo: na largura do iPhone o titulo quebrava
  // em tres linhas quando cada um queria a propria coluna
  const c = el('div', 'corpo');
  c.appendChild(el('div', `titulo${quitado ? ' feito' : ''}`, e.descricao || catRotulo(e.categoria)));
  const estado = quitado ? 'paga' : emAberto(e) ? 'a pagar' : null;
  // so a palavra do estado leva cor: a linha inteira laranja fica gritando
  const sub = el('div', 'sub');
  sub.appendChild(el('span', null, ddmm(new Date(e.lancado_em).getTime())));
  if (estado) {
    sub.appendChild(el('span', null, ' · '));
    const marca = el('span', null, estado);
    marca.style.color = estado === 'paga' ? 'var(--ok)' : 'var(--aviso)';
    sub.appendChild(marca);
  }
  sub.appendChild(el('span', null, ' · ' + [formaTexto(e), parcelaTexto(e)].filter(Boolean).join(' · ')));
  c.appendChild(sub);
  i.appendChild(c);
  const v = el('div', 'valor-linha', (entrada ? '+ ' : '- ') + dinheiro(e.valor));
  if (entrada) v.style.color = 'var(--ok)';
  else if (quitado) v.style.color = 'var(--fraco)';
  i.appendChild(v);
  i.onclick = () => (podeQuitar(e) ? folhaQuitar(e) : folhaLancamento(e));
  return i;
}

/** Quitar uma parcela ou a compra inteira. Nao muda o gasto do ciclo: muda o "a pagar". */
function folhaQuitar(e) {
  abrirFolha(e.descricao || catRotulo(e.categoria), (c) => {
    c.appendChild(el('p', 'muted', [dinheiro(e.valor), parcelaTexto(e), 'Cartão'].filter(Boolean).join(' · ')));
    c.appendChild(el('p', 'muted', e.pago
      ? 'Esta parcela está marcada como paga.'
      : 'Marcar como paga só tira do "a pagar". O gasto continua contando no ciclo em que foi lançado.'));
    const irmas = e.grupo ? dados.financas.filter((x) => x.grupo === e.grupo && emAberto(x)) : [];
    if (irmas.length > 1) c.appendChild(el('p', 'muted', `Esta compra tem ${irmas.length} parcelas em aberto.`));

    const acao = async (ids, pago) => {
      ocupado(true);
      try {
        for (const id of ids) await atualizar('finance_entries', id, { pago });
        await recarregar();
        fecharFolha();
        toast(pago ? 'Quitada.' : 'Desmarcada.');
      } catch (err) { toast(`Não salvou: ${err.message}`, true); } finally { ocupado(false); }
    };

    if (e.pago) {
      const b = el('button', null, 'Desmarcar');
      b.onclick = () => acao([e.id], false);
      c.appendChild(b);
    } else {
      const b = el('button', 'primary', 'Já paguei esta');
      b.onclick = () => acao([e.id], true);
      c.appendChild(b);
      if (irmas.length > 1) {
        const b2 = el('button', null, `Quitar as ${irmas.length} em aberto`);
        b2.onclick = () => acao(irmas.map((x) => x.id), true);
        c.appendChild(b2);
      }
    }
    const ed = el('button', null, 'Editar');
    ed.onclick = () => folhaLancamento(e);
    c.appendChild(ed);
  });
}

/** Novo lancamento ou edicao. Parcelado cria uma entrada por fatura, como no tablet. */
function folhaLancamento(existente) {
  abrirFolha(existente ? 'Editar lançamento' : 'Novo lançamento', (c) => {
    let tipo = existente ? existente.tipo : 'despesa';
    let categoria = existente ? existente.categoria : 'outros';
    let aquario = existente ? existente.aquario : 'marinho';
    let forma = existente ? existente.forma : 'pix';
    let parcelas = existente && existente.parcelas ? existente.parcelas : 1;

    const linhaTipo = el('div', 'chips');
    const linhaCat = el('div', 'chips');
    const linhaAq = el('div', 'chips');
    const linhaForma = el('div', 'chips');
    const linhaParc = el('label');

    const desc = document.createElement('input');
    desc.type = 'text';
    desc.placeholder = 'Descrição';
    desc.value = existente ? existente.descricao || '' : '';
    const valor = document.createElement('input');
    valor.type = 'number';
    valor.inputMode = 'decimal';
    valor.step = '0.01';
    valor.placeholder = 'Valor';
    valor.value = existente ? existente.valor : '';
    const obs = document.createElement('input');
    obs.type = 'text';
    obs.placeholder = 'Observação (opcional)';
    obs.value = existente ? existente.observacao || '' : '';

    const redesenhar = () => {
      chips(linhaTipo, [['despesa', 'Gasto'], ['receita', 'Venda'], ['aporte', 'Depósito'], ['retirada', 'Retirada']], tipo, (v) => { tipo = v; redesenhar(); });
      const caixaTipo = tipo === 'aporte' || tipo === 'retirada';
      linhaCat.classList.toggle('hidden', caixaTipo);
      linhaAq.classList.toggle('hidden', caixaTipo);
      linhaForma.classList.toggle('hidden', tipo !== 'despesa');
      linhaParc.classList.toggle('hidden', !(tipo === 'despesa' && forma === 'cartao') || !!existente);
      if (!caixaTipo) {
        chips(linhaCat, tipo === 'receita' ? CAT_RECEITA : CAT_DESPESA, categoria, (v) => { categoria = v; redesenhar(); });
        chips(linhaAq, AQUARIOS, aquario, (v) => { aquario = v; redesenhar(); });
      }
      if (tipo === 'despesa') chips(linhaForma, [['pix', 'Pix (sai da caixinha)'], ['cartao', 'Cartão']], forma, (v) => { forma = v; redesenhar(); });
    };

    linhaParc.appendChild(el('span', null, 'Em quantas parcelas'));
    const selParc = document.createElement('select');
    for (let i = 1; i <= 24; i++) {
      const o = document.createElement('option');
      o.value = String(i);
      o.textContent = i === 1 ? 'à vista' : `${i}x`;
      selParc.appendChild(o);
    }
    selParc.value = String(parcelas);
    selParc.onchange = () => { parcelas = Number(selParc.value); };
    linhaParc.appendChild(selParc);

    [linhaTipo, desc, valor, linhaCat, linhaAq, linhaForma, linhaParc, obs].forEach((x) => c.appendChild(x));
    redesenhar();

    const salvar = el('button', 'primary', 'Salvar');
    salvar.onclick = async () => {
      const v = parseFloat(String(valor.value).replace(',', '.'));
      if (!Number.isFinite(v) || v <= 0) { toast('Qual o valor?', true); return; }
      ocupado(true);
      try {
        const base = {
          tipo, categoria: tipo === 'aporte' || tipo === 'retirada' ? 'outros' : categoria,
          descricao: desc.value.trim(), aquario, forma: tipo === 'despesa' ? forma : 'pix',
          observacao: obs.value.trim() || null,
        };
        if (existente) {
          await atualizar('finance_entries', existente.id, { ...base, valor: v });
        } else if (tipo === 'despesa' && forma === 'cartao' && parcelas > 1) {
          // parcelas iguais e a sobra dos centavos na ultima, como o cartao faz
          const grupo = crypto.randomUUID();
          const parte = Math.floor((v / parcelas) * 100) / 100;
          const linhas = [];
          const hoje = new Date();
          for (let i = 0; i < parcelas; i++) {
            const quando = new Date(hoje.getFullYear(), hoje.getMonth() + i, hoje.getDate(), hoje.getHours(), hoje.getMinutes());
            linhas.push({
              ...base, valor: i === parcelas - 1 ? Math.round((v - parte * (parcelas - 1)) * 100) / 100 : parte,
              lancado_em: quando.toISOString(), parcela: i + 1, parcelas, grupo,
            });
          }
          await inserir('finance_entries', linhas);
        } else {
          await inserir('finance_entries', { ...base, valor: v, lancado_em: new Date().toISOString() });
        }
        await recarregar();
        fecharFolha();
        toast('Salvo.');
      } catch (err) { toast(`Não salvou: ${err.message}`, true); } finally { ocupado(false); }
    };
    c.appendChild(salvar);

    if (existente) {
      const del = el('button', null, 'Apagar');
      del.onclick = async () => {
        ocupado(true);
        try { await apagar('finance_entries', existente.id); await recarregar(); fecharFolha(); toast('Apagado.'); }
        catch (err) { toast(err.message, true); } finally { ocupado(false); }
      };
      c.appendChild(del);
    }
  });
}

function aCaixinha(caixa) {
  const movs = dados.financas.filter((e) => caixaDelta(e) !== 0).sort((a, b) => new Date(a.lancado_em) - new Date(b.lancado_em));
  let acc = 0;
  const linhas = movs.map((e) => { acc += caixaDelta(e); return [e, acc]; }).reverse();

  const g = el('div', 'grid');
  numeroGrande(g, 'Saldo da caixinha', acc, acc < 0 ? 'var(--critico)' : null);
  caixa.appendChild(g);
  caixa.appendChild(el('p', 'muted', 'Entram depósitos e vendas. Saem gastos no Pix e retiradas. Compra no cartão não mexe na caixinha: ela tem a conta "a pagar" própria.'));

  const bot = el('div', 'linha-botoes');
  const dep = el('button', 'primary', 'Depositar');
  dep.onclick = () => folhaLancamento({ tipo: 'aporte', categoria: 'outros', aquario: 'marinho', forma: 'pix', valor: '', descricao: '', id: null });
  const ret = el('button', null, 'Retirar');
  ret.onclick = () => folhaLancamento({ tipo: 'retirada', categoria: 'outros', aquario: 'marinho', forma: 'pix', valor: '', descricao: '', id: null });
  bot.appendChild(dep);
  bot.appendChild(ret);
  caixa.appendChild(bot);

  caixa.appendChild(el('h3', null, 'Histórico'));
  const lista = el('div', 'lista');
  if (!linhas.length) lista.appendChild(el('div', 'vazio', 'Nenhum movimento ainda.'));
  linhas.slice(0, 80).forEach(([e, saldo]) => {
    const d = caixaDelta(e);
    const i = el('div', 'item');
    i.appendChild(selo('financas', d > 0 ? AREA.financas : AREA.calc, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', e.descricao || formaTexto(e)));
    c.appendChild(el('div', 'sub', `${ddmm(new Date(e.lancado_em).getTime())} · saldo depois: ${dinheiro(saldo)}`));
    i.appendChild(c);
    const v = el('div', 'valor-linha', (d > 0 ? '+ ' : '- ') + dinheiro(Math.abs(d)));
    v.style.color = d > 0 ? 'var(--ok)' : 'var(--creme)';
    i.appendChild(v);
    lista.appendChild(i);
  });
  caixa.appendChild(lista);
}

function aListaDeCompras(caixa) {
  const abertos = dados.compras.filter((i) => !i.comprado_em);
  const comprados = dados.compras.filter((i) => i.comprado_em).slice(0, 20);

  const novo = el('button', 'primary cheio', 'Novo item');
  novo.onclick = () => folhaItem(null);
  caixa.appendChild(novo);

  caixa.appendChild(el('h3', null, 'Para comprar'));
  const lista = el('div', 'lista');
  if (!abertos.length) lista.appendChild(el('div', 'vazio', 'Lista vazia.'));
  abertos.forEach((it) => {
    const i = el('div', 'item');
    i.appendChild(selo('mais', AREA.equip, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', it.nome));
    c.appendChild(el('div', 'sub', [catRotulo(it.categoria), aquarioRotulo(it.aquario), it.valor_medio ? `~${dinheiro(it.valor_medio)}` : null].filter(Boolean).join(' · ')));
    i.appendChild(c);
    const b = el('button', 'mini', 'Comprei');
    b.onclick = (ev) => { ev.stopPropagation(); folhaComprei(it); };
    i.appendChild(b);
    i.onclick = () => folhaItem(it);
    lista.appendChild(i);
  });
  caixa.appendChild(lista);

  if (comprados.length) {
    caixa.appendChild(el('h3', null, 'Comprados'));
    const l2 = el('div', 'lista');
    comprados.forEach((it) => {
      const i = el('div', 'item');
      i.appendChild(el('div', 'hora', ddmm(new Date(it.comprado_em).getTime())));
      const c = el('div', 'corpo');
      c.appendChild(el('div', 'titulo feito', it.nome));
      i.appendChild(c);
      if (it.valor_pago) i.appendChild(el('div', 'valor-linha', dinheiro(it.valor_pago)));
      l2.appendChild(i);
    });
    caixa.appendChild(l2);
  }
}

function folhaItem(existente) {
  abrirFolha(existente ? 'Editar item' : 'Novo item', (c) => {
    let categoria = existente ? existente.categoria : 'outros';
    let aquario = existente ? existente.aquario : 'marinho';
    const nome = document.createElement('input');
    nome.type = 'text';
    nome.placeholder = 'O que falta comprar';
    nome.value = existente ? existente.nome : '';
    const preco = document.createElement('input');
    preco.type = 'number';
    preco.inputMode = 'decimal';
    preco.step = '0.01';
    preco.placeholder = 'Valor estimado (opcional)';
    preco.value = existente && existente.valor_medio ? existente.valor_medio : '';
    const lc = el('div', 'chips');
    const la = el('div', 'chips');
    const pintaCat = () => chips(lc, CAT_DESPESA, categoria, (v) => { categoria = v; pintaCat(); });
    const pintaAq = () => chips(la, AQUARIOS, aquario, (v) => { aquario = v; pintaAq(); });
    pintaCat();
    pintaAq();
    [nome, preco, lc, la].forEach((x) => c.appendChild(x));

    const b = el('button', 'primary', 'Salvar');
    b.onclick = async () => {
      if (!nome.value.trim()) { toast('Qual item?', true); return; }
      ocupado(true);
      try {
        const linha = { nome: nome.value.trim(), categoria, aquario, valor_medio: parseFloat(String(preco.value).replace(',', '.')) || null };
        if (existente) await atualizar('shopping_items', existente.id, linha);
        else await inserir('shopping_items', linha);
        await recarregar();
        fecharFolha();
        toast('Salvo.');
      } catch (err) { toast(err.message, true); } finally { ocupado(false); }
    };
    c.appendChild(b);
  });
}

/** "Comprei": marca o item e lanca o gasto, como no tablet. */
function folhaComprei(item) {
  abrirFolha(`Comprei: ${item.nome}`, (c) => {
    let forma = 'pix';
    let parcelas = 1;
    const valor = document.createElement('input');
    valor.type = 'number';
    valor.inputMode = 'decimal';
    valor.step = '0.01';
    valor.placeholder = 'Quanto pagou';
    if (item.valor_medio) valor.value = item.valor_medio;
    const lf = el('div', 'chips');
    const lp = el('label');
    lp.appendChild(el('span', null, 'Em quantas parcelas'));
    const sel = document.createElement('select');
    for (let i = 1; i <= 24; i++) {
      const o = document.createElement('option');
      o.value = String(i);
      o.textContent = i === 1 ? 'à vista' : `${i}x`;
      sel.appendChild(o);
    }
    sel.onchange = () => { parcelas = Number(sel.value); };
    lp.appendChild(sel);
    const atualiza = () => {
      chips(lf, [['pix', 'Pix (sai da caixinha)'], ['cartao', 'Cartão']], forma, (v) => { forma = v; atualiza(); });
      lp.classList.toggle('hidden', forma !== 'cartao');
    };
    atualiza();
    [valor, lf, lp].forEach((x) => c.appendChild(x));

    const b = el('button', 'primary', 'Confirmar compra');
    b.onclick = async () => {
      const v = parseFloat(String(valor.value).replace(',', '.'));
      if (!Number.isFinite(v) || v <= 0) { toast('Quanto custou?', true); return; }
      ocupado(true);
      try {
        const base = { tipo: 'despesa', categoria: item.categoria, descricao: item.nome, aquario: item.aquario, forma, shopping_item_id: item.id };
        if (forma === 'cartao' && parcelas > 1) {
          const grupo = crypto.randomUUID();
          const parte = Math.floor((v / parcelas) * 100) / 100;
          const hoje = new Date();
          const linhas = [];
          for (let i = 0; i < parcelas; i++) {
            const quando = new Date(hoje.getFullYear(), hoje.getMonth() + i, hoje.getDate(), hoje.getHours(), hoje.getMinutes());
            linhas.push({ ...base, valor: i === parcelas - 1 ? Math.round((v - parte * (parcelas - 1)) * 100) / 100 : parte, lancado_em: quando.toISOString(), parcela: i + 1, parcelas, grupo });
          }
          await inserir('finance_entries', linhas);
        } else {
          await inserir('finance_entries', { ...base, valor: v, lancado_em: new Date().toISOString() });
        }
        await atualizar('shopping_items', item.id, { comprado_em: new Date().toISOString(), valor_pago: v });
        await recarregar();
        fecharFolha();
        toast('Compra lançada.');
      } catch (err) { toast(`Não salvou: ${err.message}`, true); } finally { ocupado(false); }
    };
    c.appendChild(b);
  });
}
