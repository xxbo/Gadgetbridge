/*  Copyright (C) 2026 Arjan Schrijver, José Rebelo, Dany Mestas

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.externalservices.wanderer

import android.content.Context
import android.view.View
import android.widget.EditText
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.externalservices.AbstractApiTokenSetupBottomSheet
import nodomain.freeyourgadget.gadgetbridge.util.InternetUtils
import org.slf4j.LoggerFactory
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WandererSetupBottomSheet : AbstractApiTokenSetupBottomSheet(
    R.layout.wanderer_bottomsheet_setup_wizard,
    R.id.wanderer_api_token
) {
    override val service = WandererService

    private lateinit var serverNameInput: EditText

    /**
     * Handle read while the token was being checked, so it is stored alongside it. Null when the
     * server did not give one, which only costs the link to the trail.
     */
    private var handle: String? = null

    private val serverName: String get() = serverNameInput.text.toString().trim()

    override fun onViewsBound(view: View) {
        serverNameInput = view.findViewById(R.id.wanderer_server_name)
        serverNameInput.setText(WandererService.serverUrl().orEmpty())
    }

    override fun validate(apiToken: String): String? = when {
        serverName.isEmpty() || apiToken.isEmpty() ->
            getString(R.string.wanderer_setup_missing_information)

        !apiToken.startsWith(WandererTokenManager.TOKEN_PREFIX) ->
            getString(R.string.wanderer_setup_api_token_error)

        else -> null
    }

    override fun checkToken(apiToken: String): Pair<Boolean, String?> {
        val context = requireContext()
        val server = serverName
        val apiClient = WandererApiClient(server, WandererTokenManager(context))

        val reachable = await<Pair<Boolean, String?>> { done ->
            apiClient.checkServerReachable(apiToken) { ok, reason -> done(ok to reason) }
        } ?: return false to InternetUtils.connectFailureReason(context, server)
        if (!reachable.first) {
            return false to (reachable.second ?: InternetUtils.connectFailureReason(context, server))
        }

        handle = await<String?> { done -> apiClient.fetchUserHandle(apiToken) { done(it) } }
        return true to null
    }

    override fun onTokenAccepted(apiToken: String) {
        val server = serverName
        LOG.info("Saving Wanderer server ({}) and API token", server)
        GBApplication.getPrefs().preferences.edit {
            putString(WandererService.serverPrefKey, server)
        }
        WandererTokenManager(requireContext()).apply {
            saveToken(apiToken)
            handle?.let { saveHandle(it) }
        }
    }

    /** Runs a callback-style call to completion, answering null when it does not come back. */
    private fun <T> await(start: ((T) -> Unit) -> Unit): T? {
        val latch = CountDownLatch(1)
        var result: T? = null
        start { value ->
            result = value
            latch.countDown()
        }
        return if (latch.await(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) result else null
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WandererSetupBottomSheet::class.java)
        private const val CHECK_TIMEOUT_SECONDS = 30L
    }
}
