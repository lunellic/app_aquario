/*
 * Aba "Mais", parte do aquario: habitantes, equipamentos, produtos e a calculadora de sal.
 *
 * Mesmas tabelas e mesmos valores permitidos do tablet (CHECKs do schema), para que o que se
 * cadastra no celular apareca igual la. No celular a vantagem e o teclado: cadastrar um peixe com
 * nome cientifico e origem e muito mais rapido digitando no iPhone do que no Fire.
 */
'use strict';

const TIPOS_HAB = [['coral', 'Coral'], ['peixe', 'Peixe'], ['invertebrado', 'Invertebrado'], ['anemona', 'Anêmona'], ['alga', 'Alga']];
const TIPOS_HAB_PLURAL = { coral: 'Corais', peixe: 'Peixes', invertebrado: 'Invertebrados', anemona: 'Anêmonas', alga: 'Algas' };
const CATEGORIAS = [['LPS', 'LPS'], ['SPS', 'SPS'], ['mole', 'Soft'], ['zoa', 'Zoa/Paly'], ['outro', 'Outro']];
const TIPOS_EQUIP = [
  ['skimmer', 'Skimmer'], ['bomba_circulacao', 'Circulação'], ['bomba_retorno', 'Retorno'], ['luminaria', 'Luminária'],
  ['termostato', 'Termostato'], ['osmose', 'Osmose'], ['outro', 'Outro'],
];
const TIPOS_PROD = [['suplemento', 'Suplemento'], ['aditivo', 'Aditivo'], ['tratamento', 'Tratamento'], ['midia_filtrante', 'Mídia'], ['outro', 'Outro']];
const UNIDADES_DOSE = [['gotas', 'gotas'], ['ml', 'ml'], ['g', 'g'], ['colher', 'colher'], ['outra', 'outra']];
const rotuloDe = (lista, v) => (lista.find((x) => x[0] === v) || [v, v])[1];

/** Campo de texto ou numero, com rotulo em cima. Devolve o input. */
function campo(c, rotulo, valor, tipo, dica) {
  const l = el('label', 'campo');
  l.appendChild(el('span', 'campo-rot', rotulo));
  const i = document.createElement('input');
  i.type = tipo === 'numero' ? 'text' : (tipo || 'text');
  if (tipo === 'numero') i.inputMode = 'decimal';
  i.value = valor == null ? '' : String(valor);
  if (dica) i.placeholder = dica;
  l.appendChild(i);
  c.appendChild(l);
  return i;
}
const texto = (i) => i.value.trim() || null;
const hojeIso = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const dataCurta = (iso) => (iso ? iso.split('-').reverse().slice(0, 2).join('/') + '/' + iso.slice(2, 4) : '');

async function salvarLinha(tabela, existente, linha, msg) {
  ocupado(true);
  try {
    if (existente && existente.id) await atualizar(tabela, existente.id, linha);
    else await inserir(tabela, linha);
    await recarregar();
    fecharFolha();
    toast(msg || 'Salvo.');
  } catch (err) { toast(`Não salvou: ${err.message}`, true); } finally { ocupado(false); }
}

// ---------------------------------------------------------------- habitantes

let habHistorico = false;

