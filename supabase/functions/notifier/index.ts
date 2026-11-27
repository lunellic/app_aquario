// Edge Function `notifier` (spec 12): avisos (rotinas atrasadas, alertas criticos e o resumo das 23h30) pelo ntfy e
// pelo Web Push do PWA no iPhone, e e-mail semanal pelo Resend.
// Chamada pelo pg_cron (header x-cron-secret) ou pelo app nos botoes de teste (JWT do usuario).
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";
import { ANALYSIS_REQUEST, askClaude, checkLimit, CORS, json } from "../_shared/ai.ts";
import { loadCtx, summarize } from "../_shared/context.ts";
import { compliance, criticalAlerts, DAY_MS, fmt, healthScore, isNight, LABEL, latest, local, MAIN, ms, numShort, occurrencesOn, reminderText, statusOf, textoDaNoite } from "../_shared/rules.ts";
// @deno-types="npm:@types/web-push@3.6.4"
import webpush from "npm:web-push@3.6.7";

const LATE_AFTER_MS = 15 * 60_000; // da tempo de marcar no tablet antes de avisar o iPhone

async function ntfy(title: string, message: string, priority: number, tags: string[]): Promise<void> {
  const topic = Deno.env.get("NTFY_TOPIC");
  if (!topic) throw new Error("NTFY_TOPIC não configurado nas Edge Functions.");
  const res = await fetch("https://ntfy.sh", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ topic, title, message, priority, tags }) });
  if (!res.ok) throw new Error(`ntfy ${res.status}: ${await res.text()}`);
}

// ---- Web Push (PWA no iPhone) ----
// As chaves VAPID sao geradas uma vez pelo workflow de backend e ficam so nos secrets das funcoes.
// O "subject" e o endereco do proprio PWA: a Apple exige um, e assim nenhum e-mail fica no repositorio.
const VAPID_SUBJECT = "https://lunellic.github.io/app_aquario/";

function vapidPronto(): boolean {
  const pub = Deno.env.get("VAPID_PUBLIC_KEY"), priv = Deno.env.get("VAPID_PRIVATE_KEY");
  if (!pub || !priv) return false;
  webpush.setVapidDetails(VAPID_SUBJECT, pub, priv);
  return true;
}

/**
 * Manda para todos os iPhones inscritos do usuario. Devolve quantos receberam e quantos falharam.
 * Inscricao que a Apple diz que nao existe mais (404/410: PWA apagado, permissao tirada) sai da tabela.
 */
async function pushAll(service: SupabaseClient, userId: string, title: string, body: string, tag: string): Promise<{ ok: number; falhas: number }> {
  if (!vapidPronto()) return { ok: 0, falhas: 0 };
  const { data: subs, error } = await service.from("push_subscriptions").select("id, endpoint, p256dh, auth").eq("user_id", userId);
  if (error) throw new Error(`push_subscriptions: ${error.message}`);
  let ok = 0, falhas = 0;
  const payload = JSON.stringify({ title, body, tag });
  for (const s of subs ?? []) {
    try {
      await webpush.sendNotification({ endpoint: s.endpoint, keys: { p256dh: s.p256dh, auth: s.auth } }, payload, { TTL: 6 * 3600, urgency: "high" });
      ok++;
      await service.from("push_subscriptions").update({ usado_em: new Date().toISOString() }).eq("id", s.id);
    } catch (e) {
      const code = (e as { statusCode?: number }).statusCode;
      if (code === 404 || code === 410) await service.from("push_subscriptions").delete().eq("id", s.id);
      else { falhas++; console.error("web push", code, (e as { body?: string }).body ?? e); }
    }
  }
  return { ok, falhas };
}

/** true se a chave ainda nao tinha sido enviada (e marca como enviada). */
async function firstTime(service: SupabaseClient, userId: string, chave: string): Promise<boolean> {
  const { error } = await service.from("notify_log").insert({ user_id: userId, chave });
  if (!error) return true;
  if (error.code === "23505") return false; // chave duplicada: ja avisado
  throw new Error(`notify_log: ${error.message}`);
}

