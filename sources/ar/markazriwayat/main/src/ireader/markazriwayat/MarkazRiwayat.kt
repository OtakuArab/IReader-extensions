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
@GenerateTests(
    unitTests = true,
    integrationTests = true,
    searchQuery = "sword",
    minSearchResults = 1
)
@TestFixture(
    novelUrl = "https://markazriwayat.com/novel/زوجتي-هي-حاكمة-السيف/",
    chapterUrl = "https://markazriwayat.com/novel/زوجتي-هي-حاكمة-السيف/الفصل-1/",
    expectedTitle = "زوجتي هي حاكمة السيف",
    expectedAuthor = "لورد غامض"
)
@TestExpectations(
    minLatestNovels = 10,
    minChapters = 100,
    supportsPagination = true,
    requiresLogin = false
)
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

    override val exploreFetchers = listOf(
        BaseExploreFetcher("Recently Added", "/new/", "a.lib-card", ".lib-card__title",
            ".lib-card__img img", "data-src", true, "a.lib-card", "href", true),
        BaseExploreFetcher("Library", "/library/", "a.lib-card", ".lib-card__title",
            ".lib-card__img img", "data-src", true, "a.lib-card", "href", true),
        BaseExploreFetcher("Search", "/?s={query}", "a.lib-card", ".lib-card__title",
            ".lib-card__img img", "data-src", true, "a.lib-card", "href", true,
            type = SourceFactory.Type.Search)
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
        val seasonIds = document.select(".season-item, .season-tab, .season-button")
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

    override suspend fun getMangaList(filters: FilterList, page: Int): MangasPageInfo {
        val query = filters.findInstance<Filter.Title>()?.value
        return if (!query.isNullOrBlank()) searchViaApi(query) else super.getMangaList(filters, page)
    }

    private suspend fun searchViaApi(query: String, perPage: Int = 20): MangasPageInfo {
        val encoded = query.encodeURLParameter()
        val apiUrl = "$baseUrl/wp-json/theam/v1/novel-search?term=$encoded&per_page=$perPage"
        val response = client.get(requestBuilder(apiUrl)).bodyAsText()
        val jsonObj = json.parseToJsonElement(response).jsonObject
        val items = jsonObj["items"]?.jsonArray ?: emptyList()

        val novels = items.mapNotNull { el ->
            val item = el.jsonObject
            val title = item["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val link = item["link"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val cover = item["cover"]?.jsonPrimitive?.contentOrNull ?: ""
            val genres = item["genres"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            val chaptersCount = item["chapters_count"]?.jsonPrimitive?.intOrNull ?: 0
            MangaInfo(
                key = link,
                title = title,
                cover = cover,
                genres = genres,
                description = if (chaptersCount > 0) "عدد الفصول: $chaptersCount" else ""
            )
        }
        return MangasPageInfo(novels, hasNextPage = false)
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
