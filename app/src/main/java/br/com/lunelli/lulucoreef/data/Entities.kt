package br.com.lunelli.lulucoreef.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

// GERADO a partir do modelo da spec (secao 5). Propriedades em camelCase viram snake_case no JSON do Supabase.
// `pending` e local (nao vai para o servidor): true = ainda nao sincronizado.

interface Syncable {
    val id: String
    val updatedAt: Long
    val deletedAt: Long?
    val pending: Boolean
}

fun newId(): String = UUID.randomUUID().toString()

/** So as leituras confiaveis entram em status, alertas, projecao e nota de saude. */
fun List<WaterTest>.reliable(): List<WaterTest> = filter { !it.baixaConfianca }

@Serializable
@Entity(tableName = "tank_config")
data class TankConfig(
    @PrimaryKey override val id: String = newId(),
    val nome: String = "Luluco Reef",
    val volumeDosagemL: Double = 100.0,
    val salAtual: String? = null,
    val montadoEm: String? = null,
    val fotoVitrinePath: String? = null,
    val observacoes: String? = null,
    val ntfyAtivo: Boolean = true,
    val emailSemanalAtivo: Boolean = true,
    val noiteInicio: Int = 0,
    val noiteFim: Int = 7,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "param_ranges")
data class ParamRange(
    val parametro: String,
    val idealMin: Double,
    val idealMax: Double,
    val alertaMin: Double,
    val alertaMax: Double,
    val unidade: String,
    @PrimaryKey override val id: String = newId(),
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "water_tests")
data class WaterTest(
    @Serializable(with = TsSerializer::class) val medidoEm: Long,
    val parametro: String,
    val valor: Double,
    val unidade: String,
    @PrimaryKey override val id: String = newId(),
    val kit: String? = null,
    val observacao: String? = null,
    /** Leitura de baixa confianca: aparece vazada no grafico e fica fora de projecao, alertas e nota de saude. */
    @ColumnInfo(defaultValue = "0") val baixaConfianca: Boolean = false,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "water_changes")
data class WaterChange(
    @Serializable(with = TsSerializer::class) val feitaEm: Long,
    val litros: Double,
    @PrimaryKey override val id: String = newId(),
    val sal: String? = null,
    val salinidadeNovaAgua: Double? = null,
    val temperaturaNovaAgua: Double? = null,
    val metodo: String? = null,
    val observacao: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "products")
data class Product(
    val nome: String,
    @PrimaryKey override val id: String = newId(),
    val marca: String? = null,
    val tipo: String = "suplemento",
    val doseQuantidade: Double? = null,
    val doseUnidade: String? = null,
    val doseBaseLitros: Double? = null,
    val observacao: String? = null,
    val ativo: Boolean = true,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "equipment")
data class Equipment(
    @PrimaryKey override val id: String = newId(),
    val tipo: String = "outro",
    val marca: String? = null,
    val modelo: String? = null,
    val instaladoEm: String? = null,
    val configuracao: String? = null,
    val observacao: String? = null,
    val ativo: Boolean = true,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "dosings")
data class Dosing(
    @Serializable(with = TsSerializer::class) val dosadoEm: Long,
    val productId: String,
    @PrimaryKey override val id: String = newId(),
    val quantidade: Double? = null,
    val unidade: String? = null,
    val observacao: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "routines")
data class Routine(
    val titulo: String,
    @PrimaryKey override val id: String = newId(),
    val tipo: String = "outro",
    val productId: String? = null,
    val equipmentId: String? = null,
    val recorrencia: String = "diaria",
    val diasSemana: List<Int>? = null,
    val intervaloN: Int? = null,
    val horario: String = "20:00",
    @Serializable(with = TsSerializer::class) val proximaEm: Long? = null,
    val instrucao: String? = null,
    val ativa: Boolean = true,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "routine_logs")
data class RoutineLog(
    val routineId: String,
    @Serializable(with = TsSerializer::class) val previstoPara: Long,
    val status: String,
    @PrimaryKey override val id: String = newId(),
    @Serializable(with = TsSerializer::class) val feitoEm: Long? = null,
    val observacao: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "livestock")
data class Livestock(
    val nomePopular: String,
    @PrimaryKey override val id: String = newId(),
    val tipo: String = "coral",
    val nomeCientifico: String? = null,
    val categoria: String? = null,
    val quantidade: Int = 1,
    val localNoAquario: String? = null,
    val adicionadoEm: String? = null,
    val origem: String? = null,
    val status: String = "ativo",
    val removidoEm: String? = null,
    val revisar: Boolean = false,
    val observacao: String? = null,
    val fotoPath: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "health_cases")
