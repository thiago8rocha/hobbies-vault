package com.hobbiesvault.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hobbiesvault.data.db.dao.*
import com.hobbiesvault.data.db.entity.*

@Database(
    entities = [
        MediaItemEntity::class,
        MediaDetailsCacheEntity::class,
        MovieListEntity::class,
        MovieListItemEntity::class,
        SeriesEpisodeEntity::class,
        GameCacheEntity::class,
        MangaReviewEntity::class,
        BookQuoteEntity::class,
    ],
    version = 13,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mediaItemDao(): MediaItemDao
    abstract fun mediaDetailsCacheDao(): MediaDetailsCacheDao
    abstract fun movieListDao(): MovieListDao
    abstract fun seriesEpisodeDao(): SeriesEpisodeDao
    abstract fun gameCacheDao(): GameCacheDao
    abstract fun mangaReviewDao(): MangaReviewDao
    abstract fun bookQuoteDao(): BookQuoteDao

    companion object {
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Resenha de livro — separada do log de comentários de leitura (`comentario`).
                db.execSQL("ALTER TABLE media_items ADD COLUMN resenha_livro TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS book_quotes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        media_item_id INTEGER NOT NULL,
                        citacao TEXT NOT NULL,
                        comentario TEXT,
                        criado_em_ms INTEGER NOT NULL
                    )
                """)
            }
        }
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Anotações livres do usuário — independentes da resenha de mangá/comentários de
                // leitura de livro, disponíveis em qualquer tipo de mídia via o menu "...".
                db.execSQL("ALTER TABLE media_items ADD COLUMN anotacoes_pessoais TEXT")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN titulo_resenha TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS manga_reviews (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        media_item_id INTEGER NOT NULL,
                        nota REAL,
                        titulo_resenha TEXT,
                        resenha TEXT,
                        concluido_em_ms INTEGER NOT NULL
                    )
                """)
            }
        }
        // Migrations from Drift/Flutter schema (column names stay in original snake_case)
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN data_conclusao_historia_ms INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN data_conclusao_extras_ms INTEGER")
                db.execSQL("ALTER TABLE media_items ADD COLUMN data_conclusao_platina_ms INTEGER")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE filme_listas ADD COLUMN descricao TEXT")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN data_releitura_ms INTEGER")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media_items ADD COLUMN data_inicio_leitura_ms INTEGER")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE serie_episodios_assistidos ADD COLUMN nome_serie TEXT")
                db.execSQL("ALTER TABLE serie_episodios_assistidos ADD COLUMN capa_url TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS serie_episodios_assistidos (
                        media_item_id INTEGER NOT NULL,
                        temporada INTEGER NOT NULL,
                        episodio INTEGER NOT NULL,
                        assistido_em_ms INTEGER NOT NULL,
                        nome_episodio TEXT,
                        PRIMARY KEY (media_item_id, temporada, episodio)
                    )
                """)
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_cache (
                        name TEXT NOT NULL PRIMARY KEY,
                        gb_id INTEGER,
                        igdb_id INTEGER,
                        release_date TEXT,
                        deck TEXT,
                        capa_url TEXT,
                        summary TEXT,
                        storyline TEXT,
                        generos TEXT,
                        plataformas TEXT,
                        updated_at INTEGER,
                        capa_ok INTEGER NOT NULL DEFAULT 0
                    )
                """)
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS filme_listas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        nome TEXT NOT NULL,
                        criada_em_ms INTEGER NOT NULL
                    )
                """)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS filme_lista_itens (
                        lista_id INTEGER NOT NULL,
                        media_item_id INTEGER NOT NULL,
                        PRIMARY KEY (lista_id, media_item_id)
                    )
                """)
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS media_details_cache (
                        media_item_id INTEGER NOT NULL PRIMARY KEY,
                        dados_json TEXT NOT NULL,
                        ultima_verificacao_ms INTEGER NOT NULL
                    )
                """)
            }
        }
    }
}
