package com.imsharman.liber.ui.reader

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.imsharman.liber.data.parser.BookChapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

data class TtsUiState(
    val isAvailable: Boolean = false,
    val isSpeaking: Boolean = false,
    val chapterIndex: Int = 0,
    val blockIndex: Int = 0,
    val stopAtEndOfChapter: Boolean = false,
    val sleepSecondsRemaining: Int? = null
)

/**
 * Built-in offline Text-to-Speech (TTS) read-aloud engine powered by Android's `TextToSpeech` service.
 * Highlights paragraphs in real time, advances chapters automatically, and includes a Sleep Timer.
 */
class TtsController(
    context: Context,
    private val scope: CoroutineScope,
    private val onActivePositionChanged: (chapterIndex: Int, blockIndex: Int) -> Unit
) {
    private var tts: TextToSpeech? = null
    private var chapters: List<BookChapter> = emptyList()
    private var speechRate: Float = 1.0f
    private var pitch: Float = 1.0f
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(TtsUiState())
    val state: StateFlow<TtsUiState> = _state.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                runCatching { tts?.language = Locale.getDefault() }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(utteranceId: String?) {
                        scope.launch(Dispatchers.Main) {
                            if (_state.value.isSpeaking) {
                                advanceToNextSpeakableBlock()
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        scope.launch(Dispatchers.Main) {
                            _state.value = _state.value.copy(isSpeaking = false)
                        }
                    }
                })
            }
            _state.value = _state.value.copy(isAvailable = ok)
        }
    }

    fun bindChapters(bookChapters: List<BookChapter>) {
        chapters = bookChapters
    }

    fun updateVoiceParameters(rate: Float, voicePitch: Float) {
        speechRate = rate.coerceIn(0.5f, 2.5f)
        pitch = voicePitch.coerceIn(0.6f, 1.6f)
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
        if (_state.value.isSpeaking) {
            speakCurrentBlock()
        }
    }

    fun startSpeakingFrom(chapterIdx: Int, blockIdx: Int) {
        if (!_state.value.isAvailable || chapters.isEmpty()) return
        val safeChap = chapterIdx.coerceIn(0, chapters.lastIndex)
        val safeBlock = blockIdx.coerceIn(0, (chapters[safeChap].blocks.size - 1).coerceAtLeast(0))
        _state.value = _state.value.copy(
            isSpeaking = true,
            chapterIndex = safeChap,
            blockIndex = safeBlock
        )
        onActivePositionChanged(safeChap, safeBlock)
        speakCurrentBlock()
    }

    fun togglePlayPause(fallbackChapter: Int, fallbackBlock: Int) {
        if (_state.value.isSpeaking) {
            pause()
        } else {
            val c = if (_state.value.chapterIndex in chapters.indices) _state.value.chapterIndex else fallbackChapter
            val b = _state.value.blockIndex.takeIf { it > 0 } ?: fallbackBlock
            startSpeakingFrom(c, b)
        }
    }

    fun pause() {
        tts?.stop()
        _state.value = _state.value.copy(isSpeaking = false)
    }

    fun skipNext() {
        if (chapters.isEmpty()) return
        advanceToNextSpeakableBlock()
    }

    fun skipPrevious() {
        if (chapters.isEmpty()) return
        val c = _state.value.chapterIndex
        val b = _state.value.blockIndex
        if (b > 0) {
            startSpeakingFrom(c, b - 1)
        } else if (c > 0) {
            val prevChap = chapters[c - 1]
            startSpeakingFrom(c - 1, (prevChap.blocks.size - 1).coerceAtLeast(0))
        }
    }

    fun setSleepTimerMinutes(minutes: Int?, stopAtChapterEnd: Boolean = false) {
        sleepJob?.cancel()
        if (stopAtChapterEnd) {
            _state.value = _state.value.copy(
                stopAtEndOfChapter = true,
                sleepSecondsRemaining = null
            )
            return
        }
        if (minutes == null || minutes <= 0) {
            _state.value = _state.value.copy(
                stopAtEndOfChapter = false,
                sleepSecondsRemaining = null
            )
            return
        }
        val totalSecs = minutes * 60
        _state.value = _state.value.copy(
            stopAtEndOfChapter = false,
            sleepSecondsRemaining = totalSecs
        )
        sleepJob = scope.launch {
            var rem = totalSecs
            while (rem > 0) {
                delay(1000L)
                rem--
                _state.value = _state.value.copy(sleepSecondsRemaining = rem)
            }
            pause()
            _state.value = _state.value.copy(sleepSecondsRemaining = null)
        }
    }

    private fun advanceToNextSpeakableBlock() {
        val current = _state.value
        val chap = chapters.getOrNull(current.chapterIndex) ?: run {
            pause()
            return
        }
        if (current.blockIndex + 1 < chap.blocks.size) {
            val nextBlock = current.blockIndex + 1
            _state.value = current.copy(blockIndex = nextBlock)
            onActivePositionChanged(current.chapterIndex, nextBlock)
            speakCurrentBlock()
        } else if (!current.stopAtEndOfChapter && current.chapterIndex + 1 < chapters.size) {
            val nextChap = current.chapterIndex + 1
            _state.value = current.copy(chapterIndex = nextChap, blockIndex = 0)
            onActivePositionChanged(nextChap, 0)
            speakCurrentBlock()
        } else {
            pause()
            _state.value = _state.value.copy(stopAtEndOfChapter = false)
        }
    }

    private fun speakCurrentBlock() {
        val st = _state.value
        val block = chapters.getOrNull(st.chapterIndex)?.blocks?.getOrNull(st.blockIndex) ?: return
        val text = block.plainText().trim()
        if (text.isEmpty()) {
            advanceToNextSpeakableBlock()
            return
        }
        tts?.setSpeechRate(speechRate)
        tts?.setPitch(pitch)
        val params = Bundle()
        tts?.speak(
            text.take(3900),
            TextToSpeech.QUEUE_FLUSH,
            params,
            "utt_${st.chapterIndex}_${st.blockIndex}"
        )
    }

    fun shutdown() {
        sleepJob?.cancel()
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
    }
}