function desenharAquario(caixa) {
  const ativos = dados.habitantes.filter((h) => h.status === 'ativo');
  const saiu = dados.habitantes.filter((h) => h.status !== 'ativo');

  // resumo em mosaico: quantos de cada, contando a quantidade e nao a linha
  const g = el('div', 'grid');
  const soma = (t) => ativos.filter((h) => h.tipo === t).reduce((a, h) => a + (h.quantidade || 0), 0);
  [['coral', 'Corais'], ['peixe', 'Peixes']].forEach(([t, r]) => g.appendChild(mosaico('animal', AREA.animal, String(soma(t)), null, r)));
  caixa.appendChild(g);
  const outros = ativos.filter((h) => !['coral', 'peixe'].includes(h.tipo)).reduce((a, h) => a + (h.quantidade || 0), 0);
  if (outros) caixa.appendChild(el('div', 'sub', `e mais ${outros} entre invertebrados, anêmonas e algas`));

  const topo = el('div', 'linha-botoes');
  const novo = el('button', 'primary', 'Adicionar habitante');
  novo.onclick = () => folhaHabitante(null);
  topo.appendChild(novo);
  caixa.appendChild(topo);

  const seg = el('div', 'chips');
  segmentado(seg, [['ativos', `No aquário · ${ativos.length}`], ['historico', `Saíram · ${saiu.length}`]], habHistorico ? 'historico' : 'ativos',
    (v) => { habHistorico = v === 'historico'; desenharMais(); });
  caixa.appendChild(seg);

  const lista = habHistorico ? saiu : ativos;
  if (!lista.length) { caixa.appendChild(el('div', 'vazio', habHistorico ? 'Ninguém saiu do aquário.' : 'Nenhum habitante cadastrado.')); return; }

  TIPOS_HAB.forEach(([tipo]) => {
    const grupo = lista.filter((h) => h.tipo === tipo).sort((a, b) => a.nome_popular.localeCompare(b.nome_popular, 'pt-BR'));
    if (!grupo.length) return;
    caixa.appendChild(el('h3', null, `${TIPOS_HAB_PLURAL[tipo]} · ${grupo.reduce((a, h) => a + (h.quantidade || 0), 0)}`));
    const l = el('div', 'lista');
    grupo.forEach((h) => {
      const item = el('div', 'item clicavel');
      item.appendChild(selo('animal', AREA.animal, 30));
      const c = el('div', 'corpo');
      c.appendChild(el('div', 'titulo', (h.quantidade > 1 ? `${h.quantidade}× ` : '') + h.nome_popular));
      const sub = [h.nome_cientifico, h.categoria && rotuloDe(CATEGORIAS, h.categoria), h.local_no_aquario].filter(Boolean).join(' · ');
      if (sub) c.appendChild(el('div', 'sub', sub));
      if (h.status !== 'ativo') c.appendChild(pilula(h.status === 'morto' ? `morreu ${dataCurta(h.removido_em)}` : `saiu ${dataCurta(h.removido_em)}`, '#8B95A3'));
      else if (h.revisar) c.appendChild(pilula('confirmar dados', ZONA.atencao));
      item.appendChild(c);
      item.onclick = () => folhaHabitante(h);
      l.appendChild(item);
    });
    caixa.appendChild(l);
  });
}

