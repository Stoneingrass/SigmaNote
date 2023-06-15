

package com.sigmanote.notes.ui.note

object HighlightHelper {

    const val START_ELLIPSIS = "\u2026\uFEFF"

    fun findHighlightsInString(text: String, query: String, max: Int = Int.MAX_VALUE): MutableList<IntRange> {
        val highlights = mutableListOf<IntRange>()
        var queryClean = query
        if (query.first() == '"' && query.last() == '"') {
            queryClean = queryClean.substring(1, queryClean.length - 1)
        }
        if (max > 0) {
            var i = 0
            while (i < text.length) {
                i = text.indexOf(queryClean, i, ignoreCase = true)
                if (i == -1) {
                    break
                }
                highlights += i..(i + queryClean.length)
                if (highlights.size == max) {
                    break
                }
                i++
            }
        }
        return highlights
    }

    fun getStartEllipsizedText(
        text: String,
        highlights: MutableList<IntRange>,
        startEllipsisThreshold: Int,
        startEllipsisDistance: Int
    ): Highlighted {
        var ellipsizedText = text
        if (highlights.isNotEmpty()) {
            val firstIndex = highlights.first().first
            if (firstIndex > startEllipsisThreshold) {
                var highlightShift = firstIndex - minOf(startEllipsisDistance, startEllipsisThreshold)
                // Skip white space between ellipsis start and text
                while (text[highlightShift].isWhitespace()) {
                    highlightShift++
                }
                highlightShift -= START_ELLIPSIS.length
                if (highlightShift > 0) {
                    ellipsizedText = START_ELLIPSIS + text.substring(highlightShift + START_ELLIPSIS.length)
                    for ((i, highlight) in highlights.withIndex()) {
                        highlights[i] = (highlight.first - highlightShift)..(highlight.last - highlightShift)
                    }
                }
            }
        }
        return Highlighted(ellipsizedText, highlights)
    }
}
