package com.nlab.androidboot.apps.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import io.github.openflocon.flocon.okhttp.FloconOkhttpInterceptor
import okhttp3.Interceptor

/**
 * @author Thalys
 */
@Module
@InstallIn(SingletonComponent::class)
object DebugAppScopeProvideModule {
    @Provides
    @IntoSet
    fun provideFloconOkhttpInterceptor(): Interceptor = FloconOkhttpInterceptor()
}