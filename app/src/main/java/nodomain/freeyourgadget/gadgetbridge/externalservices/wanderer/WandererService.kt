/*  Copyright (C) 2026 Dany Mestas, José Rebelo

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
import androidx.fragment.app.Fragment
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.TrackUpdateResult
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutPayloadFormat
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploadStore
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploadTarget
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploader
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload
import nodomain.freeyourgadget.gadgetbridge.externalservices.AuthStyle
import java.io.File

object WandererService : WorkoutUploadTarget {
    override val id = "wanderer"
    override val nameRes = R.string.pref_category_wanderer
    override val authStyle = AuthStyle.API_TOKEN

    override val service = WorkoutUploadStore.SERVICE_WANDERER
    override val uploadMenuItemRes = R.id.activity_action_upload_to_wanderer
    override val format = WorkoutPayloadFormat.GPX

    // Wanderer stores trails, so there is nothing to upload without a track.
    override val requiresTrack = true

    override fun isLoggedIn(context: Context) = WandererTokenManager(context).isLoggedIn()

    override fun statusSummary(context: Context): CharSequence =
        if (isConfigured(context)) {
            context.getString(R.string.wanderer_logged_in, serverUrl())
        } else {
            context.getString(R.string.endurain_not_logged_in_integration_disabled)
        }

    override fun showSetup(fragment: Fragment) {
        WandererSetupBottomSheet().show(fragment.parentFragmentManager, "wanderer_setup")
    }

    // The API token is issued and revoked on the server, so there is nothing to tell it about.
    override fun logout(context: Context): Boolean {
        WandererTokenManager(context).clearTokens()
        return true
    }

    override fun failureMessage(context: Context, reason: String): String =
        context.getString(R.string.auto_upload_failed_wanderer, reason)

    override val uploadedRes = R.string.wanderer_toast_successfully_uploaded

    override fun uploadFailedMessage(context: Context, reason: String?): String =
        context.getString(
            R.string.wanderer_toast_upload_error,
            reason ?: context.getString(R.string.unknown_error)
        )

    /**
     * A trail page is namespaced by the handle of the account that owns it, and a handle that does
     * not resolve answers 500 rather than falling back to the trail, so there is no link to give
     * until the handle is known.
     */
    override fun activityUrl(context: Context, remoteActivityId: String): String? {
        val server = serverUrl() ?: return null
        val handle = WandererTokenManager(context).getHandle() ?: return null
        return "${server.trimEnd('/')}/trail/view/@$handle/$remoteActivityId"
    }

    override fun upload(context: Context, summary: BaseActivitySummary, payload: File) =
        WorkoutUploader.uploadToWandererBlocking(context, payload)

    // Wanderer infers a trail's name and type from the uploaded file.
    override fun updateMetadata(context: Context, remoteActivityId: String, summary: BaseActivitySummary) = true

    // Wanderer replaces the track on the existing trail, so the id and the user's edits survive.
    override fun updateTrack(
        context: Context,
        remoteActivityId: String,
        payload: File,
        summary: BaseActivitySummary,
        row: WorkoutUpload
    ) = TrackUpdateResult(
        success = WorkoutUploader.updateWandererTrackBlocking(context, remoteActivityId, payload, summary)
    )

    override fun syncPhoto(
        context: Context,
        remoteActivityId: String,
        photoFile: File?,
        previousMediaId: Int?
    ): WorkoutUploader.PhotoSyncResult? = null
}
