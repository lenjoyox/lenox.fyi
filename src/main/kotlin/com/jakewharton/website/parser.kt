package com.jakewharton.website

import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.readText
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.commonmark.Extension
import org.commonmark.parser.Parser
import org.yaml.snakeyaml.Yaml

internal class SiteParser(
	markdownExtensions: List<Extension>,
) {
	private val yaml = Yaml()
	private val mdParser = Parser.Builder()
		.extensions(markdownExtensions)
		.build()

	fun parse(path: Path): Site {

		val posts = path.resolve("posts")
			.asDatedCollection()
			.map(::parseBlogPost)
			.toList()
			.sortedBy(BlogPost::title) // Make same-day posts deterministic.
			.sortedByDescending(BlogPost::date)

		return Site(posts)
	}

	private data class DatedEntry(
		val path: Path,
		val date: LocalDate,
		val slug: String,
		val content: String,
	)

	private fun Path.asDatedCollection(): Sequence<DatedEntry> {
		return walk(maxDepth = 1)
			.drop(1) // Starts with self.
			.map { file ->
				val name = file.fileName.toString().substringBeforeLast('.')
				val (rawDate, slug) = name.splitAround(10)
				val date = LocalDate.parse(rawDate)
				val content = file.readText()
				DatedEntry(
					path = file,
					date = date,
					slug = slug,
					content = content,
				)
			}
	}

	private fun parseBlogPost(entry: DatedEntry): BlogPost {
		val (rawFrontMatter, rawMarkdown) = entry.content.splitFrontMatter()
		val frontMatter = (yaml.load(rawFrontMatter) as Map<String, Any>).toMutableMap()

		val title = frontMatter.remove("title") as String? ?: error("Missing title: ${entry.path}")
		frontMatter.remove("lead") // TODO figure out what to do
		frontMatter.remove("image") // TODO figure out what to do

		val blogName = frontMatter.remove("blog") as String?
		val blogLink = frontMatter.remove("blog_link") as String?
		val externalLink = if (blogName != null) {
			checkNotNull(blogLink) { "Blog link required if blog name specified: ${entry.path}" }
			BlogPost.ExternalLink(blogName, blogLink.toHttpUrl())
		} else {
			check(blogLink == null) { "Blog name required if blog link specified: ${entry.path}" }
			null
		}

		val tags = (frontMatter.remove("tags") as List<String>?).orEmpty()

		val content = mdParser.parse(rawMarkdown)

		checkFrontMatterIsEmpty(frontMatter, entry)
		return BlogPost(
			path = entry.path,
			date = entry.date,
			slug = entry.slug,
			title = title,
			externalLink = externalLink,
			tags = tags.asSetChecked(),
			content = content,
		)
	}

	private fun checkFrontMatterIsEmpty(
		frontMatter: Map<String, Any?>,
		entry: DatedEntry,
	) {
		check(frontMatter.isEmpty()) {
			buildString {
				appendLine("Unhandled front matter in ${entry.path}:")
				frontMatter.keys.joinTo(this, prefix = " - ", separator = "\n - ")
			}
		}
	}
}

private fun String.splitAround(index: Int): Pair<String, String> {
	return take(index) to substring(index + 1)
}

private fun <T> List<T>.asSetChecked(): Set<T> {
	val set = toSet()
	check(set.size == size) { "Duplicate items found" }
	return set
}

private fun String.splitFrontMatter(): Pair<String?, String> {
	if (startsWith("---\n")) {
		val second = indexOf("---\n", startIndex = 4)
		if (second != -1) {
			return substring(4, second) to substring(second + 4)
		}
	}
	return null to this
}
