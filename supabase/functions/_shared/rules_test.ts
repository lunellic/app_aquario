import assert from "node:assert/strict";
const assertEquals = (a: unknown, b: unknown) => assert.deepStrictEqual(a, b);
import { compliance, criticalAlerts, doseFor, fmt, formatDose, healthScore, isNight, local, localToMs, occurrencesOn, reminderText } from "./rules.ts";

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