async function tick(service: SupabaseClient, userId: string, now: number): Promise<string[]> {
  const sent: string[] = [];
  const c = await loadCtx(service, userId, now);
  if (!c.tank) return sent;
  // dois canais: o ntfy (se ligado no app e com topico) e o Web Push do iPhone (se houver inscricao)
  const ntfyOn = c.tank.ntfy_ativo !== false && !!Deno.env.get("NTFY_TOPIC");
  const { count: nSubs } = await service.from("push_subscriptions").select("id", { count: "exact", head: true }).eq("user_id", userId);
  const pushOn = (nSubs ?? 0) > 0 && vapidPronto();
  // sem canal nao ha para onde enviar: nao marca nada como avisado
  if (!ntfyOn && !pushOn) return ["sem canal"];
  /** Manda pelos canais ligados. Falha so se todos falharem: um iPhone sem sinal nao reenvia o ntfy. */
  const enviar = async (title: string, message: string, priority: number, tags: string[], tag: string) => {
    let entregue = false, erro: unknown = null;
    if (ntfyOn) { try { await ntfy(title, message, priority, tags); entregue = true; } catch (e) { erro = e; } }
    if (pushOn) {
      try { const r = await pushAll(service, userId, title, message, tag); if (r.ok) entregue = true; else if (r.falhas && !erro) erro = new Error("web push falhou"); } catch (e) { erro = erro ?? e; }
    }
    if (!entregue && erro) throw erro;
  };
  // se o envio falhar, desfaz a marca para tentar de novo no proximo ciclo
  const unmark = (keys: string[]) => service.from("notify_log").delete().eq("user_id", userId).in("chave", keys);
  const night = isNight(local(now).hour, c.tank.noite_inicio ?? 0, c.tank.noite_fim ?? 7);
  const vol = Number(c.tank.volume_dosagem_l ?? 100);

  // alertas criticos: saem mesmo no modo noite
  for (const a of criticalAlerts(c.tests, c.ranges, now)) {
    if (await firstTime(service, userId, a.key)) {
      try {
        await enviar("Luluco Reef: alerta crítico", `${a.fato} Reteste para confirmar e corrija devagar.`, 5, ["rotating_light"], a.key);
      } catch (e) {
        await unmark([a.key]);
        throw e;
      }
      sent.push(a.key);
    }
  }

  // rotinas vencidas e nao marcadas, agrupadas por horario (nao envia no modo noite)
  if (!night) {
    const late = occurrencesOn(c.routines, c.logs, local(now).date).filter((o) => o.open && o.routine.com_horario !== false && now - o.effectiveDueAt >= LATE_AFTER_MS);
    const groups = new Map<number, typeof late>();
    for (const o of late) groups.set(Math.floor(o.effectiveDueAt / 60_000), [...(groups.get(Math.floor(o.effectiveDueAt / 60_000)) ?? []), o]);
    for (const group of groups.values()) {
      const fresh = [];
      for (const o of group) if (await firstTime(service, userId, `rot|${o.routine.id}|${o.effectiveDueAt}`)) fresh.push(o);
      if (fresh.length) {
        try {
          await enviar("Luluco Reef: rotina atrasada", reminderText(fresh, c.products, vol), 3, ["alarm_clock"], "atrasada");
        } catch (e) {
          await unmark(fresh.map((o) => `rot|${o.routine.id}|${o.effectiveDueAt}`));
          throw e;
        }
        sent.push(...fresh.map((o) => `rot|${o.routine.titulo}`));
      }
    }
  }
  // resumo das 23h30: o que ainda falta do dia, inclusive o que nao tem horario (sai mesmo no modo noite,
  // e o lembrete que o Lucas pediu para todo dia). O tick roda de 5 em 5 minutos; a chave do dia
  // garante um so. Depois da meia-noite nao manda mais: aquilo ja virou atrasado.
  const t = local(now);
  if (t.hour === 23 && t.minute >= 30) {
    const key = `resumo|${t.date}`;
    if (await firstTime(service, userId, key)) {
      // sempre chega: o que falta de hoje ou, com tudo feito, o que vem amanha
      const texto = textoDaNoite(c.routines, c.logs, c.products, vol, now);
      try {
        await enviar("Luluco Reef: 23h30", texto, 4, ["memo"], "resumo");
      } catch (e) {
        await unmark([key]);
        throw e;
      }
      sent.push(key);
    }
  }
  return sent;
}

