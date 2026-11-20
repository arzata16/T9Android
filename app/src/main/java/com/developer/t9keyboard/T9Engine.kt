package com.developer.t9keyboard

import android.os.Handler
import android.os.Looper

enum class TypingMode {
    T9_PREDICTIVE, // T9 Prediktif
    MULTI_TAP      // Nokia Klasik 3310 style
}

enum class CapitalizationState {
    LOWERCASE,
    CAPITALIZED,
    UPPERCASE
}

class T9Engine {

    companion object {
        val shared = T9Engine()
    }

    private val charToDigit = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
    )

    val digitToLetters = mapOf(
        "1" to listOf(".", ",", "?", "!", "'", "-", "@", "1"),
        "2" to listOf("a", "b", "c", "2"),
        "3" to listOf("d", "e", "f", "3"),
        "4" to listOf("g", "h", "i", "4"),
        "5" to listOf("j", "k", "l", "5"),
        "6" to listOf("m", "n", "o", "6"),
        "7" to listOf("p", "q", "r", "s", "7"),
        "8" to listOf("t", "u", "v", "8"),
        "9" to listOf("w", "x", "y", "z", "9"),
        "0" to listOf(" ", "0")
    )

    private val digitWordMap = HashMap<String, MutableList<String>>()

    var currentDigits: String = ""
    var currentMode: TypingMode = TypingMode.T9_PREDICTIVE
    var capState: CapitalizationState = CapitalizationState.CAPITALIZED
    var currentPredictions: List<String> = emptyList()

    // Multi-tap State (400ms timeout ala GO Keyboard Android)
    private var lastTappedKey: String? = null
    private var multiTapIndex: Int = 0
    private val handler = Handler(Looper.getMainLooper())
    private var multiTapRunnable: Runnable? = null
    private val multiTapTimeout = 400L

    var onMultiTapInsert: ((String) -> Unit)? = null
    var onMultiTapReplace: ((String) -> Unit)? = null
    var onMultiTapCommit: (() -> Unit)? = null

    val hasActiveMultiTap: Boolean
        get() = lastTappedKey != null

    val currentMultiTapChar: String?
        get() {
            val key = lastTappedKey ?: return null
            val chars = digitToLetters[key] ?: return null
            return applyCapitalization(chars[multiTapIndex])
        }

    init {
        buildDictionaryIndex()
    }

    private fun buildDictionaryIndex() {
        for (word in DictionaryData.words) {
            val lower = word.lowercase()
            val digits = StringBuilder()
            for (char in lower) {
                charToDigit[char]?.let { digits.append(it) }
            }
            val key = digits.toString()
            if (key.isNotEmpty()) {
                if (!digitWordMap.containsKey(key)) {
                    digitWordMap[key] = mutableListOf()
                }
                digitWordMap[key]?.add(lower)
            }
        }
    }

    // MARK: - T9 Predictive Methods

    fun addDigit(digit: String): List<String> {
        currentDigits += digit
        return getPredictions()
    }

    fun removeLastDigit(): List<String> {
        if (currentDigits.isNotEmpty()) {
            currentDigits = currentDigits.dropLast(1)
        }
        return getPredictions()
    }

    fun resetSequence() {
        currentDigits = ""
        currentPredictions = emptyList()
    }

    fun getPredictions(): List<String> {
        if (currentDigits.isEmpty()) {
            currentPredictions = emptyList()
            return emptyList()
        }

        val results = mutableListOf<String>()

        // 1. Exact match
        digitWordMap[currentDigits]?.let {
            results.addAll(it)
        }

        // 2. Prefix match
        for ((digits, words) in digitWordMap) {
            if (digits != currentDigits && digits.startsWith(currentDigits)) {
                results.addAll(words)
                if (results.size >= 15) break
            }
        }

        // 3. Fallback
        if (results.isEmpty()) {
            results.add(generateFallbackWord(currentDigits))
        }

        val capitalized = results.map { applyCapitalization(it) }
        currentPredictions = capitalized
        return capitalized
    }

    private fun generateFallbackWord(digits: String): String {
        val word = StringBuilder()
        for (d in digits) {
            digitToLetters[d.toString()]?.firstOrNull()?.let {
                word.append(it)
            }
        }
        return word.toString()
    }

    fun applyCapitalization(word: String): String {
        return when (capState) {
            CapitalizationState.LOWERCASE -> word.lowercase()
            CapitalizationState.CAPITALIZED -> {
                if (word.isEmpty()) word
                else word.substring(0, 1).uppercase() + word.substring(1).lowercase()
            }
            CapitalizationState.UPPERCASE -> word.uppercase()
        }
    }

    fun toggleShift() {
        capState = when (capState) {
            CapitalizationState.LOWERCASE -> CapitalizationState.CAPITALIZED
            CapitalizationState.CAPITALIZED -> CapitalizationState.UPPERCASE
            CapitalizationState.UPPERCASE -> CapitalizationState.LOWERCASE
        }
    }

    // MARK: - Multi-Tap Mode Methods

    fun handleMultiTap(key: String) {
        multiTapRunnable?.let { handler.removeCallbacks(it) }

        val chars = digitToLetters[key] ?: return
        if (chars.isEmpty()) return

        if (lastTappedKey == key) {
            multiTapIndex = (multiTapIndex + 1) % chars.size
            val currentChar = applyCapitalization(chars[multiTapIndex])
            onMultiTapReplace?.invoke(currentChar)
        } else {
            commitPendingMultiTap()
            lastTappedKey = key
            multiTapIndex = 0
            val currentChar = applyCapitalization(chars[multiTapIndex])
            onMultiTapInsert?.invoke(currentChar)
        }

        val runnable = Runnable { commitPendingMultiTap() }
        multiTapRunnable = runnable
        handler.postDelayed(runnable, multiTapTimeout)
    }

    fun commitPendingMultiTap() {
        multiTapRunnable?.let { handler.removeCallbacks(it) }
        multiTapRunnable = null
        if (lastTappedKey == null) return
        lastTappedKey = null
        multiTapIndex = 0
        onMultiTapCommit?.invoke()
    }

    fun cancelMultiTap() {
        multiTapRunnable?.let { handler.removeCallbacks(it) }
        multiTapRunnable = null
        lastTappedKey = null
        multiTapIndex = 0
    }
}
