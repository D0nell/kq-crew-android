package com.donell.kqcrew

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
import org.json.JSONObject
import retrofit2.Response

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
    var signedInRole by remember { mutableStateOf("CREW") }
    var transits by remember { mutableStateOf<List<Transit>>(emptyList()) }
    var dispatchItems by remember { mutableStateOf<List<DispatchTransit>>(emptyList()) }
    var dispatchFilter by remember { mutableStateOf(0) }
    var openTransit by remember { mutableStateOf<Transit?>(null) }
    var openDispatch by remember { mutableStateOf<DispatchTransit?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // The Back button closes a detail screen and returns to the list, instead of leaving the app.
    BackHandler(enabled = openTransit != null || openDispatch != null) {
        openTransit = null
        openDispatch = null
    }

    suspend fun loadDispatch(filter: Int): Response<DispatchList> {
        val (sort, status) = filterParams(filter)
        return KqClient.api.dispatchTransits(sort, status, 50)
    }

    // Runs after any successful sign-in (password or Google): loads the right home screen.
    suspend fun finishSignIn(role: String?) {
        if (role != "CREW" && role != "DISPATCHER") {
            KqClient.api.logout()
            message = "This account has no role the app can use."
            return
        }
        val me = KqClient.api.me().body()
        if (role == "DISPATCHER") {
            dispatchFilter = 0
            val list = loadDispatch(0)
            if (list.isSuccessful) {
                dispatchItems = list.body()?.items ?: emptyList()
                signedInRole = "DISPATCHER"
                signedInName = me?.full_name ?: "Dispatcher"
            } else {
                message = "Could not load the transits (HTTP ${list.code()})."
            }
        } else {
            val list = KqClient.api.myTransits()
            if (list.isSuccessful) {
                transits = list.body()?.items ?: emptyList()
                signedInRole = "CREW"
                signedInName = me?.full_name ?: "Crew member"
            } else {
                message = "Could not load your transits (HTTP ${list.code()})."
            }
        }
    }

    fun signIn(email: String, password: String, role: String) {
        scope.launch {
            busy = true
            message = ""
            try {
                val login = KqClient.api.login(LoginBody(email.trim(), password, role))
                if (!login.isSuccessful) {
                    message = when (login.code()) {
                        401 -> "Invalid email or password."
                        403 -> errorDetail(login) ?: "This account cannot sign in here."
                        else -> "Sign-in failed (HTTP ${login.code()})."
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
    fun googleSignIn(role: String) {
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
                    val login = KqClient.api.googleLogin(GoogleBody(idToken, role))
                    if (!login.isSuccessful) {
                        message = when (login.code()) {
                            403 -> errorDetail(login) ?: "This Google account cannot sign in here."
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
                if (signedInRole == "DISPATCHER") {
                    val list = loadDispatch(dispatchFilter)
                    if (list.code() == 401) {
                        signedInName = null
                        message = "Your session ended. Please sign in again."
                    } else if (list.isSuccessful) {
                        dispatchItems = list.body()?.items ?: emptyList()
                    } else {
                        message = "Could not refresh (HTTP ${list.code()})."
                    }
                } else {
                    val list = KqClient.api.myTransits()
                    if (list.code() == 401) {
                        signedInName = null
                        message = "Your session ended. Please sign in again."
                    } else if (list.isSuccessful) {
                        transits = list.body()?.items ?: emptyList()
                    } else {
                        message = "Could not refresh (HTTP ${list.code()})."
                    }
                }
            } catch (e: Exception) {
                message = "Cannot reach the server. Check that the API is running."
            }
            busy = false
        }
    }

    fun selectFilter(index: Int) {
        dispatchFilter = index
        refresh()
    }

    fun signOut() {
        scope.launch {
            try {
                KqClient.api.logout()
            } catch (e: Exception) {
                // Signing out locally is enough if the server cannot be reached.
            }
            signedInName = null
            openTransit = null
            openDispatch = null
            transits = emptyList()
            dispatchItems = emptyList()
            message = ""
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val who = signedInName
            val openCrew = openTransit
            val openDisp = openDispatch
            if (who == null) {
                LoginScreen(
                    busy,
                    message,
                    { email, password, role -> signIn(email, password, role) },
                    { role -> googleSignIn(role) }
                )
            } else if (signedInRole == "DISPATCHER") {
                if (openDisp != null) {
                    DispatchDetailScreen(openDisp) { openDispatch = null }
                } else {
                    DispatcherScreen(
                        who, dispatchItems, dispatchFilter, busy, message,
                        { selectFilter(it) }, { refresh() }, { signOut() }, { openDispatch = it }
                    )
                }
            } else if (openCrew != null) {
                ChecklistScreen(
                    transit = openCrew,
                    onBack = { openTransit = null },
                    onSessionEnded = {
                        openTransit = null
                        signedInName = null
                        message = "Your session ended. Please sign in again."
                    }
                )
            } else {
                TransitScreen(who, transits, busy, message, { refresh() }, { signOut() }, { openTransit = it })
            }
        }
    }
}

@Composable
fun RowScope.RoleButton(text: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = Modifier.weight(1f)) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = Modifier.weight(1f)) { Text(text) }
    }
}

@Composable
fun LoginScreen(
    busy: Boolean,
    message: String,
    onSignIn: (String, String, String) -> Unit,
    onGoogle: (String) -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("CREW") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("KQ Crew", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Sign in to continue", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Text("I am signing in as", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RoleButton("Crew", role == "CREW") { role = "CREW" }
            RoleButton("Dispatcher", role == "DISPATCHER") { role = "DISPATCHER" }
        }
        Spacer(Modifier.height(16.dp))
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
            onClick = { onSignIn(email, password, role) },
            enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (busy) "Signing in..." else "Sign in")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { onGoogle(role) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue with Google")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (role == "CREW") {
                "New here? Continue with Google creates a crew account."
            } else {
                "Dispatcher accounts are created by an administrator."
            },
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
    onSignOut: () -> Unit,
    onOpen: (Transit) -> Unit
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
                items(transits, key = { it.transit_id }) { t -> TransitCard(t) { onOpen(t) } }
            }
        }
    }
}

