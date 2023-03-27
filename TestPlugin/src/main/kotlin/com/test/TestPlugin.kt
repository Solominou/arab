package com.test

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TestingPlugin: Plugin() {
    override fun load(context: Context) {
        // Extractors

        // Providers
        registerMainAPI(EgyBest())
        //registerMainAPI(LiveKoora())
    }
}
