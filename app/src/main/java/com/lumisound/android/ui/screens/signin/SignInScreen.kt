package com.lumisound.android.ui.screens.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumisound.android.AppContainer
import com.lumisound.android.bridge.SignInResult
import com.lumisound.android.ui.aura.Aura
import com.lumisound.android.ui.aura.AuraBackdrop
import com.lumisound.android.ui.components.Eyebrow
import com.lumisound.android.ui.components.GlassPanel
import com.lumisound.android.ui.components.GlowButton
import com.lumisound.android.ui.components.SegmentedPill
import com.lumisound.android.ui.components.VinylDisc
import com.lumisound.android.ui.theme.LocalLumiPalette
import kotlinx.coroutines.launch

enum class SignInMode(val label: String) { SignIn("Sign in"), Register("Create account"), TwoFactor("Verify") }

data class SignInForm(
    val mode: SignInMode = SignInMode.SignIn,
    val username: String = "",
    val password: String = "",
    val email: String = "",
    val displayName: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/**
 * Sign-in, registration and the 2FA continuation, all against the bridge's own
 * `/auth` routes -- so an account made in Lumisound signs in here directly and
 * an account made here works in Lumisound. There is no linking step and no
 * LumiMusic-specific account.
 */
@Composable
fun SignInScreen(container: AppContainer, checkingSession: Boolean) {
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(SignInForm()) }
    var pendingToken by remember { mutableStateOf<String?>(null) }

    fun handle(result: SignInResult) {
        form = when (result) {
            is SignInResult.Success -> form.copy(busy = false, error = null).also { pendingToken = null }
            is SignInResult.TwoFactorRequired -> form.copy(busy = false, error = null, mode = SignInMode.TwoFactor).also { pendingToken = result.pendingToken }
            is SignInResult.Failure -> form.copy(busy = false, error = result.message)
        }
    }

    SignInContent(
        form = form,
        checkingSession = checkingSession,
        onChange = { form = it },
        onSubmit = {
            form = form.copy(busy = true, error = null)
            scope.launch {
                val result = when (form.mode) {
                    SignInMode.SignIn -> container.account.signIn(form.username, form.password)
                    SignInMode.Register -> container.account.register(form.username, form.email, form.password, form.displayName)
                    SignInMode.TwoFactor -> container.account.completeTwoFactor(pendingToken.orEmpty(), form.code)
                }
                handle(result)
            }
        },
    )
}

/**
 * The first screen anyone sees, so it carries the whole idea of the redesign: the aura
 * already moving, a record already turning, and the form on a sheet of glass over both.
 */
@Composable
fun SignInContent(form: SignInForm, checkingSession: Boolean, onChange: (SignInForm) -> Unit, onSubmit: () -> Unit) {
    val palette = LocalLumiPalette.current
    AuraBackdrop(Aura.forAccent(palette.accent), Modifier.fillMaxSize(), intensity = 1.4f) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(210.dp).background(Brush.radialGradient(listOf(palette.accent.copy(alpha = 0.45f), Color.Transparent)), CircleShape))
                VinylDisc(artworkModel = null, fallbackKey = "lumimusic", size = 150.dp, spinning = true)
            }
            Text(
                "LumiMusic",
                style = MaterialTheme.typography.displayMedium.merge(
                    TextStyle(brush = Brush.horizontalGradient(listOf(Color.White, palette.accent.copy(alpha = 0.9f).compositeWhite())))
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your Lumisound account, on Android.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(26.dp))

            if (checkingSession) {
                CircularProgressIndicator(color = Color.White)
                return@Column
            }

            if (form.mode != SignInMode.TwoFactor) {
                SegmentedPill(
                    listOf(SignInMode.SignIn, SignInMode.Register),
                    form.mode,
                    { it.label },
                    { onChange(form.copy(mode = it, error = null)) },
                )
                Spacer(Modifier.height(14.dp))
            }

            GlassPanel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (form.mode) {
                        SignInMode.TwoFactor -> {
                            Eyebrow("Two-step verification", color = palette.accent)
                            Text("Enter the 6-digit code from your authenticator app.", style = MaterialTheme.typography.bodyMedium)
                            Field(form.code, { onChange(form.copy(code = it.filter(Char::isDigit).take(6))) }, "Authentication code", KeyboardType.NumberPassword, password = true)
                        }
                        else -> {
                            Field(form.username, { onChange(form.copy(username = it)) }, "Username")
                            if (form.mode == SignInMode.Register) {
                                Field(form.email, { onChange(form.copy(email = it)) }, "Email", KeyboardType.Email)
                                Field(form.displayName, { onChange(form.copy(displayName = it)) }, "Display name (optional)")
                            }
                            Field(form.password, { onChange(form.copy(password = it)) }, "Password", KeyboardType.Password, password = true)
                        }
                    }
                    form.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    val ready = !form.busy && when (form.mode) {
                        SignInMode.TwoFactor -> form.code.length >= 6
                        SignInMode.Register -> form.username.isNotBlank() && form.password.isNotBlank() && form.email.isNotBlank()
                        SignInMode.SignIn -> form.username.isNotBlank() && form.password.isNotBlank()
                    }
                    Spacer(Modifier.height(4.dp))
                    GlowButton(
                        when {
                            form.busy -> "Working…"
                            form.mode == SignInMode.Register -> "Create account"
                            form.mode == SignInMode.TwoFactor -> "Verify"
                            else -> "Sign in"
                        },
                        Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = { if (ready) onSubmit() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            if (form.mode == SignInMode.TwoFactor) {
                TextButton(onClick = { onChange(SignInForm(username = form.username)) }) { Text("Start over", color = Color.White) }
            } else {
                Text(
                    "Same username and password as Lumisound on iPhone.\nAn account made here works there too.",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    val palette = LocalLumiPalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLabelColor = palette.accent,
            cursorColor = palette.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun Color.compositeWhite(): Color = Color(
    red = red * alpha + (1 - alpha),
    green = green * alpha + (1 - alpha),
    blue = blue * alpha + (1 - alpha),
    alpha = 1f,
)
