package br.com.lunelli.lulucoreef.data

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
