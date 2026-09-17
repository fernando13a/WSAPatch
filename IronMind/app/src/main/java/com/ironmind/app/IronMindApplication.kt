package com.ironmind.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point. [HiltAndroidApp] triggers Hilt's code generation and DI graph. */
@HiltAndroidApp
class IronMindApplication : Application()