data class HealthCase(
    val titulo: String,
    @Serializable(with = TsSerializer::class) val abertoEm: Long,
    @PrimaryKey override val id: String = newId(),
    val livestockId: String? = null,
    val tipo: String = "observacao",
    @Serializable(with = TsSerializer::class) val fechadoEm: Long? = null,
    val desfecho: String? = null,
    val observacao: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "health_entries")
data class HealthEntry(
    val caseId: String,
    @Serializable(with = TsSerializer::class) val registradoEm: Long,
    @PrimaryKey override val id: String = newId(),
    val texto: String? = null,
    val fotoPath: String? = null,
    val productId: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "events")
data class Event(
    @Serializable(with = TsSerializer::class) val ocorridoEm: Long,
    val texto: String,
    @PrimaryKey override val id: String = newId(),
    val tipo: String = "observacao",
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "species_info")
data class SpeciesInfo(
    val livestockId: String,
    @PrimaryKey override val id: String = newId(),
    /** Ficha gerada pela IA (jsonb no servidor, texto JSON aqui). */
    @Serializable(with = JsonTextSerializer::class) val conteudoJson: String = "{}",
    @Serializable(with = TsSerializer::class) val geradaEm: Long = System.currentTimeMillis(),
    val confirmadaPeloUsuario: Boolean = false,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "ai_analyses")
data class AiAnalysis(
    val texto: String,
    @PrimaryKey override val id: String = newId(),
    @Serializable(with = TsSerializer::class) val geradaEm: Long = System.currentTimeMillis(),
    val gatilho: String = "manual",
    val pergunta: String? = null,
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "finance_entries")
data class FinanceEntry(
    @Serializable(with = TsSerializer::class) val lancadoEm: Long = System.currentTimeMillis(),
    val tipo: String = "despesa",
    val categoria: String = "outros",
    val descricao: String = "",
    val valor: Double = 0.0,
    val aquario: String = "marinho",
    val shoppingItemId: String? = null,
    val observacao: String? = null,
    /** pix (sai da caixinha) ou cartao (nao sai). */
    val forma: String = "pix",
    val parcela: Int? = null,
    val parcelas: Int? = null,
    /** mesmo id para todas as parcelas de uma compra. */
    val grupo: String? = null,
    /**
     * Gasto no cartao ja quitado. Serve para a parcela que ele ja pagou parar de pesar no "a pagar".
     * Gasto no Pix sai da caixinha na hora, entao nao usa este campo.
     */
    val pago: Boolean = false,
    @PrimaryKey override val id: String = newId(),
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

@Serializable
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val nome: String = "",
    val categoria: String = "outros",
    val valorMedio: Double? = null,
    val prioridade: String = "media",
    val aquario: String = "marinho",
    val observacao: String? = null,
    @Serializable(with = TsSerializer::class) val compradoEm: Long? = null,
    val valorPago: Double? = null,
    @PrimaryKey override val id: String = newId(),
    @Serializable(with = TsSerializer::class) val createdAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val updatedAt: Long = System.currentTimeMillis(),
    @Serializable(with = TsSerializer::class) override val deletedAt: Long? = null,
    @Transient override val pending: Boolean = false,
) : Syncable

/**
 * Alarme do tablet: toca na hora marcada e nao e tarefa (nao entra no calendario, no "Hoje", na nota
 * de saude nem nos relatorios). Fica so neste aparelho: nao sincroniza e nao entra no backup, porque
 * um alarme que toca so serve no tablet que esta ao lado do aquario.
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey val id: String = newId(),
    val titulo: String = "",
    val hora: Int = 7,
    val minuto: Int = 0,
    /** "unica", "diaria" ou "dias_da_semana". */
    val recorrencia: String = "unica",
    /** ISO: 1 = segunda ... 7 = domingo. So para "dias_da_semana". */
    val diasSemana: List<Int>? = null,
    /** Alto usa o som de alarme do tablet no volume cheio; normal toca mais baixo. */
    val alto: Boolean = true,
    val ativo: Boolean = true,
    /** Para "unica": o dia em que deve tocar (epoch ms). Depois de tocar, o alarme se desliga. */
    val diaUnico: Long? = null,
)
