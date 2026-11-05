package com.raad.browser.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * أيقونات مخصصة غير متوفرة في حزمة material-icons-core
 * (تحميل، إيقاف مؤقت، مجلد، سجل) — مرسومة كـ Vector مباشرة.
 */
object AppIcons {

    val Download: ImageVector by lazy {
        buildIcon("Download", "M19,9h-4V3H9v6H5l7,7 7,-7zM5,18v2h14v-2H5z")
    }

    val Pause: ImageVector by lazy {
        buildIcon("Pause", "M6,19h4V5H6v14zm8,-14v14h4V5h-4z")
    }

    val Folder: ImageVector by lazy {
        buildIcon("Folder", "M10,4H4c-1.1,0 -1.99,0.9 -1.99,2L2,18c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V8c0,-1.1 -0.9,-2 -2,-2h-8l-2,-2z")
    }

    val History: ImageVector by lazy {
        buildIcon(
            "History",
            "M11.99,2C6.47,2 2,6.48 2,12s4.47,10 9.99,10C17.52,22 22,17.52 22,12S17.52,2 11.99,2zM12,20c-4.42,0 -8,-3.58 -8,-8s3.58,-8 8,-8 8,3.58 8,8 -3.58,8 -8,8zm0.5,-13H11v6l5.25,3.15 -0.75,-1.23 -4.5,-2.67z"
        )
    }

    val Tabs: ImageVector by lazy {
        buildIcon("Tabs", "M19,3H5c-1.1,0 -2,0.9 -2,2v14c0,1.1 0.9,2 2,2h14c1.1,0 2,-0.9 2,-2V5c0,-1.1 -0.9,-2 -2,-2zm0,16H5V5h14v14z")
    }

    private fun buildIcon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black)
        ).build()
}
