package com.nlab.androidboot.apps.init

import android.content.Context
import androidx.startup.Initializer
import io.github.openflocon.flocon.Flocon

/**
 * @author Thalys
 */
class FloconInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        Flocon.initialize(context)
    }

    override fun dependencies(): List<Class<out Initializer<*>?>?> = emptyList()
}