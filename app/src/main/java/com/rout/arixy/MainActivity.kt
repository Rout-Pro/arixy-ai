package com.rout.arixy

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.rout.arixy.core.ARIXYAccessibilityService
import com.rout.arixy.core.ARIXYCore
import com.rout.arixy.core.ARIXYNotificationService
import com.rout.arixy.core.AssistantController
import com.rout.arixy.core.AssistantState
import com.rout.arixy.core.BrainResponse
import com.rout.arixy.core.JealousyLevel
import com.rout.arixy.core.KeyStore
import com.rout.arixy.core.MemoryEntry
import com.rout.arixy.core.MemoryManager
import com.rout.arixy.core.OverlayService
import com.rout.arixy.core.PersonalityEngine
import com.rout.arixy.core.PersonalityMode
import com.rout.arixy.core.TaskStepStatus
import com.rout.arixy.core.VoiceEngine
import com.rout.arixy.ui.ArixyColorScheme
import com.rout.arixy.ui.ArixyColors
import com.rout.arixy.ui.ArixyOrb
import java.io.ByteArrayOutputStream
import java.io.File

data class ChatMsg(val text: String, val fromUser: Boolean, val emotion: String = "neutral")

class MainActivity : ComponentActivity() {

    private val messages = mutableStateListOf<ChatMsg>()
    private var voice: VoiceEngine? = null
    private var statusLine = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = VoiceEngine(
            context = this,
            onPartial = {},
            onFinalResult = { spoken ->
                addUser(spoken)
                AssistantController.ask(this, spoken, null, ::addUser, ::addARIXY)
            },
            onListeningState = { on ->
                if (on) AssistantController.setState(AssistantState.LISTENING)
                else if (AssistantController.state.value == AssistantState.LISTENING)
                    AssistantController.setState(AssistantState.IDLE)
            },
            onSpeakingState = { on ->
                if (on) AssistantController.setState(AssistantState.SPEAKING)
                else if (AssistantController.state.value == AssistantState.SPEAKING)
                    AssistantController.setState(AssistantState.IDLE)
            }
        )
        AssistantController.attachVoice(voice)
        setContent { ArixyApp() }
    }

    private fun addUser(text: String) { messages.add(ChatMsg(text, true)) }
    private fun addARIXY(resp: BrainResponse) { messages.add(ChatMsg(resp.speech, false, resp.emotion)) }

    private fun refreshStatus() {
        val a11y = ARIXYAccessibilityService.isConnected()
        val keySet = KeyStore.getApiKey(this).isNotBlank()
        statusLine.value = "Accessibility ${if (a11y) "ON" else "OFF"} · Gemini key ${if (keySet) "SET" else "MISSING"}"
    }

    override fun onResume() { super.onResume(); refreshStatus() }

    override fun onDestroy() {
        AssistantController.attachVoice(null)
        voice?.shutdown()
        super.onDestroy()
    }

    // ---------- shared glass components ----------

    @Composable
    fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(ArixyColors.Glass, RoundedCornerShape(16.dp))
                .border(1.dp, ArixyColors.Border, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) { content() }
    }

    @Composable
    fun SectionTitle(text: String) {
        Text(text, color = ArixyColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }

    @Composable
    fun HintText(text: String) {
        Text(text, color = ArixyColors.TextSecondary, fontSize = 11.sp)
    }

    // ---------- app shell ----------

    @Composable
    fun ArixyApp() {
        var tab by remember { mutableStateOf(0) }
        val tabs = listOf("Home", "Chat", "Control", "Memory", "Settings")
        MaterialTheme(colorScheme = ArixyColorScheme) {
            Scaffold(
                containerColor = ArixyColors.Black,
                bottomBar = {
                    NavigationBar(containerColor = ArixyColors.NearBlack) {
                        tabs.forEachIndexed { i, label ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (tab == i) ArixyColors.Accent else ArixyColors.TextSecondary.copy(alpha = 0.4f),
                                                CircleShape
                                            )
                                    )
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = ArixyColors.Accent,
                                    unselectedTextColor = ArixyColors.TextSecondary,
                                    indicatorColor = ArixyColors.Glass
                                )
                            )
                        }
                    }
                }
            ) { pad ->
                Box(modifier = Modifier.fillMaxSize().padding(pad).background(ArixyColors.Black)) {
                    when (tab) {
                        0 -> HomeScreen()
                        1 -> ChatScreen()
                        2 -> ControlScreen()
                        3 -> MemoryScreen()
                        4 -> SettingsScreen()
                    }
                    ConfirmationOverlay()
                }
            }
        }
    }

    /** Global confirmation gate for consequential actions (call / WhatsApp / notification reply). */
    @Composable
    fun ConfirmationOverlay() {
        val pending by AssistantController.pendingConfirmation
        pending?.let { p ->
            AlertDialog(
                onDismissRequest = { p.onDecision(false) },
                containerColor = ArixyColors.NearBlack,
                title = { Text(p.title, color = ArixyColors.TextPrimary) },
                text = { Text(p.detail, color = ArixyColors.TextSecondary) },
                confirmButton = {
                    TextButton(onClick = { p.onDecision(true) }) { Text("Haan, karo", color = ArixyColors.Accent) }
                },
                dismissButton = {
                    TextButton(onClick = { p.onDecision(false) }) { Text("Nahi", color = ArixyColors.TextSecondary) }
                }
            )
        }
    }

    // ---------- HOME ----------

    @Composable
    fun HomeScreen() {
        val context = LocalContext.current
        val state by AssistantController.state
        var input by remember { mutableStateOf("") }
        val name = remember { KeyStore.getUserName(context) }
        val quickActions = remember { mutableStateListOf<String>().apply { addAll(loadQuickActions(context)) } }

        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ARIXY", color = ArixyColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text(statusLine.value, color = ArixyColors.TextSecondary, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (name.isNotBlank()) "Namaste, $name" else "Namaste",
                color = ArixyColors.TextSecondary, fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))

            ArixyOrb(state = state, size = 200.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                state.label,
                color = when (state) {
                    AssistantState.STOPPED -> ArixyColors.Error
                    AssistantState.IDLE -> ArixyColors.TextSecondary
                    else -> ArixyColors.Accent
                },
                fontSize = 13.sp, letterSpacing = 2.sp
            )

            // Persistent stop control: visible whenever anything is active.
            if (state != AssistantState.IDLE && state != AssistantState.STOPPED) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { AssistantController.stopAll(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = ArixyColors.GlassStrong, contentColor = ArixyColors.Error),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("■ STOP", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(Modifier.height(18.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(quickActions) { qa ->
                    Box(
                        modifier = Modifier
                            .background(ArixyColors.Glass, RoundedCornerShape(20.dp))
                            .border(1.dp, ArixyColors.Border, RoundedCornerShape(20.dp))
                            .clickable {
                                addUser(qa)
                                AssistantController.ask(context, qa, null, ::addUser, ::addARIXY)
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) { Text(qa, color = ArixyColors.TextPrimary, fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Bolo ya likho...", fontSize = 13.sp, color = ArixyColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArixyColors.Border,
                        unfocusedBorderColor = ArixyColors.Border,
                        cursorColor = ArixyColors.Accent
                    )
                )
                Spacer(Modifier.width(8.dp))
                OrbButton("🎤") {
                    permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED) {
                        voice?.applyVoiceSettings(context)
                        voice?.listen()
                    }
                }
                Spacer(Modifier.width(8.dp))
                OrbButton("➤") {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        input = ""
                        addUser(t)
                        AssistantController.ask(context, t, null, ::addUser, ::addARIXY)
                    }
                }
            }
        }
    }

    @Composable
    fun OrbButton(label: String, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            shape = CircleShape,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            modifier = Modifier.size(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ArixyColors.Glass, contentColor = ArixyColors.Accent)
        ) { Text(label, fontSize = 16.sp) }
    }

    // ---------- CHAT ----------

    @Composable
    fun ChatScreen() {
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        var input by remember { mutableStateOf("") }
        var partial by remember { mutableStateOf("") }
        val listState = rememberLazyListState()
        val state by AssistantController.state

        val cameraFile = remember { File(context.cacheDir, "camera/shot.jpg") }
        val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) {
                val b64 = encodeImageFile(cameraFile)
                if (b64 != null) {
                    addUser("[photo bheji]")
                    AssistantController.ask(context,
                        "Is photo ko 2 line Hinglish mein describe karo.", b64, ::addUser, ::addARIXY)
                } else addARIXY(BrainResponse("Photo process nahi ho payi.", "alert", false, emptyList()))
            }
        }
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Chat")
                Spacer(Modifier.weight(1f))
                if (state == AssistantState.THINKING || state == AssistantState.SPEAKING) {
                    TextButton(onClick = { AssistantController.stopAll(context) }) {
                        Text("Stop", color = ArixyColors.Error, fontSize = 12.sp)
                    }
                }
                TextButton(onClick = {
                    messages.clear()
                    com.rout.arixy.core.Brain.clearHistory()
                }) { Text("Clear", color = ArixyColors.TextSecondary, fontSize = 12.sp) }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp)) {
                if (messages.isEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 20.dp)) {
                            Text("Kuch bhi poochho.", color = ArixyColors.Accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            HintText("Follow-up questions, context aur history sab chalta hai. Reply ko long-press karke copy karo.")
                        }
                    }
                }
                items(messages) { msg ->
                    ChatBubble(msg, onCopy = { clipboard.setText(AnnotatedString(msg.text)) })
                }
                if (partial.isNotBlank()) {
                    item { Text(partial, color = ArixyColors.AccentBlue, fontSize = 12.sp, modifier = Modifier.padding(6.dp)) }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message...", fontSize = 13.sp, color = ArixyColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border,
                        cursorColor = ArixyColors.Accent
                    )
                )
                Spacer(Modifier.width(6.dp))
                OrbButton("🎤") {
                    permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED) {
                        voice = VoiceEngine(context, onPartial = { partial = it },
                            onFinalResult = { spoken ->
                                partial = ""
                                addUser(spoken)
                                AssistantController.ask(context, spoken, null, ::addUser, ::addARIXY)
                            },
                            onListeningState = { on ->
                                if (on) AssistantController.setState(AssistantState.LISTENING)
                            },
                            onSpeakingState = {},
                            onError = { partial = "" })
                        AssistantController.attachVoice(voice)
                        voice?.applyVoiceSettings(context)
                        voice?.listen()
                    }
                }
                Spacer(Modifier.width(6.dp))
                OrbButton("📷") {
                    permLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                        cameraFile.parentFile?.mkdirs()
                        takePicture.launch(FileProvider.getUriForFile(context, "com.rout.arixy.fileprovider", cameraFile))
                    }
                }
                Spacer(Modifier.width(6.dp))
                OrbButton("➤") {
                    val t = input.trim()
                    if (t.isNotEmpty()) {
                        input = ""
                        addUser(t)
                        AssistantController.ask(context, t, null, ::addUser, ::addARIXY)
                    }
                }
            }
        }
    }

    @Composable
    fun ChatBubble(msg: ChatMsg, onCopy: () -> Unit) {
        val align = if (msg.fromUser) Alignment.End else Alignment.Start
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalAlignment = align) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        if (msg.fromUser) ArixyColors.GlassStrong else ArixyColors.Glass,
                        RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, ArixyColors.Border, RoundedCornerShape(14.dp))
                    .clickable { onCopy() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(msg.text, color = ArixyColors.TextPrimary, fontSize = 14.sp)
            }
        }
    }

    // ---------- CONTROL ----------

    @Composable
    fun ControlScreen() {
        val context = LocalContext.current
        val log = AssistantController.taskLog
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()) { refreshStatus() }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Control")
            HintText("Phone actions, permissions aur running tasks - sab yahan. Jo OFF hai use on karo jab zarurat ho.")

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Supported actions", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    HintText("Apps kholna · YouTube search · Chrome pages · alarms/timers · volume · torch · Wi-Fi/Bluetooth panels · media control · calls/WhatsApp (confirmation ke saath) · screen tap/scroll/type (Accessibility on hone par) · screenshot/screen reading.")
                }
            }

            PermRow("Accessibility (screen control)",
                "Screen dekhna, tap/scroll/type karna. Iske bina touch automation nahi.",
                ARIXYAccessibilityService.isConnected()) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PermRow("Microphone / camera / contacts",
                "Voice commands, camera vision, contact se call/WhatsApp.",
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA, Manifest.permission.READ_CONTACTS)
                if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
                permLauncher.launch(perms.toTypedArray())
            }
            PermRow("Notification access",
                "Messages padhna aur reply (Ghost mode).",
                ARIXYNotificationService.isListenerEnabled(context)) {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PermRow("Floating orb overlay",
                "Har app ke upar ARIXY orb.",
                Settings.canDrawOverlays(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PermRow("Write settings / DND",
                "Brightness aur Do-Not-Disturb control.",
                Settings.System.canWrite(context)) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Task log")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { AssistantController.clearLog() }) {
                    Text("Clear", color = ArixyColors.TextSecondary, fontSize = 11.sp)
                }
            }
            if (log.isEmpty()) {
                HintText("Abhi koi task nahi chala. Jo bhi automation hogi, har step yahan dikhega - current, done, failed, confirmations.")
            }
            log.asReversed().forEach { e ->
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (mark, color) = when (e.status) {
                            TaskStepStatus.RUNNING -> "…" to ArixyColors.Warning
                            TaskStepStatus.DONE -> "✓" to ArixyColors.Success
                            TaskStepStatus.FAILED -> "✕" to ArixyColors.Error
                            TaskStepStatus.SKIPPED -> "–" to ArixyColors.TextSecondary
                            TaskStepStatus.WAITING_CONFIRM -> "?" to ArixyColors.Warning
                        }
                        Text(mark, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(e.step, color = ArixyColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (e.detail.isNotBlank()) Text(e.detail, color = ArixyColors.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PermRow(title: String, desc: String, granted: Boolean, onGrant: () -> Unit) {
        GlassCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(desc, color = ArixyColors.TextSecondary, fontSize = 11.sp)
                }
                Text(if (granted) "ON" else "OFF",
                    color = if (granted) ArixyColors.Success else ArixyColors.Error,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            if (!granted) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGrant) { Text("Grant", fontSize = 12.sp, color = ArixyColors.Accent) }
            }
        }
    }

    // ---------- MEMORY ----------

    @Composable
    fun MemoryScreen() {
        val context = LocalContext.current
        var enabled by remember { mutableStateOf(MemoryManager.isEnabled(context)) }
        val memories = remember { mutableStateListOf<MemoryEntry>().apply { addAll(MemoryManager.list(context)) } }
        var newMemory by remember { mutableStateOf("") }
        var editingId by remember { mutableStateOf<Long?>(null) }
        var editingText by remember { mutableStateOf("") }

        fun reload() {
            memories.clear()
            memories.addAll(MemoryManager.list(context))
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle("Memory")
            HintText("ARIXY in baaton ko yaad rakhti hai. Sab kuch tumhare phone pe hai - dekho, edit karo, delete karo, ya poori memory band kar do. Passwords, OTP ya payment details kabhi mat save karo.")

            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Long-term memory", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        HintText(if (enabled) "ON - memories AI replies mein use hoti hain" else "OFF - saved memories ignore hoti hain")
                    }
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it; MemoryManager.setEnabled(context, it)
                    }, colors = SwitchDefaults.colors(checkedTrackColor = ArixyColors.AccentViolet))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMemory, onValueChange = { newMemory = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Nayi memory likho...", fontSize = 12.sp, color = ArixyColors.TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border,
                        cursorColor = ArixyColors.Accent
                    )
                )
                Spacer(Modifier.width(8.dp))
                OrbButton("+") {
                    if (MemoryManager.add(context, newMemory) != null) {
                        newMemory = ""
                        reload()
                    }
                }
            }

            if (memories.isEmpty()) {
                HintText("Koi memory nahi hai abhi.")
            }
            memories.forEach { m ->
                GlassCard {
                    if (editingId == m.id) {
                        OutlinedTextField(
                            value = editingText, onValueChange = { editingText = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border)
                        )
                        Row {
                            TextButton(onClick = {
                                MemoryManager.update(context, m.id, editingText)
                                editingId = null; reload()
                            }) { Text("Save", color = ArixyColors.Accent, fontSize = 12.sp) }
                            TextButton(onClick = { editingId = null }) { Text("Cancel", color = ArixyColors.TextSecondary, fontSize = 12.sp) }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.text, color = ArixyColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { editingId = m.id; editingText = m.text }) {
                                Text("Edit", color = ArixyColors.TextSecondary, fontSize = 11.sp)
                            }
                            TextButton(onClick = { MemoryManager.delete(context, m.id); reload() }) {
                                Text("Delete", color = ArixyColors.Error, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            if (memories.isNotEmpty()) {
                OutlinedButton(onClick = { MemoryManager.clearAll(context); reload() }) {
                    Text("Delete all memories", color = ArixyColors.Error, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(6.dp))
            SectionTitle("Privacy")
            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    HintText("· API key encrypted on-device rehti hai, kabhi cloud pe upload nahi hoti.")
                    HintText("· Screen reading sirf tab hoti hai jab tum feature chalate ho; continuous recording nahi.")
                    HintText("· Chat history sirf is session ki hoti hai; Clear se turant mit jaati hai.")
                }
            }
        }
    }

    // ---------- SETTINGS ----------

    @Composable
    fun SettingsScreen() {
        val context = LocalContext.current
        var name by remember { mutableStateOf(KeyStore.getUserName(context)) }
        var apiKey by remember { mutableStateOf(KeyStore.getApiKey(context)) }
        var model by remember { mutableStateOf(KeyStore.getModel(context)) }
        var personality by remember { mutableStateOf(KeyStore.getPersonality(context)) }
        var jealousy by remember { mutableStateOf(KeyStore.getJealousy(context)) }
        var speechRate by remember { mutableStateOf(KeyStore.getSpeechRate(context)) }
        var pitch by remember { mutableStateOf(KeyStore.getVoicePitch(context)) }
        var tts by remember { mutableStateOf(KeyStore.isTtsEnabled(context)) }
        var wake by remember { mutableStateOf(KeyStore.isWakeWordEnabled(context)) }
        var ghost by remember { mutableStateOf(KeyStore.isGhostModeEnabled(context)) }
        var ghostText by remember { mutableStateOf(KeyStore.getGhostReplyText(context)) }
        var orb by remember { mutableStateOf(KeyStore.isOrbEnabled(context)) }
        var quickCsv by remember { mutableStateOf(loadQuickActions(context).joinToString(", ")) }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("Settings")

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Tumhara naam", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border)
                    )
                    Button(onClick = { KeyStore.setUserName(context, name) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArixyColors.GlassStrong, contentColor = ArixyColors.Accent)
                    ) { Text("Save naam", fontSize = 12.sp) }
                    HintText("Naam kabhi hardcode nahi hota - khali chhodo to ARIXY bina naam ke baat karegi.")
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Gemini API key (encrypted on-device)", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    HintText("Free key: aistudio.google.com/apikey. Kabhi share mat karo. Bina key ke time/date/battery/torch jaise local commands chalte rehte hain.")
                    OutlinedTextField(
                        value = apiKey, onValueChange = { apiKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        placeholder = { Text("AIza...", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border)
                    )
                    OutlinedTextField(
                        value = model, onValueChange = { model = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text("Model", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border)
                    )
                    Button(onClick = {
                        KeyStore.setApiKey(context, apiKey)
                        KeyStore.setModel(context, model.ifBlank { "gemini-2.5-flash" })
                        refreshStatus()
                    }, colors = ButtonDefaults.buttonColors(containerColor = ArixyColors.GlassStrong, contentColor = ArixyColors.Accent)
                    ) { Text("Save key", fontSize = 12.sp) }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Personality mode", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    HintText(personality.desc)
                    ModePicker(
                        options = PersonalityMode.values().map { it.label },
                        selected = personality.label
                    ) { label ->
                        PersonalityMode.values().firstOrNull { it.label == label }?.let {
                            personality = it
                            KeyStore.setPersonality(context, it)
                            if (!PersonalityEngine.jealousyAllowed(it)) {
                                jealousy = JealousyLevel.OFF
                                KeyStore.setJealousy(context, JealousyLevel.OFF)
                            }
                        }
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Playful jealousy", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    if (PersonalityEngine.jealousyAllowed(personality)) {
                        ModePicker(
                            options = JealousyLevel.values().map { it.label },
                            selected = jealousy.label
                        ) { label ->
                            JealousyLevel.values().firstOrNull { it.label == label }?.let {
                                jealousy = it; KeyStore.setJealousy(context, it)
                            }
                        }
                        HintText("Sirf jab tum clearly chhed rahe ho. Kabhi controlling ya guilt-trip nahi. \"band karo\" bolo to turant stop.")
                    } else {
                        HintText("Professional aur Serious Support mode mein jealousy hamesha OFF rehti hai.")
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Voice", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    SettingSwitch("Voice replies (TTS)", tts) {
                        tts = it; KeyStore.setTtsEnabled(context, it); if (!it) voice?.stopSpeaking()
                    }
                    Text("Speed ${"%.2f".format(speechRate)}x", color = ArixyColors.TextSecondary, fontSize = 11.sp)
                    Slider(value = speechRate, onValueChange = {
                        speechRate = it; KeyStore.setSpeechRate(context, it); voice?.applyVoiceSettings(context)
                    }, valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = ArixyColors.Accent, activeTrackColor = ArixyColors.AccentViolet))
                    Text("Pitch ${"%.2f".format(pitch)}", color = ArixyColors.TextSecondary, fontSize = 11.sp)
                    Slider(value = pitch, onValueChange = {
                        pitch = it; KeyStore.setVoicePitch(context, it); voice?.applyVoiceSettings(context)
                    }, valueRange = 0.6f..1.6f,
                        colors = SliderDefaults.colors(thumbColor = ArixyColors.Accent, activeTrackColor = ArixyColors.AccentViolet))
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Wake phrase", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    SettingSwitch("\"Hey ARIXY\" (experimental)", wake) {
                        wake = it; KeyStore.setWakeWordEnabled(context, it)
                        if (it && !orb) {
                            orb = true; KeyStore.setOrbEnabled(context, true)
                            if (Settings.canDrawOverlays(context)) OverlayService.start(context)
                        }
                    }
                    HintText("Floating orb ke saath kaam karta hai aur battery use karta hai. True always-on wake word abhi implemented nahi hai - ye best-effort experimental hai.")
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SettingSwitch("Floating ARIXY orb", orb) {
                        orb = it; KeyStore.setOrbEnabled(context, it)
                        if (it) {
                            if (Settings.canDrawOverlays(context)) OverlayService.start(context)
                            else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")))
                        } else OverlayService.stop(context)
                    }
                    SettingSwitch("Ghost auto-reply (WhatsApp)", ghost) {
                        ghost = it; KeyStore.setGhostModeEnabled(context, it)
                    }
                    if (ghost) {
                        OutlinedTextField(
                            value = ghostText,
                            onValueChange = { ghostText = it; KeyStore.setGhostReplyText(context, it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Ghost reply message", fontSize = 11.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border)
                        )
                    }
                }
            }

            GlassCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Quick actions (Home screen)", color = ArixyColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = quickCsv, onValueChange = { quickCsv = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Comma se alag karo", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArixyColors.Border, unfocusedBorderColor = ArixyColors.Border)
                    )
                    Button(onClick = {
                        KeyStore.raw(context).edit().putString("quick_actions", quickCsv).apply()
                    }, colors = ButtonDefaults.buttonColors(containerColor = ArixyColors.GlassStrong, contentColor = ArixyColors.Accent)
                    ) { Text("Save quick actions", fontSize = 12.sp) }
                }
            }
        }
    }

    @Composable
    fun ModePicker(options: List<String>, selected: String, onSelect: (String) -> Unit) {
        var open by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { open = true }) {
                Text(selected, color = ArixyColors.Accent, fontSize = 12.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false },
                modifier = Modifier.background(ArixyColors.NearBlack)) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = if (opt == selected) ArixyColors.Accent else ArixyColors.TextSecondary, fontSize = 13.sp) },
                        onClick = { onSelect(opt); open = false }
                    )
                }
            }
        }
    }

    @Composable
    fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = ArixyColors.TextPrimary, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange,
                colors = SwitchDefaults.colors(checkedTrackColor = ArixyColors.AccentViolet))
        }
    }

    private fun loadQuickActions(context: Context): List<String> {
        val raw = KeyStore.raw(context).getString("quick_actions", "") ?: ""
        val defaults = listOf("YouTube kholo", "Torch on karo", "Time kya hai", "Subah 7 baje ka alarm lagao")
        val parsed = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return parsed.ifEmpty { defaults }
    }

    private fun encodeImageFile(file: File): String? {
        return try {
            val bmp = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val scaled = if (bmp.width > 720) {
                val r = 720f / bmp.width
                Bitmap.createScaledBitmap(bmp, 720, (bmp.height * r).toInt(), true)
            } else bmp
            val bos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, bos)
            Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) { null }
    }
}
