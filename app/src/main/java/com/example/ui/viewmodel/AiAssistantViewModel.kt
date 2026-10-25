package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Task
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

private const val TAG = "AiAssistantVM"

enum class GeminiModelTier(
    val modelName: String,
    val displayName: String,
    val subtitle: String
) {
    GENERAL_FLASH(
        modelName = "gemini-3.8-flash",
        displayName = "Gemini 3.8 Flash",
        subtitle = "General tasks & Google Search Grounding"
    ),
    COMPLEX_ARGON(
        modelName = "gemini-4-argon",
        displayName = "Gemini 4 Argon",
        subtitle = "Complex planning & deep reasoning"
    ),
    FAST_LITE(
        modelName = "gemini-3.5-flash-lite",
        displayName = "Gemini 3.5 Flash-Lite",
        subtitle = "Fast triage & quick summaries"
    )
}

data class GroundingSourceUi(
    val title: String,
    val uri: String,
    val domain: String
)

data class ChatMessageUi(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val modelBadge: String? = null,
    val searchQueries: List<String> = emptyList(),
    val sources: List<GroundingSourceUi> = emptyList(),
    val isStreaming: Boolean = false
)

sealed interface ChatUiState {
    data object Idle : ChatUiState
    data object Loading : ChatUiState
    data class Error(val message: String) : ChatUiState
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val prompt: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class AiAssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val systemPromptText = """
        You are the SnapTask AI Assistant — a specialized productivity coach, screenshot analyzer, and research assistant built into the SnapTask Android app.
        Your role:
        1. Help users break down goals, notes, and screenshots into clear, actionable to-do tasks and schedules.
        2. When Google Search Grounding is active, provide up-to-date, accurate facts (such as business hours, event dates, official requirements, or travel details) to help users complete their tasks.
        3. Keep answers well-structured with bullet points and clear action steps so users can easily save them as tasks.
    """.trimIndent()

