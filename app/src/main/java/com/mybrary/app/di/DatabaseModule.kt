package com.mybrary.app.di

import android.content.Context
import androidx.room.Room
import com.mybrary.app.data.local.AppDatabase
import com.mybrary.app.data.local.BookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "mybrary.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()
}