const esc = (s: unknown) => String(s ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");

async function weeklyEmail(service: SupabaseClient, userId: string, now: number, withAi: boolean): Promise<string> {
  const key = Deno.env.get("RESEND_API_KEY");
  if (!key) throw new Error("RESEND_API_KEY não configurada nas Edge Functions.");
  const { data: u, error: ue } = await service.auth.admin.getUserById(userId);
  const email = u?.user?.email;
  if (ue || !email) throw new Error("Usuário sem e-mail.");
  const c = await loadCtx(service, userId, now);
  const weekAgo = now - 7 * DAY_MS;
  const h = healthScore(c.tests, c.ranges, c.changes, c.routines, c.logs, c.cases, now);
  const color = { ideal: "#1f9d6b", atencao: "#F05A35", critico: "#d92d20" };
  const label = { ideal: "Ideal", atencao: "Atenção", critico: "Crítico" };

  const rows = MAIN.map((p) => {
    const last = latest(c.tests, p);
    const r = c.ranges.find((x) => x.parametro === p);
    const week = c.tests.filter((t) => t.parametro === p && ms(t.medido_em) >= weekAgo);
    const st = last && r ? statusOf(Number(last.valor), r) : null;
    const range = week.length ? `${fmt(p, Math.min(...week.map((t) => t.valor)))} a ${fmt(p, Math.max(...week.map((t) => t.valor)))}` : "sem medição";
    return `<tr><td style="padding:6px 10px">${LABEL[p]}</td><td style="padding:6px 10px"><b>${last ? fmt(p, last.valor) : "-"}</b> ${esc(r?.unidade ?? "")}</td><td style="padding:6px 10px">${range}</td><td style="padding:6px 10px;color:${st ? color[st] : "#667"}"><b>${st ? label[st] : "Sem medição"}</b></td></tr>`;
  }).join("");

  const tpas = c.changes.filter((w) => ms(w.feita_em) >= weekAgo);
  const [done, expected] = compliance(c.routines, c.logs, now);
  const open = c.cases.filter((k) => !k.fechado_em);
  const active = c.livestock.filter((l) => l.status === "ativo");
  const groups = [["coral", "Corais"], ["anemona", "Anêmonas"], ["peixe", "Peixes"], ["invertebrado", "Invertebrados"], ["alga", "Algas"]]
    .map(([tipo, nome]) => {
      const g = active.filter((l) => l.tipo === tipo);
      return g.length ? `<p style="margin:6px 0"><b>${nome} (${g.reduce((s, l) => s + l.quantidade, 0)}):</b> ${g.map((l) => `${l.quantidade > 1 ? l.quantidade + "x " : ""}${esc(l.nome_popular)}`).join(", ")}</p>` : "";
    }).join("");

  let analysis = "Análise da IA não incluída neste envio de teste.";
  if (withAi) {
    try {
      await checkLimit(service, userId);
      analysis = await askClaude(summarize(c, now), ANALYSIS_REQUEST, 1200);
      await service.from("ai_analyses").insert({ user_id: userId, gatilho: "semanal", texto: analysis });
    } catch (e) {
      analysis = `Não foi possível gerar a análise desta semana (${e instanceof Error ? e.message : "erro"}).`;
    }
  }

  const html = `<div style="font-family:Arial,Helvetica,sans-serif;max-width:640px;margin:auto;color:#12213a">
<div style="background:#0A47B5;color:#FBF1E4;padding:18px 22px;border-radius:12px 12px 0 0"><div style="font-size:22px;font-weight:bold">Luluco Reef</div><div>Relatório semanal, ${local(now).date.split("-").reverse().join("/")}</div></div>
<div style="border:1px solid #d7deea;border-top:0;padding:18px 22px;border-radius:0 0 12px 12px">
<p style="font-size:18px;margin:0 0 4px">Nota de saúde: <b style="font-size:26px">${h.score ?? "-"}</b></p>
${h.score == null ? `<p>Faltam dados: ${esc(h.missing.join(", "))}.</p>` : h.items.length ? `<ul style="margin:4px 0 12px;padding-left:20px">${h.items.map((i) => `<li>${numShort(i.points)}: ${esc(i.label)}</li>`).join("")}</ul>` : "<p>Nenhum desconto. Tudo em dia.</p>"}
<h3 style="margin:16px 0 6px">Parâmetros</h3>
<table style="border-collapse:collapse;width:100%;font-size:14px"><tr style="background:#eef2f8;text-align:left"><th style="padding:6px 10px">Parâmetro</th><th style="padding:6px 10px">Último</th><th style="padding:6px 10px">Na semana</th><th style="padding:6px 10px">Status</th></tr>${rows}</table>
<h3 style="margin:16px 0 6px">Manejo da semana</h3>
<p style="margin:4px 0">TPAs: ${tpas.length ? tpas.map((w) => `${numShort(w.litros)} L`).join(", ") : "nenhuma registrada"}.</p>
<p style="margin:4px 0">Rotinas: ${done} de ${expected} cumpridas.</p>
<h3 style="margin:16px 0 6px">Saúde</h3>
<p style="margin:4px 0">${open.length ? open.map((k) => esc(`${k.titulo} (${k.tipo})`)).join("; ") : "Nenhum caso aberto."}</p>
<h3 style="margin:16px 0 6px">Análise (IA)</h3>
<p style="white-space:pre-wrap;margin:4px 0">${esc(analysis)}</p>
<p style="color:#667;font-size:12px">Texto gerado por IA a partir dos seus registros. Confira antes de agir.</p>
<h3 style="margin:16px 0 6px">Meu aquário</h3>${groups || "<p>Inventário vazio.</p>"}
</div></div>`;

  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "content-type": "application/json" },
    body: JSON.stringify({ from: "Luluco Reef <onboarding@resend.dev>", to: [email], subject: `Luluco Reef: semana com nota ${h.score ?? "-"}`, html }),
  });
  if (!res.ok) throw new Error(`Resend ${res.status}: ${await res.text()}`);
  return email;
}

