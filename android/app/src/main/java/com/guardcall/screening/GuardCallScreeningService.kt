package com.guardcall.screening

import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.guardcall.GuardCallApp
import com.guardcall.domain.CallAction
import com.guardcall.domain.CallDecision
import com.guardcall.domain.HistoryEntry
import com.guardcall.domain.RuleEngine
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class GuardCallScreeningService : CallScreeningService() {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS, ArrayBlockingQueue<Runnable>(8))
    private val pending = java.util.IdentityHashMap<Call.Details, Runnable>() // Main-thread confined.
    private val app get() = application as GuardCallApp
    private var destroyed = false

    override fun onScreenCall(callDetails: Call.Details) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            // The framework itself completes outgoing-call screening after this callback.
            return
        }
        val fallback = Runnable {
            finish(callDetails, CallDecision(CallAction.ALLOW, "Délai de filtrage dépassé", null), false)
        }
        pending[callDetails] = fallback
        // Telecom imposes 5s. Arm a separate main-loop deadline before any disk access.
        main.postDelayed(fallback, 3_000)
        val number = callDetails.handle?.schemeSpecificPart
        try {
            worker.execute {
                var record = false
                val decision = try {
                    val payload = app.blocklistStore.loadNow()
                    record = payload.settings.historyEnabled
                    RuleEngine(payload).evaluate(number).let {
                        if (it.action == CallAction.SILENCE && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            it.copy(action = CallAction.ALLOW, reason = "${it.reason} — silencieux disponible dès Android 10")
                        } else it
                    }
                } catch (e: Exception) {
                    // No raw numbers or JSON contents in logcat. Fail open, never replace corrupt storage.
                    Log.w(TAG, "Filtrage indisponible : ${e.javaClass.simpleName}")
                    CallDecision(CallAction.ALLOW, "Filtrage indisponible", null)
                }
                main.post { finish(callDetails, decision, record) }
            }
        } catch (e: RejectedExecutionException) {
            finish(callDetails, CallDecision(CallAction.ALLOW, "Service occupé", null), false)
        }
    }

    private fun finish(details: Call.Details, decision: CallDecision, record: Boolean) {
        val timeout = pending.remove(details) ?: return // Exactly one response; late results discarded.
        main.removeCallbacks(timeout)
        if (destroyed) return
        val response = CallResponse.Builder().apply {
            when (decision.action) {
                CallAction.BLOCK -> {
                    setDisallowCall(true)
                    setRejectCall(true)
                    setSkipCallLog(false)
                    setSkipNotification(false)
                }
                CallAction.SILENCE -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setSilenceCall(true)
                    }
                }
                CallAction.ALLOW -> Unit
            }
        }.build()
        // respondToCall uses the framework's main-thread-managed binder adapter.
        if (safeRespond(details, response) && record) {
            val entry = HistoryEntry(timestamp = System.currentTimeMillis(), number = decision.number,
                action = decision.action, reason = decision.reason)
            app.historyExecutor.execute {
                try { app.historyStore.appendNow(entry) }
                catch (e: Exception) { Log.w(TAG, "Historique indisponible : ${e.javaClass.simpleName}") }
            }
        }
    }

    private fun safeRespond(details: Call.Details, response: CallResponse): Boolean = try {
        respondToCall(details, response)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Réponse impossible : ${e.javaClass.simpleName}")
        false
    }

    override fun onUnbind(intent: Intent?): Boolean {
        pending.keys.toList().forEach {
            finish(it, CallDecision(CallAction.ALLOW, "Service déconnecté", null), false)
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        destroyed = true
        main.removeCallbacksAndMessages(null)
        pending.clear()
        worker.shutdownNow() // Never wait for IO on the main thread.
        super.onDestroy()
    }

    private companion object { const val TAG = "OnraccrocheScreening" }
}
