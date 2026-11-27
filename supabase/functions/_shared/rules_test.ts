import assert from "node:assert/strict";
const assertEquals = (a: unknown, b: unknown) => assert.deepStrictEqual(a, b);
import { compliance, criticalAlerts, doseFor, fmt, formatDose, healthScore, isNight, local, localToMs, occurrencesOn, reminderText, resumoDaNoite, textoDaNoite } from "./rules.ts";

const at = (date: string, time = "12:00") => localToMs(date, time);
const iso = (t: number) => new Date(t).toISOString();
const ranges = [
  { parametro: "kh", ideal_min: 7.5, ideal_max: 9, alerta_min: 7, alerta_max: 10, unidade: "dKH" },
  { parametro: "ca", ideal_min: 400, ideal_max: 450, alerta_min: 380, alerta_max: 480, unidade: "ppm" },
  { parametro: "mg", ideal_min: 1250, ideal_max: 1400, alerta_min: 1200, alerta_max: 1450, unidade: "ppm" },
  { parametro: "salinidade", ideal_min: 1.024, ideal_max: 1.026, alerta_min: 1.023, alerta_max: 1.027, unidade: "SG" },
  { parametro: "no3", ideal_min: 2, ideal_max: 10, alerta_min: 0.5, alerta_max: 25, unidade: "ppm" },
];
const now = at("2026-11-16");
const t = (p: string, v: number, daysAgo = 1, id = p) => ({ id, parametro: p, valor: v, medido_em: iso(now - daysAgo * 86_400_000) });
const goni = { id: "g", dose_quantidade: 1, dose_unidade: "colher", dose_base_litros: 400 };
const amino = { id: "a", dose_quantidade: 2, dose_unidade: "gotas", dose_base_litros: 100 };
const rGoni = { id: "r1", titulo: "Goni Supreme", tipo: "dosagem", product_id: "g", recorrencia: "diaria", horario: "20:00:00", ativa: true };
const rAmino = { id: "r2", titulo: "Amino Coral", tipo: "dosagem", product_id: "a", recorrencia: "dias_da_semana", dias_semana: [1, 3, 5], horario: "20:00:00", ativa: true };

Deno.test("fuso de Sao Paulo", () => {
  assertEquals(iso(at("2026-11-16", "20:00")), "2026-11-16T23:00:00.000Z");
  assertEquals(local(Date.parse("2026-11-16T02:30:00Z")), { date: "2026-11-15", hour: 23, minute: 30, dow: 7 });
  assertEquals(local(at("2026-11-16")).dow, 1); // segunda
});

Deno.test("rotinas do dia e aviso agrupado iguais aos do app", () => {
  const seg = occurrencesOn([rGoni, rAmino], [], "2026-11-16");
  assertEquals(seg.map((o) => o.routine.titulo), ["Amino Coral", "Goni Supreme"]);
  assertEquals(reminderText(seg, [goni, amino], 100), "20h: Amino Coral 2 gotas, Goni Supreme 1/4 de colher");
  assertEquals(occurrencesOn([rGoni, rAmino], [], "2026-11-17").map((o) => o.routine.titulo), ["Goni Supreme"]);
});

Deno.test("doses para 125 L e casas extras iguais as do app", () => {
  const f = (p: Record<string, unknown>) => formatDose(doseFor(p, 125), p.dose_unidade as string);
  assertEquals([f(goni), f(amino), f({ dose_quantidade: 1, dose_unidade: "g", dose_base_litros: 200 }), f({ dose_quantidade: 1, dose_unidade: "gotas" })],
    ["~1/3 de colher", "2,5 gotas", "~0,6 g", "1 gota"]);
  assertEquals([fmt("salinidade", 1.025), fmt("salinidade", 1.0225), fmt("kh", 7.85), fmt("ca", 500)], ["1,025", "1,0225", "7,85", "500"]);
});

