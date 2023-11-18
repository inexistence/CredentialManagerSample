package com.janbean.common

import android.app.Application

object AppUtils {
    private var app: Application? = null

    fun setApplication(application: Application) {
        app = application
    }

    fun getApplication(): Application? {
        return app
    }
}