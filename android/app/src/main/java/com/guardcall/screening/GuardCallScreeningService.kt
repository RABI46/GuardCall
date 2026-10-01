package com.guardcall.screening

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.guardcall.data.BlocklistStore

/**
 * Service de filtrage d'appels Android.
 * Équivalent iOS : CallDirectoryHandler (CXCallDirectoryProvider).
 *
 * - L'utilisateur doit accorder le rôle ROLE_CALL_SCREENING (Android 10+) ou être définie comme app d'écran.
 * - Le service est appelé pour chaque appel entrant non-contact.
 */
class GuardCallScreeningService : CallScreeningService() {

    private val tag = "GuardCallScreening"
    private lateinit var store: BlocklistStore

    override fun onCreate() {
        super.onCreate()
        store = BlocklistStore(applicationContext)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart
        val payload = store.loadSync()
        val blocked = store.isBlocked(incomingNumber, payload)
        val label = store.identificationLabel(incomingNumber, payload)

        Log.i(tag, "Appel entrant: $incomingNumber | blocked=$blocked label=$label | totalBlocked=${payload.blocked.size}")

        val response = CallResponse.Builder().apply {
            when {
                blocked -> {
                    // Bloque silencieusement : rejet sans sonnerie, sans notif d'appel manqué
                    setDisallowCall(true)
                    setRejectCall(true)
                    setSkipCallLog(false) // garde trace dans journal
                    setSkipNotification(false)
                }
                label != null -> {
                    // Spam suspect : on coupe la sonnerie mais laisse le journal
                    setDisallowCall(false)
                    setRejectCall(false)
                    setSilenceCall(true)
                    setSkipCallLog(false)
                    setSkipNotification(false)
                }
                else -> {
                    // Appel légitime : on laisse passer
                    setDisallowCall(false)
                    setRejectCall(false)
                    setSilenceCall(false)
                }
            }
        }.build()

        respondToCall(callDetails, response)
    }
}