    // Lazy GenerativeModel instances (never evaluated eagerly at construction time)
    private val flashSearchModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            tools = listOf(Tool.googleSearch()),
            systemInstruction = content { text(systemPromptText) }
        )
    }

    private val flashStandardModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content { text(systemPromptText) }
        )
    }

    private val argonComplexModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            systemInstruction = content {
                text("$systemPromptText\nProvide thorough, multi-step analytical reasoning and comprehensive plans.")
            }
        )
    }

    private val flashLiteFastModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            systemInstruction = content {
                text("$systemPromptText\nRespond ultra-concisely with immediate action items.")
            }
        )
    }

    // Nano Banana 2.1 model for image creation and editing
    private val imageModel: GenerativeModel by lazy {
        val config = generationConfig {
            responseModalities = listOf(ResponseModality.IMAGE)
        }
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = config
        )
    }

    // Selected model tier & Google Search grounding toggle
    private val _selectedTier = MutableStateFlow(GeminiModelTier.GENERAL_FLASH)
    val selectedTier: StateFlow<GeminiModelTier> = _selectedTier.asStateFlow()

    private val _isSearchGroundingEnabled = MutableStateFlow(true)
    val isSearchGroundingEnabled: StateFlow<Boolean> = _isSearchGroundingEnabled.asStateFlow()

    // Multi-turn chat state
    private val conversationHistory = mutableListOf<Content>()

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(
        listOf(
            ChatMessageUi(
                isUser = false,
                text = "Hi! I'm your SnapTask AI Assistant. Ask me to plan your day, break a complex project into tasks, or research real-time details with Google Search.",
                modelBadge = "Gemini 3.8 Flash"
            )
        )
    )
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _chatUiState = MutableStateFlow<ChatUiState>(ChatUiState.Idle)
    val chatUiState: StateFlow<ChatUiState> = _chatUiState.asStateFlow()

    // Image Studio state
    private val _imageUiState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageUiState: StateFlow<ImageGenUiState> = _imageUiState.asStateFlow()

    private val _selectedEditBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedEditBitmap: StateFlow<Bitmap?> = _selectedEditBitmap.asStateFlow()

    fun selectModelTier(tier: GeminiModelTier) {
        _selectedTier.value = tier
    }

    fun setSearchGroundingEnabled(enabled: Boolean) {
        _isSearchGroundingEnabled.value = enabled
    }

    fun clearChat() {
        conversationHistory.clear()
        _messages.value = listOf(
            ChatMessageUi(
                isUser = false,
                text = "Conversation cleared. How can I help organize or research your tasks today?",
                modelBadge = _selectedTier.value.displayName
            )
        )
        _chatUiState.value = ChatUiState.Idle
    }

    /**
     * Sends a message in the multi-turn chat, maintaining conversation history and optionally
     * grounding with Google Search.
     */
    fun sendChatMessage(
        userText: String,
        activeTasksContext: List<Task> = emptyList(),
        includeTasksContext: Boolean = false
    ) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty()) return

        val tier = _selectedTier.value
        val useSearch = _isSearchGroundingEnabled.value && tier == GeminiModelTier.GENERAL_FLASH

        // Add user message to UI thread
        val userMsg = ChatMessageUi(isUser = true, text = trimmed)
        _messages.value = _messages.value + userMsg
        _chatUiState.value = ChatUiState.Loading

        val promptToSend = if (includeTasksContext && activeTasksContext.isNotEmpty()) {
            val tasksSummary = activeTasksContext.take(15).joinToString("\n") { task ->
                "- ${task.title}" + (if (task.notes.isNotBlank()) " (${task.notes})" else "")
            }
            "User's current active tasks in SnapTask:\n$tasksSummary\n\nUser request: $trimmed"
        } else {
            trimmed
        }

        viewModelScope.launch {
            val streamingMessageId = UUID.randomUUID().toString()
            val badgeLabel = if (useSearch) "${tier.displayName} • Google Search" else tier.displayName

            // Insert placeholder streaming message
            _messages.value = _messages.value + ChatMessageUi(
                id = streamingMessageId,
                isUser = false,
                text = "",
                modelBadge = badgeLabel,
                isStreaming = true
            )

            try {
                val activeModel = when (tier) {
                    GeminiModelTier.GENERAL_FLASH -> if (useSearch) flashSearchModel else flashStandardModel
                    GeminiModelTier.COMPLEX_ARGON -> argonComplexModel
                    GeminiModelTier.FAST_LITE -> flashLiteFastModel
                }

                val chat = activeModel.startChat(history = conversationHistory.toList())
                val accumulatedText = StringBuilder()
                val discoveredQueries = mutableListOf<String>()
                val discoveredSources = mutableListOf<GroundingSourceUi>()

                withTimeout(60_000L) {
                    chat.sendMessageStream(promptToSend).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        accumulatedText.append(chunkText)

                        // Extract Google Search grounding metadata if present on candidate
                        val metadata = chunk.candidates.firstOrNull()?.groundingMetadata
                        if (metadata != null) {
                            metadata.webSearchQueries.forEach { q ->
                                if (q.isNotBlank() && !discoveredQueries.contains(q)) {
                                    discoveredQueries.add(q)
                                }
                            }
                            metadata.groundingChunks.forEach { groundingChunk ->
                                val web = groundingChunk.web
                                val webUri = web?.uri
                                if (web != null && !webUri.isNullOrBlank()) {
                                    val src = GroundingSourceUi(
                                        title = web.title?.ifBlank { web.domain ?: "Web Source" } ?: "Web Source",
                                        uri = webUri,
                                        domain = web.domain ?: ""
                                    )
                                    if (discoveredSources.none { it.uri == src.uri }) {
                                        discoveredSources.add(src)
                                    }
                                }
                            }
                        }

                        // Update streaming bubble in place
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == streamingMessageId) {
                                msg.copy(
                                    text = accumulatedText.toString(),
                                    searchQueries = discoveredQueries.toList(),
                                    sources = discoveredSources.toList(),
                                    isStreaming = true
                                )
                            } else msg
                        }
                    }
                }

                val finalText = accumulatedText.toString().trim()
                if (finalText.isNotEmpty()) {
                    // Record turn in conversation history
                    conversationHistory.add(content("user") { text(promptToSend) })
                    conversationHistory.add(content("model") { text(finalText) })

                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == streamingMessageId) {
                            msg.copy(
                                text = finalText,
                                searchQueries = discoveredQueries.toList(),
                                sources = discoveredSources.toList(),
                                isStreaming = false
                            )
                        } else msg
                    }
                    _chatUiState.value = ChatUiState.Idle
                } else {
                    _messages.value = _messages.value.filterNot { it.id == streamingMessageId }
                    _chatUiState.value = ChatUiState.Error("Model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat generation timed out", e)
                _messages.value = _messages.value.filterNot { it.id == streamingMessageId }
                _chatUiState.value = ChatUiState.Error("Request timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                _messages.value = _messages.value.filterNot { it.id == streamingMessageId }
                _chatUiState.value = ChatUiState.Error(e.localizedMessage ?: "Failed to generate response.")
            }
        }
    }

    /**
     * Quick helper to research a specific task using gemini-3.8-flash with Google Search Grounding.
     */
    fun researchTaskWithGoogleSearch(task: Task) {
        _selectedTier.value = GeminiModelTier.GENERAL_FLASH
        _isSearchGroundingEnabled.value = true
        val query = buildString {
            append("Research up-to-date information and actionable tips to complete this task: \"${task.title}\".")
            if (task.notes.isNotBlank()) {
                append(" Additional context: ${task.notes}")
            }
        }
        sendChatMessage(query)
    }

    // --- Image Generation & Editing (gemini-nano-banana-2.1) ---

    fun setEditSourceBitmap(bitmap: Bitmap?) {
        _selectedEditBitmap.value = bitmap
    }

    fun loadEditBitmapFromUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                decodeSafeBitmap(uri)
            }
            if (bmp != null) {
                _selectedEditBitmap.value = bmp
            } else {
                _imageUiState.value = ImageGenUiState.Error("Could not load the selected image.")
            }
        }
    }

    /**
     * Generates a new image from a text prompt or edits the currently selected bitmap
     * using gemini-nano-banana-2.1.
     */
    fun generateOrEditImage(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) return

        val inputBitmap = _selectedEditBitmap.value

        viewModelScope.launch {
            _imageUiState.value = ImageGenUiState.Loading
            try {
                val resultBitmap = withTimeout(60_000L) {
                    if (inputBitmap != null) {
                        // Image editing (Image + Text prompt)
                        val response = imageModel.generateContent(
                            content {
                                image(inputBitmap)
                                text(cleanPrompt)
                            }
                        )
                        response.candidates.firstOrNull()
                            ?.content?.parts?.filterIsInstance<ImagePart>()
                            ?.firstOrNull()?.image
                    } else {
                        // Text-to-image generation
                        val response = imageModel.generateContent(cleanPrompt)
                        response.candidates.firstOrNull()
                            ?.content?.parts?.filterIsInstance<ImagePart>()
                            ?.firstOrNull()?.image
                    }
                }

                if (resultBitmap != null) {
                    _imageUiState.value = ImageGenUiState.Success(resultBitmap, cleanPrompt)
                } else {
                    _imageUiState.value = ImageGenUiState.Error("No image was returned by gemini-nano-banana-2.1.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _imageUiState.value = ImageGenUiState.Error("Image generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation/editing failed", e)
                _imageUiState.value = ImageGenUiState.Error(e.localizedMessage ?: "Image generation failed.")
            }
        }
    }

    /**
     * Use the newly generated bitmap as the base image for further iterative edits.
     */
    fun useGeneratedImageForFurtherEdits() {
        val currentState = _imageUiState.value
        if (currentState is ImageGenUiState.Success) {
            _selectedEditBitmap.value = currentState.bitmap
        }
    }

    /**
     * Saves a generated/edited Bitmap to cache and returns its Uri so it can be scanned for tasks or shared.
     */
    suspend fun saveBitmapToCacheUri(bitmap: Bitmap): Uri? = withContext(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val dir = File(context.cacheDir, "generated_images").apply { mkdirs() }
            val file = File(dir, "snaptask_ai_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save generated bitmap", e)
            null
        }
    }

    private fun decodeSafeBitmap(uri: Uri): Bitmap? {
        return try {
            val cr = getApplication<Application>().contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            cr.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > 1024 || bounds.outHeight / sampleSize > 1024) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            cr.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from URI", e)
            null
        }
    }
}
