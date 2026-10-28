package com.rout.arixy.core

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Orchestrates the real assistant cycle:
 * UNDERSTAND -> PLAN -> CHECK PERMISSIONS -> OBSERVE -> ACT -> VERIFY -> REPORT
 * Owns the live AssistantState (orb/status always mirror this), the task log,
 * immediate stop, and the confirmation gate for consequential actions.
 */
object AssistantController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val state = mutableStateOf(AssistantState.IDLE)
    val taskLog = mutableStateListOf<TaskLogEntry>()
    val pendingConfirmation = mutableStateOf<PendingConfirmation?>(null)

    private var currentJob: Job? = null
    private var voiceRef: VoiceEngine? = null

    data class PendingConfirmation(
        val title: String,
        val detail: String,
        val onDecision: (Boolean) -> Unit
    )

    fun attachVoice(v: VoiceEngine?) { voiceRef = v }

    fun setState(s: AssistantState) { state.value = s }

    fun log(step: String, status: TaskStepStatus, detail: String = "") {
        taskLog.add(TaskLogEntry(step, status, detail))
        while (taskLog.size > 60) taskLog.removeAt(0)
    }

    fun clearLog() { taskLog.clear() }

    /** Immediate stop: overrides everything. Stops speech, listening and automation. */
    fun stopAll(context: Context, speakConfirmation: Boolean = true) {
        currentJob?.cancel()
        currentJob = null
        try { voiceRef?.stopSpeaking() } catch (_: Exception) {}
        try { voiceRef?.cancelListening() } catch (_: Exception) {}
        pendingConfirmation.value?.onDecision?.invoke(false)
        pendingConfirmation.value = null
        setState(AssistantState.STOPPED)
        log("Stop command", TaskStepStatus.DONE, "Speech, listening aur pending steps cancel")
        if (speakConfirmation && KeyStore.isTtsEnabled(context)) {
            // One short confirmation, then silence. Nothing resumes automatically.
            try { voiceRef?.speak("Theek hai, rok diya.") } catch (_: Exception) {}
        }
    }

    /** Ask the brain and run the resulting action chain. Cancel-safe. */
    fun ask(context: Context, userText: String, screenshotB64: String?,
            onUserMessage: (String) -> Unit, onAssistantMessage: (BrainResponse) -> Unit) {
        // Stop commands never reach the brain.
        if (PersonalityEngine.isStopCommand(userText)) {
            stopAll(context)
            onAssistantMessage(BrainResponse("Theek hai, sab rok diya.", "serious", false, emptyList()))
            return
        }
        currentJob?.cancel()
        currentJob = scope.launch {
            try {
                setState(AssistantState.THINKING)
                val reply = ARIXYCore.ask(context, userText, screenshotB64)
                ensureActive()
                onAssistantMessage(reply)
                if (reply.actions.isEmpty()) {
                    if (KeyStore.isTtsEnabled(context)) {
                        setState(AssistantState.SPEAKING)
                        voiceRef?.speak(reply.speech)
                    }
                    setState(AssistantState.IDLE)
                } else {
                    if (KeyStore.isTtsEnabled(context)) {
                        setState(AssistantState.SPEAKING)
                        voiceRef?.speak(reply.speech)
                    }
                    runActions(context, reply.actions)
                }
            } catch (e: CancellationException) {
                setState(AssistantState.STOPPED)
                throw e
            } catch (e: Exception) {
                setState(AssistantState.IDLE)
                log("Brain error", TaskStepStatus.FAILED, e.message ?: "unknown")
            }
        }
    }

    private suspend fun runActions(context: Context, actions: List<ARIXYAction>) {
        setState(AssistantState.EXECUTING)
        val executor = Executor(context) { task, b64 ->
            // Vision re-query: fire a follow-up ask with the captured screen.
            ask(context, task, b64, {}, {})
        }
        log("Plan: ${actions.size} action(s)", TaskStepStatus.RUNNING,
            actions.joinToString(" -> ") { it.type })
        actions.forEachIndexed { i, action ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (needsConfirmation(action.type)) {
                val ok = awaitConfirmation(context, action)
                if (!ok) {
                    log(action.type, TaskStepStatus.SKIPPED, "User ne confirm nahi kiya")
                    return@forEachIndexed
                }
            }
            log(action.type, TaskStepStatus.RUNNING, describe(action))
            val ok = executor.executeOne(action)
            log(action.type, if (ok) TaskStepStatus.DONE else TaskStepStatus.FAILED, describe(action))
        }
        setState(AssistantState.IDLE)
    }

    fun needsConfirmation(type: String): Boolean = when (type.uppercase()) {
        "MAKE_CALL", "SEND_WHATSAPP", "REPLY_NOTIFICATION" -> true
        else -> false
    }

    private fun describe(action: ARIXYAction): String {
        val p = action.params
        return when (action.type.uppercase()) {
            "OPEN_APP" -> "App: ${p["appName"] ?: p["package"] ?: "?"}"
            "MAKE_CALL" -> "Call: ${p["contactName"] ?: p["phoneNumber"] ?: "?"}"
            "SEND_WHATSAPP" -> "To: ${p["contactName"] ?: p["phoneNumber"] ?: "?"}"
            "TYPE_TEXT" -> "Type: ${p["text"]?.toString()?.take(30) ?: ""}"
            "CLICK_NODE" -> "Tap: ${p["targetText"] ?: "?"}"
            "SET_ALARM" -> "Alarm ${p["hour"]}:${p["minute"]}"
            "SET_TIMER" -> "Timer ${p["seconds"]}s"
            "MEDIA_CONTROL" -> "${p["command"]} ${p["query"] ?: ""}"
            "HARDWARE_TOGGLE" -> "${p["command"]}"
            "OPEN_WEBPAGE" -> "${p["url"] ?: ""}"
            else -> ""
        }
    }

    private suspend fun awaitConfirmation(context: Context, action: ARIXYAction): Boolean =
        suspendCancellableCoroutine { cont ->
            val title = when (action.type.uppercase()) {
                "MAKE_CALL" -> "Call karein?"
                "SEND_WHATSAPP" -> "WhatsApp message bhejein?"
                else -> "Notification reply bhejein?"
            }
            log(action.type, TaskStepStatus.WAITING_CONFIRM, describe(action))
            pendingConfirmation.value = PendingConfirmation(title, describe(action)) { approved ->
                pendingConfirmation.value = null
                if (cont.isActive) cont.resume(approved)
            }
            cont.invokeOnCancellation {
                pendingConfirmation.value = null
            }
        }
}
