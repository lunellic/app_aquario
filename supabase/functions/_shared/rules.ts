// Regras fixas do Luluco Reef no servidor (espelham app/.../domain/*.kt). Sem IA.
// America/Sao_Paulo e UTC-3 fixo (sem horario de verao desde 2019).

export const TZ_OFFSET_MS = -3 * 3600_000;
export const DAY_MS = 86_400_000;
export const HOUR_MS = 3_600_000;
export const SNOOZE_MS = 30 * 60_000;

export const MAIN = ["kh", "ca", "mg", "salinidade"];
export const OPTIONAL = ["no3", "po4"];

export type Row = Record<string, any>;
export type Status = "ideal" | "atencao" | "critico";

export const LABEL: Record<string, string> = {
  kh: "KH", ca: "Cálcio", mg: "Magnésio", salinidade: "Salinidade", temperatura: "Temperatura", no3: "NO3", po4: "PO4",
};
const DECIMALS: Record<string, number> = { kh: 1, temperatura: 1, salinidade: 3, po4: 2, no3: 1, ca: 0, mg: 0 };

/** Casas padrao; uma a mais quando o valor registrado precisa (1,0225 / 7,85). */
export function fmt(p: string, v: number): string {
  const d = DECIMALS[p] ?? 1;
  const scaled = Number(v) * 10 ** d;
  return Number(v).toFixed(Math.abs(scaled - Math.round(scaled)) > 1e-6 ? d + 1 : d).replace(".", ",");
}

export function numShort(v: number): string {
  return String(Math.round(v * 1000) / 1000).replace(".", ",");
}

export function ms(ts: string | null | undefined): number {
  return ts ? Date.parse(ts) : 0;
}

/** Data e hora locais (Sao Paulo) de um instante. dow: 1 = segunda ... 7 = domingo. */
export function local(t: number) {
  const d = new Date(t + TZ_OFFSET_MS);
  const date = d.toISOString().slice(0, 10);
  return { date, hour: d.getUTCHours(), minute: d.getUTCMinutes(), dow: ((d.getUTCDay() + 6) % 7) + 1 };
}

export function localToMs(date: string, time: string): number {
  return Date.parse(`${date}T${time.slice(0, 5)}:00Z`) - TZ_OFFSET_MS;
}

export function addDays(date: string, n: number): string {
  return new Date(Date.parse(`${date}T00:00:00Z`) + n * DAY_MS).toISOString().slice(0, 10);
}

export function isNight(hour: number, start: number, end: number): boolean {
  if (start === end) return false;
  return start < end ? hour >= start && hour < end : hour >= start || hour < end;
}

export function statusOf(v: number, r: Row): Status {
  if (v < r.alerta_min || v > r.alerta_max) return "critico";
  if (v < r.ideal_min || v > r.ideal_max) return "atencao";
  return "ideal";
}

export function latest(tests: Row[], p: string): Row | null {
  let best: Row | null = null;
  for (const t of tests) if (t.parametro === p && (!best || ms(t.medido_em) > ms(best.medido_em))) best = t;
  return best;
}

// ---- rotinas ----

export interface Occurrence {
  routine: Row;
  dueAt: number;
  log: Row | null;
  state: "pendente" | "adiado" | "feito" | "pulado";
  effectiveDueAt: number;
  open: boolean;
}

export function dueOn(r: Row, date: string): number | null {
  if (!r.ativa || r.deleted_at) return null;
  const time = String(r.horario ?? "20:00");
  if (r.recorrencia === "diaria") return localToMs(date, time);
  if (r.recorrencia === "dias_da_semana") {
    const dow = local(localToMs(date, "12:00")).dow;
    return (r.dias_semana ?? []).includes(dow) ? localToMs(date, time) : null;
  }
  // por intervalo: proxima_em e a ancora; vence nela e segue aberta ate ser feita ou pulada
  if (!r.proxima_em) return null;
  const anchor = ms(r.proxima_em);
  return local(anchor).date <= date ? anchor : null;
}

