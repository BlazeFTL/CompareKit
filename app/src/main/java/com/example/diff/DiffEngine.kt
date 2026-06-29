package com.example.diff

enum class DiffType {
    EQUAL, INSERT, DELETE, MODIFIED
}

data class DiffItem<T>(
    val type: DiffType,
    val value: T,
    val originalIndex: Int? = null,
    val revisedIndex: Int? = null,
    // For MODIFIED type, we can have character level diff details
    val subHighlights: List<SubRange>? = null
)

data class SubRange(
    val start: Int,
    val end: Int
)

data class DiffOptions(
    val ignoreWhitespace: Boolean = false,
    val ignoreEmptyLines: Boolean = false,
    val matchCase: Boolean = true
)

object MyersDiff {
    fun diff(
        original: List<String>,
        revised: List<String>,
        options: DiffOptions
    ): List<DiffItem<String>> {
        // Preprocess lines if required
        val processedOriginal = original.map { preprocess(it, options) }
        val processedRevised = revised.map { preprocess(it, options) }

        val origIndices = original.indices.filter { !options.ignoreEmptyLines || original[it].isNotBlank() }
        val revIndices = revised.indices.filter { !options.ignoreEmptyLines || revised[it].isNotBlank() }

        val n = origIndices.size
        val m = revIndices.size

        if (n == 0 && m == 0) return emptyList()
        if (n == 0) {
            return revised.mapIndexed { idx, v -> DiffItem(DiffType.INSERT, v, revisedIndex = idx) }
        }
        if (m == 0) {
            return original.mapIndexed { idx, v -> DiffItem(DiffType.DELETE, v, originalIndex = idx) }
        }

        // Classic Myers Diff algorithm
        val max = n + m
        // We're indexing with k from -max to max. The array size is 2 * max + 1
        val v = IntArray(2 * max + 1)
        val trace = ArrayList<IntArray>()

        var found = false
        for (d in 0..max) {
            val vCopy = v.clone()
            trace.add(vCopy)

            for (k in -d..d step 2) {
                val idx = k + max
                var x: Int
                if (k == -d || (k != d && v[idx - 1] < v[idx + 1])) {
                    x = v[idx + 1]
                } else {
                    x = v[idx - 1] + 1
                }
                var y = x - k

                while (x < n && y < m && 
                    processedOriginal[origIndices[x]] == processedRevised[revIndices[y]]
                ) {
                    x++
                    y++
                }
                v[idx] = x
                if (x >= n && y >= m) {
                    found = true
                    break
                }
            }
            if (found) break
        }

        // Backtracking
        val path = ArrayList<Pair<Int, Int>>()
        var currX = n
        var currY = m
        var d = trace.size - 1

        while (currX > 0 || currY > 0) {
            if (d < 0) break
            val k = currX - currY
            val idx = k + max
            val vD = trace[d]

            val kPrev = if (k == -d || (k != d && vD[idx - 1] < vD[idx + 1])) {
                k + 1
            } else {
                k - 1
            }
            val prevX = vD[kPrev + max]
            val prevY = prevX - kPrev

            while (currX > prevX && currY > prevY) {
                path.add((currX - 1) to (currY - 1))
                currX--
                currY--
            }

            if (currX > 0 || currY > 0) {
                path.add(currX to currY)
            }

            currX = prevX
            currY = prevY
            d--
        }

        path.reverse()

        val rawResult = ArrayList<DiffItem<String>>()
        var lastX = 0
        var lastY = 0

        for (step in path) {
            val px = step.first
            val py = step.second
            if (px == lastX && py == lastY + 1) {
                // Insert
                val realY = revIndices[lastY]
                rawResult.add(DiffItem(DiffType.INSERT, revised[realY], revisedIndex = realY))
                lastY++
            } else if (px == lastX + 1 && py == lastY) {
                // Delete
                val realX = origIndices[lastX]
                rawResult.add(DiffItem(DiffType.DELETE, original[realX], originalIndex = realX))
                lastX++
            } else {
                // Equal
                val realX = origIndices[lastX]
                val realY = revIndices[lastY]
                rawResult.add(DiffItem(DiffType.EQUAL, original[realX], originalIndex = realX, revisedIndex = realY))
                lastX++
                lastY++
            }
        }

        // Add any remaining
        while (lastX < n || lastY < m) {
            if (lastX < n && lastY < m) {
                val realX = origIndices[lastX]
                val realY = revIndices[lastY]
                rawResult.add(DiffItem(DiffType.EQUAL, original[realX], originalIndex = realX, revisedIndex = realY))
                lastX++
                lastY++
            } else if (lastY < m) {
                val realY = revIndices[lastY]
                rawResult.add(DiffItem(DiffType.INSERT, revised[realY], revisedIndex = realY))
                lastY++
            } else {
                val realX = origIndices[lastX]
                rawResult.add(DiffItem(DiffType.DELETE, original[realX], originalIndex = realX))
                lastX++
            }
        }

        // Handle lines omitted because of ignoreEmptyLines
        val finalResult = ArrayList<DiffItem<String>>()
        var origPtr = 0
        var revPtr = 0

        // Combine raw results and ignored blank lines in sequential order
        var rawIdx = 0
        while (origPtr < original.size || revPtr < revised.size) {
            val nextRaw = if (rawIdx < rawResult.size) rawResult[rawIdx] else null

            if (nextRaw != null) {
                val targetOrig = nextRaw.originalIndex
                val targetRev = nextRaw.revisedIndex

                // Insert blank lines in original up to targetOrig
                if (targetOrig != null && origPtr < targetOrig) {
                    while (origPtr < targetOrig) {
                        finalResult.add(DiffItem(DiffType.DELETE, original[origPtr], originalIndex = origPtr))
                        origPtr++
                    }
                }
                // Insert blank lines in revised up to targetRev
                if (targetRev != null && revPtr < targetRev) {
                    while (revPtr < targetRev) {
                        finalResult.add(DiffItem(DiffType.INSERT, revised[revPtr], revisedIndex = revPtr))
                        revPtr++
                    }
                }

                // Append the raw diff item
                finalResult.add(nextRaw)
                if (targetOrig != null) origPtr = targetOrig + 1
                if (targetRev != null) revPtr = targetRev + 1
                rawIdx++
            } else {
                // Drain any leftovers
                while (origPtr < original.size) {
                    finalResult.add(DiffItem(DiffType.DELETE, original[origPtr], originalIndex = origPtr))
                    origPtr++
                }
                while (revPtr < revised.size) {
                    finalResult.add(DiffItem(DiffType.INSERT, revised[revPtr], revisedIndex = revPtr))
                    revPtr++
                }
            }
        }

        // Post-process to merge matching Delete + Insert lines into MODIFIED status
        return postProcessModifiedLines(finalResult)
    }

