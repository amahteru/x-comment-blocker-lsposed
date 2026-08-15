package com.xtwitter.blocker.engine

import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

class RegexTrie {

    private class TrieNode {
        val children = linkedMapOf<Char, TrieNode>()
    }

    companion object {
        private const val MAX_KEYWORD_LENGTH = 1000
        private val REGEX_SPECIAL_CHARS = setOf(
            '.', '*', '+', '?', '^', '$', '{', '}', '(', ')', '|', '[', ']', '\\'
        )

        private fun escapeChar(c: Char): String {
            return if (REGEX_SPECIAL_CHARS.contains(c)) "\\$c" else c.toString()
        }

        private fun stringify(node: TrieNode): String {
            val keys = node.children.keys.toList()
            if (keys.isEmpty()) return ""
            val branches = keys.map { k ->
                escapeChar(k) + stringify(node.children[k]!!)
            }
            return if (branches.size > 1) {
                "(?:" + branches.joinToString("|") + ")"
            } else {
                branches[0]
            }
        }

        /**
         * Builds a single optimized RegExp Pattern using Trie structure from plain text keywords.
         */
        fun buildTriePattern(plainKeywords: Collection<String>?): Pattern? {
            if (plainKeywords.isNullOrEmpty()) return null

            val seen = mutableSetOf<String>()
            for (kw in plainKeywords) {
                val normalized = SpamCharCleaner.normalizeText(kw)
                if (normalized.isNotEmpty() && normalized.length <= MAX_KEYWORD_LENGTH) {
                    seen.add(normalized)
                }
                val cleaned = SpamCharCleaner.removeInvisibleChars(kw).trim().lowercase()
                if (cleaned.isNotEmpty() && cleaned.length <= MAX_KEYWORD_LENGTH) {
                    seen.add(cleaned)
                }
            }
            if (seen.isEmpty()) return null

            val sorted = seen.sortedBy { it.length }

            // Pruning: if kw already contains an existing shorter keyword as substring, kw is redundant
            val pruned = mutableListOf<String>()
            for (kw in sorted) {
                if (pruned.none { kw.contains(it) }) {
                    pruned.add(kw)
                }
            }

            val root = TrieNode()
            for (kw in pruned) {
                var node = root
                for (ch in kw) {
                    node = node.children.getOrPut(ch) { TrieNode() }
                }
            }

            val regexStr = stringify(root)
            if (regexStr.isEmpty()) return null

            return try {
                Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parses a raw keyword string (e.g. multi-line text) into:
         * 1. Plain keywords list
         * 2. Custom Regex patterns (lines starting and ending with '/')
         */
        fun parseKeywords(rawText: String?): ParsedKeywords {
            val plainList = mutableListOf<String>()
            val regexList = mutableListOf<Pattern>()

            if (rawText.isNullOrEmpty()) {
                return ParsedKeywords(plainList, regexList)
            }

            for (line in rawText.lines()) {
                val cleaned = SpamCharCleaner.removeInvisibleChars(line).trim()
                if (cleaned.isEmpty()) continue

                if (cleaned.length >= 3 && cleaned.startsWith("/")) {
                    val lastSlash = cleaned.lastIndexOf('/')
                    if (lastSlash > 0) {
                        val patternContent = cleaned.substring(1, lastSlash)
                        val flagsStr = cleaned.substring(lastSlash + 1)
                        var flags = Pattern.UNICODE_CASE
                        if (flagsStr.contains("i", ignoreCase = true)) {
                            flags = flags or Pattern.CASE_INSENSITIVE
                        }
                        if (flagsStr.contains("m", ignoreCase = true)) {
                            flags = flags or Pattern.MULTILINE
                        }
                        if (flagsStr.contains("s", ignoreCase = true)) {
                            flags = flags or Pattern.DOTALL
                        }

                        try {
                            val compiled = Pattern.compile(patternContent, flags)
                            regexList.add(compiled)
                            continue
                        } catch (_: PatternSyntaxException) {
                            // If regex compilation fails, fall back to plain keyword
                        }
                    }
                }

                plainList.add(cleaned.lowercase())
            }

            return ParsedKeywords(plainList, regexList)
        }
    }

    data class ParsedKeywords(
        val plainKeywords: List<String>,
        val customRegexes: List<Pattern>
    )
}
