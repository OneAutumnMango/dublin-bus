package dev.dublinbustracker.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dublinbustracker.data.ServerSettingsRepository
import dev.dublinbustracker.data.TransitApiClient

@Composable
fun SettingsScreen(settingsRepository: ServerSettingsRepository, apiClient: TransitApiClient) {
    val viewModel = viewModel<SettingsViewModel>(
        factory = viewModelFactory { initializer { SettingsViewModel(settingsRepository, apiClient) } },
    )
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Server", style = MaterialTheme.typography.titleMedium)
        Text(
            "Address of your Dublin Bus Server. Arrivals, stops and live buses all come from it.",
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.baseUrl,
            onValueChange = viewModel::onBaseUrlChanged,
            label = { Text("Server address") },
            placeholder = { Text("https://buses.example.com/") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                capitalization = KeyboardCapitalization.None,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text("Request headers", style = MaterialTheme.typography.titleMedium)
        Text(
            "Sent with every request. Use X-Api-Key for the server's own key, plus anything a " +
                "proxy in front of it needs.",
            style = MaterialTheme.typography.bodySmall,
        )

        state.headers.forEachIndexed { index, header ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = header.name,
                    onValueChange = { viewModel.onHeaderNameChanged(index, it) },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = header.value,
                    onValueChange = { viewModel.onHeaderValueChanged(index, it) },
                    label = { Text("Value") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        capitalization = KeyboardCapitalization.None,
                    ),
                    modifier = Modifier.weight(1.4f),
                )
                IconButton(onClick = { viewModel.removeHeader(index) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove header")
                }
            }
        }

        OutlinedButton(onClick = viewModel::addHeader) { Text("Add header") }

        HorizontalDivider()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.save() }) { Text("Save") }
            OutlinedButton(onClick = viewModel::testConnection) { Text("Save & test") }
        }

        state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        state.problems.forEach { problem ->
            Text(problem, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        when (val test = state.connectionTest) {
            is ConnectionTest.Idle -> Unit
            is ConnectionTest.Testing -> CircularProgressIndicator()
            is ConnectionTest.Success -> Text(test.summary, color = MaterialTheme.colorScheme.primary)
            is ConnectionTest.Failure -> Text(test.message, color = MaterialTheme.colorScheme.error)
        }
    }
}