export function occurrencesOn(routines: Row[], logs: Row[], date: string): Occurrence[] {
  const out: Occurrence[] = [];
  for (const r of routines) {
    const dueAt = dueOn(r, date);
    if (dueAt == null) continue;
    let log: Row | null = null;
    for (const l of logs) {
      if (l.deleted_at || l.routine_id !== r.id || ms(l.previsto_para) !== dueAt) continue;
      if (!log || ms(l.updated_at) > ms(log.updated_at)) log = l;
    }
    const state = log?.status === "feito" ? "feito" : log?.status === "pulado" ? "pulado" : log?.status === "adiado" ? "adiado" : "pendente";
    const effectiveDueAt = state === "adiado" ? ms(log!.updated_at) + SNOOZE_MS : dueAt;
    out.push({ routine: r, dueAt, log, state, effectiveDueAt, open: state === "pendente" || state === "adiado" });
  }
  return out.sort((a, b) => a.dueAt - b.dueAt || String(a.routine.titulo).localeCompare(String(b.routine.titulo)));
}

/** [feitas, previstas] nos ultimos `days` dias, ate agora. Pulada conta como nao cumprida. */
export function compliance(routines: Row[], logs: Row[], now: number, days = 7): [number, number] {
  const today = local(now).date;
  const seen = new Set<string>();
  let done = 0, expected = 0;
  for (let i = 0; i < days; i++) {
    for (const o of occurrencesOn(routines, logs, addDays(today, -i))) {
      const key = `${o.routine.id}|${o.dueAt}`;
      if (o.dueAt > now || seen.has(key)) continue;
      seen.add(key);
      expected++;
      if (o.state === "feito") done++;
    }
  }
  return [done, expected];
}

export function doseFor(p: Row, volume: number): number | null {
  if (p.dose_quantidade == null) return null;
  return p.dose_base_litros > 0 ? p.dose_quantidade * volume / p.dose_base_litros : p.dose_quantidade;
}

/** Mesmo arredondamento pratico do app. */
export function formatDose(q: number | null, unit: string | null): string {
  if (q == null) return "dose a definir";
  const approx = (shown: number) => Math.abs(shown - q) > 0.005 * Math.max(q, 1e-9) + 1e-9 ? "~" : "";
  if (unit === "colher") {
    if (q < 0.875) {
      const opts: [number, string][] = [[0.25, "1/4"], [1 / 3, "1/3"], [0.5, "1/2"], [2 / 3, "2/3"], [0.75, "3/4"]];
      const [v, label] = opts.reduce((a, b) => Math.abs(b[0] - q) < Math.abs(a[0] - q) ? b : a);
      return `${approx(v)}${label} de colher`;
    }
    const v = Math.round(q * 2) / 2;
    return `${approx(v)}${numShort(v)} ${v > 1 ? "colheres" : "colher"}`;
  }
  if (unit === "gotas") {
    const v = Math.max(0.5, Math.round(q * 2) / 2);
    return `${approx(v)}${numShort(v)} ${Math.abs(v - 1) < 1e-6 ? "gota" : "gotas"}`;
  }
  if (unit === "g" || unit === "ml") {
    const v = q >= 10 ? Math.round(q) : Math.round(q * 10) / 10;
    return `${approx(v)}${numShort(v)} ${unit}`;
  }
  if (!unit || unit === "outra") return numShort(q);
  return `${numShort(q)} ${unit}`;
}

/** "20h: Goni Supreme 1/4 de colher, Amino Coral 2 gotas" */
export function reminderText(items: Occurrence[], products: Row[], volume: number): string {
  if (!items.length) return "";
  const t = local(items[0].dueAt);
  const hour = t.minute === 0 ? `${t.hour}h` : `${t.hour}h${String(t.minute).padStart(2, "0")}`;
  return `${hour}: ` + items.map((o) => {
    const p = products.find((x) => x.id === o.routine.product_id);
    return o.routine.tipo === "dosagem" && p ? `${o.routine.titulo} ${formatDose(doseFor(p, volume), p.dose_unidade)}` : o.routine.titulo;
  }).join(", ");
}

// ---- alertas criticos (para push imediato) ----

export interface Critical { key: string; fato: string }

