import assert from "node:assert/strict";
import { parseSpecies } from "./species.ts";

const ok = { nome_cientifico: "Duncanopsammia axifuga", tipo: "coral LPS", dificuldade: "fácil", agressividade: "baixa", alcance_tentaculos: "2 a 3 cm",
  distancia_recomendada: "8 cm", volume_minimo_l: 60, parametros: "KH 7,5-9", iluminacao: "moderada", fluxo: "moderado",
  compatibilidade: "Compatível.", conflitos: [{ com: "Euphyllia martelo", motivo: "tentáculos varredores" }, { com: 1 }], observacoes: "" };

Deno.test("aceita JSON com cerca de codigo e descarta conflito malformado", () => {
  const f = parseSpecies("```json\n" + JSON.stringify(ok) + "\n```");
  assert.equal(f.nome_cientifico, "Duncanopsammia axifuga");
  assert.deepEqual(f.conflitos, [{ com: "Euphyllia martelo", motivo: "tentáculos varredores" }]);
  assert.equal(f.volume_minimo_l, 60);
});

Deno.test("recusa resposta sem JSON ou incompleta", () => {
  assert.throws(() => parseSpecies("Desculpe, não sei."));
  assert.throws(() => parseSpecies(JSON.stringify({ ...ok, fluxo: "" })));
  assert.throws(() => parseSpecies("{ nome: "));
});
