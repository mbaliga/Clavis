package io.github.mbaliga.clavis.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/** The test tag convention used throughout the UI (`nav-learn`, `cloze-option-0`, `balloon-<sceneId>` …). */
fun Modifier.tagged(tag: String): Modifier = this.testTag(tag)
