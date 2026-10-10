package com.guardcall.screening

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.guardcall.data.BlocklistMatcher
import com.guardcall.data.BlocklistStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Service Android de filtrage d'appels entrants.
 *
 * Deux points corrigés par rapport à la version initiale :
 * 1. `setSilenceCall` n'existe qu'à partir de l'**API 29** alors que `minSdk`
 *    vaut 26 : l'appel est désormais gardé par `Build.VERSION.SDK_INT`, sinon un
 *    `NoSuchMethodError` faisait planter le service sur Android 8 et 9.
 * 2. La lecture du fichier se fait sur un executor dédié : `onScreenCall` est
 *    appelé sur le thread principal et une E/S disque y provoquait un ANR.
 */
class GuardCallScreeningService : CallScreeningService() {

    private lateinit var store: BlocklistStore
    private val executor by lazy { Executors.newSingleThreadExecutor() }

    override fun onCreate() {
        super.onCreate()
        store = BlocklistStore(applicationContext)
    }

    override fun onScreenCall(callDetails: Call.Details) {
        val incomingNumber = callDetails.handle?.schemeSpecificPart

        executor.execute {
            val payload = store.loadNow()
            val blocked = BlocklistMatcher.isBlocked(incomingNumber, payload)
            val label = BlocklistMatcher.identificationLabel(incomingNumber, payload)

            Log.i(
                TAG,
                "Appel entrant ${mask(incomingNumber)} | blocked=$blocked label=$label | " +
                    "bloqués=${payload.blocked.size} identifiés=${payload.identified.size}"
            )

            val response = CallResponse.Builder().apply {
                when {
                    blocked -> {
                        // Blocage : appel rejeté, sans sonnerie ni notification.
                        setDisallowCall(true)
                        setRejectCall(true)
                        setSkipCallLog(false)
                        setSkipNotification(false)
                    }

                    label != null -> {
                        // Spam suspect : on laisse passer mais on coupe la sonnerie.
                        // `setSilenceCall` est disponible à partir d'Android 10 (API 29).
                        setDisallowCall(false)
                        setRejectCall(false)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            setSilenceCall(true)
                        }
                        setSkipCallLog(false)
                        setSkipNotification(false)
                    }

                    else -> {
                        setDisallowCall(false)
                        setRejectCall(false)
                    }
                }
            }.build()

            respondToCall(callDetails, response)
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        super.onDestroy()
    }

    /** Le journal système ne doit pas contenir de numéro en clair. */
    private fun mask(number: String?): String {
        if (number.isNullOrEmpty()) return "<inconnu>"
        return if (number.length <= 4) "****" else "****" + number.takeLast(2)
    }

    private companion object {
        const val TAG = "GuardCallScreening"
    }
}
