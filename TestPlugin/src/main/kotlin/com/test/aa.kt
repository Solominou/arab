package com.test

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.nicehttp.Requests

class E : MainAPI() {
    data class Sources (
        @JsonProperty("quality") val quality: Int?,
        @JsonProperty("link") val link: String
    )


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        println("Started...")
        val baseURL = data.split("/")[0] + "//" + data.split("/")[2]

        val session = Requests()
        val episodeSoup = session.get(data).document

        val vidstreamURL = fixUrlNull(episodeSoup.selectFirst("iframe.auto-size")?.attr("src") ) ?: throw ErrorLoadingException("No iframe")

        val videoSoup = app.get(vidstreamURL).document
        fixUrlNull( videoSoup.select("source").firstOrNull { it.hasAttr("src") }?.attr("src"))?.let {
            println("It has a link...")
            println("LINK: ( $it )")
            callback.invoke(
                ExtractorLink(
                    this.name,
                    this.name,
                    it,
                    this.mainUrl,
                    Qualities.Unknown.value,
                    true,
                    // Does not work without these headers!
                    headers = mapOf("range" to "bytes=0-"),
                )
            )
            return true
        } ?: run {
            println(videoSoup.html())
            println("It does not have a link connecting the api...")
            val jsCode = videoSoup.select("script")[1].html()
//            val function = videoSoup.select("script")[2].attr("onload")
//            val verificationToken = Regex("\\{'[0-9a-zA-Z_]*':'ok'\\}").findAll(jsCode).first().value.replace("\\{'|':.*".toRegex(), "")
//            val encodedAdLinkVar = Regex("\\([0-9a-zA-Z_]{2,12}\\[Math").findAll(jsCode).first().value.replace("\\(|\\[M.*".toRegex(),"")
//            val encodingArraysRegEx = Regex(",[0-9a-zA-Z_]{2,12}=\\[\\]").findAll(jsCode).toList()
//            val firstEncodingArray = encodingArraysRegEx[1].value.replace(",|=.*".toRegex(),"")
//            val secondEncodingArray = encodingArraysRegEx[2].value.replace(",|=.*".toRegex(),"")
//            println("verificationToken: $verificationToken")
//            println("encodedAdLinkVar: $encodedAdLinkVar")
//            println("firstEncodingArray: $firstEncodingArray")
//            println("secondEncodingArray: $secondEncodingArray")
//            jsCode = jsCode.replace("^<script type=\"text/javascript\">".toRegex(),"")
//            jsCode = jsCode.replace("[;,]\\\$\\('\\*'\\)(.*)\$".toRegex(),";")
//            jsCode = jsCode.replace(",ismob=(.*)\\(navigator\\[(.*)\\]\\)[,;]".toRegex(),";")
//            jsCode = jsCode.replace("var a0b=function\\(\\)(.*)a0a\\(\\);".toRegex(),"")
//            jsCode = "$jsCode var link = ''; for (var i = 0; i <= $secondEncodingArray['length']; i++) { link += $firstEncodingArray[$secondEncodingArray[i]] || ''; } return [link, $encodedAdLinkVar[0]] };var result = $function"
            // javascriptResult replaced the lines up
            val javascriptResult =
                app.post("https://helper.zr5.repl.co", data = mapOf("data" to jsCode, "baseUrl" to baseURL)).text.split(",")
            // this was supposed to be in app, but javascript is funky in kotlin
            val adLink = javascriptResult[0]
            println("adLink: $adLink")
            val verificationLink = javascriptResult[1]
            println("verificationLink: $verificationLink")
            val verificationToken = javascriptResult[2]
            println("verificationToken: $verificationToken")
            session.get(adLink) // Ad
            session.post(verificationLink, data=mapOf(verificationToken to "ok")) // Verification
            println("Session: $session")
            val vidstreamResponse = session.get(vidstreamURL).document
            println("vidstreamResponse: " + vidstreamResponse.html())
            val qualityLinksFileURL = baseURL + vidstreamResponse.select("source").attr("src")
            println("qualityLinksFileURL: $qualityLinksFileURL") // isn't getting the m3u link :(
            val requestJSON = app.get("https://egybest-sgu8utcomnfb.runkit.sh/egybest?url=$data").text
            // To solve this you need to send a verify request which is pretty hidden, see
            // https://vear.egybest.deals/tvc.php?verify=.......
            val jsonArray = AppUtils.parseJson<List<Sources>>(requestJSON)
            for (i in jsonArray) {
                val quality = i.quality
                val link = i.link
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        link,
                        this.mainUrl,
                        quality!!,
                        link.replace(".*\\.".toRegex(),"") == "m3u8",
                        // Does not work without these headers!
                        headers = mapOf("range" to "bytes=0-"),
                    )
                )
            }
            return true
        }
    }
}