package com.example.tunes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class Song(
    val title: String,
    val artist: String,
    val url: String,
    val thumbnail: String?,
    val durationSec: Long
)

class YouTubeRepository {

    private val yt = ServiceList.YouTube

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val extractor = yt.getSearchExtractor(
            query,
            listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
            ""
        )
        extractor.fetchPage()
        extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map {
                Song(
                    title = it.name,
                    artist = it.uploaderName ?: "",
                    url = it.url,
                    thumbnail = it.thumbnails.lastOrNull()?.url,
                    durationSec = it.duration
                )
            }
    }

    suspend fun audioUrl(videoUrl: String): String = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(yt, videoUrl)
        info.audioStreams
            .filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .maxByOrNull { it.averageBitrate }
            ?.content
            ?: error("No playable audio stream found")
    }
}
