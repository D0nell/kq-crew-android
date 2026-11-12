package com.donell.kqcrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.donell.kqcrew.ui.theme.KQCrewTheme
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KQCrewTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var signedInName by remember { mutableStateOf<String?>(null) }
    var transits by remember { mutableStateOf<List<Transit>>(emptyList()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Runs after any successful sign-in (password or Google): checks the role, loads the transits.
    suspend fun finishSignIn(role: String?) {
        if (role != "CREW") {
            KqClient.api.logout()
            message = "This app is for crew accounts. Dispatchers use the web dashboard."
            return
        }
        val me = KqClient.api.me().body()
        val list = KqClient.api.myTransits()
        if (list.isSuccessful) {
            transits = list.body()?.items ?: emptyList()
            signedInName = me?.full_name ?: "Crew member"
        } else {
            message = "Could not load your transits (HTTP ${list.code()})."
        }
    }

    fun signIn(email: String, password: String) {
        scope.launch {
            busy = true
            message = ""
            try {
                val login = KqClient.api.login(LoginBody(email.trim(), password))
                if (!login.isSuccessful) {
                    message = if (login.code() == 401) {
                        "Invalid email or password."
                    } else {
                        "Sign-in failed (HTTP ${login.code()})."
                    }
                } else {
                    finishSignIn(login.body()?.role)
                }
            } catch (e: Exception) {
                message = "Cannot reach the server. Check that the API is running."
            }
            busy = false
        }
    }

    // Sign in, or sign up as a crew member, with a Google account on this device.
    fun googleSignIn() {
        scope.launch {
            busy = true
            message = ""
            try {
                val option = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(WEB_CLIENT_ID)
                    .build()
                val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val result = CredentialManager.create(context).getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val login = KqClient.api.googleLogin(GoogleBody(idToken))
                    if (!login.isSuccessful) {
                        message = when (login.code()) {
                            503 -> "Google sign-in is not set up on the server."
                            else -> "Google sign-in was refused (HTTP ${login.code()})."
                        }
                    } else {
                        finishSignIn(login.body()?.role)
                    }
                } else {
                    message = "Google sign-in is not available on this device."
                }
            } catch (e: GetCredentialException) {
                message = "Google sign-in was cancelled, or no Google account is set up on this device."
            } catch (e: Exception) {
                message = "Cannot reach the server. Check that the API is running."
            }
            busy = false
        }
    }

    fun refresh() {
        scope.launch {
            busy = true
            message = ""
            try {
                val list = KqClient.api.myTransits()
                if (list.code() == 401) {
                    signedInName = null
                    message = "Your session ended. Please sign in again."
                } else if (list.isSuccessful) {
                    transits = list.body()?.items ?: emptyList()
                } else {
                    message = "Could not refresh (HTTP ${list.code()})."
                }
            } catch (e: Exception) {
                message = "Cannot reach the server. Check that the API is running."
            }
            busy = false
        }
    }

    fun signOut() {
        scope.launch {
            try {
                KqClient.api.logout()
            } catch (e: Exception) {
                // Signing out locally is enough if the server cannot be reached.
            }
            signedInName = null
            transits = emptyList()
            message = ""
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val who = signedInName
            if (who == null) {
                LoginScreen(busy, message, { email, password -> signIn(email, password) }, { googleSignIn() })
            } else {
                TransitScreen(who, transits, busy, message, { refresh() }, { signOut() })
            }
        }
    }
}

@Composable
fun LoginScreen(
    busy: Boolean,
    message: String,
    onSignIn: (String, String) -> Unit,
    onGoogle: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("KQ Crew", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Sign in to see your transits", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { onSignIn(email, password) },
            enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Signing in..." else "Sign in")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onGoogle,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue with Google")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "New here? Continue with Google creates a crew account.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun TransitScreen(
    name: String,
    transits: List<Transit>,
    busy: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("My transits", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                TextButton(onClick = onRefresh, enabled = !busy) { Text(if (busy) "..." else "Refresh") }
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (transits.isEmpty()) {
            Text("No transits are assigned to you yet.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(transits, key = { it.transit_id }) { TransitCard(it) }
            }
        }
    }
}

@Composable
fun TransitCard(t: Transit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(formatTime(t.scheduled_pickup_time), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(t.corridor ?: "-", style = MaterialTheme.typography.bodyMedium)
            Text((t.pickup_location ?: "-") + " to " + (t.dropoff_location ?: "-"), style = MaterialTheme.typography.bodyMedium)
            Text(
                (t.vehicle_type ?: "-") + "  |  Flight " + (t.flight_number ?: "-") + " to " + (t.destination ?: "-"),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(riskLabel(t.risk_level), color = riskColor(t.risk_level), fontWeight = FontWeight.Bold)
                if (t.open_alert) {
                    Text("   Alert", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun formatTime(iso: String?): String = iso?.replace('T', ' ')?.take(16) ?: "-"

fun riskLabel(risk: String?): String = if (risk == null) "Not scored yet" else "Delay risk: $risk"

fun riskColor(risk: String?): Color = when (risk) {
    "LOW" -> Color(0xFF2E7D32)
    "MEDIUM" -> Color(0xFFB26A00)
    "HIGH", "CRITICAL" -> Color(0xFFC62828)
    else -> Color.Gray
}