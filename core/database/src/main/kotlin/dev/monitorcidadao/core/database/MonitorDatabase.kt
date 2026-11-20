package dev.monitorcidadao.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import dev.monitorcidadao.core.database.dao.FeedDao
import dev.monitorcidadao.core.database.dao.FilaEnvioDao
import dev.monitorcidadao.core.database.dao.PessoaDao
import dev.monitorcidadao.core.database.dao.SeguidoDao
import dev.monitorcidadao.core.database.entity.ControleEntity
import dev.monitorcidadao.core.database.entity.FeedPaginaEntity
import dev.monitorcidadao.core.database.entity.FilaEnvioEntity
import dev.monitorcidadao.core.database.entity.PessoaEntity
import dev.monitorcidadao.core.database.entity.PostEntity
import dev.monitorcidadao.core.database.entity.SeguidoEntity

object DatabaseConfig {
    const val NAME = "monitor_cidadao.db"
}

/**
 * Cache em SQLite (Room): `pessoa`, `post`, `feed_pagina`, `seguido`, `fila_envio`, `controle`.
 * Esquema exportado em `core/database/schemas/` (um JSON por versão).
 */
@Database(
    entities = [PessoaEntity::class, PostEntity::class, FeedPaginaEntity::class, SeguidoEntity::class, FilaEnvioEntity::class, ControleEntity::class],
    version = MonitorDatabase.VERSAO,
    exportSchema = true,
)
@TypeConverters(Conversores::class)
abstract class MonitorDatabase : RoomDatabase() {
    abstract fun pessoaDao(): PessoaDao
    abstract fun feedDao(): FeedDao
    abstract fun seguidoDao(): SeguidoDao
    abstract fun filaEnvioDao(): FilaEnvioDao

    companion object {
        const val VERSAO = 1

        fun create(context: Context): MonitorDatabase =
            Room.databaseBuilder(context.applicationContext, MonitorDatabase::class.java, DatabaseConfig.NAME)
                .addMigrations(*Migrations.todas)
                .build()

        /** Banco em memória (testes). */
        fun inMemory(context: Context): MonitorDatabase =
            Room.inMemoryDatabaseBuilder(context.applicationContext, MonitorDatabase::class.java).build()
    }
}

/**
 * Migrations numeradas: cada mudança de esquema sobe [MonitorDatabase.VERSAO] e entra aqui como
 * `MIGRATION_<de>_<para>` (SQL explícito, testado contra o JSON exportado da versão anterior).
 * Sem `fallbackToDestructiveMigration`: `seguido` e `fila_envio` são dados do usuário, não cache.
 * Versão 1 = esquema inicial, nenhuma migration ainda.
 */
object Migrations {
    val todas: Array<Migration> = emptyArray()
}
