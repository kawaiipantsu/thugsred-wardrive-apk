package red.thugs.wardrive.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun CredentialsDialog(
    purpose: CredentialPurpose,
    initialUsername: String,
    initialBaseUrl: String,
    onDismiss: () -> Unit,
    onSubmit: (username: String, password: String, baseUrl: String) -> Unit,
) {
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var password by rememberSaveable { mutableStateOf("") }
    var baseUrl by rememberSaveable { mutableStateOf(initialBaseUrl) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    val title = when (purpose) {
        CredentialPurpose.GO_LIVE -> "Go Live"
        CredentialPurpose.UPLOAD -> "Upload session"
    }
    val blurb = when (purpose) {
        CredentialPurpose.GO_LIVE ->
            "Sign in to wardrive.thugs.red. The app creates an ingest token and streams WiFi observations live as you drive."
        CredentialPurpose.UPLOAD ->
            "Sign in to wardrive.thugs.red. The current session is exported to WiGLE CSV and sent to the upload queue."
    } + "\n\nNeeds a THUGS(red) Wardrive account approved by staff — register on the site, then ask on Discord for approval. Scanning works without one."

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(blurb)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username or email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(if (showAdvanced) "Hide server" else "Change server")
                }
                if (showAdvanced) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(username.trim(), password, baseUrl.trim()) },
                enabled = username.isNotBlank() && password.isNotBlank(),
            ) { Text(if (purpose == CredentialPurpose.GO_LIVE) "Go Live" else "Upload") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
