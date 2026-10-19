package com.nlab.androidboot.apps.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * @author Thalys
 */
@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule