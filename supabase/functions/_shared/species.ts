// Ficha da especie: pedido e validacao do JSON devolvido pela IA.

export const SPECIES_REQUEST = (nome: string, cientifico: string | null, tipo: string, categoria: string | null) =>
  `Gere a ficha da espécie para o habitante que acabei de cadastrar: "${nome}"${cientifico ? ` (nome científico informado: ${cientifico})` : ""}, tipo ${tipo}${categoria ? `, categoria ${categoria}` : ""}.
Avalie a compatibilidade com o INVENTÁRIO ATIVO do contexto e liste conflitos concretos (agressão química ou por tentáculos, predação, competição por espaço), citando o nome do outro habitante.
Responda SOMENTE com um objeto JSON válido, sem texto antes ou depois, exatamente com estas chaves:
{
 "nome_cientifico": "nome científico provável, ou 'incerto' se o nome popular não permitir identificar",
 "tipo": "ex.: coral LPS, peixe, anêmona",
 "dificuldade": "fácil | moderada | difícil",
 "agressividade": "texto curto",
 "alcance_tentaculos": "texto curto com alcance aproximado de tentáculos/pólipos, ou 'não se aplica'",
 "distancia_recomendada": "texto curto, em cm",
 "volume_minimo_l": número ou null,
 "parametros": "faixas recomendadas em uma linha",
 "iluminacao": "texto curto",
 "fluxo": "texto curto",
 "compatibilidade": "resumo em uma ou duas frases para este aquário",
 "conflitos": [{"com": "nome do habitante do inventário", "motivo": "texto curto"}],
 "observacoes": "cuidados importantes, ou string vazia"
}`;

const TEXT_KEYS = ["nome_cientifico", "tipo", "dificuldade", "agressividade", "alcance_tentaculos", "distancia_recomendada", "parametros", "iluminacao", "fluxo", "compatibilidade", "observacoes"];

/** Extrai e valida o JSON. Lanca erro se a resposta nao servir. */
export function parseSpecies(text: string): Record<string, unknown> {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) throw new Error("A IA não devolveu JSON.");
  let obj: Record<string, unknown>;
  try {
    obj = JSON.parse(text.slice(start, end + 1));
  } catch {
    throw new Error("A IA devolveu JSON inválido.");
  }
  const out: Record<string, unknown> = {};
  for (const k of TEXT_KEYS) {
    const v = obj[k];
    if (k !== "observacoes" && (typeof v !== "string" || !v.trim())) throw new Error(`Ficha incompleta: falta "${k}".`);
    out[k] = typeof v === "string" ? v.trim().slice(0, 600) : "";
  }
  out.volume_minimo_l = typeof obj.volume_minimo_l === "number" && isFinite(obj.volume_minimo_l) ? obj.volume_minimo_l : null;
  const conflicts = Array.isArray(obj.conflitos) ? obj.conflitos : [];
  out.conflitos = conflicts
    .filter((c): c is { com: string; motivo: string } => !!c && typeof c.com === "string" && typeof c.motivo === "string")
    .slice(0, 20)
    .map((c) => ({ com: c.com.slice(0, 120), motivo: c.motivo.slice(0, 400) }));
  return out;
}
