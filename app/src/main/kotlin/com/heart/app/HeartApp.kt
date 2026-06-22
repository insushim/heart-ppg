package com.heart.app

import android.app.Application
import com.heart.app.data.HeartDatabase

class HeartApp : Application() {
    val database: HeartDatabase by lazy { HeartDatabase.create(this) }
}
