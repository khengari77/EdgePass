package com.edgepass.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SuitSelector(
    selectedSuit: Int,
    onSuitSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        SuitButton("None", selectedSuit == 0) { onSuitSelected(0) }
        SuitButton("Suit 1", selectedSuit == 1) { onSuitSelected(1) }
        SuitButton("Suit 2", selectedSuit == 2) { onSuitSelected(2) }
        SuitButton("Suit 3", selectedSuit == 3) { onSuitSelected(3) }
    }
}

@Composable
private fun SuitButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondary 
                             else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.height(48.dp)
    ) {
        Text(text)
    }
}