function folhaHabitante(h) {
  abrirFolha(h ? h.nome_popular : 'Novo habitante', (c) => {
    let tipo = h ? h.tipo : 'coral';
    let categoria = h ? h.categoria : 'LPS';
    const linhaTipo = el('div', 'chips');
    const linhaCat = el('div', 'chips');
    c.appendChild(linhaTipo);
    c.appendChild(linhaCat);
    const redesenhar = () => {
      chips(linhaTipo, TIPOS_HAB, tipo, (v) => { tipo = v; redesenhar(); });
      linhaCat.style.display = tipo === 'coral' ? '' : 'none';
      chips(linhaCat, CATEGORIAS, categoria, (v) => { categoria = v; redesenhar(); });
    };
    redesenhar();

    const nome = campo(c, 'Nome popular', h && h.nome_popular, 'text', 'ex.: Torch verde');
    const qtd = campo(c, 'Quantidade', h ? h.quantidade : 1, 'numero');
    const cient = campo(c, 'Nome científico (opcional)', h && h.nome_cientifico, 'text', 'ex.: Euphyllia glabrescens');
    const local = campo(c, 'Local no aquário (opcional)', h && h.local_no_aquario, 'text', 'ex.: rocha da esquerda, meia altura');
    const origem = campo(c, 'Origem (opcional)', h && h.origem, 'text', 'loja, amigo, frag...');
    const data = campo(c, 'Entrou no aquário em', h ? h.adicionado_em : hojeIso(), 'date');
    const obs = campo(c, 'Observação (opcional)', h && h.observacao, 'text');

    const salvar = el('button', 'primary', h && h.revisar ? 'Salvar e confirmar' : 'Salvar');
    salvar.onclick = () => {
      const q = parseInt(qtd.value, 10);
      if (!texto(nome)) { toast('Falta o nome.', true); return; }
      if (!Number.isFinite(q) || q < 0) { toast('Quantidade inválida.', true); return; }
      salvarLinha('livestock', h, {
        tipo, nome_popular: texto(nome), quantidade: q,
        categoria: tipo === 'coral' ? categoria : null,
        nome_cientifico: texto(cient), local_no_aquario: texto(local), origem: texto(origem),
        adicionado_em: data.value || null, observacao: texto(obs),
        revisar: false, // editar pelo Lucas confirma o item, como no tablet
      }, h ? 'Atualizado.' : 'Adicionado ao aquário.');
    };
    c.appendChild(salvar);

    if (!h) return;
    // Saida e morte mudam o status e guardam a data: o habitante sai da lista mas fica no
    // historico, como no tablet. Apagar e so para cadastro errado.
    const linha = el('div', 'linha-botoes');
    if (h.status === 'ativo') {
      [['removido', 'Saiu do aquário'], ['morto', 'Morreu']].forEach(([st, rot]) => {
        const b = el('button', null, rot);
        b.onclick = () => {
          if (!confirm(`${h.nome_popular}: ${rot.toLowerCase()} hoje? Sai da lista e fica no histórico.`)) return;
          salvarLinha('livestock', h, { status: st, removido_em: hojeIso() }, 'Movido para o histórico.');
        };
        linha.appendChild(b);
      });
    } else {
      const b = el('button', null, 'Voltar para o aquário');
      b.onclick = () => salvarLinha('livestock', h, { status: 'ativo', removido_em: null }, 'De volta ao aquário.');
      linha.appendChild(b);
    }
    c.appendChild(linha);
    const apagarB = el('button', 'ghost perigo', 'Apagar cadastro (só se foi engano)');
    apagarB.onclick = async () => {
      if (!confirm('Apagar este cadastro? Para saída ou morte use os botões de cima, que guardam o histórico.')) return;
      ocupado(true);
      try { await apagar('livestock', h.id); await recarregar(); fecharFolha(); toast('Apagado.'); }
      catch (err) { toast(err.message, true); } finally { ocupado(false); }
    };
    c.appendChild(apagarB);
  });
}

// ---------------------------------------------------------------- equipamentos

