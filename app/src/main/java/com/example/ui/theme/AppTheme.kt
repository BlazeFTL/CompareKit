package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppTheme(val displayName: String) {
    SLATE("Compare Slate"),
    MIDNIGHT("Midnight Indigo"),
    FOREST("Forest Green"),
    SUNSET("Sunset Amber"),
    OCEAN("Ocean Cyan"),
    ROSE_GOLD("Sakura Rose"),
    NORDIC_FROST("Nordic Ice"),
    COSMIC_NEBULA("Cosmic Purple"),
    VINTAGE_SEPIA("Vintage Sepia")
}

// Fixed color schemas for light and dark modes
object ThemePalettes {
    // Slate
    val SlateLight = Palette(
        primary = Color(0xFF1E293B), // Slate 800
        secondary = Color(0xFF475569), // Slate 600
        tertiary = Color(0xFF0F172A),
        background = Color(0xFFF8FAFC), // Slate 50
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A)
    )
    val SlateDark = Palette(
        primary = Color(0xFF94A3B8), // Slate 400
        secondary = Color(0xFF64748B), // Slate 500
        tertiary = Color(0xFFE2E8F0),
        background = Color(0xFF0F172A), // Slate 900
        surface = Color(0xFF1E293B), // Slate 800
        onPrimary = Color(0xFF0F172A),
        onSecondary = Color.White,
        onBackground = Color(0xFFF1F5F9),
        onSurface = Color(0xFFF1F5F9)
    )

    // Midnight
    val MidnightLight = Palette(
        primary = Color(0xFF4F46E5), // Indigo 600
        secondary = Color(0xFF7C3AED), // Violet 600
        tertiary = Color(0xFFC084FC),
        background = Color(0xFFFAF5FF),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1E1B4B),
        onSurface = Color(0xFF1E1B4B)
    )
    val MidnightDark = Palette(
        primary = Color(0xFF818CF8), // Indigo 400
        secondary = Color(0xFFA78BFA), // Violet 400
        tertiary = Color(0xFFF472B6),
        background = Color(0xFF0F0C1B),
        surface = Color(0xFF1D1B2E),
        onPrimary = Color(0xFF0F0C1B),
        onSecondary = Color(0xFF0F0C1B),
        onBackground = Color(0xFFF3E8FF),
        onSurface = Color(0xFFF3E8FF)
    )

    // Forest
    val ForestLight = Palette(
        primary = Color(0xFF059669), // Emerald 600
        secondary = Color(0xFF0891B2), // Cyan 600
        tertiary = Color(0xFF10B981),
        background = Color(0xFFECFDF5),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF064E3B),
        onSurface = Color(0xFF064E3B)
    )
    val ForestDark = Palette(
        primary = Color(0xFF34D399), // Emerald 400
        secondary = Color(0xFF22D3EE), // Cyan 400
        tertiary = Color(0xFF6EE7B7),
        background = Color(0xFF061A13),
        surface = Color(0xFF0F2D24),
        onPrimary = Color(0xFF061A13),
        onSecondary = Color(0xFF061A13),
        onBackground = Color(0xFFE6F4EA),
        onSurface = Color(0xFFE6F4EA)
    )

    // Sunset
    val SunsetLight = Palette(
        primary = Color(0xFFEA580C), // Orange 600
        secondary = Color(0xFFD97706), // Amber 600
        tertiary = Color(0xFFF43F5E),
        background = Color(0xFFFFF7ED),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF451A03),
        onSurface = Color(0xFF451A03)
    )
    val SunsetDark = Palette(
        primary = Color(0xFFFB923C), // Orange 400
        secondary = Color(0xFFFBBF24), // Amber 400
        tertiary = Color(0xFFFDA4AF),
        background = Color(0xFF1C0A00),
        surface = Color(0xFF2E1505),
        onPrimary = Color(0xFF1C0A00),
        onSecondary = Color(0xFF1C0A00),
        onBackground = Color(0xFFFFEDD5),
        onSurface = Color(0xFFFFEDD5)
    )

    // Ocean
    val OceanLight = Palette(
        primary = Color(0xFF0284C7), // Sky 600
        secondary = Color(0xFF0D9488), // Teal 600
        tertiary = Color(0xFF38BDF8),
        background = Color(0xFFF0F9FF),
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF0C4A6E),
        onSurface = Color(0xFF0C4A6E)
    )
    val OceanDark = Palette(
        primary = Color(0xFF38BDF8), // Sky 400
        secondary = Color(0xFF2DD4BF), // Teal 400
        tertiary = Color(0xFF7DD3FC),
        background = Color(0xFF04121F),
        surface = Color(0xFF0C2237),
        onPrimary = Color(0xFF04121F),
        onSecondary = Color(0xFF04121F),
        onBackground = Color(0xFFE0F2FE),
        onSurface = Color(0xFFE0F2FE)
    )

    // Rose Gold
    val RoseGoldLight = Palette(
        primary = Color(0xFFBE185D), // Pink 700
        secondary = Color(0xFFB45309), // Amber 700
        tertiary = Color(0xFFEC4899),
        background = Color(0xFFFFF1F2), // Rose 50
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF4C0519),
        onSurface = Color(0xFF4C0519)
    )
    val RoseGoldDark = Palette(
        primary = Color(0xFFF472B6), // Pink 400
        secondary = Color(0xFFFBBF24), // Amber 400
        tertiary = Color(0xFFF43F5E),
        background = Color(0xFF1C0A10),
        surface = Color(0xFF2D121B),
        onPrimary = Color(0xFF1C0A10),
        onSecondary = Color(0xFF1C0A10),
        onBackground = Color(0xFFFFE4E6),
        onSurface = Color(0xFFFFE4E6)
    )

    // Nordic Frost
    val NordicFrostLight = Palette(
        primary = Color(0xFF0369A1), // Sky 700
        secondary = Color(0xFF0F766E), // Teal 700
        tertiary = Color(0xFF14B8A6),
        background = Color(0xFFF0FDF4), // Mint 50
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF042F2E),
        onSurface = Color(0xFF042F2E)
    )
    val NordicFrostDark = Palette(
        primary = Color(0xFF38BDF8), // Sky 400
        secondary = Color(0xFF34D399), // Mint 400
        tertiary = Color(0xFF2DD4BF),
        background = Color(0xFF0B131A),
        surface = Color(0xFF14202C),
        onPrimary = Color(0xFF0B131A),
        onSecondary = Color(0xFF0B131A),
        onBackground = Color(0xFFCCFBF1),
        onSurface = Color(0xFFCCFBF1)
    )

    // Cosmic Nebula
    val CosmicNebulaLight = Palette(
        primary = Color(0xFF7E22CE), // Purple 700
        secondary = Color(0xFFDB2777), // Pink 600
        tertiary = Color(0xFFA855F7),
        background = Color(0xFFFAF5FF), // Purple 50
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF2E1065),
        onSurface = Color(0xFF2E1065)
    )
    val CosmicNebulaDark = Palette(
        primary = Color(0xFFA855F7), // Purple 500
        secondary = Color(0xFFEC4899), // Pink 500
        tertiary = Color(0xFFF472B6),
        background = Color(0xFF090514),
        surface = Color(0xFF1A112D),
        onPrimary = Color(0xFF090514),
        onSecondary = Color(0xFF090514),
        onBackground = Color(0xFFF3E8FF),
        onSurface = Color(0xFFF3E8FF)
    )

    // Vintage Sepia
    val VintageSepiaLight = Palette(
        primary = Color(0xFF78350F), // Amber 900
        secondary = Color(0xFF4D7C0F), // Lime 700
        tertiary = Color(0xFFD97706),
        background = Color(0xFFFEFBF3), // Vintage Sepia/Ivory
        surface = Color(0xFFFFFFFF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF271300),
        onSurface = Color(0xFF271300)
    )
    val VintageSepiaDark = Palette(
        primary = Color(0xFFF59E0B), // Amber 500
        secondary = Color(0xFFA3E635), // Lime 400
        tertiary = Color(0xFFFBBF24),
        background = Color(0xFF120C06),
        surface = Color(0xFF22160C),
        onPrimary = Color(0xFF120C06),
        onSecondary = Color(0xFF120C06),
        onBackground = Color(0xFFFEF3C7),
        onSurface = Color(0xFFFEF3C7)
    )
}

data class Palette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color
)