Deno.test("adiamento e cumprimento", () => {
  const due = at("2026-11-16", "20:00");
  const adiado = { routine_id: "r1", previsto_para: iso(due), status: "adiado", updated_at: iso(due + 5 * 60_000) };
  const o = occurrencesOn([rGoni], [adiado], "2026-11-16")[0];
  assertEquals([o.state, o.effectiveDueAt], ["adiado", due + 35 * 60_000]);
  const logs = [
    { routine_id: "r1", previsto_para: iso(at("2026-11-16", "20:00")), status: "feito", updated_at: iso(now) },
    { routine_id: "r1", previsto_para: iso(at("2026-11-15", "20:00")), status: "feito", updated_at: iso(now) },
    { routine_id: "r1", previsto_para: iso(at("2026-11-14", "20:00")), status: "pulado", updated_at: iso(now) },
  ];
  assertEquals(compliance([rGoni], logs, at("2026-11-16", "21:00")), [2, 7]);
});

Deno.test("rotina por intervalo vencida continua aberta", () => {
  const carvao = { id: "c", titulo: "Carvão", recorrencia: "a_cada_n_semanas", intervalo_n: 4, horario: "19:00", ativa: true, proxima_em: iso(at("2026-11-10", "19:00")) };
  assertEquals(occurrencesOn([carvao], [], "2026-11-16")[0].dueAt, at("2026-11-10", "19:00"));
  assertEquals(occurrencesOn([{ ...carvao, proxima_em: null }], [], "2026-11-16").length, 0);
});

Deno.test("atividade de uma vez so", () => {
  const muda = { id: "m", titulo: "Muda de coral", recorrencia: "unica", horario: "19:00", ativa: true, proxima_em: iso(at("2026-11-19", "19:00")) };
  assertEquals(occurrencesOn([muda], [], "2026-11-18").length, 0);
  assertEquals(occurrencesOn([muda], [], "2026-11-19")[0].dueAt, at("2026-11-19", "19:00"));
  assertEquals(occurrencesOn([muda], [], "2026-11-21").length, 1); // nao feita: continua atrasada
  const feito = [{ routine_id: "m", previsto_para: muda.proxima_em, status: "feito", feito_em: iso(at("2026-11-20", "10:00")), updated_at: iso(at("2026-11-20", "10:00")) }];
  assertEquals([occurrencesOn([muda], feito, "2026-11-20").length, occurrencesOn([muda], feito, "2026-11-21").length], [1, 0]);
});

Deno.test("nota de saude igual a do app", () => {
  const bons = [t("kh", 8), t("ca", 420), t("mg", 1300), t("salinidade", 1.025)];
  const tpa = (d: number) => [{ feita_em: iso(now - d * 86_400_000) }];
  assertEquals(healthScore(bons, ranges, tpa(2), [], [], [], now).score, 100);
  assertEquals(healthScore(bons, ranges, tpa(17), [], [], [], now).score, 80);
  const ruins = [t("kh", 7.2), t("ca", 370), t("mg", 1300, 9), t("salinidade", 1.025), t("no3", 40)];
  const h = healthScore(ruins, ranges, tpa(12), [], [], [{}, {}, {}, {}], now);
  assertEquals([h.score, h.items.length], [39, 6]);
  const falta = healthScore([t("kh", 8)], ranges, [], [], [], [], now);
  assertEquals([falta.score, falta.missing], [null, ["medir Cálcio", "medir Magnésio", "medir Salinidade", "registrar uma TPA"]]);
});

Deno.test("alertas criticos e modo noite", () => {
  const c = criticalAlerts([t("kh", 6.5), t("ca", 420), t("mg", 1100, 10)], ranges, now);
  assertEquals(c.map((x) => x.key), ["crit|kh|kh"]); // Mg velho demais nao dispara
  const swing = criticalAlerts([t("kh", 8, 1, "x1"), t("kh", 9.3, 0.5, "x2")], ranges, now);
  assertEquals(swing.map((x) => x.key), ["crit|kh_swing|x2"]);
  assertEquals([isNight(3, 0, 7), isNight(7, 0, 7), isNight(23, 22, 6), isNight(12, 22, 6)], [true, false, true, false]);
});

