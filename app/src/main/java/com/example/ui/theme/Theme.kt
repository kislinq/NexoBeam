package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class NexoDesignConcept(val title: String, val subtitle: String) {
  LUMINESCENT_CYBER("Luminescent Cyber-Minimalism", "Световые импульсы в глубоком пространстве"),
  SPATIAL_NEO_GLASS("Spatial Neo-Glass", "Лёгкие кристаллические пластины и размытие"),
  INDUSTRIAL_MONOCHROME("Industrial Tech-Monochrome", "Строгий аэрокосмический терминал связи")
}

@Immutable
data class NexoExtraTheme(
  val concept: NexoDesignConcept,
  val accentColor: Color,
  val accentSecondary: Color,
  val backgroundBrush: Brush,
  val bubbleOutgoingBrush: Brush,
  val bubbleIncomingColor: Color,
  val bubbleOutgoingTextColor: Color,
  val bubbleIncomingTextColor: Color,
  val bubbleIncomingBorder: Color,
  val bubbleOutgoingShape: Shape,
  val bubbleIncomingShape: Shape,
  val bubbleOutgoingBorderBrush: Brush?,
  val inputBarShape: Shape,
  val inputBarBackground: Color,
  val inputBarBorderBrush: Brush?,
  val avatarRingColor: Color?,
  val avatarIsNexusRing: Boolean,
  val isMonospaceMetadata: Boolean,
  val showSecondsInTime: Boolean,
  val showTelemetryBar: Boolean,
  val statusOnlineText: String,
  val cardBorderColor: Color,
  val cornerRadius: Dp,
  val bubbleCornerRadius: Dp,
  val beamGlowColor: Color,
  val chipBackground: Color
)

val LocalNexoExtra = staticCompositionLocalOf {
  NexoExtraTheme(
    concept = NexoDesignConcept.LUMINESCENT_CYBER,
    accentColor = LuminescentElectricCyan,
    accentSecondary = LuminescentPhotonBlue,
    backgroundBrush = Brush.verticalGradient(listOf(LuminescentObsidian, Color(0xFF090D18))),
    bubbleOutgoingBrush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF0072FF))),
    bubbleIncomingColor = LuminescentCard,
    bubbleOutgoingTextColor = Color.White,
    bubbleIncomingTextColor = LuminescentTextPrimary,
    bubbleIncomingBorder = LuminescentBorder,
    bubbleOutgoingShape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
    bubbleIncomingShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
    bubbleOutgoingBorderBrush = null,
    inputBarShape = RoundedCornerShape(32.dp),
    inputBarBackground = Color(0xF2090D17),
    inputBarBorderBrush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFF9D4EDD), Color(0xFF0072FF))),
    avatarRingColor = LuminescentElectricCyan,
    avatarIsNexusRing = true,
    isMonospaceMetadata = false,
    showSecondsInTime = false,
    showTelemetryBar = false,
    statusOnlineText = "онлайн",
    cardBorderColor = LuminescentBorder,
    cornerRadius = 20.dp,
    bubbleCornerRadius = 20.dp,
    beamGlowColor = LuminescentElectricCyan.copy(alpha = 0.4f),
    chipBackground = LuminescentCard
  )
}

// 1. Luminescent Cyber-Minimalism Color Scheme
private val LuminescentDarkColorScheme = darkColorScheme(
  primary = LuminescentElectricCyan,
  onPrimary = Color(0xFF05101A),
  primaryContainer = Color(0xFF0A223B),
  onPrimaryContainer = LuminescentElectricCyan,
  secondary = LuminescentPhotonBlue,
  onSecondary = Color.White,
  tertiary = LuminescentUltraviolet,
  background = LuminescentObsidian,
  onBackground = LuminescentTextPrimary,
  surface = LuminescentDeepNavy,
  onSurface = LuminescentTextPrimary,
  surfaceVariant = LuminescentCard,
  onSurfaceVariant = LuminescentTextSecondary,
  outline = LuminescentBorder
)

