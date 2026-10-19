package com.nlab.androidboot.apps.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * @author Thalys
 */
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelScopeModule