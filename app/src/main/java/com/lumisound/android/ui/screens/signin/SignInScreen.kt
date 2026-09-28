package com.lumisound.android.ui.screens.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.SignInResult
import kotlinx.coroutines.launch

/**
 * Sign-in, registration and the 2FA continuation, all against the bridge's own
 * `/auth` routes -- so an account made in Lumisound signs in here directly and
 * an account made here works in Lumisound. There is no linking step and no
 * LumiMusic-specific account.
 */
@Composable
fun SignInScreen(container: AppContainer, checkingSession: Boolean) {
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(Mode.SignIn) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pendingToken by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handle(result: SignInResult) {
        busy = false
        when (result) {
            is SignInResult.Success -> {
                error = null
                pendingToken = null
            }
            is SignInResult.TwoFactorRequired -> {
                error = null
                pendingToken = result.pendingToken
                mode = Mode.TwoFactor
            }
            is SignInResult.Failure -> error = result.message
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("LumiMusic", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Your Lumisound account, on Android. Sign in with the same username and password you use on iPhone.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        if (checkingSession) {
            CircularProgressIndicator()
            return@Column
        }

        when (mode) {
            Mode.TwoFactor -> {
                Text("Enter the 6-digit code from your authenticator app.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("Authentication code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (mode == Mode.Register) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        supportingText = { Text("Required, and checked for a real mail domain.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val result = when (mode) {
                        Mode.SignIn -> container.account.signIn(username, password)
                        Mode.Register -> container.account.register(username, email, password, displayName)
                        Mode.TwoFactor -> container.account.completeTwoFactor(pendingToken.orEmpty(), code)
                    }
                    handle(result)
                }
            },
            enabled = !busy && when (mode) {
                Mode.TwoFactor -> code.length >= 6
                Mode.Register -> username.isNotBlank() && password.isNotBlank() && email.isNotBlank()
                Mode.SignIn -> username.isNotBlank() && password.isNotBlank()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    busy -> "Working…"
                    mode == Mode.Register -> "Create account"
                    mode == Mode.TwoFactor -> "Verify"
                    else -> "Sign in"
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        when (mode) {
            Mode.SignIn -> TextButton(onClick = { mode = Mode.Register; error = null }) {
                Text("No account yet? Create one")
            }
            Mode.Register -> TextButton(onClick = { mode = Mode.SignIn; error = null }) {
                Text("I already have a Lumisound account")
            }
            Mode.TwoFactor -> TextButton(onClick = { mode = Mode.SignIn; code = ""; error = null }) {
                Text("Start over")
            }
        }
    }
}

private enum class Mode { SignIn, Register, TwoFactor }
