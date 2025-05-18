package com.faselhd

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.nicehttp.requestCreator
import com.lagradost.cloudstream3.network.WebViewResolver
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.network.CloudflareKiller
import org.jsoup.nodes.Element

class FaselHD : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://faselhd.io"
    private val alternativeUrl = "https://www.faselhd.club"
    override var name = "FaselHD"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.AsianDrama, TvType.Anime)
    private val cfKiller = CloudflareKiller()

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val url = select("div.postDiv a").attr("href") ?: return null
        val posterUrl = select("div.postDiv a imgdiv-class img").attr("data-src")
            ?: select("div.postDiv a imgdiv-class img").attr("src")
        val title = select("div.postDiv a imgdiv-class img").attr("alt")
        val quality = select(".quality").first()?.text()?.replace("1080p |-".toRegex(), "")
        val type = if (title.contains("فيلم")) TvType.Movie else TvType.TvSeries
        return MovieSearchResponse(
            title.replace("الموسم الأول|برنامج|فيلم|مترجم|اون لاين|مسلسل|مشاهدة|انمي|أنمي".toRegex(), ""),
            url,
            this@FaselHD.name,
            type,
            posterUrl,
            null,
            null,
            quality = getQualityFromString(quality),
            posterHeaders = cfKiller.getCookieHeaders(alternativeUrl).toMap()
        )
    }

    override val mainPage = mainPageOf(
        "$mainUrl/all-movies/page/" to "الافلام",
        "$mainUrl/movies_top_views/page/" to "الافلام الاعلي مشاهدة",
        "$mainUrl/dubbed-movies/page/" to "الأفلام المدبلجة",
        "$mainUrl/movies_top_imdb/page/" to "الافلام الاعلي تقييما IMDB",
        "$mainUrl/series/page/" to "مسلسلات",
        "$mainUrl/recent_series/page/" to "المضاف حديثا",
        "$mainUrl/anime/page/" to "الأنمي",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        var doc = app.get(request.data + page).document
        if (doc.select("title").text() == "Just a moment...") {
            doc = app.get(request.data.replace(mainUrl, alternativeUrl) + page, interceptor = cfKiller, timeout = 120).document
        }
        val list = doc.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull { element ->
                element.toSearchResponse()
            }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.replace(" ", "+")
        var d = app.get("$mainUrl/?s=$q").document
        if (d.select("title").text() == "Just a moment...") {
            d = app.get("$alternativeUrl/?s=$q", interceptor = cfKiller, timeout = 120).document
        }
        return d.select("div[id=\"postList\"] div[class=\"col-xl-2 col-lg-2 col-md-3 col-sm-3\"]")
            .mapNotNull {
                it.toSearchResponse()
            }
    }

    override suspend fun load(url: String): LoadResponse {
        var doc = app.get(url).document
        if (doc.select("title").text() == "Just a moment...") {
            doc = app.get(url, interceptor = cfKiller, timeout = 120).document
        }
        val isMovie = doc.select("div.epAll").isEmpty()
        val title = doc.select("title").text().replace(" - فاصل إعلاني", "")
        val poster = doc.select("div.posterImg img").attr("src")
        val year = doc.select("span.year").text().toIntOrNull()
        val plot = doc.select("div.singleDesc p").text()

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        } else {
            val episodes = doc.select("div.epAll a").map {
                Episode(it.attr("href"), it.text())
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(data).document
        val iframe = doc.select("iframe").attr("src")
        WebViewResolver(iframe, cfKiller).getSafeLink()?.let { videoUrl ->
            M3u8Helper.generateM3u8(
                name,
                videoUrl,
                "$mainUrl/"
            ).forEach(callback)
        }
    }
}