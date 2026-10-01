package com.ironmind.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ironmind.app.R
import com.ironmind.app.domain.model.ChatAuthor
import com.ironmind.app.domain.model.ChatMessage
import com.ironmind.app.ui.components.AccentButton
import com.ironmind.app.ui.components.GlassCard
import com.ironmind.app.ui.theme.Black
import com.ironmind.app.ui.theme.Cyan
import com.ironmind.app.ui.theme.Gold
import com.ironmind.app.ui.theme.TextMuted

private val ErrorRed = Color(0xFFFF6B6B)

/**
 * The conversational coach: a free-form chat over the athlete's real training data, answered by
 * the on-device model. Everything else in the app asks the AI a fixed question; this is where the
 * athlete asks their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onDownloadModel: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val modelAvailable by viewModel.modelAvailable.collectAsStateWithLifecycle()

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { viewModel.refreshModelAvailability() }

    // Follow the stream: the last bubble grows token by token, so react to its text too, not just
    // to the message count.
    LaunchedEffect(messages.size, messages.lastOrNull()?.text) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun send(text: String) {
        viewModel.send(text)
        draft = ""
    }

    Scaffold(
        containerColor = Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.chat_title), color = Gold, fontWeight = FontWeight.Bold) },
                actions = {
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = viewModel::clear) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.chat_clear),
                                tint = Cyan,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Black),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (!modelAvailable) {
                ModelMissingCard(
                    onDownloadModel = onDownloadModel,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("chatTranscript"),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (messages.isEmpty()) {
                    item { EmptyState(onSuggestionClick = { suggestion -> send(suggestion) }) }
                }
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message = message, onRetry = viewModel::retryLast)
                }
            }

            Composer(
                draft = draft,
                onDraftChange = { draft = it },
                isGenerating = isGenerating,
                enabled = modelAvailable,
                onSend = { send(draft) },
                onStop = viewModel::stop,
            )
        }
    }
}

@Composable
private fun ModelMissingCard(onDownloadModel: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier) {
        Text(stringResource(R.string.ai_model_missing), color = TextMuted)
        Spacer(Modifier.height(12.dp))
        AccentButton(text = stringResource(R.string.ai_download_model), onClick = onDownloadModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(onSuggestionClick: (String) -> Unit) {
    val suggestions = listOf(
        stringResource(R.string.chat_suggestion_week),
        stringResource(R.string.chat_suggestion_today),
        stringResource(R.string.chat_suggestion_plateau),
        stringResource(R.string.chat_suggestion_technique),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Gold,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.chat_empty_body), color = TextMuted)
        Spacer(Modifier.height(16.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { suggestion ->
                SuggestionChip(text = suggestion, onClick = { onSuggestionClick(suggestion) })
            }
        }
    }
}

@Composable
private fun SuggestionChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Cyan,
        modifier = Modifier
            .clip(shape)
            .background(Cyan.copy(alpha = 0.10f))
            .border(1.dp, Cyan.copy(alpha = 0.40f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun MessageBubble(message: ChatMessage, onRetry: () -> Unit) {
    val fromAthlete = message.author == ChatAuthor.USER
    val accent = when {
        message.isError -> ErrorRed
        fromAthlete -> Gold
        else -> Cyan
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (fromAthlete) 18.dp else 4.dp,
        bottomEnd = if (fromAthlete) 4.dp else 18.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromAthlete) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(shape)
                .background(accent.copy(alpha = 0.10f))
                .border(1.dp, accent.copy(alpha = 0.35f), shape)
                .padding(14.dp),
        ) {
            Text(
                text = stringResource(if (fromAthlete) R.string.chat_author_you else R.string.chat_author_coach),
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))

            if (message.text.isBlank() && message.isStreaming) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.chat_thinking), color = TextMuted)
                }
            } else {
                Text(
                    text = message.text,
                    color = if (message.isError) ErrorRed else Color.White,
                )
            }

            if (message.isError) {
                TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.action_retry), color = Cyan)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    isGenerating: Boolean,
    enabled: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = { Text(stringResource(R.string.chat_input_hint), color = TextMuted) },
            enabled = enabled && !isGenerating,
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .testTag("chatInput"),
        )

        if (isGenerating) {
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.chat_stop), tint = ErrorRed)
            }
        } else {
            val canSend = enabled && draft.isNotBlank()
            IconButton(onClick = onSend, enabled = canSend) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    tint = if (canSend) Gold else TextMuted,
                )
            }
        }
    }
}
