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

private val DarkColorScheme =
  darkColorScheme(primary = Purple80, secondary = PurpleGrey80, tertiary = Pink80)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
  )

fun AppTheme.getColorScheme(darkTheme: Boolean): ColorScheme {
    val palette = when (this) {
        AppTheme.SLATE -> if (darkTheme) ThemePalettes.SlateDark else ThemePalettes.SlateLight
        AppTheme.MIDNIGHT -> if (darkTheme) ThemePalettes.MidnightDark else ThemePalettes.MidnightLight
        AppTheme.FOREST -> if (darkTheme) ThemePalettes.ForestDark else ThemePalettes.ForestLight
        AppTheme.SUNSET -> if (darkTheme) ThemePalettes.SunsetDark else ThemePalettes.SunsetLight
        AppTheme.OCEAN -> if (darkTheme) ThemePalettes.OceanDark else ThemePalettes.OceanLight
        AppTheme.ROSE_GOLD -> if (darkTheme) ThemePalettes.RoseGoldDark else ThemePalettes.RoseGoldLight
        AppTheme.NORDIC_FROST -> if (darkTheme) ThemePalettes.NordicFrostDark else ThemePalettes.NordicFrostLight
        AppTheme.COSMIC_NEBULA -> if (darkTheme) ThemePalettes.CosmicNebulaDark else ThemePalettes.CosmicNebulaLight
        AppTheme.VINTAGE_SEPIA -> if (darkTheme) ThemePalettes.VintageSepiaDark else ThemePalettes.VintageSepiaLight
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            onPrimary = palette.onPrimary,
            onSecondary = palette.onSecondary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.onSurface,
            outline = palette.secondary.copy(alpha = 0.5f)
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            secondary = palette.secondary,
            tertiary = palette.tertiary,
            background = palette.background,
            surface = palette.surface,
            onPrimary = palette.onPrimary,
            onSecondary = palette.onSecondary,
            onBackground = palette.onBackground,
            onSurface = palette.onSurface,
            surfaceVariant = palette.background,
            onSurfaceVariant = palette.onBackground,
            outline = palette.secondary.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  appTheme: AppTheme = AppTheme.SLATE,
  // Use custom theme colors by default
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

