package org.ireader.core

import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import tachiyomi.source.Dependencies
import tachiyomi.source.HttpSource
import tachiyomi.source.model.*
import java.security.MessageDigest

/** Taken from https://tachiyomi.org/ **/
abstract class ParsedHttpSource(private val dependencies: Dependencies) : HttpSource(dependencies) {


    override val id: Long by lazy {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
                .reduce(Long::or) and Long.MAX_VALUE
    }

    private fun headersBuilder() = io.ktor.http.Headers.build {
        append(HttpHeaders.UserAgent, "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.91 Mobile Safari/537.36")
        append(HttpHeaders.CacheControl, "max-age=0")
    }

    open val headers: Headers = headersBuilder()


    fun requestBuilder(
            url: String,
            mHeaders: io.ktor.http.Headers = headers,
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(url)
            headersBuilder()
        }
    }


    protected open fun detailRequest(manga: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(manga.key)
            headers { headers }
        }
    }

    override suspend fun getMangaDetails(manga: MangaInfo): MangaInfo {
        return detailParse(client.get<String>(detailRequest(manga)).parseHtml())
    }

    open fun chaptersRequest(book: MangaInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(book.key)
            headers { headers }
        }
    }

    override suspend fun getPageList(chapter: ChapterInfo): List<Page> {
        return getContents(chapter).map { Text(it) }
    }

    open suspend fun getContents(chapter: ChapterInfo): List<String> {
        return pageContentParse(client.get<String>(contentRequest(chapter)).parseHtml())
    }

    open fun contentRequest(chapter: ChapterInfo): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            url(chapter.key)
            headers { headers }
        }
    }

    fun String.parseHtml(): Document {
        return Jsoup.parse(this)
    }


    abstract fun chapterFromElement(element: Element): ChapterInfo


    fun bookListParse(document: Document, elementSelector: String, nextPageSelector: String?, parser: (element: Element) -> MangaInfo): MangasPageInfo {
        val books = document.select(elementSelector).map { element ->
            parser(element)
        }

        val hasNextPage = nextPageSelector?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPageInfo(books, hasNextPage)
    }

    abstract fun chaptersSelector(): String?

    open fun chaptersParse(document: Document): List<ChapterInfo> {
        return document.select(chaptersSelector()).map { chapterFromElement(it) }
    }


    abstract fun pageContentParse(
            document: Document,
    ): List<String>


    abstract fun detailParse(document: Document): MangaInfo
}