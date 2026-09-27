package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.GlassMint
import com.example.ui.theme.IndustrialLime
import com.example.ui.theme.LocalNexoExtra
import com.example.ui.theme.LuminescentElectricCyan
import com.example.ui.theme.NexoDesignConcept

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSelectorSheet(
    currentConcept: NexoDesignConcept,
    onSelectConcept: (NexoDesignConcept) -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit
) {
    val extra = LocalNexoExtra.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = extra.accentColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.theme_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ThemeOptionCard(
                concept = NexoDesignConcept.INDUSTRIAL_MONOCHROME,
                title = stringResource(R.string.theme_monochrome),
                description = stringResource(R.string.theme_monochrome_desc),
                accentColor = IndustrialLime,
                isSelected = currentConcept == NexoDesignConcept.INDUSTRIAL_MONOCHROME,
                onClick = { onSelectConcept(NexoDesignConcept.INDUSTRIAL_MONOCHROME) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ThemeOptionCard(
                concept = NexoDesignConcept.LUMINESCENT_CYBER,
                title = stringResource(R.string.theme_luminescent),
                description = stringResource(R.string.theme_luminescent_desc),
                accentColor = LuminescentElectricCyan,
                isSelected = currentConcept == NexoDesignConcept.LUMINESCENT_CYBER,
                onClick = { onSelectConcept(NexoDesignConcept.LUMINESCENT_CYBER) }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ThemeOptionCard(
                concept = NexoDesignConcept.SPATIAL_NEO_GLASS,
                title = stringResource(R.string.theme_glass),
                description = stringResource(R.string.theme_glass_desc),
                accentColor = GlassMint,
                isSelected = currentConcept == NexoDesignConcept.SPATIAL_NEO_GLASS,
                onClick = { onSelectConcept(NexoDesignConcept.SPATIAL_NEO_GLASS) }
            )
        }
    }
}

@Composable
private fun ThemeOptionCard(
    concept: NexoDesignConcept,
    title: String,
    description: String,
    accentColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) accentColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    val shape = RoundedCornerShape(12.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .testTag("theme_card_${concept.name}")
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.2f))
                .border(2.dp, accentColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Выбрано",
                tint = accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
