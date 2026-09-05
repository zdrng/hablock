package dev.hablock.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.group
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few glyphs material-icons-core lacks, drawn by hand to stay dependency-free. */
object HablockIcons {

    /** Material Symbols `directions_run`, rounded fill. */
    val Run: ImageVector by lazy {
        materialSymbol(
            "Hablock.Run",
            "M520-80v-200l-84-80-31 138q-4 16-17.5 24.5T358-192l-198-40q-17-3-26-17t-6-31q3-17 17-26.5t31-5.5l152 32 64-324-72 28v96q0 17-11.5 28.5T280-440q-17 0-28.5-11.5T240-480v-122q0-12 6.5-21.5T264-638l134-58q35-15 51.5-19.5T480-720q21 0 39 11t29 29l40 64q21 34 54.5 59t77.5 33q17 3 28.5 15t11.5 29q0 17-11.5 28t-27.5 9q-54-8-101-33.5T540-540l-24 120 72 68q6 6 9 13.5t3 15.5v243q0 17-11.5 28.5T560-40q-17 0-28.5-11.5T520-80Zm20-660q-33 0-56.5-23.5T460-820q0-33 23.5-56.5T540-900q33 0 56.5 23.5T620-820q0 33-23.5 56.5T540-740Z",
        )
    }

    /** Material Symbols `self_improvement`. */
    val Meditate: ImageVector by lazy {
        materialSymbol(
            "Hablock.Meditate",
            "M272-160q-30 0-51-21t-21-51q0-21 12-39.5t32-26.5l156-62v-90q-54 63-125.5 96.5T120-320v-80q68 0 123.5-28T344-508l54-64q12-14 28-21t34-7h40q18 0 34 7t28 21l54 64q45 52 100.5 80T840-400v80q-83 0-154.5-33.5T560-450v90l156 62q20 8 32 26.5t12 39.5q0 30-21 51t-51 21H400v-20q0-26 17-43t43-17h120q9 0 14.5-5.5T600-260q0-9-5.5-14.5T580-280H460q-42 0-71 29t-29 71v20h-88Zm151.5-503.5Q400-687 400-720t23.5-56.5Q447-800 480-800t56.5 23.5Q560-753 560-720t-23.5 56.5Q513-640 480-640t-56.5-23.5Z",
        )
    }

    /** A lifter mid-press — no Material Symbol has one, so it's drawn by hand. */
    val Lift: ImageVector by lazy {
        ImageVector.Builder("Hablock.Lift", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 7.7f)
                arcTo(1.9f, 1.9f, 0f, true, true, 11.99f, 7.7f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4.2f, 5.6f)
                lineTo(19.8f, 5.6f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.6f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6.6f, 3.4f); lineTo(6.6f, 7.8f)
                moveTo(17.4f, 3.4f); lineTo(17.4f, 7.8f)
                moveTo(12f, 12f); lineTo(12f, 15.2f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8.1f, 6.6f)
                lineTo(10.3f, 11.4f)
                moveTo(15.9f, 6.6f)
                lineTo(13.7f, 11.4f)
                moveTo(12f, 15.2f)
                lineTo(9.1f, 17.6f)
                lineTo(9.1f, 20.8f)
                moveTo(12f, 15.2f)
                lineTo(14.9f, 17.6f)
                lineTo(14.9f, 20.8f)
            }
        }.build()
    }

    private fun materialSymbol(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 960f, 960f).apply {
            group(translationY = 960f) {
                addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
            }
        }.build()

    val Clock: ImageVector by lazy {
        ImageVector.Builder("Hablock.Clock", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 3.5f)
                arcTo(8.5f, 8.5f, 0f, true, true, 11.99f, 3.5f)
                close()
                moveTo(12f, 7.5f)
                lineTo(12f, 12f)
                lineTo(15.5f, 14f)
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder("Hablock.Pause", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(8f, 5f); lineTo(10.5f, 5f)
                arcTo(1f, 1f, 0f, false, true, 11.5f, 6f)
                lineTo(11.5f, 18f)
                arcTo(1f, 1f, 0f, false, true, 10.5f, 19f)
                lineTo(8f, 19f)
                arcTo(1f, 1f, 0f, false, true, 7f, 18f)
                lineTo(7f, 6f)
                arcTo(1f, 1f, 0f, false, true, 8f, 5f)
                close()
                moveTo(13.5f, 5f); lineTo(16f, 5f)
                arcTo(1f, 1f, 0f, false, true, 17f, 6f)
                lineTo(17f, 18f)
                arcTo(1f, 1f, 0f, false, true, 16f, 19f)
                lineTo(13.5f, 19f)
                arcTo(1f, 1f, 0f, false, true, 12.5f, 18f)
                lineTo(12.5f, 6f)
                arcTo(1f, 1f, 0f, false, true, 13.5f, 5f)
                close()
            }
        }.build()
    }

    val Copy: ImageVector by lazy {
        ImageVector.Builder("Hablock.Copy", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
            ) {
                moveTo(9f, 9f)
                lineTo(18f, 9f)
                arcTo(1.5f, 1.5f, 0f, false, true, 19.5f, 10.5f)
                lineTo(19.5f, 19f)
                arcTo(1.5f, 1.5f, 0f, false, true, 18f, 20.5f)
                lineTo(9f, 20.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 7.5f, 19f)
                lineTo(7.5f, 10.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 9f, 9f)
                close()
                moveTo(4.5f, 15f)
                lineTo(4.5f, 5.5f)
                arcTo(2f, 2f, 0f, false, true, 6.5f, 3.5f)
                lineTo(15f, 3.5f)
            }
        }.build()
    }

    val ArrowUp: ImageVector by lazy {
        ImageVector.Builder("Hablock.ArrowUp", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 19f)
                lineTo(12f, 6f)
                moveTo(6.5f, 11f)
                lineTo(12f, 5.5f)
                lineTo(17.5f, 11f)
            }
        }.build()
    }

    val Minus: ImageVector by lazy {
        ImageVector.Builder("Hablock.Minus", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2.4f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(6f, 12f)
                lineTo(18f, 12f)
            }
        }.build()
    }

    val LockOpen: ImageVector by lazy {
        ImageVector.Builder("Hablock.LockOpen", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(8f, 11f)
                lineTo(16f, 11f)
                arcTo(1.5f, 1.5f, 0f, false, true, 17.5f, 12.5f)
                lineTo(17.5f, 17.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 16f, 19f)
                lineTo(8f, 19f)
                arcTo(1.5f, 1.5f, 0f, false, true, 6.5f, 17.5f)
                lineTo(6.5f, 12.5f)
                arcTo(1.5f, 1.5f, 0f, false, true, 8f, 11f)
                close()
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9.5f, 11f)
                lineTo(9.5f, 8f)
                arcTo(2.5f, 2.5f, 0f, false, true, 14.5f, 5.5f)
            }
        }.build()
    }

    val Timer: ImageVector by lazy {
        ImageVector.Builder("Hablock.Timer", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(12f, 8f)
                lineTo(12f, 12f)
                lineTo(15f, 14f)
            }
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(12f, 4.5f)
                arcTo(7.5f, 7.5f, 0f, true, true, 11.99f, 4.5f)
                close()
            }
        }.build()
    }
}
