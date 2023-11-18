package com.janbean.sample

import android.app.Application
import android.content.Context
import com.janbean.common.AppUtils

class MyApplication: Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        AppUtils.setApplication(this)
    }

    override fun onCreate() {
        super.onCreate()
    }
}