export function criticalAlerts(tests: Row[], ranges: Row[], now: number): Critical[] {
  const out: Critical[] = [];
  for (const p of [...MAIN, ...OPTIONAL]) {
    const last = latest(tests, p);
    const r = ranges.find((x) => x.parametro === p);
    if (!last || !r) continue;
    const age = now - ms(last.medido_em);
    if (age > (MAIN.includes(p) ? 7 : 14) * DAY_MS) continue;
    if (statusOf(Number(last.valor), r) === "critico") {
      const low = last.valor < r.alerta_min;
      out.push({ key: `crit|${p}|${last.id}`, fato: `${LABEL[p]} em ${fmt(p, last.valor)} ${r.unidade}, ${low ? "abaixo" : "acima"} do limite de alerta.` });
    }
  }
  const kh = tests.filter((t) => t.parametro === "kh").sort((a, b) => ms(b.medido_em) - ms(a.medido_em));
  if (kh.length >= 2) {
    const dt = ms(kh[0].medido_em) - ms(kh[1].medido_em);
    const delta = Math.abs(kh[0].valor - kh[1].valor);
    if (dt >= HOUR_MS && now - ms(kh[0].medido_em) < 2 * DAY_MS && delta / Math.max(dt / DAY_MS, 1) > 1.0) {
      out.push({ key: `crit|kh_swing|${kh[0].id}`, fato: `KH variou ${fmt("kh", delta)} dKH (de ${fmt("kh", kh[1].valor)} para ${fmt("kh", kh[0].valor)}).` });
    }
  }
  return out;
}

// ---- nota de saude (spec 10) ----

export interface Health { score: number | null; items: { label: string; points: number }[]; missing: string[] }

export function healthScore(tests: Row[], ranges: Row[], changes: Row[], routines: Row[], logs: Row[], cases: Row[], now: number): Health {
  const items: { label: string; points: number }[] = [];
  const missing: string[] = [];
  const param = (p: string, factor: number, required: boolean) => {
    const last = latest(tests, p);
    if (!last) {
      if (required) missing.push(`medir ${LABEL[p]}`);
      return;
    }
    if (now - ms(last.medido_em) > 7 * DAY_MS) {
      items.push({ label: `${LABEL[p]} sem medição há mais de 7 dias`, points: -5 * factor });
      return;
    }
    const r = ranges.find((x) => x.parametro === p);
    if (!r) return;
    const st = statusOf(Number(last.valor), r);
    if (st === "atencao") items.push({ label: `${LABEL[p]} em atenção (${fmt(p, last.valor)})`, points: -8 * factor });
    if (st === "critico") items.push({ label: `${LABEL[p]} crítico (${fmt(p, last.valor)})`, points: -15 * factor });
  };
  MAIN.forEach((p) => param(p, 1, true));
  OPTIONAL.forEach((p) => {
    const last = latest(tests, p);
    if (last && now - ms(last.medido_em) <= 14 * DAY_MS) param(p, 0.5, false);
  });
  const lastTpa = changes.reduce((m, c) => Math.max(m, ms(c.feita_em)), 0);
  if (!lastTpa) missing.push("registrar uma TPA");
  else {
    const days = Math.floor((now - lastTpa) / DAY_MS);
    if (days > 16) items.push({ label: `TPA há ${days} dias (mais de 16)`, points: -20 });
    else if (days > 9) items.push({ label: `TPA há ${days} dias (mais de 9)`, points: -10 });
  }
  const [done, expected] = compliance(routines, logs, now);
  if (expected > 0 && done < expected) items.push({ label: `Rotinas dos últimos 7 dias: ${done} de ${expected} cumpridas`, points: -15 * (1 - done / expected) });
  const open = cases.filter((c) => !c.fechado_em && !c.deleted_at).length;
  if (open > 0) items.push({ label: `${open} caso(s) de saúde aberto(s)`, points: -Math.min(15, 5 * open) });
  if (missing.length) return { score: null, items, missing };
  // mesmo arredondamento do app (Kotlin roundToInt: metade para cima)
  const score = Math.max(0, Math.min(100, Math.floor(100 + items.reduce((s, i) => s + i.points, 0) + 0.5)));
  return { score, items, missing };
}
