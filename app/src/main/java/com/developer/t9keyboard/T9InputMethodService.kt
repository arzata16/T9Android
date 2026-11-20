package com.developer.t9keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView

class T9InputMethodService : InputMethodService() {

    private val engine = T9Engine.shared
    private var isSymbolMode = false

    private lateinit var suggestionContainer: LinearLayout
    private lateinit var tvSuggestionPlaceholder: TextView
    private lateinit var tvMode: TextView
    private lateinit var tvShift: TextView
    private lateinit var tvSymbol: TextView

    private lateinit var let1: TextView
    private lateinit var let2: TextView
    private lateinit var let3: TextView
    private lateinit var let4: TextView
    private lateinit var let5: TextView
    private lateinit var let6: TextView
    private lateinit var let7: TextView
    private lateinit var let8: TextView
    private lateinit var let9: TextView
    private lateinit var let0: TextView

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    // Handler untuk rapid backspace
    private val repeatHandler = Handler(Looper.getMainLooper())
    private var backspaceRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.keyboard_view, null)

        suggestionContainer = view.findViewById(R.id.suggestionContainer)
        tvSuggestionPlaceholder = view.findViewById(R.id.tvSuggestionPlaceholder)
        tvMode = view.findViewById(R.id.tvMode)
        tvShift = view.findViewById(R.id.tvShift)
        tvSymbol = view.findViewById(R.id.tvSymbol)

        let1 = view.findViewById(R.id.let_1)
        let2 = view.findViewById(R.id.let_2)
        let3 = view.findViewById(R.id.let_3)
        let4 = view.findViewById(R.id.let_4)
        let5 = view.findViewById(R.id.let_5)
        let6 = view.findViewById(R.id.let_6)
        let7 = view.findViewById(R.id.let_7)
        let8 = view.findViewById(R.id.let_8)
        let9 = view.findViewById(R.id.let_9)
        let0 = view.findViewById(R.id.let_0)

        setupCallbacks()
        setupKeys(view)

        return view
    }

    private fun setupCallbacks() {
        engine.onMultiTapInsert = { char ->
            currentInputConnection?.commitText(char, 1)
            setSuggestions(listOf(char))
        }

        engine.onMultiTapReplace = { char ->
            currentInputConnection?.deleteSurroundingText(1, 0)
            currentInputConnection?.commitText(char, 1)
            setSuggestions(listOf(char))
        }

        engine.onMultiTapCommit = {
            setSuggestions(emptyList())
        }
    }

    private fun setupKeys(root: View) {
        val numberKeys = listOf(
            R.id.key_1 to "1",
            R.id.key_2 to "2",
            R.id.key_3 to "3",
            R.id.key_4 to "4",
            R.id.key_5 to "5",
            R.id.key_6 to "6",
            R.id.key_7 to "7",
            R.id.key_8 to "8",
            R.id.key_9 to "9"
        )

        for ((id, digit) in numberKeys) {
            val keyView = root.findViewById<View>(id)
            setInstantTouch(keyView) {
                onNumberKey(digit)
            }
        }

        // Tombol 0 (Spasi) di Tengah
        val spaceView = root.findViewById<View>(R.id.key_space)
        setInstantTouch(spaceView) {
            handleSpace()
        }

        // Tombol Shift / Caps
        val shiftView = root.findViewById<View>(R.id.key_shift)
        setInstantTouch(shiftView) {
            handleShift()
        }

        // Tombol Mode T9 / ABC
        val modeView = root.findViewById<View>(R.id.key_mode)
        setInstantTouch(modeView) {
            toggleTypingMode()
        }

        // Tombol Simbol / 123
        val symbolView = root.findViewById<View>(R.id.key_symbol)
        setInstantTouch(symbolView) {
            toggleSymbolMode()
        }

        // Tombol Enter / Kirim
        val enterView = root.findViewById<View>(R.id.key_enter)
        setInstantTouch(enterView) {
            handleEnter()
        }

        // Tombol Bola Dunia (Switch Keyboard)
        val globeView = root.findViewById<View>(R.id.key_globe)
        setInstantTouch(globeView) {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                switchToNextInputMethod(false)
            } else {
                imm.showInputMethodPicker()
            }
        }

        // Tombol Backspace dengan Rapid Repeat saat Ditahan
        val backspaceView = root.findViewById<View>(R.id.key_backspace)
        setupBackspace(backspaceView)
    }

    private fun setInstantTouch(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                playFeedback()
                v.isPressed = true
                action()
                true
            } else if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                v.isPressed = false
                true
            } else {
                false
            }
        }
    }

    private fun setupBackspace(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    playFeedback()
                    handleBackspace()

                    // Mulai rapid repeat setelah 350ms
                    val runnable = object : Runnable {
                        override fun run() {
                            handleBackspace()
                            playFeedback()
                            repeatHandler.postDelayed(this, 50)
                        }
                    }
                    backspaceRunnable = runnable
                    repeatHandler.postDelayed(runnable, 350)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    backspaceRunnable?.let { repeatHandler.removeCallbacks(it) }
                    backspaceRunnable = null
                    true
                }
                else -> false
            }
        }
    }

    private fun playFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(18)
            }
            audioManager?.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD)
        } catch (_: Exception) {}
    }

    // MARK: - Key Actions

    private fun onNumberKey(digit: String) {
        if (isSymbolMode) {
            currentInputConnection?.commitText(digit, 1)
            return
        }

        if (engine.currentMode == TypingMode.T9_PREDICTIVE) {
            val suggestions = engine.addDigit(digit)
            setSuggestions(suggestions)
        } else {
            engine.handleMultiTap(digit)
        }
    }

    private fun handleSpace() {
        if (isSymbolMode) {
            currentInputConnection?.commitText("0", 1)
            return
        }

        if (engine.currentMode == TypingMode.T9_PREDICTIVE) {
            if (engine.currentDigits.isNotEmpty()) {
                val bestWord = engine.currentPredictions.firstOrNull() ?: engine.getPredictions().firstOrNull()
                if (bestWord != null) {
                    currentInputConnection?.commitText("$bestWord ", 1)
                } else {
                    currentInputConnection?.commitText(" ", 1)
                }
                engine.resetSequence()
                setSuggestions(emptyList())
            } else {
                currentInputConnection?.commitText(" ", 1)
            }
        } else {
            engine.commitPendingMultiTap()
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun handleBackspace() {
        if (engine.currentMode == TypingMode.T9_PREDICTIVE && engine.currentDigits.isNotEmpty()) {
            val updated = engine.removeLastDigit()
            setSuggestions(updated)
        } else {
            if (engine.hasActiveMultiTap) {
                engine.cancelMultiTap()
                setSuggestions(emptyList())
            }
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleEnter() {
        if (engine.currentMode == TypingMode.T9_PREDICTIVE && engine.currentDigits.isNotEmpty()) {
            val bestWord = engine.currentPredictions.firstOrNull() ?: engine.getPredictions().firstOrNull()
            if (bestWord != null) {
                currentInputConnection?.commitText(bestWord, 1)
            }
            engine.resetSequence()
            setSuggestions(emptyList())
        } else if (engine.hasActiveMultiTap) {
            engine.commitPendingMultiTap()
        }

        val ic = currentInputConnection ?: return
        val currentEditorInfo = currentInputEditorInfo
        val imeAction = currentEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        if (imeAction != EditorInfo.IME_ACTION_NONE && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(imeAction)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun handleShift() {
        engine.toggleShift()
        tvShift.text = when (engine.capState) {
            CapitalizationState.LOWERCASE -> "⇩"
            CapitalizationState.CAPITALIZED -> "⇧"
            CapitalizationState.UPPERCASE -> "⇪"
        }

        if (engine.currentMode == TypingMode.T9_PREDICTIVE) {
            val updated = engine.getPredictions()
            setSuggestions(updated)
        } else if (engine.hasActiveMultiTap) {
            engine.currentMultiTapChar?.let { newChar ->
                currentInputConnection?.deleteSurroundingText(1, 0)
                currentInputConnection?.commitText(newChar, 1)
                setSuggestions(listOf(newChar))
            }
        }
    }

    private fun toggleTypingMode() {
        if (engine.currentMode == TypingMode.T9_PREDICTIVE) {
            engine.currentMode = TypingMode.MULTI_TAP
            tvMode.text = "ABC"
        } else {
            engine.currentMode = TypingMode.T9_PREDICTIVE
            tvMode.text = "T9"
        }
        engine.resetSequence()
        engine.commitPendingMultiTap()
        setSuggestions(emptyList())
    }

    private fun toggleSymbolMode() {
        isSymbolMode = !isSymbolMode
        tvSymbol.text = if (isSymbolMode) "ABC" else "123"

        val visibility = if (isSymbolMode) View.GONE else View.VISIBLE
        listOf(let1, let2, let3, let4, let5, let6, let7, let8, let9, let0).forEach {
            it.visibility = visibility
        }
    }

    private fun setSuggestions(words: List<String>) {
        suggestionContainer.removeAllViews()

        if (words.isEmpty()) {
            tvSuggestionPlaceholder.visibility = View.VISIBLE
            suggestionContainer.addView(tvSuggestionPlaceholder)
            return
        }

        tvSuggestionPlaceholder.visibility = View.GONE

        for ((index, word) in words.withIndex()) {
            val tv = TextView(this).apply {
                text = word
                textSize = if (index == 0) 16f else 15f
                setTypeface(null, if (index == 0) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(if (index == 0) Color.parseColor("#0066FF") else Color.parseColor("#1A1A1A"))
                setBackgroundResource(R.drawable.bg_suggestion_chip)
                setPadding(30, 16, 30, 16)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(8, 0, 8, 0)
                }
                layoutParams = params

                setOnClickListener {
                    playFeedback()
                    currentInputConnection?.commitText("$word ", 1)
                    engine.resetSequence()
                    setSuggestions(emptyList())
                }
            }
            suggestionContainer.addView(tv)
        }
    }
}