// 2. Spatial Neo-Glass Color Scheme
private val GlassDarkColorScheme = darkColorScheme(
  primary = GlassMint,
  onPrimary = Color(0xFF041A12),
  primaryContainer = Color(0xFF0D3325),
  onPrimaryContainer = GlassMint,
  secondary = GlassLavender,
  onSecondary = Color(0xFF140D26),
  background = GlassSpaceNavy,
  onBackground = GlassTextPrimary,
  surface = Color(0x351F2F40),
  onSurface = GlassTextPrimary,
  surfaceVariant = Color(0x28FFFFFF),
  onSurfaceVariant = GlassTextSecondary,
  outline = GlassBorder
)

// 3. Industrial Tech-Monochrome Color Scheme
private val IndustrialDarkColorScheme = darkColorScheme(
  primary = IndustrialLime,
  onPrimary = Color(0xFF0C1014),
  primaryContainer = Color(0xFF222834),
  onPrimaryContainer = IndustrialLime,
  secondary = IndustrialLaserOrange,
  onSecondary = Color.White,
  background = IndustrialCharcoal,
  onBackground = IndustrialTextPrimary,
  surface = IndustrialTitanium,
  onSurface = IndustrialTextPrimary,
  surfaceVariant = IndustrialCard,
  onSurfaceVariant = IndustrialTextSecondary,
  outline = IndustrialBorder
)

private val IndustrialLightColorScheme = lightColorScheme(
  primary = Color(0xFF5B8A00),
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE9F7B8),
  onPrimaryContainer = Color(0xFF223500),
  secondary = IndustrialLaserOrange,
  onSecondary = Color.White,
  background = IndustrialLightBg,
  onBackground = IndustrialLightTextPrimary,
  surface = IndustrialLightSurface,
  onSurface = IndustrialLightTextPrimary,
  surfaceVariant = Color(0xFFE8ECF2),
  onSurfaceVariant = IndustrialLightTextSecondary,
  outline = IndustrialLightBorder
)

