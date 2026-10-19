package com.nlab.androidboot.apps.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * @author Thalys
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(
        interceptors: Set<Interceptor>
    ): OkHttpClient = OkHttpClient.Builder()
        .apply { interceptors.forEach { addInterceptor(it) } }
        .build()

    @Provides
    @Singleton
    fun provideImageLoader(
        okHttpClientProvider: dagger.Lazy<OkHttpClient>,
        @ApplicationContext context: Context,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient { okHttpClientProvider.get() }
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve("image_cache"))
                .maxSizeBytes(100L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .respectCacheHeaders(false)
        .build()
}