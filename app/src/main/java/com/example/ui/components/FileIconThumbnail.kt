package com.example.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ApkIconCache {
    private val memoryCache = LruCache<String, ImageBitmap>(80)

    fun get(key: String): ImageBitmap? = memoryCache.get(key)
    fun put(key: String, bitmap: ImageBitmap) {
        memoryCache.put(key, bitmap)
    }

    suspend fun loadIcon(context: Context, file: File): ImageBitmap? {
        val path = file.absolutePath
        get(path)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || !file.isFile || file.length() == 0L) return@withContext null
                val pm = context.packageManager
                val packageInfo = pm.getPackageArchiveInfo(path, PackageManager.GET_META_DATA) ?: return@withContext null
                val appInfo = packageInfo.applicationInfo ?: return@withContext null
                appInfo.sourceDir = path
                appInfo.publicSourceDir = path
                val drawable = appInfo.loadIcon(pm) ?: return@withContext null
                val bitmap = drawableToBitmap(drawable)
                val imageBitmap = bitmap.asImageBitmap()
                put(path, imageBitmap)
                imageBitmap
            } catch (e: Throwable) {
                null
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}

@Composable
fun FileThumbnailIcon(
    file: File,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isDir = file.isDirectory
    val nameLower = file.name.lowercase()
    val isApk = !isDir && (nameLower.endsWith(".apk") || nameLower.endsWith(".apks") || nameLower.endsWith(".xapk"))
    val isZip = !isDir && (nameLower.endsWith(".zip") || nameLower.endsWith(".jar") || nameLower.endsWith(".aab"))
    val isDex = !isDir && nameLower.endsWith(".dex")

    if (isApk) {
        val cached = remember(file.absolutePath) { ApkIconCache.get(file.absolutePath) }
        val apkIcon by produceState<ImageBitmap?>(initialValue = cached, key1 = file.absolutePath) {
            if (value == null) {
                value = ApkIconCache.loadIcon(context, file)
            }
        }

        if (apkIcon != null) {
            Image(
                bitmap = apkIcon!!,
                contentDescription = file.name,
                modifier = modifier
                    .clip(RoundedCornerShape(6.dp))
            )
        } else {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = modifier
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Filled.Android,
                        contentDescription = file.name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    } else {
        val (icon, bgTint, iconTint) = when {
            isDir -> Triple(
                Icons.Filled.Folder,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.primary
            )
            isZip -> Triple(
                Icons.Filled.FolderZip,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.secondary
            )
            isDex -> Triple(
                Icons.Outlined.Code,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                MaterialTheme.colorScheme.tertiary
            )
            nameLower.endsWith(".xml") || nameLower.endsWith(".json") || nameLower.endsWith(".yaml") -> Triple(
                Icons.Outlined.DataObject,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                MaterialTheme.colorScheme.primary
            )
            nameLower.endsWith(".smali") || nameLower.endsWith(".java") || nameLower.endsWith(".kt") || nameLower.endsWith(".c") || nameLower.endsWith(".cpp") -> Triple(
                Icons.Outlined.Code,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.tertiary
            )
            nameLower.endsWith(".png") || nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") || nameLower.endsWith(".webp") || nameLower.endsWith(".gif") -> Triple(
                Icons.Outlined.Image,
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                MaterialTheme.colorScheme.secondary
            )
            else -> Triple(
                Icons.Outlined.Article,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.10f),
                MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            shape = RoundedCornerShape(6.dp),
            color = bgTint,
            modifier = modifier
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
