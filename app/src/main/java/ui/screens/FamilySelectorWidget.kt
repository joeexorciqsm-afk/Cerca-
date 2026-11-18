package com.cerca.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cerca.app.model.FamilyGroup

/**
 * Selector horizontal de familia.
 * Muestra chip "Todas" + uno por cada familia.
 * selectedCode == null significa "Todas".
 */
@Composable
fun FamilySelector(
    groups: List<FamilyGroup>,
    selectedCode: String?,            // null = todas
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.size <= 1) return      // con una sola familia no hace falta el selector

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Para:", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Chip "Todas"
        FilterChip(
            selected  = selectedCode == null,
            onClick   = { onSelect(null) },
            label     = { Text("🏘️ Todas", fontSize = 12.sp, fontWeight = FontWeight.Medium) }
        )

        // Chip por familia
        groups.forEach { group ->
            FilterChip(
                selected  = selectedCode == group.code,
                onClick   = { onSelect(group.code) },
                label     = {
                    Text(
                        group.name.ifBlank { group.code },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            )
        }
    }
}