@Composable
fun TransitCard(t: Transit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
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
            Spacer(Modifier.height(4.dp))
            Text("Tap to open the checklist", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// The readiness checklist of one transit: three tick boxes and an "I've arrived" button.
@Composable
fun ChecklistScreen(
    transit: Transit,
    onBack: () -> Unit,
    onSessionEnded: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var checklist by remember { mutableStateOf<Checklist?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    // Shows the server's answer, or the reason there is none.
    fun show(resp: Response<Checklist>) {
        when {
            resp.code() == 401 -> onSessionEnded()
            resp.isSuccessful -> checklist = resp.body()
            else -> error = "Could not reach the checklist (HTTP ${resp.code()})."
        }
    }

    // Makes one call to the server (load, tick or arrive) and shows the result.
    fun send(call: suspend () -> Response<Checklist>) {
        scope.launch {
            saving = true
            error = ""
            try {
                show(call())
            } catch (e: Exception) {
                error = "Cannot reach the server. Check that the API is running."
            }
            saving = false
            loading = false
        }
    }

    LaunchedEffect(transit.transit_id) {
        send { KqClient.api.checklist(transit.transit_id) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Readiness checklist", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(formatTime(transit.scheduled_pickup_time), style = MaterialTheme.typography.titleMedium)
        Text(transit.corridor ?: "-", style = MaterialTheme.typography.bodyMedium)
        Text(
            (transit.vehicle_type ?: "-") + "  |  Flight " + (transit.flight_number ?: "-"),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(16.dp))

        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (loading) {
            Text("Loading...", style = MaterialTheme.typography.bodyLarge)
        }

        val current = checklist
        if (current != null) {
            current.tasks.forEach { task ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.done,
                        enabled = !saving,
                        onCheckedChange = { ticked ->
                            send { KqClient.api.setTask(transit.transit_id, task.code, TaskBody(ticked)) }
                        }
                    )
                    Text(task.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(16.dp))
            if (current.arrived) {
                Text(
                    "Arrival confirmed " + formatTime(current.arrived_at),
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Button(
                    onClick = { send { KqClient.api.arrived(transit.transit_id) } },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("I've arrived")
                }
            }
        }
    }
}

// The dispatcher's home screen: the transits, with a filter and the delay risk of each.
@Composable
fun DispatcherScreen(
    name: String,
    rows: List<DispatchTransit>,
    filter: Int,
    busy: Boolean,
    message: String,
    onFilter: (Int) -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpen: (DispatchTransit) -> Unit
) {
    val labels = listOf("Next up", "Highest risk", "Alerts")
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Dispatcher", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                TextButton(onClick = onRefresh, enabled = !busy) { Text(if (busy) "..." else "Refresh") }
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { index, label ->
                RoleButton(label, filter == index) { onFilter(index) }
            }
        }
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("No transits match this view.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(rows, key = { it.transit_id }) { t -> DispatchCard(t) { onOpen(t) } }
            }
        }
    }
}

@Composable
fun DispatchCard(t: DispatchTransit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "#" + t.transit_id + "  " + formatTime(t.scheduled_pickup_time),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(t.corridor ?: "-", style = MaterialTheme.typography.bodyMedium)
            Text(
                (t.vehicle_type ?: "-") + "  |  Crew rank " + (t.rank ?: "-") + "  |  To " + (t.destination ?: "-"),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    riskLabel(t.risk_level) + percentText(t.delay_probability),
                    color = riskColor(t.risk_level),
                    fontWeight = FontWeight.Bold
                )
                if (t.open_alert) {
                    Text("   Alert", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// One transit for the dispatcher, with the hand-off to the web dashboard where the prediction is made.
@Composable
fun DispatchDetailScreen(t: DispatchTransit, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Text("Transit #" + t.transit_id, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Pickup: " + formatTime(t.scheduled_pickup_time), style = MaterialTheme.typography.bodyLarge)
        Text("Corridor: " + (t.corridor ?: "-"), style = MaterialTheme.typography.bodyLarge)
        Text("Vehicle: " + (t.vehicle_type ?: "-"), style = MaterialTheme.typography.bodyLarge)
        Text("Crew rank: " + (t.rank ?: "-"), style = MaterialTheme.typography.bodyLarge)
        Text("Flight destination: " + (t.destination ?: "-"), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                riskLabel(t.risk_level) + percentText(t.delay_probability),
                color = riskColor(t.risk_level),
                fontWeight = FontWeight.Bold
            )
            if (t.open_alert) {
                Text("   Alert", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { uriHandler.openUri(BASE_URL + "dashboard") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Predict via dashboard")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "The dashboard opens in your browser. Sign in there as a dispatcher and search for transit #" +
                    t.transit_id + " to score it.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// The server explains a refused sign-in in a "detail" field; show that text when there is one.
fun errorDetail(resp: Response<*>): String? = try {
    val raw = resp.errorBody()?.string()
    if (raw.isNullOrBlank()) null else JSONObject(raw).optString("detail").ifBlank { null }
} catch (e: Exception) {
    null
}

// Which sort and status the dispatcher's three view buttons ask the server for.
fun filterParams(index: Int): Pair<String, String> = when (index) {
    1 -> Pair("probability", "scored")
    2 -> Pair("probability", "alert")
    else -> Pair("pickup_time", "all")
}

fun percentText(p: Double?): String = if (p == null) "" else "  " + (p * 100).toInt() + "%"

fun formatTime(iso: String?): String = iso?.replace('T', ' ')?.take(16) ?: "-"

fun riskLabel(risk: String?): String = if (risk == null) "Not scored yet" else "Delay risk: $risk"

fun riskColor(risk: String?): Color = when (risk) {
    "LOW" -> Color(0xFF2E7D32)
    "MEDIUM" -> Color(0xFFB26A00)
    "HIGH", "CRITICAL" -> Color(0xFFC62828)
    else -> Color.Gray
}