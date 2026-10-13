package com.orbit.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orbit.launcher.R
import com.orbit.launcher.apps.InstalledApp
import com.orbit.launcher.ui.theme.OrbitColors

/**
 * App-selection editor. Edits a temporary copy of the selection so that Cancel
 * discards changes; only Save writes back. Searching never drops hidden picks.
 */
@Composable
fun EditAppsScreen(
    allApps: List<InstalledApp>,
    initialSelection: Set<String>,
    onSave: (Set<String>) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Seeded once when the editor opens; not keyed on the live settings object so
    // edits are never clobbered by a background settings emission.
    var temp by remember { mutableStateOf(initialSelection) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(allApps, query) {
        if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(OrbitColors.Background)
            .systemBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = OrbitColors.TextPrimary
                )
            }
            Text(
                text = stringResource(R.string.edit_apps),
                color = OrbitColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${temp.size} selected",
                color = OrbitColors.TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            OrbitSearchBar(
                query = query,
                onQueryChange = { query = it },
                onClear = { query = "" }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { temp = temp + filtered.map { it.id } }) {
                Text(stringResource(R.string.select_all), color = OrbitColors.Accent)
            }
            TextButton(onClick = { temp = emptySet() }) {
                Text(stringResource(R.string.clear_selection), color = OrbitColors.TextSecondary)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(items = filtered, key = { it.id }) { app ->
                val checked = app.id in temp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            temp = if (checked) temp - app.id else temp + app.id
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EditRowIcon(app)
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = app.label,
                        color = OrbitColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isOn ->
                            temp = if (isOn) temp + app.id else temp - app.id
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = OrbitColors.TextSecondary)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { onSave(temp) }) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

@Composable
private fun EditRowIcon(app: InstalledApp) {
    val icon = app.icon
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().clip(CircleShape).background(OrbitColors.Surface)
            )
        }
    }
}
