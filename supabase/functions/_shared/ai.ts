// Chamada ao Claude (API da Anthropic) com as instrucoes fixas e o limite diario de uso.
import type { SupabaseClient } from "npm:@supabase/supabase-js@2";

export const DAILY_LIMIT = 60;

export const SYSTEM_PROMPT = `Você é o assistente do Luluco Reef, o aquário marinho do Lucas (LPS dominante, cubo de 100 L de volume de dosagem).
Regras fixas:
- Responda em português do Brasil, curto e direto, sem enrolação.
- Baseie-se somente nos dados registrados que vêm no contexto. Quando faltar dado para concluir algo, diga qual dado falta.
- Respeite o regime do Lucas: TPAs semanais + Tropic Marin All-For-Reef + suplementos da Shask. NUNCA recomende suplementos separados de cálcio, KH/alcalinidade ou magnésio (buffers, cloretos, balling, kalkwasser etc.). Para corrigir Ca, KH ou Mg, as opções são: retestar, ajustar a dose do All-For-Reef e TPAs com água bem calibrada.
- Nunca invente dose de produto. Só cite dose que esteja no contexto ou diga para seguir a bula do fabricante.
- Recomende sempre correções graduais, nunca mudanças bruscas.
- Em saúde animal, deixe claro que é orientação preliminar e não substitui a avaliação de quem está vendo o animal.
- Não use travessões. Use texto simples, com listas curtas com hífen quando ajudar.`;

export class LimitError extends Error {}

/** Conta a chamada do dia (fuso de Sao Paulo) e bloqueia acima do limite. Precisa do client service role. */
export async function checkLimit(service: SupabaseClient, userId: string): Promise<number> {
  const { data, error } = await service.rpc("bump_ai_usage", { p_user: userId });
  if (error) throw new Error(`limite: ${error.message}`);
  const n = Number(data);
  if (n > DAILY_LIMIT) throw new LimitError(`Limite diário de ${DAILY_LIMIT} chamadas de IA atingido. Volta amanhã.`);
  return n;
}

export async function askClaude(context: string, question: string, maxTokens: number): Promise<string> {
  const key = Deno.env.get("ANTHROPIC_API_KEY");
  if (!key) throw new Error("ANTHROPIC_API_KEY não configurada nas Edge Functions.");
  const model = Deno.env.get("ANTHROPIC_MODEL") || "claude-haiku-5-5";
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": key, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({
      model,
      max_tokens: Math.min(maxTokens, 2000),
      system: SYSTEM_PROMPT,
      messages: [{ role: "user", content: `DADOS REGISTRADOS NO APP:\n${context}\n\nPEDIDO:\n${question}` }],
    }),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(`Anthropic ${res.status}: ${body?.error?.message ?? "erro"}`);
  const text = (body.content ?? []).filter((b: { type: string }) => b.type === "text").map((b: { text: string }) => b.text).join("\n").trim();
  if (!text) throw new Error("A IA devolveu resposta vazia.");
  return text;
}

export const ANALYSIS_REQUEST = `Analise meu aquário com base nos dados: (1) tendência de KH, Ca, Mg e salinidade; (2) relação entre TPAs/doses e os parâmetros; (3) consumo estimado, se houver medições suficientes; (4) pontos de atenção, em ordem de prioridade, cada um com uma ação segura. No máximo 250 palavras.`;

export const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-cron-secret",
};

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...CORS, "content-type": "application/json; charset=utf-8" } });
}
