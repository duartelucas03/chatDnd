package com.example.easychat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase


/** Claude AI - início
 * Prompt: Crie a camada de banco de dados local com Room pra salvar mensagens no celular. Preciso de uma tabela de mensagens com todos os campos principais, um DAO com as queries básicas, o banco em si como singleton, e um repositório que converte entre a entidade do banco e o modelo do app.
 */
@Database(entities = [MessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "easychat_db"
                ).fallbackToDestructiveMigration()
                 .build()
                 .also { INSTANCE = it }
            }
    }
}
/** Claude AI - final */