@Composable
fun NexoBeamTheme(
  concept: NexoDesignConcept = NexoDesignConcept.LUMINESCENT_CYBER,
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme: ColorScheme = when (concept) {
    NexoDesignConcept.LUMINESCENT_CYBER -> LuminescentDarkColorScheme
    NexoDesignConcept.SPATIAL_NEO_GLASS -> GlassDarkColorScheme
    NexoDesignConcept.INDUSTRIAL_MONOCHROME -> {
      if (darkTheme) IndustrialDarkColorScheme else IndustrialLightColorScheme
    }
  }

  val extraTheme = when (concept) {
    NexoDesignConcept.LUMINESCENT_CYBER -> NexoExtraTheme(
      concept = concept,
      accentColor = LuminescentElectricCyan,
      accentSecondary = LuminescentPhotonBlue,
      backgroundBrush = Brush.verticalGradient(
        listOf(
          Color(0xFF05070D),
          Color(0xFF090D18),
          Color(0xFF0C1222),
          Color(0xFF05070D)
        )
      ),
      bubbleOutgoingBrush = Brush.horizontalGradient(
        listOf(Color(0xFF00E5FF), Color(0xFF0077FE))
      ),
      bubbleIncomingColor = Color(0xFF131824),
      bubbleOutgoingTextColor = Color.White,
      bubbleIncomingTextColor = LuminescentTextPrimary,
      bubbleIncomingBorder = Color(0xFF232D42),
      bubbleOutgoingShape = RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp),
      bubbleIncomingShape = RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp),
      bubbleOutgoingBorderBrush = null,
      inputBarShape = RoundedCornerShape(32.dp),
      inputBarBackground = Color(0xF20B101D),
      inputBarBorderBrush = Brush.horizontalGradient(
        listOf(Color(0xFF00F2FE), Color(0xFF9D4EDD), Color(0xFF0077FE))
      ),
      avatarRingColor = LuminescentElectricCyan,
      avatarIsNexusRing = true,
      isMonospaceMetadata = false,
      showSecondsInTime = false,
      showTelemetryBar = false,
      statusOnlineText = "онлайн",
      cardBorderColor = LuminescentBorder,
      cornerRadius = 20.dp,
      bubbleCornerRadius = 20.dp,
      beamGlowColor = LuminescentElectricCyan.copy(alpha = 0.5f),
      chipBackground = LuminescentCard
    )
    NexoDesignConcept.SPATIAL_NEO_GLASS -> NexoExtraTheme(
      concept = concept,
      accentColor = GlassMint,
      accentSecondary = GlassLavender,
      backgroundBrush = Brush.linearGradient(
        listOf(
          Color(0xFF091118),
          Color(0xFF0E252D),
          Color(0xFF1B172E),
          Color(0xFF09121A)
        )
      ),
      bubbleOutgoingBrush = Brush.linearGradient(
        listOf(Color(0xD9149472), Color(0xD922B58A))
      ),
      bubbleIncomingColor = Color(0x38223647),
      bubbleOutgoingTextColor = Color.White,
      bubbleIncomingTextColor = GlassTextPrimary,
      bubbleIncomingBorder = Color(0x40FFFFFF),
      bubbleOutgoingShape = RoundedCornerShape(24.dp), // Superellipse 22-26dp
      bubbleIncomingShape = RoundedCornerShape(24.dp),
      bubbleOutgoingBorderBrush = Brush.linearGradient(listOf(Color(0x6055FFAA), Color(0x2055FFAA))),
      inputBarShape = RoundedCornerShape(28.dp),
      inputBarBackground = Color(0x3A223447),
      inputBarBorderBrush = Brush.linearGradient(
        listOf(Color(0x55FFFFFF), Color(0x20FFFFFF))
      ),
      avatarRingColor = Color(0x55FFFFFF),
      avatarIsNexusRing = false,
      isMonospaceMetadata = false,
      showSecondsInTime = false,
      showTelemetryBar = false,
      statusOnlineText = "в сети",
      cardBorderColor = GlassBorder,
      cornerRadius = 24.dp,
      bubbleCornerRadius = 24.dp,
      beamGlowColor = GlassMint.copy(alpha = 0.35f),
      chipBackground = Color(0x351F2F40)
    )
    NexoDesignConcept.INDUSTRIAL_MONOCHROME -> NexoExtraTheme(
      concept = concept,
      accentColor = IndustrialLime,
      accentSecondary = IndustrialLaserOrange,
      backgroundBrush = Brush.verticalGradient(
        listOf(
          Color(0xFF0C0E12),
          Color(0xFF12151B),
          Color(0xFF0D0F14)
        )
      ),
      bubbleOutgoingBrush = Brush.linearGradient(
        listOf(IndustrialLime, IndustrialLime) // High-contrast Cyber-Lime
      ),
      bubbleIncomingColor = Color(0xFF181B22),
      bubbleOutgoingTextColor = Color(0xFF000000), // Bold black on cyber-lime
      bubbleIncomingTextColor = IndustrialTextPrimary,
      bubbleIncomingBorder = Color(0xFF2C3240),
      bubbleOutgoingShape = CutCornerShape(topStart = 10.dp, topEnd = 2.dp, bottomEnd = 10.dp, bottomStart = 10.dp),
      bubbleIncomingShape = CutCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 10.dp, bottomStart = 2.dp),
      bubbleOutgoingBorderBrush = null,
      inputBarShape = CutCornerShape(10.dp),
      inputBarBackground = Color(0xFF151820),
      inputBarBorderBrush = Brush.linearGradient(
        listOf(Color(0xFF2E3544), Color(0xFF2E3544))
      ),
      avatarRingColor = null,
      avatarIsNexusRing = false,
      isMonospaceMetadata = true,
      showSecondsInTime = true,
      showTelemetryBar = true,
      statusOnlineText = "• онлайн",
      cardBorderColor = IndustrialBorder,
      cornerRadius = 8.dp,
      bubbleCornerRadius = 10.dp,
      beamGlowColor = IndustrialLime.copy(alpha = 0.35f),
      chipBackground = Color(0xFF1C2028)
    )
  }

  CompositionLocalProvider(LocalNexoExtra provides extraTheme) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}