const BACKUP_TABLES = ["tank_config", "param_ranges", "water_tests", "water_changes", "products", "dosings", "routines", "routine_logs", "events",
  "livestock", "health_cases", "health_entries", "equipment", "finance_entries", "shopping_items", "species_info", "ai_analyses", "temperature_readings"];

/** Copia de todos os dados do usuario em JSON, anexada num e-mail para a propria conta (backup diario e botao "Exportar meus dados"). */
async function backupEmail(service: SupabaseClient, userId: string, email: string, auto: boolean): Promise<string> {
  const key = Deno.env.get("RESEND_API_KEY");
  if (!key) throw new Error("RESEND_API_KEY não configurada nas Edge Functions.");
  const tables: Record<string, unknown[]> = {};
  const counts: string[] = [];
  for (const t of BACKUP_TABLES) {
    const all: unknown[] = [];
    for (let from = 0; ; from += 1000) {
      const { data, error } = await service.from(t).select("*").eq("user_id", userId).range(from, from + 999);
      if (error) { console.error("backup", t, error.message); break; }
      all.push(...(data ?? []));
      if (!data || data.length < 1000) break;
    }
    tables[t] = all;
    if (all.length) counts.push(`${t}: ${all.length}`);
  }
  const day = new Date().toISOString().slice(0, 10);
  const payload = JSON.stringify({ app: "Luluco Reef", exported_at: new Date().toISOString(), user_id: userId, tables });
  const bytes = new TextEncoder().encode(payload);
  let bin = "";
  for (let i = 0; i < bytes.length; i += 0x8000) bin += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  const b64 = btoa(bin);
  const text = `${auto ? "Backup diário" : "Exportação"} do Luluco Reef (${day}).\n\nO arquivo anexo tem todos os seus dados em JSON. Guarde este e-mail: com ele dá para restaurar o app num banco novo.\n\n${counts.join("\n")}`;
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "content-type": "application/json" },
    body: JSON.stringify({
      from: "Luluco Reef <onboarding@resend.dev>", to: [email],
      subject: `Luluco Reef: ${auto ? "backup" : "exportação"} ${day}`, text,
      attachments: [{ filename: `luluco-reef-${day}.json`, content: b64 }],
    }),
  });
  if (!res.ok) throw new Error(`Resend ${res.status}: ${await res.text()}`);
  return email;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const url = Deno.env.get("SUPABASE_URL")!;
    const service = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const body = await req.json().catch(() => ({}));
    const mode = String(body.mode ?? "");
    const now = Date.now();
    const secret = Deno.env.get("CRON_SECRET");
    const isCron = !!secret && req.headers.get("x-cron-secret") === secret;

    if (mode === "backup") {
      if (!isCron) return json({ error: "Não autorizado." }, 401);
      const { data: tanks, error } = await service.from("tank_config").select("user_id").is("deleted_at", null);
      if (error) throw new Error(error.message);
      const results: Record<string, unknown> = {};
      for (const t of tanks ?? []) {
        try {
          const { data: au } = await service.auth.admin.getUserById(t.user_id);
          const email = au?.user?.email;
          results[t.user_id] = email ? await backupEmail(service, t.user_id, email, true) : "sem e-mail";
        } catch (e) {
          console.error(t.user_id, e);
          results[t.user_id] = `erro: ${e instanceof Error ? e.message : e}`;
        }
      }
      return json({ ok: true, results });
    }

    // previa do aviso das 23h30 no iPhone, com os dados de agora (disparada a mao pelo diagnostico)
    if (mode === "resumo_previa") {
      if (!isCron) return json({ error: "Não autorizado." }, 401);
      const { data: tanks, error } = await service.from("tank_config").select("user_id").is("deleted_at", null);
      if (error) throw new Error(error.message);
      const results: Record<string, unknown> = {};
      for (const t of tanks ?? []) {
        const c = await loadCtx(service, t.user_id, now);
        const vol = Number(c.tank?.volume_dosagem_l ?? 100);
        results[t.user_id] = await pushAll(service, t.user_id, "Luluco Reef: 23h30 (prévia)", textoDaNoite(c.routines, c.logs, c.products, vol, now), "resumo");
      }
      return json({ ok: true, results });
    }

    if (mode === "tick" || mode === "weekly") {
      if (!isCron) return json({ error: "Não autorizado." }, 401);
      const { data: tanks, error } = await service.from("tank_config").select("user_id, email_semanal_ativo").is("deleted_at", null);
      if (error) throw new Error(error.message);
      const results: Record<string, unknown> = {};
      for (const t of tanks ?? []) {
        try {
          if (mode === "tick") results[t.user_id] = await tick(service, t.user_id, now);
          else if (t.email_semanal_ativo !== false) results[t.user_id] = await weeklyEmail(service, t.user_id, now, true);
        } catch (e) {
          console.error(t.user_id, e);
          results[t.user_id] = `erro: ${e instanceof Error ? e.message : e}`;
        }
      }
      // limpeza do controle de envios
      if (mode === "weekly") await service.from("notify_log").delete().lt("enviado_em", new Date(now - 30 * DAY_MS).toISOString());
      return json({ ok: true, results });
    }

    // botoes "enviar teste" do app: exigem o JWT do usuario
    const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    const { data: u } = await service.auth.getUser(token);
    const userId = u?.user?.id;
    if (!userId) return json({ error: "Não autenticado." }, 401);
    // inscricao do PWA do iPhone no Web Push
    if (mode === "push_key") {
      const key = Deno.env.get("VAPID_PUBLIC_KEY");
      if (!key) return json({ error: "Avisos no iPhone ainda não configurados no servidor." }, 503);
      return json({ ok: true, key });
    }
    if (mode === "push_subscribe") {
      const sub = body.subscription ?? {};
      const endpoint = String(sub.endpoint ?? ""), p256dh = String(sub.keys?.p256dh ?? ""), auth = String(sub.keys?.auth ?? "");
      if (!/^https:\/\//.test(endpoint) || !p256dh || !auth) return json({ error: "Inscrição inválida." }, 400);
      const { error } = await service.from("push_subscriptions").upsert(
        { user_id: userId, endpoint, p256dh, auth, aparelho: String(body.aparelho ?? "").slice(0, 160) || null },
        { onConflict: "endpoint" });
      if (error) throw new Error(error.message);
      return json({ ok: true });
    }
    if (mode === "push_unsubscribe") {
      await service.from("push_subscriptions").delete().eq("user_id", userId).eq("endpoint", String(body.endpoint ?? ""));
      return json({ ok: true });
    }
    if (mode === "push_test") {
      const r = await pushAll(service, userId, "Luluco Reef: teste", "Se chegou, os avisos estão ligados neste iPhone.", "teste");
      if (!r.ok) return json({ error: r.falhas ? "A Apple recusou o envio. Desligue e ligue os avisos de novo." : "Nenhum iPhone inscrito." }, 400);
      return json({ ok: true, message: `Aviso de teste enviado (${r.ok}).` });
    }
    if (mode === "test_ntfy") {
      await ntfy("Luluco Reef: teste", "Push de teste. Se chegou no iPhone, está tudo certo.", 3, ["tropical_fish"]);
      return json({ ok: true, message: "Push de teste enviado." });
    }
    if (mode === "report") {
      // relatorio em texto montado no app, enviado para o e-mail da propria conta
      const key = Deno.env.get("RESEND_API_KEY");
      if (!key) throw new Error("RESEND_API_KEY não configurada nas Edge Functions.");
      const email = u?.user?.email;
      if (!email) throw new Error("Usuário sem e-mail.");
      const text = String(body.text ?? "").slice(0, 120_000);
      if (!text.trim()) return json({ error: "Relatório vazio." }, 400);
      const subject = String(body.subject ?? "Luluco Reef - relatório").slice(0, 150);
      const html = `<pre style="font-family:Menlo,Consolas,monospace;font-size:13px;line-height:1.5;white-space:pre-wrap;color:#12213a">${esc(text)}</pre>`;
      const res = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: { Authorization: `Bearer ${key}`, "content-type": "application/json" },
        body: JSON.stringify({ from: "Luluco Reef <onboarding@resend.dev>", to: [email], subject, text, html }),
      });
      if (!res.ok) throw new Error(`Resend ${res.status}: ${await res.text()}`);
      return json({ ok: true, message: `Relatório enviado para ${email}.` });
    }
    if (mode === "export") {
      const email = u?.user?.email;
      if (!email) throw new Error("Usuário sem e-mail.");
      await backupEmail(service, userId, email, false);
      return json({ ok: true, message: `Seus dados foram enviados para ${email}.` });
    }
    if (mode === "test_email") {
      const email = await weeklyEmail(service, userId, now, false);
      return json({ ok: true, message: `E-mail de teste enviado para ${email}.` });
    }
    return json({ error: "Modo inválido." }, 400);
  } catch (e) {
    console.error(e);
    return json({ error: e instanceof Error ? e.message : "Erro inesperado." }, 500);
  }
});
