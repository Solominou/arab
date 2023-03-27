package com.test


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import java.util.*

class LiveKoora : MainAPI() {
    override var mainUrl = "https://livekoora.online"
    override var name = "Live Koora"
    override var lang = "ar"
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val dataMap = mapOf(
            "Matches Today " to mainUrl,
        )
        return HomePageResponse(dataMap.apmap { (title, data) ->
            val document = app.get(data).document
            val shows = document.select(".match-container").mapNotNull {
                println("Does it exist?: "+it.select("div.live").isNotEmpty())
                if(it.select("div.live").isNotEmpty()) {
                    val title = it.select(".team-name").map{ it.text() }.joinToString(" Vs ")
                    LiveSearchResponse(
                        title,
                        it.select("a").attr("href"),
                        this@LiveKoora.name,
                        TvType.Live,
                        "https://img.zr5.repl.co/vs?title=${title}&leftUrl=${it.select("div.left-team img").attr("data-img")}&rightUrl=${it.select("div.right-team img").attr("data-img")}",
                        lang = "ar"
                    )
                } else return@mapNotNull null
            }
            HomePageList(
                title,
                shows.ifEmpty {
                              arrayListOf(LiveSearchResponse(
                                  "لا يوجد اي مباراة حاليا",
                                  mainUrl,
                                  this@LiveKoora.name,
                                  TvType.Live,
                                  "https://img.zr5.repl.co/vs",
                                  lang = "ar"
                              ))
                },
                isHorizontalImages = true
            )
        })
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.select("h1.topic-title").text()
        return LiveStreamLoadResponse(
            title,
            url,
            this.name,
            url,
        )
    }
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data).document
        doc.select(".servers-name button").apmap {
            val quality = it.text().replace("p| ".toRegex(),"").toInt()
            val iframeLink = it.attr("onclick")
                .replace(".*setURL\\(\"|\"\\)".toRegex(), "")
                .replace("\"\\+back\\+\"".toRegex(), "${((0..100).shuffled().last() * 50) + 1}")
            println("Iframe Link: $iframeLink for $quality")
            val m3u8Link = app.get(iframeLink, referer = mainUrl).document.select("script").get(6).html().replace(".*hls: '|',.*".toRegex() ,"")
            println("M3U8 Link: $m3u8Link")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    m3u8Link,
                    mainUrl,
                    quality,
                    isM3u8 = true
                )
            )
        }
        return true
    }
}
