/*
 * Blocksy - offline call blocker
 * Copyright (C) 2026 BlueW9
 * Licensed under the GNU General Public License v3.0 or later.
 * See <https://www.gnu.org/licenses/gpl-3.0.html>.
 */
package com.bluew9.blocksy

import android.telecom.Call
import android.telecom.CallScreeningService

/**
 * Android binds this service by itself for every incoming call, so blocking
 * works even when the Blocksy app is closed. It only does one indexed lookup
 * in a local database. Nothing is logged and nothing leaves the phone.
 */
class BlockService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val response = CallResponse.Builder()
        try {
            val incoming = details.callDirection == Call.Details.DIRECTION_INCOMING
            val number = details.handle?.schemeSpecificPart
            if (incoming && number != null) {
                val key = Normalizer.key(number)
                if (key != null && BlockDb.get(this).isBlocked(key)) {
                    response
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                }
            }
        } catch (e: Exception) {
            // On any error the call is allowed through rather than lost.
        }
        respondToCall(details, response.build())
    }
}
