package com.lumisound.android.bridge.api

import com.lumisound.android.bridge.model.GalleryImageDto
import com.lumisound.android.bridge.model.GallerySyncDto
import retrofit2.http.GET

/**
 * The account's Gallery Background, as Lumisound on iPhone backs it up. Read-only on
 * purpose.
 *
 * - No uploads or deletes: Lumisound reconciles the cloud gallery by count rather than by
 *   id. When a photo is removed on the iPhone, it deletes whichever cloud entries sit past
 *   its local count, so a photo added from here would be the one deleted.
 * - Settings come from the read-only `GET /user/sync`. Its `POST` twin replaces the
 *   account's favorites and playlists wholesale (see [LibraryDataApi]), so nothing here
 *   writes through it.
 */
interface GalleryApi {
    @GET("user/gallery/images")
    suspend fun images(): List<GalleryImageDto>

    @GET("user/sync")
    suspend fun syncSnapshot(): GallerySyncDto
}