function desenharEquipamentos(caixa) {
  const topo = el('div', 'linha-botoes');
  const novo = el('button', 'primary', 'Adicionar equipamento');
  novo.onclick = () => folhaEquipamento(null);
  topo.appendChild(novo);
  caixa.appendChild(topo);
  const ativos = dados.equipamentos.filter((e) => e.ativo);
  if (!ativos.length) { caixa.appendChild(el('div', 'vazio', 'Nenhum equipamento cadastrado.')); return; }
  const l = el('div', 'lista');
  ativos.sort((a, b) => TIPOS_EQUIP.findIndex((t) => t[0] === a.tipo) - TIPOS_EQUIP.findIndex((t) => t[0] === b.tipo)).forEach((e) => {
    const item = el('div', 'item clicavel');
    item.appendChild(selo('manutencao', AREA.equip, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', [e.marca, e.modelo].filter(Boolean).join(' ') || rotuloDe(TIPOS_EQUIP, e.tipo)));
    c.appendChild(el('div', 'sub', [rotuloDe(TIPOS_EQUIP, e.tipo), e.instalado_em && `desde ${dataCurta(e.instalado_em)}`].filter(Boolean).join(' · ')));
    if (e.configuracao) c.appendChild(el('div', 'sub', e.configuracao));
    item.appendChild(c);
    item.onclick = () => folhaEquipamento(e);
    l.appendChild(item);
  });
  caixa.appendChild(l);
}

function folhaEquipamento(e) {
  abrirFolha(e ? 'Editar equipamento' : 'Novo equipamento', (c) => {
    let tipo = e ? e.tipo : 'outro';
    const linhaTipo = el('div', 'chips');
    c.appendChild(linhaTipo);
    const r = () => chips(linhaTipo, TIPOS_EQUIP, tipo, (v) => { tipo = v; r(); });
    r();
    const marca = campo(c, 'Marca', e && e.marca);
    const modelo = campo(c, 'Modelo', e && e.modelo);
    const desde = campo(c, 'Instalado em', e && e.instalado_em, 'date');
    const conf = campo(c, 'Configuração (opcional)', e && e.configuracao, 'text', 'ex.: 60% branco, 100% azul, 10h às 20h');
    const obs = campo(c, 'Observação (opcional)', e && e.observacao);
    const s = el('button', 'primary', 'Salvar');
    s.onclick = () => salvarLinha('equipment', e, {
      tipo, marca: texto(marca), modelo: texto(modelo), instalado_em: desde.value || null,
      configuracao: texto(conf), observacao: texto(obs),
    });
    c.appendChild(s);
    if (e) {
      const tirar = el('button', 'ghost perigo', 'Tirar de uso');
      tirar.onclick = () => salvarLinha('equipment', e, { ativo: false }, 'Fora de uso.');
      c.appendChild(tirar);
    }
  });
}

// ---------------------------------------------------------------- produtos

function desenharProdutos(caixa) {
  const topo = el('div', 'linha-botoes');
  const novo = el('button', 'primary', 'Adicionar produto');
  novo.onclick = () => folhaProduto(null);
  topo.appendChild(novo);
  caixa.appendChild(topo);
  caixa.appendChild(el('div', 'sub', `Doses calculadas para ${numeroCurto(volume())} L, a litragem de dosagem do aquário.`));
  if (!dados.produtos.length) { caixa.appendChild(el('div', 'vazio', 'Nenhum produto cadastrado.')); return; }
  const l = el('div', 'lista');
  dados.produtos.forEach((p) => {
    const item = el('div', 'item clicavel');
    item.appendChild(selo('dose', AREA.dose, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', p.nome));
    c.appendChild(el('div', 'sub', [p.marca, rotuloDe(TIPOS_PROD, p.tipo)].filter(Boolean).join(' · ')));
    item.appendChild(c);
    // a dose para o volume do aquario, que e o numero que se usa na hora
    if (p.dose_quantidade != null && p.dose_base_litros) {
      const q = Number(p.dose_quantidade) * volume() / Number(p.dose_base_litros);
      item.appendChild(el('div', 'valor-linha', `${numeroCurto(q)} ${p.dose_unidade || ''}`.trim()));
    }
    item.onclick = () => folhaProduto(p);
    l.appendChild(item);
  });
  caixa.appendChild(l);
}

const numeroCurto = (v) => (Math.round(v * 100) / 100).toLocaleString('pt-BR');

function folhaProduto(p) {
  abrirFolha(p ? 'Editar produto' : 'Novo produto', (c) => {
    let tipo = p ? p.tipo : 'suplemento';
    let unidade = p ? p.dose_unidade || 'ml' : 'ml';
    const linhaTipo = el('div', 'chips');
    c.appendChild(linhaTipo);
    const nome = campo(c, 'Nome', p && p.nome, 'text', 'ex.: Supreme Goni');
    const marca = campo(c, 'Marca', p && p.marca, 'text', 'ex.: Chasque');
    c.appendChild(el('div', 'campo-rot', 'Dose do rótulo'));
    const qtd = campo(c, 'Quantidade', p && p.dose_quantidade, 'numero');
    const linhaUn = el('div', 'chips');
    c.appendChild(linhaUn);
    const base = campo(c, 'Para quantos litros', p && p.dose_base_litros, 'numero', 'ex.: 100');
    const obs = campo(c, 'Observação (opcional)', p && p.observacao);
    const r = () => {
      chips(linhaTipo, TIPOS_PROD, tipo, (v) => { tipo = v; r(); });
      chips(linhaUn, UNIDADES_DOSE, unidade, (v) => { unidade = v; r(); });
    };
    r();
    const s = el('button', 'primary', 'Salvar');
    s.onclick = () => {
      if (!texto(nome)) { toast('Falta o nome.', true); return; }
      const q = numero(qtd.value);
      const b = numero(base.value);
      if (b != null && b <= 0) { toast('Os litros da dose têm que ser maiores que zero.', true); return; }
      salvarLinha('products', p, {
        nome: texto(nome), marca: texto(marca), tipo, dose_quantidade: q, dose_unidade: q != null ? unidade : null,
        dose_base_litros: b, observacao: texto(obs),
      });
    };
    c.appendChild(s);
    if (p) {
      const tirar = el('button', 'ghost perigo', 'Parei de usar');
      tirar.onclick = () => salvarLinha('products', p, { ativo: false }, 'Fora da lista.');
      c.appendChild(tirar);
    }
  });
}

// ---------------------------------------------------------------- calculadora de sal

/*
 * Mesmas contas do tablet (Calculadoras.kt), lineares em (densidade - 1):
 *   agua nova:  gramas = litros x g/L x (alvo - 1) / 0,025
 *   balde fraco: gramas = litros x g/L x (alvo - medido) / 0,025
 *   balde forte: agua de osmose = litros x ((medido - 1) / (alvo - 1) - 1)
 * O "g/L do seu sal" fica guardado neste aparelho.
 */
function desenharSal(caixa) {
  let gpl = 37;
  try { gpl = Number(localStorage.getItem('sal_gpl')) || 37; } catch (_) { /* navegador sem armazenamento */ }

  const cfg = el('div', 'card');
  const g = campo(cfg, 'Seu sal para 1,025 (g/L)', String(gpl).replace('.', ','), 'numero');
  cfg.appendChild(el('div', 'sub', 'Fica salvo no celular. Para o seu sal, 35 a 39 g por litro de água de osmose.'));
  caixa.appendChild(cfg);

  const nova = el('div', 'card');
  nova.appendChild(el('div', 'eyebrow', 'Preparar água nova'));
  const litros = campo(nova, 'Água de osmose (L)', '15', 'numero');
  const alvo = campo(nova, 'Salinidade alvo', '1,025', 'numero');
  const r1 = el('div', 'resultado');
  nova.appendChild(r1);
  caixa.appendChild(nova);

  const balde = el('div', 'card');
  balde.appendChild(el('div', 'eyebrow', 'Corrigir balde pronto'));
  const vol = campo(balde, 'Volume no balde (L)', '15', 'numero');
  const med = campo(balde, 'Mediu', '', 'numero', 'ex.: 22 ou 1,022');
  const quer = campo(balde, 'Quer', '1,025', 'numero');
  balde.appendChild(el('div', 'sub', 'Pode digitar só o final: 22 vira 1,022.'));
  const r2 = el('div', 'resultado');
  balde.appendChild(r2);
  caixa.appendChild(balde);

  const res = (alvoEl, grande, pequeno) => {
    alvoEl.innerHTML = '';
    if (!grande) return;
    alvoEl.appendChild(el('div', 'resultado-grande', grande));
    if (pequeno) alvoEl.appendChild(el('div', 'sub', pequeno));
  };
  const fmt3 = (v) => v.toFixed(3).replace('.', ',');

  const calcular = () => {
    const gv = numero(g.value);
    if (gv != null && gv >= 20 && gv <= 50) { try { localStorage.setItem('sal_gpl', String(gv)); } catch (_) { /* sem armazenamento */ } }
    const l = numero(litros.value);
    const t = salinidade(numero(alvo.value));
    if (gv && l && t && t > 1) res(r1, `${Math.round(l * gv * (t - 1) / 0.025)} g de sal`, `${numeroCurto(l)} L a ${numeroCurto(gv)} g/L para ${fmt3(t)}`);
    else res(r1, null);

    const v = numero(vol.value);
    const m = salinidade(numero(med.value));
    const q = salinidade(numero(quer.value));
    if (!(gv && v && m && q && m > 1 && q > 1)) { res(r2, null); return; }
    if (Math.abs(m - q) < 0.0005) res(r2, 'Já está no ponto', 'Diferença menor que 0,0005.');
    else if (m < q) res(r2, `Adicione ${Math.round(v * gv * (q - m) / 0.025)} g de sal`, 'Misture bem e meça de novo depois de uns 30 min.');
    else res(r2, `Adicione ${numeroCurto(v * ((m - 1) / (q - 1) - 1))} L de água de osmose`, `O balde vai ficar com ${numeroCurto(v * (m - 1) / (q - 1))} L.`);
  };
  [g, litros, alvo, vol, med, quer].forEach((i) => { i.oninput = calcular; });
  calcular();
}
