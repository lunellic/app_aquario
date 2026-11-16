// Edge Function `assistant`: unica porta do app para a IA (spec 11). O app nunca fala com a Anthropic.
import { createClient } from "npm:@supabase/supabase-js@2";
import { ANALYSIS_REQUEST, askClaude, checkLimit, CORS, json, LimitError } from "../_shared/ai.ts";
import { loadCtx, summarize } from "../_shared/context.ts";
import { parseSpecies, SPECIES_REQUEST } from "../_shared/species.ts";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const auth = req.headers.get("Authorization") ?? "";
    const url = Deno.env.get("SUPABASE_URL")!;
    // client do usuario: tudo que le e grava passa pelo RLS dele
    const db = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: auth } } });
    const service = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
    const { data: u } = await db.auth.getUser(auth.replace(/^Bearer\s+/i, ""));
    const userId = u?.user?.id;
    if (!userId) return json({ error: "Não autenticado." }, 401);

    const body = await req.json().catch(() => ({}));
    const action = String(body.action ?? "");
    if (!["analise", "pergunta", "saude", "especie"].includes(action)) return json({ error: "Ação inválida." }, 400);

    const now = Date.now();
    const ctx = await loadCtx(db, null, now);
    const context = summarize(ctx, now);
    await checkLimit(service, userId);

    if (action === "especie") {
      const l = ctx.livestock.find((x) => x.id === body.livestock_id);
      if (!l) return json({ error: "Habitante não encontrado (ainda não sincronizou?)." }, 404);
      const text = await askClaude(context, SPECIES_REQUEST(l.nome_popular, l.nome_cientifico, l.tipo, l.categoria), 1500);
      const ficha = parseSpecies(text);
      // uma ficha por habitante: a nova substitui a anterior
      await db.from("species_info").update({ deleted_at: new Date().toISOString(), updated_at: new Date().toISOString() }).eq("livestock_id", l.id).is("deleted_at", null);
      const { data: saved, error } = await db.from("species_info").insert({ livestock_id: l.id, conteudo_json: ficha }).select("id").single();
      if (error) throw new Error(error.message);
      await db.from("ai_analyses").insert({ gatilho: "especie", pergunta: l.nome_popular, texto: String(ficha.compatibilidade) });
      return json({ id: saved.id, ficha });
    }

    let question: string;
    let gatilho: string;
    let pergunta: string | null = null;
    if (action === "analise") {
      question = ANALYSIS_REQUEST;
      gatilho = "manual";
    } else if (action === "pergunta") {
      pergunta = String(body.pergunta ?? "").trim().slice(0, 1000);
      if (!pergunta) return json({ error: "Escreva a pergunta." }, 400);
      question = pergunta;
      gatilho = "chat";
    } else {
      const k = ctx.cases.find((x) => x.id === body.case_id);
      if (!k) return json({ error: "Caso não encontrado (ainda não sincronizou?)." }, 404);
      const who = ctx.livestock.find((x) => x.id === k.livestock_id);
      pergunta = `Caso: ${k.titulo}`;
      question = `Dê uma orientação preliminar para o caso de saúde "${k.titulo}" (${k.tipo}; ${who ? `${who.nome_popular}${who.nome_cientifico ? ` [${who.nome_cientifico}]` : ""}` : "aquário todo"}), usando as entradas do caso e os parâmetros recentes do contexto. Diga: causas mais prováveis, o que observar nos próximos dias, o que NÃO fazer, e quando procurar ajuda de quem possa ver o animal. Deixe claro que é orientação preliminar. No máximo 220 palavras.`;
      gatilho = "saude";
    }
    const texto = await askClaude(context, question, 1200);
    const { data: saved, error } = await db.from("ai_analyses").insert({ gatilho, pergunta, texto }).select("id").single();
    if (error) throw new Error(error.message);
    return json({ id: saved.id, texto });
  } catch (e) {
    if (e instanceof LimitError) return json({ error: e.message }, 429);
    console.error(e);
    return json({ error: e instanceof Error ? e.message : "Erro inesperado." }, 500);
  }
});
