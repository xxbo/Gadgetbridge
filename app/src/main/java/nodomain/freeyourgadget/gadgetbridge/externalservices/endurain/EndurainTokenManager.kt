/*  Copyright (C) 2026 Arjan Schrijver

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
package nodomain.freeyourgadget.gadgetbridge.externalservices.endurain

import android.content.Context
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceTokenStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class EndurainTokenManager(context: Context) : ExternalServiceTokenStore(context, "endurain_tokens") {
    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(EndurainTokenManager::class.java)

        /** Epoch seconds passed this in 2001, so anything below it is a lifetime, not a date. */
        private const val EPOCH_THRESHOLD = 1000000000
    }

    /**
     * Stores a token pair. Endurain answers with either a lifetime in seconds or an absolute
     * expiry, so a value too small to be an epoch timestamp is read as the former.
     */
    fun saveTokens(accessToken: String, refreshToken: String, accessTokenExpiry: Int, refreshTokenExpiry: Int) {
        val accessTokenExpiryTs = expiryTimestamp(accessTokenExpiry)
        val refreshTokenExpiryTs = expiryTimestamp(refreshTokenExpiry)
        preferences.edit {
            putString("access_token", accessToken)
                .putString("refresh_token", refreshToken)
                .putInt("access_token_expires_at", accessTokenExpiryTs)
                .putInt("refresh_token_expires_at", refreshTokenExpiryTs)
        }
    }

    private fun expiryTimestamp(expiry: Int): Int =
        if (expiry < EPOCH_THRESHOLD) ((System.currentTimeMillis() / 1000) + expiry).toInt() else expiry

    fun getAccessToken(): String? = preferences.getString("access_token", null)
    fun getAccessTokenExpiresAt(): Int = preferences.getInt("access_token_expires_at", 0)
    fun getRefreshToken(): String? = preferences.getString("refresh_token", null)
    fun getRefreshTokenExpiresAt(): Int = preferences.getInt("refresh_token_expires_at", 0)

    fun isLoggedIn(): Boolean {
        return getRefreshToken() != null && !isRefreshTokenExpired()
    }
    fun isAccessTokenExpired(): Boolean {
        return (System.currentTimeMillis() / 1000) >= getAccessTokenExpiresAt()
    }
    fun isRefreshTokenExpired(): Boolean {
        return (System.currentTimeMillis() / 1000) >= getRefreshTokenExpiresAt()
    }

    fun performTokenRefresh(
        serverUrl: String,
        callback: (Boolean) -> Unit
    ) {
        Thread {
            try {
                val apiClient = EndurainApiClient(serverUrl, this)
                val response = apiClient.refreshToken()

                when {
                    response == null -> {
                        LOG.error("Token refresh failed: null response")
                        callback(false)
                    }
                    response.access_token != null -> {
                        LOG.info("Token refresh successful")
                        saveTokens(
                            response.access_token,
                            response.refresh_token!!,
                            response.expires_in!!,
                            response.refresh_token_expires_in!!
                        )
                        callback(true)
                    }
                    else -> {
                        LOG.error("Token refresh failed: ${response.detail}")
                        callback(false)
                    }
                }
            } catch (e: Exception) {
                LOG.error("Token refresh error", e)
                callback(false)
            }
        }.start()
    }
}