package com.example.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight vector definitions for icons used in the app, eliminating the
 * need for the heavy 20MB+ `material-icons-extended` dependency.
 */
private fun createVector(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector {
    return ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.NonZero,
        pathBuilder = pathBuilder
    ).build()
}

val Icons.Filled.ExpandMore: ImageVector by lazy {
    createVector("ExpandMore") {
        moveTo(16.59f, 8.59f)
        lineTo(12f, 13.17f)
        lineTo(7.41f, 8.59f)
        lineTo(6f, 10f)
        lineToRelative(6f, 6f)
        lineToRelative(6f, -6f)
        close()
    }
}

val Icons.Filled.ExpandLess: ImageVector by lazy {
    createVector("ExpandLess") {
        moveTo(12f, 8f)
        lineToRelative(-6f, 6f)
        lineToRelative(1.41f, 1.41f)
        lineTo(12f, 10.83f)
        lineToRelative(4.59f, 4.58f)
        lineTo(18f, 14f)
        close()
    }
}

val Icons.Filled.ArrowDownward: ImageVector by lazy {
    createVector("ArrowDownward") {
        moveTo(20f, 12f)
        lineToRelative(-1.41f, -1.41f)
        lineTo(13f, 16.17f)
        verticalLineTo(4f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(12.17f)
        lineToRelative(-5.58f, -5.59f)
        lineTo(4f, 12f)
        lineToRelative(8f, 8f)
        lineToRelative(8f, -8f)
        close()
    }
}

val Icons.Filled.ArrowUpward: ImageVector by lazy {
    createVector("ArrowUpward") {
        moveTo(4f, 12f)
        lineToRelative(1.41f, 1.41f)
        lineTo(11f, 7.83f)
        verticalLineTo(20f)
        horizontalLineToRelative(2f)
        verticalLineTo(7.83f)
        lineToRelative(5.58f, 5.59f)
        lineTo(20f, 12f)
        lineToRelative(-8f, -8f)
        lineToRelative(-8f, 8f)
        close()
    }
}

val Icons.Filled.Print: ImageVector by lazy {
    createVector("Print") {
        moveTo(19f, 8f)
        horizontalLineTo(5f)
        curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(12f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-6f)
        curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
        close()
        moveTo(16f, 19f)
        horizontalLineTo(8f)
        verticalLineToRelative(-5f)
        horizontalLineToRelative(8f)
        verticalLineToRelative(5f)
        close()
        moveTo(19f, 12f)
        curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
        reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
        reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
        reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
        close()
        moveTo(18f, 3f)
        horizontalLineTo(6f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(12f)
        verticalLineTo(3f)
        close()
    }
}

val Icons.Filled.PictureAsPdf: ImageVector by lazy {
    createVector("PictureAsPdf") {
        moveTo(20f, 2f)
        horizontalLineTo(8f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(12f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(12f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(4f, 6f)
        horizontalLineTo(2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        verticalLineToRelative(-2f)
        horizontalLineTo(4f)
        verticalLineTo(6f)
        close()
    }
}

val Icons.Filled.Save: ImageVector by lazy {
    createVector("Save") {
        moveTo(17f, 3f)
        horizontalLineTo(5f)
        curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(7f)
        lineToRelative(-4f, -4f)
        close()
        moveTo(12f, 19f)
        curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f)
        reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
        reflectiveCurveToRelative(3f, 1.34f, 3f, 3f)
        reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f)
        close()
        moveTo(15f, 9f)
        horizontalLineTo(5f)
        verticalLineTo(5f)
        horizontalLineToRelative(10f)
        verticalLineToRelative(4f)
        close()
    }
}

val Icons.Filled.ReceiptLong: ImageVector by lazy {
    createVector("ReceiptLong") {
        moveTo(19.5f, 3.5f)
        lineTo(18f, 2f)
        lineToRelative(-1.5f, 1.5f)
        lineTo(15f, 2f)
        lineToRelative(-1.5f, 1.5f)
        lineTo(12f, 2f)
        lineToRelative(-1.5f, 1.5f)
        lineTo(9f, 2f)
        lineTo(7.5f, 3.5f)
        lineTo(6f, 2f)
        verticalLineToRelative(14f)
        horizontalLineTo(3f)
        verticalLineToRelative(3f)
        curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
        horizontalLineToRelative(12f)
        curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
        verticalLineTo(2f)
        lineToRelative(-1.5f, 1.5f)
        close()
        moveTo(15f, 20f)
        horizontalLineTo(6f)
        curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
        verticalLineToRelative(-1f)
        horizontalLineToRelative(10f)
        verticalLineToRelative(2f)
        close()
        moveTo(19f, 19f)
        curveToRelative(0f, 0.55f, -0.45f, 1f, -1f, 1f)
        reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
        verticalLineToRelative(-3f)
        horizontalLineTo(8f)
        verticalLineTo(5f)
        horizontalLineToRelative(11f)
        verticalLineToRelative(14f)
        close()
    }
}

val Icons.Filled.Storefront: ImageVector by lazy {
    createVector("Storefront") {
        moveTo(21.9f, 8.89f)
        lineToRelative(-1.05f, -4.37f)
        curveToRelative(-0.22f, -0.9f, -1f, -1.52f, -1.91f, -1.52f)
        horizontalLineTo(5.05f)
        curveToRelative(-0.9f, 0f, -1.69f, 0.63f, -1.9f, 1.52f)
        lineTo(2.1f, 8.89f)
        curveToRelative(-0.24f, 1.02f, 0.02f, 2.06f, 0.74f, 2.77f)
        curveToRelative(0.55f, 0.54f, 1.3f, 0.84f, 2.09f, 0.84f)
        curveToRelative(0.97f, 0f, 1.83f, -0.47f, 2.37f, -1.22f)
        curveToRelative(0.53f, 0.74f, 1.4f, 1.22f, 2.37f, 1.22f)
        curveToRelative(0.97f, 0f, 1.84f, -0.48f, 2.37f, -1.22f)
        curveToRelative(0.53f, 0.74f, 1.4f, 1.22f, 2.37f, 1.22f)
        curveToRelative(0.97f, 0f, 1.83f, -0.47f, 2.37f, -1.22f)
        curveToRelative(0.53f, 0.75f, 1.4f, 1.22f, 2.37f, 1.22f)
        curveToRelative(0.79f, 0f, 1.54f, -0.3f, 2.09f, -0.84f)
        curveToRelative(0.72f, -0.71f, 0.98f, -1.75f, 0.74f, -2.77f)
        close()
        moveTo(4f, 14f)
        verticalLineToRelative(6f)
        horizontalLineToRelative(16f)
        verticalLineToRelative(-6f)
        horizontalLineTo(4f)
        close()
    }
}

val Icons.Filled.Inventory2: ImageVector by lazy {
    createVector("Inventory2") {
        moveTo(20f, 2f)
        horizontalLineTo(4f)
        curveToRelative(-1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(3.01f)
        curveToRelative(0f, 0.72f, 0.43f, 1.34f, 1f, 1.69f)
        verticalLineTo(20f)
        curveToRelative(0f, 1.1f, 1.1f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(0.9f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(8.7f)
        curveToRelative(0.57f, -0.35f, 1f, -0.97f, 1f, -1.69f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -1f, -2f, -2f, -2f)
        close()
        moveTo(15f, 14f)
        horizontalLineTo(9f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(6f)
        verticalLineToRelative(2f)
        close()
        moveTo(20f, 7f)
        horizontalLineTo(4f)
        verticalLineTo(4f)
        horizontalLineToRelative(16f)
        verticalLineToRelative(3f)
        close()
    }
}

val Icons.Filled.People: ImageVector by lazy {
    createVector("People") {
        moveTo(16.5f, 13f)
        curveToRelative(-1.2f, 0f, -3.07f, 0.34f, -4.5f, 1f)
        curveToRelative(-1.43f, -0.66f, -3.3f, -1f, -4.5f, -1f)
        curveTo(5.13f, 13f, 1f, 14.13f, 1f, 16.25f)
        verticalLineTo(19f)
        horizontalLineToRelative(22f)
        verticalLineToRelative(-2.75f)
        curveTo(23f, 14.13f, 18.87f, 13f, 16.5f, 13f)
        close()
        moveTo(7.5f, 12f)
        curveToRelative(1.93f, 0f, 3.5f, -1.57f, 3.5f, -3.5f)
        reflectiveCurveTo(9.43f, 5f, 7.5f, 5f)
        reflectiveCurveTo(4f, 6.57f, 4f, 8.5f)
        reflectiveCurveTo(5.57f, 12f, 7.5f, 12f)
        close()
        moveTo(16.5f, 12f)
        curveToRelative(1.93f, 0f, 3.5f, -1.57f, 3.5f, -3.5f)
        reflectiveCurveTo(18.43f, 5f, 16.5f, 5f)
        reflectiveCurveTo(13f, 6.57f, 13f, 8.5f)
        reflectiveCurveToRelative(1.57f, 3.5f, 3.5f, 3.5f)
        close()
    }
}

val Icons.Filled.PeopleOutline: ImageVector get() = Icons.Filled.People

val Icons.Filled.PersonAdd: ImageVector by lazy {
    createVector("PersonAdd") {
        moveTo(15f, 12f)
        curveToRelative(2.21f, 0f, 4f, -1.79f, 4f, -4f)
        reflectiveCurveToRelative(-1.79f, -4f, -4f, -4f)
        reflectiveCurveToRelative(-4f, 1.79f, -4f, 4f)
        reflectiveCurveToRelative(1.79f, 4f, 4f, 4f)
        close()
        moveTo(6f, 10f)
        verticalLineTo(7f)
        horizontalLineTo(4f)
        verticalLineToRelative(3f)
        horizontalLineTo(1f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(-3f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(-2f)
        horizontalLineTo(6f)
        close()
        moveTo(15f, 14f)
        curveToRelative(-2.67f, 0f, -8f, 1.34f, -8f, 4f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(16f)
        verticalLineToRelative(-2f)
        curveToRelative(0f, -2.66f, -5.33f, -4f, -8f, -4f)
        close()
    }
}

val Icons.Filled.Backup: ImageVector by lazy {
    createVector("Backup") {
        moveTo(19.35f, 10.04f)
        curveTo(18.67f, 6.59f, 15.64f, 4f, 12f, 4f)
        curveTo(9.11f, 4f, 6.6f, 5.64f, 5.35f, 8.04f)
        curveTo(2.34f, 8.36f, 0f, 10.91f, 0f, 14f)
        curveToRelative(0f, 3.31f, 2.69f, 6f, 6f, 6f)
        horizontalLineToRelative(13f)
        curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
        curveToRelative(0f, -2.64f, -2.05f, -4.78f, -4.65f, -4.96f)
        close()
        moveTo(14f, 13f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(-4f)
        verticalLineToRelative(-4f)
        horizontalLineTo(7f)
        lineToRelative(5f, -5f)
        lineToRelative(5f, 5f)
        horizontalLineToRelative(-3f)
        close()
    }
}

val Icons.Filled.AddBusiness: ImageVector by lazy {
    createVector("AddBusiness") {
        moveTo(15f, 17f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(-3f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(-3f)
        verticalLineTo(9f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(3f)
        horizontalLineToRelative(-3f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(3f)
        verticalLineToRelative(3f)
        close()
        moveTo(2f, 4f)
        horizontalLineToRelative(15f)
        verticalLineToRelative(2f)
        horizontalLineTo(2f)
        close()
        moveTo(2f, 18f)
        horizontalLineToRelative(8f)
        verticalLineToRelative(2f)
        horizontalLineTo(2f)
        close()
    }
}

val Icons.Filled.AddCircleOutline: ImageVector by lazy {
    createVector("AddCircleOutline") {
        moveTo(13f, 7f)
        horizontalLineToRelative(-2f)
        verticalLineToRelative(4f)
        horizontalLineTo(7f)
        verticalLineToRelative(2f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(4f)
        horizontalLineToRelative(2f)
        verticalLineToRelative(-4f)
        horizontalLineToRelative(4f)
        verticalLineToRelative(-2f)
        horizontalLineToRelative(-4f)
        verticalLineTo(7f)
        close()
        moveTo(12f, 2f)
        curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
        reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
        reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
        reflectiveCurveTo(17.52f, 2f, 12f, 2f)
        close()
        moveTo(12f, 20f)
        curveToRelative(-4.41f, 0f, -8f, -3.59f, -8f, -8f)
        reflectiveCurveToRelative(3.59f, -8f, 8f, -8f)
        reflectiveCurveToRelative(8f, 3.59f, 8f, 8f)
        reflectiveCurveToRelative(-3.59f, 8f, -8f, 8f)
        close()
    }
}

val Icons.Filled.Image: ImageVector by lazy {
    createVector("Image") {
        moveTo(21f, 19f)
        verticalLineTo(5f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        horizontalLineTo(5f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(14f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        close()
        moveTo(8.5f, 13.5f)
        lineToRelative(2.5f, 3.01f)
        lineTo(14.5f, 12f)
        lineToRelative(4.5f, 6f)
        horizontalLineTo(5f)
        lineToRelative(3.5f, -4.5f)
        close()
    }
}