Deno.test("resumo das 23h30: o que falta do dia e a TPA arrastada, sem dose de ontem", () => {
  const tpa = { id: "t", titulo: "TPA", tipo: "tpa", recorrencia: "dias_da_semana", dias_semana: [4], horario: "19:00", com_horario: false, ativa: true };
  const dose = { id: "d", titulo: "Goni Supreme", tipo: "dosagem", product_id: "g", recorrencia: "diaria", horario: "22:30", com_horario: false, ativa: true };
  const prods = [{ id: "g", nome: "Goni", dose_quantidade: 1, dose_base_litros: 100, dose_unidade: "ml" }];
  // sexta 04/12, 23h30: a dose de hoje esta aberta e a TPA de quinta 03/12 nao foi feita
  const now = at("2026-12-04", "23:30");
  assertEquals(resumoDaNoite([tpa, dose], [], prods, 100, now), "Falta 2 tarefas de hoje: Goni Supreme (1 ml), TPA");
  // TPA de quinta marcada: so a dose
  const logs = [{ routine_id: "t", previsto_para: iso(at("2026-12-03", "19:00")), status: "feito", updated_at: iso(now) }];
  assertEquals(resumoDaNoite([tpa, dose], logs, prods, 100, now), "Falta 1 tarefa de hoje: Goni Supreme (1 ml)");
  const feita = [...logs, { routine_id: "d", previsto_para: iso(at("2026-12-04", "22:30")), status: "feito", updated_at: iso(now) }];
  assertEquals(resumoDaNoite([tpa, dose], feita, prods, 100, now), null);
});

Deno.test("mudar horario ou dia da rotina nao faz voltar o que ja foi feito", () => {
  const testes = { id: "x", titulo: "Testes", tipo: "teste", recorrencia: "dias_da_semana", dias_semana: [3], horario: "15:00", ativa: true };
  // feito na quarta 25/11 quando a rotina era as 18h
  const logs = [{ routine_id: "x", previsto_para: iso(at("2026-11-25", "18:00")), status: "feito", updated_at: iso(at("2026-11-25", "18:30")) }];
  assertEquals(occurrencesOn([testes], logs, "2026-11-25")[0].open, false);
  // rotina passada para quinta: a quinta 26/11 ja conta como feita, a quinta seguinte nao
  const quinta = { ...testes, dias_semana: [4] };
  assertEquals(occurrencesOn([quinta], logs, "2026-11-26")[0].open, false);
  assertEquals(occurrencesOn([quinta], logs, "2026-12-03")[0].open, true);
  // diaria: o feito de ontem nao vale para hoje
  const dose = { id: "x", titulo: "Dose", tipo: "dosagem", recorrencia: "diaria", horario: "22:30", ativa: true };
  assertEquals(occurrencesOn([dose], logs, "2026-11-26")[0].open, true);
  assertEquals(occurrencesOn([dose], logs, "2026-11-25")[0].open, false);
});

Deno.test("aviso das 23h30: falta de hoje ou, tudo feito, o que vem amanha", () => {
  const dose = { id: "d", titulo: "Goni Supreme", tipo: "dosagem", product_id: "g", recorrencia: "diaria", horario: "22:30", com_horario: false, ativa: true };
  const testes = { id: "x", titulo: "Testes", tipo: "teste", recorrencia: "dias_da_semana", dias_semana: [6], horario: "15:00", ativa: true };
  const prods = [{ id: "g", nome: "Goni", dose_quantidade: 1, dose_base_litros: 100, dose_unidade: "ml" }];
  const now = at("2026-12-04", "23:30"); // sexta; sabado tem teste as 15h
  // o teste do sabado anterior foi feito, senao viria arrastado
  const sab = { routine_id: "x", previsto_para: iso(at("2026-11-28", "15:00")), status: "feito", updated_at: iso(now) };
  assertEquals(textoDaNoite([dose, testes], [sab], prods, 100, now), "Falta 1 tarefa de hoje: Goni Supreme (1 ml)");
  const feita = [sab, { routine_id: "d", previsto_para: iso(at("2026-12-04", "22:30")), status: "feito", updated_at: iso(now) }];
  assertEquals(textoDaNoite([dose, testes], feita, prods, 100, now), "Tudo feito hoje. Amanhã: Testes às 15h, Goni Supreme (1 ml).");
  assertEquals(textoDaNoite([], [], prods, 100, now), "Tudo feito hoje. Amanhã está livre.");
});
