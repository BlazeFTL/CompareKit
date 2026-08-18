package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

fun AppTheme.getColorScheme(darkTheme: Boolean): ColorScheme {
    val palette = when (this) {
        AppTheme.FOREST -> if (darkTheme) ThemePalettes.ForestDark else ThemePalettes.ForestLight
        AppTheme.OCEAN -> if (darkTheme) ThemePalettes.OceanDark else ThemePalettes.OceanLight
        AppTheme.TEAL -> if (darkTheme) ThemePalettes.TealDark else ThemePalettes.TealLight
        AppTheme.PURPLE -> if (darkTheme) ThemePalettes.PurpleDark else ThemePalettes.PurpleLight
        AppTheme.AMBER -> if (darkTheme) ThemePalettes.AmberDark else ThemePalettes.AmberLight
        AppTheme.ROSE -> if (darkTheme) ThemePalettes.RoseDark else ThemePalettes.RoseLight
        AppTheme.SLATE -> if (darkTheme) ThemePalettes.SlateDark else ThemePalettes.SlateLight
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onPrimary = palette.onPrimary,
            onSecondary = palette.onSecondary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            onSurfaceVariant = palette.onSurfaceVariant,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondaryContainer = palette.primaryContainer,
            onSecondaryContainer = palette.onPrimaryContainer,
            tertiaryContainer = palette.primaryContainer,
            onTertiaryContainer = palette.onPrimaryContainer,
            outline = palette.secondary.copy(alpha = 0.5f),
            outlineVariant = palette.outlineVariant
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            surfaceVariant = palette.surfaceVariant,
            onPrimary = palette.onPrimary,
            onSecondary = palette.onSecondary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            onSurfaceVariant = palette.onSurfaceVariant,
            primaryContainer = palette.primaryContainer,
            onPrimaryContainer = palette.onPrimaryContainer,
            secondaryContainer = palette.primaryContainer,
            onSecondaryContainer = palette.onPrimaryContainer,
            tertiaryContainer = palette.primaryContainer,
            onTertiaryContainer = palette.onPrimaryContainer,
            outline = palette.secondary.copy(alpha = 0.3f),
            outlineVariant = palette.outlineVariant
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appTheme: AppTheme = AppTheme.FOREST,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> appTheme.getColorScheme(darkTheme)
        }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
