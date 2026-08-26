package dev.hablock.app.ui.components

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val ICON_PX = 96

private fun Drawable.toImageBitmap(): ImageBitmap {
    (this as? BitmapDrawable)?.bitmap?.let { return it.asImageBitmap() }
    val bitmap = Bitmap.createBitmap(ICON_PX, ICON_PX, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
private fun rememberAppIcon(packageManager: PackageManager, packageName: String): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { packageManager.getApplicationIcon(packageName).toImageBitmap() }.getOrNull()
        }
    }
    return icon
}

/**
 * A real launcher icon clipped into a scalloped cookie — third-party icons instantly
 * look like they were designed for Hablock. Scale up slightly so the cookie's inner
 * scallops bite into the icon's own rounded corners instead of showing gaps.
 */
@Composable
fun AppIconCookie(
    packageName: String,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    polygon: RoundedPolygon = MaterialShapes.Cookie6Sided,
) {
    val packageManager = LocalContext.current.packageManager
    val icon = rememberAppIcon(packageManager, packageName)
    val shape = polygon.toShape()
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                icon,
                contentDescription = null,
                modifier = Modifier.size(size * 1.25f),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                label.take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private data class OrbitSlot(val x: Dp, val y: Dp, val size: Dp)

private val centerSlot = OrbitSlot(15.dp, 15.dp, 30.dp)

private val satelliteSlots = listOf(
    OrbitSlot(25.dp, 0.dp, 20.dp),
    OrbitSlot(40.dp, 21.dp, 20.dp),
    OrbitSlot(22.dp, 40.dp, 20.dp),
    OrbitSlot(0.dp, 19.dp, 20.dp),
)

private val orbitShapes = listOf(
    { MaterialShapes.Circle },
    { MaterialShapes.Sunny },
    { MaterialShapes.Clover4Leaf },
    { MaterialShapes.Cookie9Sided },
    { MaterialShapes.Flower },
    { MaterialShapes.Cookie12Sided },
)

/**
 * The blocked apps as a playful bunch: one icon holds the middle, the rest orbit it —
 * which app sits where, and in which expressive form, is rolled fresh every time.
 */
@Composable
fun AppShapeCluster(
    packages: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val arrangement = remember(packages) {
        val visible = packages.shuffled().take(1 + satelliteSlots.size)
        val slots = listOf(centerSlot) + satelliteSlots.shuffled()
        val shapes = orbitShapes.shuffled()
        visible.mapIndexed { index, app -> Triple(app, slots[index], shapes[index % shapes.size]) }
    }
    val rest = packages.size - arrangement.size
    Box(modifier.size(60.dp)) {
        arrangement.forEach { (app, slot, polygon) ->
            AppIconCookie(
                app.first,
                app.second,
                Modifier.offset(x = slot.x, y = slot.y),
                size = slot.size,
                polygon = polygon(),
            )
        }
        if (rest > 0) {
            Box(
                Modifier
                    .offset(x = 41.dp, y = 41.dp)
                    .size(19.dp)
                    .clip(MaterialShapes.Cookie6Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+$rest",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