    private fun preprocess(line: String, options: DiffOptions): String {
        var res = line
        if (!options.matchCase) {
            res = res.lowercase()
        }
        if (options.ignoreWhitespace) {
            // Trim and replace multiple spaces/tabs with single space
            res = res.trim().replace("\\s+".toRegex(), " ")
        }
        return res
    }

    private fun postProcessModifiedLines(items: List<DiffItem<String>>): List<DiffItem<String>> {
        val result = ArrayList<DiffItem<String>>()
        var i = 0
        val size = items.size

        while (i < size) {
            val current = items[i]
            if (i < size - 1) {
                val next = items[i + 1]
                // Pair DELETE followed by INSERT as MODIFIED
                if (current.type == DiffType.DELETE && next.type == DiffType.INSERT) {
                    val subDiffs = computeIntraLineDiff(current.value, next.value)
                    result.add(
                        DiffItem(
                            type = DiffType.MODIFIED,
                            value = current.value, // Source version of modified line
                            originalIndex = current.originalIndex,
                            revisedIndex = next.revisedIndex,
                            subHighlights = subDiffs.first // Highlights for original version
                        )
                    )
                    result.add(
                        DiffItem(
                            type = DiffType.MODIFIED,
                            value = next.value, // Revised version of modified line
                            originalIndex = current.originalIndex,
                            revisedIndex = next.revisedIndex,
                            subHighlights = subDiffs.second // Highlights for revised version
                        )
                    )
                    i += 2
                    continue
                }
            }
            result.add(current)
            i++
        }
        return result
    }

    /**
     * Computes the character-level or word-level differences between two strings.
     * Returns a pair of highlights: (OriginalLineHighlights, RevisedLineHighlights)
     */
    fun computeIntraLineDiff(orig: String, rev: String): Pair<List<SubRange>, List<SubRange>> {
        if (orig.isBlank() || rev.isBlank()) return Pair(emptyList(), emptyList())

        // Use a simple word-based or char-based LCS to highlight differences.
        // For character-level details, we'll find matching blocks.
        val common = lcs(orig, rev)
        
        val origHighlights = ArrayList<SubRange>()
        val revHighlights = ArrayList<SubRange>()

        // Original highlights (the characters NOT in common)
        var lastOrigMatch = 0
        for (match in common) {
            if (match.first > lastOrigMatch) {
                origHighlights.add(SubRange(lastOrigMatch, match.first))
            }
            lastOrigMatch = match.first + match.third
        }
        if (lastOrigMatch < orig.length) {
            origHighlights.add(SubRange(lastOrigMatch, orig.length))
        }

        // Revised highlights (the characters NOT in common)
        var lastRevMatch = 0
        for (match in common) {
            if (match.second > lastRevMatch) {
                revHighlights.add(SubRange(lastRevMatch, match.second))
            }
            lastRevMatch = match.second + match.third
        }
        if (lastRevMatch < rev.length) {
            revHighlights.add(SubRange(lastRevMatch, rev.length))
        }

        return Pair(origHighlights, revHighlights)
    }

    // Longest Common Subsequence of characters to find matching blocks
    // Returns List of Triple(origIndex, revIndex, matchLength)
    private fun lcs(orig: String, rev: String): List<Triple<Int, Int, Int>> {
        val dp = Array(orig.length + 1) { IntArray(rev.length + 1) }
        for (i in 1..orig.length) {
            for (j in 1..rev.length) {
                if (orig[i - 1] == rev[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to extract matches
        val matches = ArrayList<Triple<Int, Int, Int>>()
        var i = orig.length
        var j = rev.length
        while (i > 0 && j > 0) {
            if (orig[i - 1] == rev[j - 1]) {
                // Find length of consecutive block matches
                val origIdx = i - 1
                val revIdx = j - 1
                var matchLen = 0
                while (i > 0 && j > 0 && orig[i - 1] == rev[j - 1]) {
                    matchLen++
                    i--
                    j--
                }
                matches.add(Triple(i, j, matchLen))
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        matches.reverse()
        return matches
    }
}
