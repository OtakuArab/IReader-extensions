package ireader.skynovel

import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import org.ireader.core_api.source.Dependencies
import org.ireader.core_api.source.SourceFactory
import org.ireader.core_api.source.asJsoup
import org.ireader.core_api.source.model.ChapterInfo
import org.ireader.core_api.source.model.Command
import org.ireader.core_api.source.model.Filter
import org.ireader.core_api.source.model.MangaInfo
import tachiyomix.annotations.Extension


@Extension
abstract class SkyNovel(private val deps: Dependencies) : SourceFactory(
        lang = "en",
        baseUrl = "https://skynovel.org",
        id = 19,
        name = "SkyNovel",
        deps = deps,
        filterList = listOf(
                Filter.Title(),
                Filter.Sort(
                        "Sort By:", arrayOf(
                        "Latest",
                        "Popular",
                        "New",
                        "Most Views",
                        "Rating",
                )
                ),
        ),
        exploreFetchers = listOf(
                BaseExploreFetcher(
                        "Latest",
                        endpoint = "/manga/page/{page}/?m_orderby=latest",
                        selector = ".page-item-detail .item-thumb",
                        nameSelector = "a",
                        nameAtt = "title",
                        linkSelector = "a",
                        linkAtt = "href",
                        coverSelector = "a img",
                        coverAtt = "src",
                        nextPageSelector = ".nav-previous",
                        nextPageValue = "Older Posts"
                ),
                BaseExploreFetcher(
                        "Search",
                        endpoint = "/page/{page}/?s={query}&post_type=wp-manga",
                        selector = ".c-tabs-item .row",
                        nameSelector = "a",
                        linkSelector = "a",
                        linkAtt = "href",
                        coverSelector = "a img",
                        coverAtt = "src",
                        nextPageSelector = ".nav-previous",
                        nextPageValue = "Older Posts",
                        type = SourceFactory.Type.Search
                ),
                BaseExploreFetcher(
                        "Trending",
                        endpoint = "/manga/page/{page}/?m_orderby=trending",
                        selector = ".page-item-detail .item-thumb",
                        nameSelector = "a",
                        nameAtt = "title",
                        linkSelector = "a",
                        linkAtt = "href",
                        coverSelector = "a img",
                        coverAtt = "src",
                        nextPageSelector = ".nav-previous",
                        nextPageValue = "Older Posts"
                ),
                BaseExploreFetcher(
                        "New",
                        endpoint = "/manga/page/{page}/?m_orderby=new-manga",
                        selector = ".page-item-detail .item-thumb",
                        nameSelector = "a",
                        nameAtt = "title",
                        linkSelector = "a",
                        linkAtt = "href",
                        coverSelector = "a img",
                        coverAtt = "src",
                        nextPageSelector = ".nav-previous",
                        nextPageValue = "Older Posts"
                ),
                BaseExploreFetcher(
                        "Most Views",
                        endpoint = "/manga/page/{page}/?m_orderby=views",
                        selector = ".page-item-detail .item-thumb",
                        nameSelector = "a",
                        nameAtt = "title",
                        linkSelector = "a",
                        linkAtt = "href",
                        coverSelector = "a img",
                        coverAtt = "src",
                        nextPageSelector = ".nav-previous",
                        nextPageValue = "Older Posts"
                ),
                BaseExploreFetcher(
                        "Rating",
                        endpoint = "/manga/page/{page}/?m_orderby=rating",
                        selector = ".page-item-detail .item-thumb",
                        nameSelector = "a",
                        nameAtt = "title",
                        linkSelector = "a",
                        linkAtt = "href",
                        coverSelector = "a img",
                        coverAtt = "src",
                        nextPageSelector = ".nav-previous",
                        nextPageValue = "Older Posts"
                ),

                ),
        detailFetcher = SourceFactory.Detail(
                nameSelector = ".post-title",
                coverSelector = ".summary_image a img",
                coverAtt = "src",
                authorBookSelector = ".author-content a",
                categorySelector = ".genres-content a",
                descriptionSelector = ".g_txt_over p",
        ),
        chapterFetcher = SourceFactory.Chapters(
                selector = ".wp-manga-chapter",
                nameSelector = "a",
                linkSelector = "a",
                linkAtt = "href",
        ),
        contentFetcher = SourceFactory.Content(
                pageTitleSelector = ".cha-tit",
                pageContentSelector = ".text-left h3,p ,.cha-content .pr .dib p",
        ),
) {

    override suspend fun getChapterList(
            manga: MangaInfo,
            commands: List<Command<*>>
    ): List<ChapterInfo> {
        val html = client.get(requestBuilder(manga.key)).asJsoup()
        val bookId = html.select(".rating-post-id").attr("value")


        var chapters = chaptersParse(
                        client.submitForm(url = "https://skynovel.org/wp-admin/admin-ajax.php", formParameters = Parameters.build {
                            append("action", "manga_get_chapters")
                            append("manga", bookId)
                        }).asJsoup(),
                )
        return chapters.reversed()
    }


}