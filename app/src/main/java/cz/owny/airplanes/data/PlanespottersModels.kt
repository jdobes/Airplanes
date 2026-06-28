package cz.owny.airplanes.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhotosEnvelope(
    val photos: List<Planephoto> = emptyList(),
    val error: String? = null
)

@Serializable
data class Planephoto(
    val id: String? = null,
    val thumbnail: Thumbnail? = null,
    @SerialName("thumbnail_large") val thumbnailLarge: Thumbnail? = null,
    val link: String? = null,
    val photographer: String? = null
)

@Serializable
data class Thumbnail(
    val src: String? = null,
    val size: ThumbnailSize? = null
)

@Serializable
data class ThumbnailSize(
    val width: Int? = null,
    val height: Int? = null
)