package com.mybrary.app.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.mybrary.app.BuildConfig
import com.mybrary.app.data.remote.AuthTokenStore
import com.mybrary.app.data.remote.GoogleBooksService
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

    private fun loggingInterceptor() = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    /** Plain client for unauthenticated APIs (Open Library). */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor())
            .build()

    /** Client for Google Sheets API — injects the current OAuth Bearer token on every request. */
    @Provides
    @Singleton
    @Named("sheets")
    fun provideSheetsOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = AuthTokenStore.get()
                val request = chain.request().newBuilder().apply {
                    if (token != null) header("Authorization", "Bearer $token")
                }.build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor())
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
    fun provideSheetsRetrofit(@Named("sheets") okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.SHEETS_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    @Named("googleBooks")
    fun provideGoogleBooksRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/books/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideOpenLibraryService(@Named("openLibrary") retrofit: Retrofit): OpenLibraryService =
        retrofit.create(OpenLibraryService::class.java)

    @Provides
    @Singleton
    fun provideGoogleBooksService(@Named("googleBooks") retrofit: Retrofit): GoogleBooksService =
        retrofit.create(GoogleBooksService::class.java)

    @Provides
    @Singleton
    fun provideGoogleSheetsService(@Named("sheets") retrofit: Retrofit): GoogleSheetsService =
        retrofit.create(GoogleSheetsService::class.java)
}
