package io.agora.onetoone

import android.app.Application

class MApp : Application() {

    companion object {
        private lateinit var app: Application

        @JvmStatic
        fun instance(): Application {
            return app
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = this
    }
}