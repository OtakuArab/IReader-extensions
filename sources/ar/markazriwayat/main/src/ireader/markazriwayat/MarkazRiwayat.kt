package ireader.markazriwayat

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLParameter
import ireader.core.source.Dependencies
import ireader.core.source.asJsoup
import ireader.core.source.findInstance
import ireader.core.source.model.ChapterInfo
import ireader.core.source.model.Command
import ireader.core.source.model.CommandList
import ireader.core.source.model.Filter
import ireader.core.source.model.FilterList
import ireader.core.source.model.MangaInfo
import ireader.core.source.model.MangaInfo.Companion.COMPLETED
import ireader.core.source.model.MangaInfo.Companion.ONGOING
import ireader.core.source.model.MangasPageInfo
import ireader.core.source.SourceFactory
import kotlinx.serialization.json.*
import tachiyomix.annotations.*

@Extension
abstract class MarkazRiwayat(deps: Dependencies) : SourceFactory(deps) {
    override val lang = "ar"
    override val baseUrl = "https://markazriwayat.com"
    override val id: Long = 842746329
    override val name = "MarkazRiwayat"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun getFilters(): FilterList = listOf(Filter.Title())
    override fun getCommands(): CommandList = listOf(
        Command.Detail.Fetch(),
        Command.Chapter.Fetch(),
        Command.Content.Fetch(),
    )

    override val detailFetcher = SourceFactory.Detail(
        nameSelector = "h1.manga-title",
        coverSelector = ".manga-cover-wrap img",
        coverAtt = "data-src",
        addBaseurlToCoverLink = true,
        authorBookSelector = ".manga-author",
        descriptionSelector = ".manga-summary",
        statusSelector = ".manga-status-pill",
        onStatus = { status ->
            val lower = status.lowercase()
            when {
                lower.contains("complete") || lower.contains("مكتملة") -> COMPLETED
                lower.contains("ongoing") || lower.contains("جارية") -> ONGOING
                else -> ONGOING
            }
        },
        categorySelector = ".pill-list .pill",
    )

    override val chapterFetcher = SourceFactory.Chapters(
        selector = ".ch-row",
        nameSelector = ".ch-title",
        linkSelector = "a",
        linkAtt = "href",
        reverseChapterList = true,
        addBaseUrlToLink = true,
    )

    override val contentFetcher = SourceFactory.Content(
        pageContentSelector = ".reading-content .text-right p",
    )

    // استخراج كل الـ mangaId سواء رواية لها مواسم أو لا
    private suspend fun extractMangaIds(novelUrl: String): List<String> {
        val document = client.get(requestBuilder(novelUrl)).asJsoup()
        val ids = mutableListOf<String>()

        // روايات بدون مواسم
        val singleId = document.select("#manga-chapters-list").attr("data-manga-id")
        if (singleId.isNotBlank()) ids.add(singleId)

        // روايات لها مواسم
        val seasonIds = document.select("[data-manga-id]")
            .mapNotNull { it.attr("data-manga-id").takeIf { id -> id.isNotBlank() } }

        ids.addAll(seasonIds)
        return ids.distinct()
    }

    private suspend fun fetchChaptersViaApi(
        mangaId: String,
        order: String = "DESC",
        perPage: Int = 30
    ): List<ChapterInfo> {
        val allChapters = mutableListOf<ChapterInfo>()
        var page = 1
        var hasMore = true

        while (hasMore) {
            val apiUrl = "$baseUrl/wp-json/theam/v1/manga-chapters?manga_id=$mangaId&order=$order&page=$page&per_page=$perPage"
            try {
                val response = client.get(requestBuilder(apiUrl)).bodyAsText()
                val jsonObj = json.parseToJsonElement(response).jsonObject
                val items = jsonObj["items"]?.jsonArray ?: emptyList()

                val pageChapters = items.mapNotNull { el ->
                    val item = el.jsonObject
                    val label = item["label"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val url = item["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    ChapterInfo(name = label, key = url, dateUpload = 0L)
                }
                allChapters.addAll(pageChapters)
                hasMore = jsonObj["has_more"]?.jsonPrimitive?.booleanOrNull == true
                page++
            } catch (e: Exception) {
                hasMore = false
            }
        }
        return allChapters
    }

    override suspend fun getChapterList(manga: MangaInfo, commands: List<Command<*>>): List<ChapterInfo> {
        val chapterFetch = commands.findInstance<Command.Chapter.Fetch>()
        if (chapterFetch != null && chapterFetch.html.isNotBlank()) {
            return chaptersParse(chapterFetch.html.asJsoup()).reversed()
        }

        try {
            val mangaIds = extractMangaIds(manga.key)
            val allChapters = mutableListOf<ChapterInfo>()
            for (id in mangaIds) {
                allChapters.addAll(fetchChaptersViaApi(id))
            }
            if (allChapters.isNotEmpty()) {
                return allChapters.sortedBy { it.name }
            }
        } catch (e: Exception) {
            // fallback
        }
        return super.getChapterList(manga, commands)
    }
}
