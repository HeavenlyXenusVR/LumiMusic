package com.lumisound.android.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Which permission this device actually needs to read its own music, and whether it
 * is held.
 *
 * `READ_MEDIA_AUDIO` was introduced in API 33; below that the audio library sits
 * behind `READ_EXTERNAL_STORAGE`. Requesting a permission the platform does not know
 * is not an error -- it is silently denied, and MediaStore then returns an empty
 * cursor rather than throwing, so the scan reports success with zero tracks. That is
 * exactly what happened on the first real device (Android 12), and it is why the
 * check lives here instead of being inlined at each call site.
 */
object AudioPermission {

    val required: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, required) == PackageManager.PERMISSION_GRANTED
}
