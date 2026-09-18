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
package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalService
import nodomain.freeyourgadget.gadgetbridge.externalservices.ExternalServiceRegistry
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import java.io.File

/** File format a service accepts when an activity is uploaded. */
enum class WorkoutPayloadFormat { FIT, GPX }

/**
 * Outcome of bringing the track of an already-uploaded activity in line with the local workout.
 *
 * [newRemoteActivityId] and [newPhotoMediaId] are set when the service could only do this by
 * re-creating the activity, so the caller stores the new ids. [unsupported] means the service
 * offers no way to do it at all and there is nothing to retry. [refusal] is a localized reason
 * the update was deliberately not attempted, to show the user.
 */
data class TrackUpdateResult(
    val success: Boolean,
    val newRemoteActivityId: String? = null,
    val newPhotoMediaId: Int? = null,
    val refusal: String? = null,
    val unsupported: Boolean = false
)

/**
 * An [ExternalService] that takes workouts, described by what it accepts and by what it can still
 * change on an activity after the initial upload. [WorkoutUploadWorker] drives every service
 * through this, so supporting another one means adding an implementation rather than another
 * branch.
 */
interface WorkoutUploadTarget : ExternalService {
    /**
     * Value stored in `WorkoutUpload.service`. Persisted, so see the registry in
     * [WorkoutUploadStore] before choosing one.
     */
    val service: Int

    /** Menu item that uploads the workout on screen to this service. */
    @get:IdRes
    val uploadMenuItemRes: Int

    /** Format to export a workout in for this service. */
    val format: WorkoutPayloadFormat

    /** Whether a workout with no GPS track can be uploaded at all. */
    val requiresTrack: Boolean

    /**
     * Notification id used to report a failure. Derived from [service], which is unique and never
     * reused, so two services cannot end up overwriting each other's notification.
     */
    val failureNotificationId: Int
        get() = FAILURE_NOTIFICATION_ID_BASE + service

    /** Whether the user asked for workouts to be uploaded to this service automatically. */
    fun isAutoUploadEnabled(prefs: GBPrefs): Boolean = prefs.getBoolean(autoUploadPrefKey, false)

    /** Localized "upload to this service failed: %s" message, for the auto-upload notification. */
    fun failureMessage(context: Context, reason: String): String

    /** Localized confirmation that this service took the workout. */
    @get:StringRes
    val uploadedRes: Int

    /**
     * Localized "the upload to this service failed" message for an upload the user asked for,
     * carrying [reason] when the service gave one.
     */
    fun uploadFailedMessage(context: Context, reason: String?): String

    /**
     * Web address of the activity [remoteActivityId] on the service, for opening it in a browser,
     * or null when it cannot be built because the service is not set up.
     */
    fun activityUrl(context: Context, remoteActivityId: String): String?

    fun upload(
        context: Context,
        summary: BaseActivitySummary,
        payload: File
    ): WorkoutUploader.UploadResult

    /** Pushes the name and activity kind onto an activity that already exists remotely. */
    fun updateMetadata(
        context: Context,
        remoteActivityId: String,
        summary: BaseActivitySummary
    ): Boolean

    /**
     * Makes the track of the remote activity match [payload]. [row] is what was last uploaded, so
     * a service that has to re-create the activity can tell a track being added from one merely
     * changing, and can weigh that against how destructive re-creating is.
     */
    fun updateTrack(
        context: Context,
        remoteActivityId: String,
        payload: File,
        summary: BaseActivitySummary,
        row: WorkoutUpload
    ): TrackUpdateResult

    /**
     * Brings the header photo of an existing remote activity in line with the local workout, or
     * null when the service has no media API. [photoFile] is null when the photo was removed.
     */
    fun syncPhoto(
        context: Context,
        remoteActivityId: String,
        photoFile: File?,
        previousMediaId: Int?
    ): WorkoutUploader.PhotoSyncResult?

    companion object {
        /** Clear of the ids the rest of the app posts notifications under. */
        const val FAILURE_NOTIFICATION_ID_BASE = 4710
    }
}

/** Every registered service that takes workouts. */
object WorkoutUploadTargets {
    val ALL: List<WorkoutUploadTarget>
        get() = ExternalServiceRegistry.services.filterIsInstance<WorkoutUploadTarget>()

    /**
     * Targets the user has finished setting up, whether or not they take workouts on their own.
     * This is the set a workout can be sent to at all, so it is what the screens showing upload
     * state answer against.
     */
    @JvmStatic
    fun configured(context: Context): List<WorkoutUploadTarget> =
        ALL.filter { it.isConfigured(context) }

    /**
     * Targets set up and asked to take every synced workout without being told to, which is the
     * set the background worker drives.
     */
    @JvmStatic
    fun autoUploadEnabled(context: Context, prefs: GBPrefs): List<WorkoutUploadTarget> =
        configured(context).filter { it.isAutoUploadEnabled(prefs) }
}
