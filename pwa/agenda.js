/*
 * Agenda: o mes em grade, o que esta marcado em cada dia e o que vem pela frente.
 *
 * Diferente do "Hoje" do Painel, aqui a rotina aparece so no dia em que esta marcada: a grade e o
 * calendario, nao a lista de pendencia. A pendencia arrastada continua no Painel, que e onde ela
 * tem que cobrar.
 *
 * Da para marcar atividade em qualquer dia, inclusive nos proximos. Ela entra como rotina de uma
 * vez so (recorrencia "unica", com a data em proxima_em), exatamente como o tablet faz.
 */
'use strict';

const DIAS_CAB = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB'];
const TIPOS_ATIV = [
  ['manutencao', 'Manutenção'], ['tpa', 'TPA'], ['teste', 'Teste'], ['dosagem', 'Dosagem'], ['outro', 'Outro'],
];

let agMes = null;
let agDia = null;

const chaveDia = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const mesmoDia = (a, b) => a && b && chaveDia(a) === chaveDia(b);

/**
 * O que esta marcado em `dia`. Rotina com data (intervalo ou uma vez so) aparece so no dia marcado;
 * diaria e dias da semana, no dia que cair. Mesma regra do agendaOn do tablet.
 */
function agendaDoDia(dia) {
  const logs = {};
  dados.logs.filter((l) => !l.deleted_at).forEach((l) => {
    const k = `${l.routine_id}|${new Date(l.previsto_para).getTime()}`;
    if (!logs[k] || new Date(l.updated_at) > new Date(logs[k].updated_at)) logs[k] = l;
  });
  return dados.rotinas
    .map((r) => {
      if (!r.ativa || r.deleted_at) return null;
      let due;
      if (r.recorrencia === 'diaria' || r.recorrencia === 'dias_da_semana') due = vencimento(r, dia);
      else if (r.proxima_em && mesmoDia(new Date(r.proxima_em), dia)) due = new Date(r.proxima_em).getTime();
      if (due == null) return null;
      return { rotina: r, due, log: logs[`${r.id}|${due}`] || null };
    })
    .filter(Boolean)
    .sort((a, b) => a.due - b.due);
}

