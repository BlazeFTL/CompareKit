package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class AppTheme(val displayName: String) {
    FOREST("Forest Green (Default)"),
    OCEAN("Ocean Blue"),
    TEAL("Teal Jade"),
    PURPLE("Royal Purple"),
    AMBER("Sunset Amber"),
    ROSE("Crimson Rose"),
    SLATE("Charcoal Slate")
}

data class Palette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val onPrimary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val outlineVariant: Color
)

object ThemePalettes {
    // 1. Forest Green - Clean Pure White Canvas with Crisp Material Green Accent
    val ForestLight = Palette(
        primary = Color(0xFF15803D), // Material Green 700
        secondary = Color(0xFF166534), // Material Green 800
        tertiary = Color(0xFF16A34A), // Material Green 600
        background = Color(0xFFFFFFFF), // Pure White Background
        surface = Color(0xFFFFFFFF), // Pure White Surface
        surfaceVariant = Color(0xFFF1F5F3),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF111815),
        onSurface = Color(0xFF111815),
        onSurfaceVariant = Color(0xFF4B5E55),
        primaryContainer = Color(0xFFDCFCE7),
        onPrimaryContainer = Color(0xFF14532D),
        outlineVariant = Color(0xFFE2E8E5)
    )
    val ForestDark = Palette(
        primary = Color(0xFF4ADE80),
        secondary = Color(0xFF34D399),
        tertiary = Color(0xFF6EE7B7),
        background = Color(0xFF111815),
        surface = Color(0xFF1A2420),
        surfaceVariant = Color(0xFF24332D),
        onPrimary = Color(0xFF022C22),
        onSecondary = Color(0xFF022C22),
        onBackground = Color(0xFFE6F4EA),
        onSurface = Color(0xFFE6F4EA),
        onSurfaceVariant = Color(0xFFA3B8AE),
        primaryContainer = Color(0xFF132D20),
        onPrimaryContainer = Color(0xFF86EFAC),
        outlineVariant = Color(0xFF2E4039)
    )

    // 2. Ocean Blue - Sapphire & Cobalt Blue MD3
    val OceanLight = Palette(
        primary = Color(0xFF0284C7),
        secondary = Color(0xFF0369A1),
        tertiary = Color(0xFF0284C7),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF0F6FA),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF64748B),
        primaryContainer = Color(0xFFE0F2FE),
        onPrimaryContainer = Color(0xFF0369A1),
        outlineVariant = Color(0xFFE2E8F0)
    )
    val OceanDark = Palette(
        primary = Color(0xFF38BDF8),
        secondary = Color(0xFF0EA5E9),
        tertiary = Color(0xFF7DD3FC),
        background = Color(0xFF0B132B),
        surface = Color(0xFF1C2541),
        surfaceVariant = Color(0xFF293241),
        onPrimary = Color(0xFF0B132B),
        onSecondary = Color(0xFF0B132B),
        onBackground = Color(0xFFE0F2FE),
        onSurface = Color(0xFFE0F2FE),
        onSurfaceVariant = Color(0xFF94A3B8),
        primaryContainer = Color(0xFF0F2942),
        onPrimaryContainer = Color(0xFF7DD3FC),
        outlineVariant = Color(0xFF334155)
    )

    // 3. Teal Jade - Cyan & Deep Teal MD3
    val TealLight = Palette(
        primary = Color(0xFF0D9488),
        secondary = Color(0xFF0F766E),
        tertiary = Color(0xFF14B8A6),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF0FDF9),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF131D1B),
        onSurface = Color(0xFF131D1B),
        onSurfaceVariant = Color(0xFF5B6E6A),
        primaryContainer = Color(0xFFCCFBF1),
        onPrimaryContainer = Color(0xFF0F766E),
        outlineVariant = Color(0xFFE0F0EC)
    )
    val TealDark = Palette(
        primary = Color(0xFF2DD4BF),
        secondary = Color(0xFF14B8A6),
        tertiary = Color(0xFF5EEAD4),
        background = Color(0xFF0F1716),
        surface = Color(0xFF1A2624),
        surfaceVariant = Color(0xFF243331),
        onPrimary = Color(0xFF042F2E),
        onSecondary = Color(0xFF042F2E),
        onBackground = Color(0xFFE6FFFA),
        onSurface = Color(0xFFE6FFFA),
        onSurfaceVariant = Color(0xFF99ADA8),
        primaryContainer = Color(0xFF133632),
        onPrimaryContainer = Color(0xFF5EEAD4),
        outlineVariant = Color(0xFF2D3D3A)
    )

    // 4. Royal Purple - Deep Violet & Iris MD3
    val PurpleLight = Palette(
        primary = Color(0xFF7C3AED),
        secondary = Color(0xFF6D28D9),
        tertiary = Color(0xFF8B5CF6),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF8F5FF),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF1E1B4B),
        onSurface = Color(0xFF1E1B4B),
        onSurfaceVariant = Color(0xFF6D6882),
        primaryContainer = Color(0xFFEDE9FE),
        onPrimaryContainer = Color(0xFF5B21B6),
        outlineVariant = Color(0xFFE9E5F5)
    )
    val PurpleDark = Palette(
        primary = Color(0xFFA78BFA),
        secondary = Color(0xFFC4B5FD),
        tertiary = Color(0xFFDDD6FE),
        background = Color(0xFF120E24),
        surface = Color(0xFF1F1A3A),
        surfaceVariant = Color(0xFF2D264E),
        onPrimary = Color(0xFF1E1B4B),
        onSecondary = Color(0xFF1E1B4B),
        onBackground = Color(0xFFF5F3FF),
        onSurface = Color(0xFFF5F3FF),
        onSurfaceVariant = Color(0xFFB4AEC7),
        primaryContainer = Color(0xFF2E2254),
        onPrimaryContainer = Color(0xFFDDD6FE),
        outlineVariant = Color(0xFF3F3563)
    )

    // 5. Sunset Amber - Radiant Warm Amber & Honey MD3
    val AmberLight = Palette(
        primary = Color(0xFFD97706),
        secondary = Color(0xFFB45309),
        tertiary = Color(0xFFF59E0B),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFFFFBEB),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF291F0A),
        onSurface = Color(0xFF291F0A),
        onSurfaceVariant = Color(0xFF786B52),
        primaryContainer = Color(0xFFFEF3C7),
        onPrimaryContainer = Color(0xFF78350F),
        outlineVariant = Color(0xFFF3EAD5)
    )
    val AmberDark = Palette(
        primary = Color(0xFFFBBF24),
        secondary = Color(0xFFFCD34D),
        tertiary = Color(0xFFFDE68A),
        background = Color(0xFF1C1608),
        surface = Color(0xFF2C2412),
        surfaceVariant = Color(0xFF3B311B),
        onPrimary = Color(0xFF451A03),
        onSecondary = Color(0xFF451A03),
        onBackground = Color(0xFFFFFBEB),
        onSurface = Color(0xFFFFFBEB),
        onSurfaceVariant = Color(0xFFC7BAA0),
        primaryContainer = Color(0xFF42310C),
        onPrimaryContainer = Color(0xFFFDE68A),
        outlineVariant = Color(0xFF4F4125)
    )

    // 6. Crimson Rose - Modern Ruby & Carmine MD3
    val RoseLight = Palette(
        primary = Color(0xFFE11D48),
        secondary = Color(0xFFBE123C),
        tertiary = Color(0xFFFB7185),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFFFF1F2),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF260D12),
        onSurface = Color(0xFF260D12),
        onSurfaceVariant = Color(0xFF755C62),
        primaryContainer = Color(0xFFFFE4E6),
        onPrimaryContainer = Color(0xFF881337),
        outlineVariant = Color(0xFFF5E1E4)
    )
    val RoseDark = Palette(
        primary = Color(0xFFFB7185),
        secondary = Color(0xFFFDA4AF),
        tertiary = Color(0xFFFECDD3),
        background = Color(0xFF1F0B10),
        surface = Color(0xFF30151C),
        surfaceVariant = Color(0xFF401F28),
        onPrimary = Color(0xFF4C0519),
        onSecondary = Color(0xFF4C0519),
        onBackground = Color(0xFFFFF1F2),
        onSurface = Color(0xFFFFF1F2),
        onSurfaceVariant = Color(0xFFC9A8B0),
        primaryContainer = Color(0xFF481422),
        onPrimaryContainer = Color(0xFFFECDD3),
        outlineVariant = Color(0xFF5A2C38)
    )

    // 7. Charcoal Slate - Clean Minimalist Neutral Slate MD3
    val SlateLight = Palette(
        primary = Color(0xFF334155),
        secondary = Color(0xFF475569),
        tertiary = Color(0xFF64748B),
        background = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFF1F5F9),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = Color(0xFF0F172A),
        onSurface = Color(0xFF0F172A),
        onSurfaceVariant = Color(0xFF64748B),
        primaryContainer = Color(0xFFE2E8F0),
        onPrimaryContainer = Color(0xFF1E293B),
        outlineVariant = Color(0xFFE2E8F0)
    )
    val SlateDark = Palette(
        primary = Color(0xFF94A3B8),
        secondary = Color(0xFFCBD5E1),
        tertiary = Color(0xFFE2E8F0),
        background = Color(0xFF0F172A),
        surface = Color(0xFF1E293B),
        surfaceVariant = Color(0xFF334155),
        onPrimary = Color(0xFF0F172A),
        onSecondary = Color(0xFF0F172A),
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC),
        onSurfaceVariant = Color(0xFF94A3B8),
        primaryContainer = Color(0xFF273549),
        onPrimaryContainer = Color(0xFFE2E8F0),
        outlineVariant = Color(0xFF475569)
    )
}
