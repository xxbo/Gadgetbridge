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
package nodomain.freeyourgadget.gadgetbridge.externalservices.endurain

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.RemoteOp
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.TrackUpdateResult
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.UploadCapabilities
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutPayloadFormat
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploadStore
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploadTarget
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutUploader
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload
import nodomain.freeyourgadget.gadgetbridge.externalservices.AuthStyle
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import java.io.File

object EndurainService : WorkoutUploadTarget {
    override val id = "endurain"
    override val nameRes = R.string.pref_category_endurain
    override val authStyle = AuthStyle.LOGIN

    override val service = WorkoutUploadStore.SERVICE_ENDURAIN
    override val uploadMenuItemRes = R.id.activity_action_upload_to_endurain

    override val capabilities = UploadCapabilities(
        // A FIT can be synthesized from the summary alone, so a trackless workout still uploads.
        formats = listOf(WorkoutPayloadFormat.FIT),
        // No endpoint replaces an activity's track, so changing one means delete plus re-upload,
        // which gives the activity a new id. See updateTrack.
        activity = setOf(RemoteOp.CREATE, RemoteOp.REPLACE, RemoteOp.DELETE),
        metadata = setOf(RemoteOp.UPDATE),
        // Media is listed, deleted and uploaded, so a photo changes by way of a new media entry.
        media = setOf(RemoteOp.CREATE, RemoteOp.REPLACE, RemoteOp.DELETE)
    )

    /**
     * Version the server reported when it was last asked, for the settings summary. Kept here
     * rather than on a screen so it survives the screen being closed and reopened.
     */
    @Volatile
    var serverVersion: String? = null

    override fun isLoggedIn(context: Context) = EndurainTokenManager(context).isLoggedIn()

    override fun statusSummary(context: Context): CharSequence {
        val server = serverUrl()
        val tokenManager = EndurainTokenManager(context)
        var summary = if (tokenManager.isLoggedIn() && server != null) {
            context.getString(
                R.string.endurain_logged_in_refresh_token,
                server,
                DateTimeUtils.parseTimeStamp(tokenManager.getRefreshTokenExpiresAt())
            )
        } else {
            context.getString(R.string.endurain_not_logged_in_integration_disabled)
        }
        serverVersion?.let { summary += context.getString(R.string.endurain_server_version, it) }
        return summary
    }

    override fun showSetup(fragment: Fragment) {
        EndurainSetupBottomSheet().show(fragment.parentFragmentManager, "endurain_setup")
    }

    override fun logout(context: Context): Boolean {
        val server = serverUrl()
        val tokenManager = EndurainTokenManager(context)
        if (server == null) {
            tokenManager.clearTokens()
            return true
        }
        return try {
            EndurainApiClient(server, tokenManager).logout()
        } catch (e: Exception) {
            // The session may well be gone on the server already, and a user who cannot reach it
            // still has to be able to disconnect, so the credentials go either way.
            tokenManager.clearTokens()
            true
        }
    }

    /**
     * Re-creating an activity to push a changed track destroys one the user may have been looking
     * at, so it is offered as a choice rather than done silently. See [updateTrack].
     */
    override fun onSettingsScreenCreated(fragment: PreferenceFragmentCompat) {
        val screen = fragment.preferenceScreen
        val replaceOnTrackChange = SwitchPreferenceCompat(screen.context).apply {
            key = GBPrefs.ENDURAIN_REPLACE_ON_TRACK_CHANGE
            setDefaultValue(false)
            layoutResource = R.layout.preference_checkbox
            setIcon(R.drawable.ic_warning)
            setTitle(R.string.pref_endurain_replace_on_track_change_title)
            setSummary(R.string.pref_endurain_replace_on_track_change_summary)
        }
        screen.addPreference(replaceOnTrackChange)
        // Set once it is in the hierarchy: a dependency named on a preference that is not yet
        // attached is resolved against an empty one, and throws.
        replaceOnTrackChange.dependency = autoUploadPrefKey
    }

    /** The refresh token expires, so it is renewed on a schedule rather than at the next upload. */
    override fun scheduleBackgroundWork(context: Context) {
        EndurainPeriodicTokenRefresher.scheduleNextExecution(context)
    }

    override fun failureMessage(context: Context, reason: String): String =
        context.getString(R.string.auto_upload_failed_endurain, reason)

    override val uploadedRes = R.string.endurain_successfully_uploaded_toast

    override fun uploadFailedMessage(context: Context, reason: String?): String =
        reason ?: context.getString(R.string.endurain_error_while_uploading_toast)

    override fun activityUrl(context: Context, remoteActivityId: String): String? {
        val server = serverUrl() ?: return null
        return "${server.trimEnd('/')}/activity/$remoteActivityId"
    }

    override fun upload(context: Context, summary: BaseActivitySummary, payload: File) =
        WorkoutUploader.uploadToEndurainBlocking(context, summary, payload)

    override fun updateMetadata(context: Context, remoteActivityId: String, summary: BaseActivitySummary) =
        WorkoutUploader.updateEndurainMetadataBlocking(context, remoteActivityId, summary)

    /**
     * Endurain can edit an activity's metadata but never its track, so the only way to push a
     * changed track is to delete the activity and upload it again under a new id.
     *
     * That is done unconditionally only when it is purely additive: the activity was uploaded
     * without a track and now has one, which is the case of a workout synced from a watch that
     * records none, with a GPX attached afterwards. Any other change re-creates an activity the
     * user may have been looking at for a while, so it needs their explicit consent.
     */
    override fun updateTrack(
        context: Context,
        remoteActivityId: String,
        payload: File,
        summary: BaseActivitySummary,
        row: WorkoutUpload
    ): TrackUpdateResult {
        val trackAdded = row.hadTrack != true && WorkoutUploader.summaryHasTrack(summary)
        val allowed = trackAdded || GBApplication.getPrefs()
            .getBoolean(GBPrefs.ENDURAIN_REPLACE_ON_TRACK_CHANGE, false)
        if (!allowed) {
            return TrackUpdateResult.Refused(
                context.getString(
                    R.string.workout_upload_status_track_change_declined,
                    context.getString(nameRes)
                )
            )
        }

        val (result, refusal) = WorkoutUploader.recreateEndurainActivityBlocking(
            context, summary, remoteActivityId, payload
        )
        if (result?.remoteActivityId != null) {
            return TrackUpdateResult.Recreated(result.remoteActivityId, result.photoMediaId)
        }
        return when (refusal) {
            WorkoutUploader.RecreateRefusal.REMOTE_EDITED ->
                TrackUpdateResult.Refused(context.getString(R.string.auto_upload_endurain_remote_edited))

            else -> TrackUpdateResult.Failed
        }
    }

    override fun syncPhoto(
        context: Context,
        remoteActivityId: String,
        photoFile: File?,
        previousMediaId: Int?
    ) = WorkoutUploader.syncEndurainPhotoBlocking(context, remoteActivityId, photoFile, previousMediaId)
}
