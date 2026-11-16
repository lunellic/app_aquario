// Carrega os dados do usuario e monta o contexto resumido enviado a IA (spec 11).
import type { SupabaseClient } from "npm:@supabase/supabase-js@2";
import { compliance, DAY_MS, doseFor, fmt, formatDose, healthScore, LABEL, local, ms, numShort, type Row } from "./rules.ts";

export interface Ctx {
  tank: Row | null;
  ranges: Row[];
  tests: Row[];
  changes: Row[];
  products: Row[];
  dosings: Row[];
  routines: Row[];
  logs: Row[];
  livestock: Row[];
  cases: Row[];
  entries: Row[];
  equipment: Row[];
}

/** Com o client do usuario o RLS ja filtra; com service role passe userId. Ultimos 90 dias de registros. */
export async function loadCtx(db: SupabaseClient, userId: string | null, now: number): Promise<Ctx> {
  const since = new Date(now - 90 * DAY_MS).toISOString();
  const q = (table: string, dateCol?: string) => {
    let s = db.from(table).select("*").is("deleted_at", null);
    if (userId) s = s.eq("user_id", userId);
    if (dateCol) s = s.gte(dateCol, since).order(dateCol, { ascending: true });
    return s.limit(5000);
  };
  const res = await Promise.all([
    q("tank_config"), q("param_ranges"), q("water_tests", "medido_em"), q("water_changes", "feita_em"), q("products"),
    q("dosings", "dosado_em"), q("routines"), q("routine_logs", "previsto_para"), q("livestock"), q("health_cases"),
    q("health_entries", "registrado_em"), q("equipment"),
  ]);
  for (const r of res) if (r.error) throw new Error(`banco: ${r.error.message}`);
  const [tank, ranges, tests, changes, products, dosings, routines, logs, livestock, cases, entries, equipment] = res.map((r) => (r.data ?? []) as Row[]);
  return { tank: tank[0] ?? null, ranges, tests, changes, products, dosings, routines, logs, livestock, cases, entries, equipment };
}

const day = (ts: string) => {
  const l = local(ms(ts));
  return `${l.date.slice(8, 10)}/${l.date.slice(5, 7)} ${String(l.hour).padStart(2, "0")}h`;
};

/** Texto compacto. Para um aquario com meses de dados fica na casa de poucos milhares de tokens. */
export function summarize(c: Ctx, now: number): string {
  const vol = Number(c.tank?.volume_dosagem_l ?? 100);
  const out: string[] = [];
  out.push(`AQUÁRIO: ${c.tank?.nome ?? "Luluco Reef"}; marinho LPS dominante; volume de dosagem ${numShort(vol)} L; sal ${c.tank?.sal_atual ?? "não informado"}.` +
    (c.tank?.observacoes ? ` Obs.: ${c.tank.observacoes}` : ""));
  out.push(`AGORA: ${day(new Date(now).toISOString())} (horário de Brasília).`);

  out.push("FAIXAS (ideal | alerta): " + c.ranges.map((r) =>
    `${LABEL[r.parametro] ?? r.parametro} ${fmt(r.parametro, r.ideal_min)}-${fmt(r.parametro, r.ideal_max)} | <${fmt(r.parametro, r.alerta_min)} ou >${fmt(r.parametro, r.alerta_max)} ${r.unidade}`).join("; "));

  const byParam = new Map<string, Row[]>();
  for (const t of c.tests) byParam.set(t.parametro, [...(byParam.get(t.parametro) ?? []), t]);
  out.push("TESTES (últimos 90 dias, data hora valor):");
  if (!byParam.size) out.push("  nenhum teste registrado.");
  for (const [p, list] of byParam) out.push(`  ${LABEL[p] ?? p}: ` + list.slice(-60).map((t) => `${day(t.medido_em)} ${fmt(p, t.valor)}`).join(", "));

  out.push("TPAs (últimos 90 dias): " + (c.changes.length ? c.changes.slice(-40).map((w) => `${day(w.feita_em)} ${numShort(w.litros)} L`).join(", ") : "nenhuma registrada."));

  out.push("PRODUTOS E DOSE PARA O VOLUME ATUAL: " + c.products.filter((p) => p.ativo).map((p) =>
    `${p.nome}${p.marca ? ` (${p.marca})` : ""}: ${formatDose(doseFor(p, vol), p.dose_unidade)}`).join("; "));

  const doseCount = new Map<string, { n30: number; last: string }>();
  for (const d of c.dosings) {
    const e = doseCount.get(d.product_id) ?? { n30: 0, last: d.dosado_em };
    if (now - ms(d.dosado_em) <= 30 * DAY_MS) e.n30++;
    e.last = d.dosado_em;
    doseCount.set(d.product_id, e);
  }
  out.push("DOSES REGISTRADAS (30 dias): " + ([...doseCount].map(([id, e]) =>
    `${c.products.find((p) => p.id === id)?.nome ?? "produto"} ${e.n30}x (última ${day(e.last)})`).join("; ") || "nenhuma."));

  const [done, expected] = compliance(c.routines, c.logs, now, 30);
  out.push(`ROTINAS ATIVAS: ` + c.routines.filter((r) => r.ativa).map((r) => `${r.titulo} (${r.recorrencia}${r.dias_semana ? " " + r.dias_semana.join(",") : ""}${r.intervalo_n ? " n=" + r.intervalo_n : ""} ${String(r.horario).slice(0, 5)})`).join("; "));
  out.push(`CUMPRIMENTO DE ROTINAS (30 dias): ${done} de ${expected} feitas.`);

  const active = c.livestock.filter((l) => l.status === "ativo");
  out.push("INVENTÁRIO ATIVO: " + (active.map((l) => `${l.quantidade}x ${l.nome_popular}${l.nome_cientifico ? ` [${l.nome_cientifico}]` : ""} (${l.tipo}${l.categoria ? "/" + l.categoria : ""}${l.local_no_aquario ? ", " + l.local_no_aquario : ""})`).join("; ") || "vazio."));

  const open = c.cases.filter((k) => !k.fechado_em);
  out.push("CASOS DE SAÚDE ABERTOS: " + (open.length ? "" : "nenhum."));
  for (const k of open) {
    const who = c.livestock.find((l) => l.id === k.livestock_id)?.nome_popular ?? "aquário todo";
    out.push(`  [${k.tipo}] ${k.titulo} (${who}), aberto em ${day(k.aberto_em)}. ` + c.entries.filter((e) => e.case_id === k.id).slice(-8).map((e) => `${day(e.registrado_em)}: ${e.texto ?? "(foto)"}`).join(" | "));
  }

  out.push("EQUIPAMENTOS: " + (c.equipment.filter((e) => e.ativo).map((e) => `${e.tipo} ${[e.marca, e.modelo].filter(Boolean).join(" ")}${e.configuracao ? ` (${e.configuracao})` : ""}`).join("; ") || "não cadastrados."));

  const h = healthScore(c.tests, c.ranges, c.changes, c.routines, c.logs, c.cases, now);
  out.push(`NOTA DE SAÚDE (regra fixa do app): ${h.score ?? "sem dados suficientes"}` + (h.items.length ? `; descontos: ${h.items.map((i) => `${i.label} (${numShort(i.points)})`).join("; ")}` : "") + (h.missing.length ? `; falta: ${h.missing.join(", ")}` : ""));
  return out.join("\n");
}
