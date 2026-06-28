package com.example

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.DuoResult
import com.example.api.DuoSystemInstruction
import com.example.api.GenerateContentRequest
import com.example.api.GenerationConfig
import com.example.api.Content
import com.example.api.Part
import com.example.api.RetrofitClient
import com.example.data.AppDatabase
import com.example.data.CalculationEntity
import com.example.data.CalculationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

sealed interface UiState {
    object Idle : UiState
    object Loading : UiState
    data class Success(val result: DuoResult) : UiState
    data class Error(val message: String) : UiState
}

class DuoViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener, RecognitionListener {

    private val database = AppDatabase.getDatabase(application)
    private val repository = CalculationRepository(database.calculationDao())

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _history = MutableStateFlow<List<CalculationEntity>>(emptyList())
    val history: StateFlow<List<CalculationEntity>> = _history.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _partialSpeechText = MutableStateFlow("")
    val partialSpeechText: StateFlow<String> = _partialSpeechText.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private val _ttsEnabled = MutableStateFlow(true)
    val ttsEnabled: StateFlow<Boolean> = _ttsEnabled.asStateFlow()

    private val _isSpeechSupported = MutableStateFlow(true)
    val isSpeechSupported: StateFlow<Boolean> = _isSpeechSupported.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private val recognizerIntent: Intent

    init {
        // Initialize TTS
        textToSpeech = TextToSpeech(application, this)

        // Check Speech Recognition support
        val speechAvailable = SpeechRecognizer.isRecognitionAvailable(application)
        _isSpeechSupported.value = speechAvailable

        if (speechAvailable) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(application).apply {
                setRecognitionListener(this@DuoViewModel)
            }
        }

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD") // Default to Bengali
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "bn-BD")
            putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, arrayOf("bn-BD", "bn-IN", "en-US"))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        // Collect history from Room database
        viewModelScope.launch {
            repository.allHistory.collectLatest { list ->
                _history.value = list
            }
        }
    }

    // --- TTS CALLBACKS ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val bnLocale = Locale("bn", "BD")
            val result = textToSpeech?.setLanguage(bnLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try Indian Bengali
                val bnInLocale = Locale("bn", "IN")
                val resultIn = textToSpeech?.setLanguage(bnInLocale)
                if (resultIn == TextToSpeech.LANG_MISSING_DATA || resultIn == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("DuoViewModel", "Bengali is not supported for TTS. Falling back to default locale.")
                    textToSpeech?.setLanguage(Locale.getDefault())
                }
            }
            _isTtsReady.value = true
        } else {
            Log.e("DuoViewModel", "TTS Initialization failed.")
        }
    }

    fun speak(text: String) {
        if (_ttsEnabled.value && _isTtsReady.value) {
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "DuoSpeech")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun toggleTts(enabled: Boolean) {
        _ttsEnabled.value = enabled
        if (!enabled) {
            stopSpeaking()
        }
    }

    // --- SPEECH RECOGNITION ACTIONS ---
    fun startListening() {
        stopSpeaking()
        if (_isSpeechSupported.value) {
            try {
                speechRecognizer?.startListening(recognizerIntent)
                _isListening.value = true
                _partialSpeechText.value = "শুনছি..."
            } catch (e: Exception) {
                Log.e("DuoViewModel", "Error starting speech recognizer", e)
                _isListening.value = false
                _partialSpeechText.value = ""
            }
        } else {
            _partialSpeechText.value = "ভয়েস ইনপুট এই ডিভাইসে উপলব্ধ নেই"
        }
    }

    fun stopListening() {
        if (_isSpeechSupported.value) {
            speechRecognizer?.stopListening()
        }
        _isListening.value = false
    }

    fun cancelListening() {
        if (_isSpeechSupported.value) {
            speechRecognizer?.cancel()
        }
        _isListening.value = false
        _partialSpeechText.value = ""
    }

    // --- RECOGNITION LISTENER CALLBACKS ---
    override fun onReadyForSpeech(params: Bundle?) {
        _partialSpeechText.value = "বলুন, আমি শুনছি..."
    }

    override fun onBeginningOfSpeech() {
        _partialSpeechText.value = "শুনছি..."
    }

    override fun onRmsChanged(rmsdB: Float) {
        // Can be used for visual waveform effects if desired
    }

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val message = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "অডিও রেকর্ডিং ত্রুটি"
            SpeechRecognizer.ERROR_CLIENT -> "ক্লায়েন্ট সাইড ত্রুটি"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "রেকর্ডিং পারমিশন নেই"
            SpeechRecognizer.ERROR_NETWORK -> "নেটওয়ার্ক সমস্যা"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "নেটওয়ার্ক টাইমআউট"
            SpeechRecognizer.ERROR_NO_MATCH -> "বুঝতে পারিনি, আবার বলুন"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "আমি ব্যস্ত আছি, একটু পর চেষ্টা করুন"
            SpeechRecognizer.ERROR_SERVER -> "সার্ভার ত্রুটি"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো শব্দ শোনা যায়নি"
            else -> "ভয়েস ইনপুট ত্রুটি"
        }
        _partialSpeechText.value = message
    }

    override fun onResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            val spokenText = matches[0]
            _partialSpeechText.value = spokenText
            processQuery(spokenText)
        } else {
            _partialSpeechText.value = "বুঝতে পারিনি, আবার বলুন"
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            _partialSpeechText.value = matches[0]
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    // --- BUSINESS LOGIC & GEMINI API ---
    fun processQuery(queryText: String) {
        if (queryText.trim().isEmpty()) return

        stopSpeaking()
        _uiState.value = UiState.Loading

        viewModelScope.launch {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                _uiState.value = UiState.Error("Gemini API Key missing. Please configure it in AI Studio Secrets panel.")
                return@launch
            }

            try {
                val request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = queryText)))),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.1f
                    ),
                    systemInstruction = Content(parts = listOf(Part(text = DuoSystemInstruction.INSTRUCTION)))
                )

                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiKey, request)
                }

                val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonText != null) {
                    val moshi = RetrofitClient.moshiInstance
                    val adapter = moshi.adapter(DuoResult::class.java)
                    val duoResult = adapter.fromJson(jsonText)

                    if (duoResult != null) {
                        _uiState.value = UiState.Success(duoResult)

                        // Save to database
                        repository.insert(
                            CalculationEntity(
                                queryText = queryText,
                                spokenResponse = duoResult.spokenResponse,
                                calculationSteps = duoResult.calculationSteps
                            )
                        )

                        // Voice out the spoken response
                        speak(duoResult.spokenResponse)
                    } else {
                        _uiState.value = UiState.Error("ফলাফল বুঝতে সমস্যা হয়েছে।")
                    }
                } else {
                    _uiState.value = UiState.Error("সার্ভার থেকে কোনো উত্তর পাওয়া যায়নি।")
                }
            } catch (e: Exception) {
                Log.e("DuoViewModel", "API Call failed", e)
                _uiState.value = UiState.Error("হিসাব করতে সমস্যা হয়েছে: ${e.localizedMessage ?: "সংযোগ ত্রুটি"}")
            }
        }
    }

    // --- REPOSITORY ACTIONS ---
    fun toggleFavorite(entity: CalculationEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(entity.id, entity.isFavorite)
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // --- CLEANUP ---
    override fun onCleared() {
        super.onCleared()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        speechRecognizer?.destroy()
    }
}