function desenharAgenda(caixa) {
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  if (!agMes) agMes = new Date(hoje.getFullYear(), hoje.getMonth(), 1);
  if (!agDia) agDia = new Date(hoje);

  // cabecalho do mes
  const topo = el('div', 'nav-mes');
  const ant = el('button', 'mini', '‹');
  ant.onclick = () => { agMes = new Date(agMes.getFullYear(), agMes.getMonth() - 1, 1); desenharMais(); };
  const dep = el('button', 'mini', '›');
  dep.onclick = () => { agMes = new Date(agMes.getFullYear(), agMes.getMonth() + 1, 1); desenharMais(); };
  const t = el('div', 'nav-mes-txt');
  const nomeMes = agMes.toLocaleDateString('pt-BR', { month: 'long', year: 'numeric' });
  const bt = el('button', 'mes-pilula', nomeMes.charAt(0).toUpperCase() + nomeMes.slice(1));
  bt.onclick = () => { agMes = new Date(hoje.getFullYear(), hoje.getMonth(), 1); agDia = new Date(hoje); desenharMais(); };
  t.appendChild(bt);
  topo.appendChild(ant);
  topo.appendChild(t);
  topo.appendChild(dep);
  caixa.appendChild(topo);

  // grade
  const grade = el('div', 'mes');
  DIAS_CAB.forEach((d, i) => {
    const c = el('div', 'mes-cab', d);
    if (i === 0 || i === 6) c.style.color = 'var(--fraco)';
    grade.appendChild(c);
  });
  const primeiro = new Date(agMes.getFullYear(), agMes.getMonth(), 1);
  const inicio = new Date(primeiro);
  inicio.setDate(1 - primeiro.getDay()); // domingo antes do dia 1
  for (let i = 0; i < 42; i++) {
    const d = new Date(inicio);
    d.setDate(inicio.getDate() + i);
    const foraDoMes = d.getMonth() !== agMes.getMonth();
    const cel = el('button', `mes-dia${foraDoMes ? ' fora' : ''}${mesmoDia(d, agDia) ? ' escolhido' : ''}${mesmoDia(d, hoje) ? ' hoje' : ''}`);
    cel.appendChild(el('span', 'mes-num', String(d.getDate())));
    const pontos = el('span', 'mes-pontos');
    const tipos = [...new Set(agendaDoDia(d).map((o) => o.rotina.tipo))].slice(0, 4);
    tipos.forEach((tp) => {
      const pt = el('span', 'mes-ponto');
      pt.style.background = areaDaRotina(tp)[1];
      pontos.appendChild(pt);
    });
    cel.appendChild(pontos);
    cel.onclick = () => { agDia = d; if (foraDoMes) agMes = new Date(d.getFullYear(), d.getMonth(), 1); desenharMais(); };
    grade.appendChild(cel);
  }
  caixa.appendChild(grade);

  // o dia escolhido
  const rotulo = agDia.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  const h = el('h3', null, mesmoDia(agDia, hoje) ? 'Hoje' : rotulo.charAt(0).toUpperCase() + rotulo.slice(1));
  caixa.appendChild(h);

  const itens = agendaDoDia(agDia);
  const lista = el('div', 'lista');
  if (!itens.length) lista.appendChild(el('div', 'vazio', 'Nada marcado neste dia.'));
  itens.forEach((o) => {
    const st = o.log ? o.log.status : 'pendente';
    const resolvido = st === 'feito' || st === 'pulado';
    const i = el('div', 'item');
    const [ic, cor] = areaDaRotina(o.rotina.tipo);
    i.appendChild(selo(ic, resolvido ? '#5B6573' : cor, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', `titulo${resolvido ? ' feito' : ''}`, o.rotina.titulo));
    const partes = [hhmm(o.due)];
    if (resolvido) partes.push(st);
    if (o.rotina.instrucao) partes.push(o.rotina.instrucao);
    c.appendChild(el('div', 'sub', partes.join(' · ')));
    i.appendChild(c);
    // so da para apagar o que foi criado como atividade de uma vez: rotina recorrente se mexe no tablet
    if (o.rotina.recorrencia === 'unica') {
      const x = el('button', 'mini', 'Remover');
      x.onclick = async () => {
        ocupado(true);
        try { await apagar('routines', o.rotina.id); await recarregar(); toast('Removida.'); }
        catch (e) { toast(e.message, true); } finally { ocupado(false); }
      };
      i.appendChild(x);
    }
    lista.appendChild(i);
  });
  caixa.appendChild(lista);

  const b = el('button', 'primary cheio', 'Marcar atividade neste dia');
  b.onclick = () => folhaAtividade(agDia);
  caixa.appendChild(b);

  // o que vem pela frente, para nao precisar abrir dia por dia
  caixa.appendChild(el('h3', null, 'Próximos 14 dias'));
  const prox = el('div', 'lista');
  const vistos = [];
  for (let i = 1; i <= 14; i++) {
    const d = new Date(hoje);
    d.setDate(hoje.getDate() + i);
    agendaDoDia(d).forEach((o) => vistos.push({ d, o }));
  }
  if (!vistos.length) prox.appendChild(el('div', 'vazio', 'Nada marcado nos próximos 14 dias.'));
  vistos.slice(0, 30).forEach(({ d, o }) => {
    const i = el('div', 'item');
    const [ic, cor] = areaDaRotina(o.rotina.tipo);
    i.appendChild(selo(ic, cor, 30));
    const c = el('div', 'corpo');
    c.appendChild(el('div', 'titulo', o.rotina.titulo));
    const quando = d.toLocaleDateString('pt-BR', { weekday: 'short', day: '2-digit', month: '2-digit' });
    c.appendChild(el('div', 'sub', `${quando} · ${hhmm(o.due)}`));
    i.appendChild(c);
    prox.appendChild(i);
  });
  caixa.appendChild(prox);
}

/** Atividade de uma vez so num dia: vira rotina com recorrencia "unica", como no tablet. */
function folhaAtividade(dia) {
  const rotulo = dia.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });
  abrirFolha(rotulo.charAt(0).toUpperCase() + rotulo.slice(1), (c) => {
    let tipo = 'manutencao';
    const titulo = document.createElement('input');
    titulo.type = 'text';
    titulo.placeholder = 'O que fazer neste dia';
    const hora = document.createElement('input');
    hora.type = 'time';
    hora.value = '19:00';
    const instr = document.createElement('input');
    instr.type = 'text';
    instr.placeholder = 'Observação (opcional)';
    const lt = el('div', 'chips');
    const pinta = () => chips(lt, TIPOS_ATIV, tipo, (v) => { tipo = v; pinta(); });
    pinta();

    const lh = el('label');
    lh.appendChild(el('span', null, 'Horário'));
    lh.appendChild(hora);
    [titulo, lt, lh, instr].forEach((x) => c.appendChild(x));

    const b = el('button', 'primary', 'Marcar');
    b.onclick = async () => {
      if (!titulo.value.trim()) { toast('O que fazer?', true); return; }
      ocupado(true);
      try {
        const [hh, mm] = (hora.value || '19:00').split(':').map(Number);
        const quando = new Date(dia.getFullYear(), dia.getMonth(), dia.getDate(), hh || 0, mm || 0);
        await inserir('routines', {
          titulo: titulo.value.trim(),
          tipo,
          recorrencia: 'unica',
          horario: `${String(hh || 0).padStart(2, '0')}:${String(mm || 0).padStart(2, '0')}:00`,
          proxima_em: quando.toISOString(),
          instrucao: instr.value.trim() || null,
          ativa: true,
        });
        await recarregar();
        fecharFolha();
        toast('Marcado.');
      } catch (err) { toast(`Não salvou: ${err.message}`, true); } finally { ocupado(false); }
    };
    c.appendChild(b);
  });
}
