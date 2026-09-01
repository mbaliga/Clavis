package io.github.mbaliga.clavis.core

import android.os.Build

actual fun platformName(): String = "Android ${Build.VERSION.SDK_INT}"
