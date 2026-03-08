package com.mybrary.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mybrary.app.BuildConfig
import com.mybrary.app.data.remote.GoogleSheetsService
import com.mybrary.app.data.remote.OpenLibraryService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
                }
            )
            .build()

    @Provides
    @Singleton
    @Named("openLibrary")
    fun provideOpenLibraryRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.OPEN_LIBRARY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Named("sheets")
    fun provideSheetsRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.SHEETS_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideOpenLibraryService(@Named("openLibrary") retrofit: Retrofit): OpenLibraryService =
        retrofit.create(OpenLibraryService::class.java)

    @Provides
    @Singleton
    fun provideGoogleSheetsService(@Named("sheets") retrofit: Retrofit): GoogleSheetsService =
        retrofit.create(GoogleSheetsService::class.java)
}
