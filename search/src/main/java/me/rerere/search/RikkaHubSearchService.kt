package me.rerere.search

import androidx.compose.runtime.Composable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema

object RikkaHubSearchService : SearchService<SearchServiceOptions.RikkaHubOptions> {
    override val name: String = "RikkaHub"

    @Composable
    override fun Description() {
    }

    override fun parameters(options: SearchServiceOptions.RikkaHubOptions): InputSchema? =
        InputSchema.Obj(
            properties = buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            },
            required = listOf("query")
        )

    override fun scrapingParameters(options: SearchServiceOptions.RikkaHubOptions): InputSchema? =
        null

    override suspend fun search(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.RikkaHubOptions
    ): Result<SearchResult> {
        return Result.failure(IllegalStateException("RikkaHub search is not available in PocketTavern-Lite"))
    }

    override suspend fun scrape(
        params: JsonObject,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.RikkaHubOptions
    ): Result<ScrapedResult> {
        error("RikkaHub does not support scraping")
    }
}
