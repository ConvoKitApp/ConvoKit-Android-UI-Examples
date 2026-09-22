package app.convokit.ui.example

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.convokit.sdk.Conversation
import app.convokit.sdk.ConvoKitClient
import app.convokit.sdk.ConvoKitException
import app.convokit.sdk.InboxSummary
import app.convokit.sdk.TokenProvider
import app.convokit.ui.client.DefaultConvoKitUiClient
import app.convokit.ui.components.ConvoKitConversation
import app.convokit.ui.components.ConvoKitConversationList
import app.convokit.ui.components.DefaultConversationItem
import app.convokit.ui.controller.ConvoKitConversationListController
import app.convokit.ui.theme.ConvoKitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Composable
internal fun LiveChatScreen(systemPadding: PaddingValues) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val demoApi = remember { DemoApi() }
    val client = remember {
        ConvoKitClient(
            clientId = BuildConfig.CONVOKIT_CLIENT_ID,
            tokenProvider = TokenProvider(demoApi::issueUserToken),
        )
    }
    var uiClient by remember(client) { mutableStateOf<DefaultConvoKitUiClient?>(null) }
    var userId by remember { mutableStateOf("convokit_open_maya") }
    var roomId by remember { mutableStateOf("") }
    var connectedUserId by remember { mutableStateOf<String?>(null) }
    var openRoomId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Enter a user ID and an existing chatroom ID.") }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(client) {
        onDispose {
            // Composition cancellation must not cancel logout/provider cleanup.
            scope.launch(NonCancellable) {
                try { client.disconnectUser() } finally { demoApi.close() }
            }
        }
    }

    ConvoKitTheme {
        val activeClient = uiClient
        val activeRoom = openRoomId
        if (activeClient == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(systemPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Live SDK example", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Room joining remains an application/backend concern; the reusable UI starts after the core SDK is connected. " +
                        "The joined room then appears in the SDK-backed inbox with its preview and unread badge, " +
                        "and its row menu can mark it unread again. Inside the room, long-press a message to reply to it, " +
                        "or one of your own to edit or delete it, and activate a quoted block to go to the message it " +
                        "points at.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Demo user ID") },
                    enabled = !busy,
                    singleLine = true,
                )
                OutlinedTextField(
                    value = roomId,
                    onValueChange = { roomId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chatroom ID") },
                    enabled = !busy,
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val nextUser = userId.trim()
                        val nextRoom = roomId.trim()
                        if (nextUser.isEmpty() || nextRoom.isEmpty()) {
                            status = "Enter both values."
                        } else {
                            busy = true
                            status = "Connecting…"
                            scope.launch {
                                try {
                                    demoApi.joinChatroom(nextRoom, nextUser)
                                    client.connectUser(nextUser)
                                    client.getConversation(nextRoom)
                                    // The adapter belongs to this login, not the reusable SDK object.
                                    uiClient = DefaultConvoKitUiClient(client)
                                    connectedUserId = nextUser
                                    openRoomId = null
                                    status = "Connected"
                                } catch (cause: Throwable) {
                                    uiClient = null
                                    connectedUserId = null
                                    openRoomId = null
                                    withContext(NonCancellable) { client.disconnectUser() }
                                    if (cause is CancellationException) throw cause
                                    status = when (cause) {
                                        is ConvoKitException -> cause.message ?: cause.code
                                        else -> cause.message ?: "Could not connect"
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                ) {
                    Text("Join room")
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                if (busy) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        } else if (activeRoom == null) {
            // The package ships no "mark unread" row affordance: the list hands its controller to the
            // host through `onController`, and the row menu below calls `markUnread` on it.
            var listController by remember(activeClient) { mutableStateOf<ConvoKitConversationListController?>(null) }
            Column(modifier = Modifier.fillMaxSize().padding(systemPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Inbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Text("Signed in as ${connectedUserId.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(
                        onClick = {
                            openRoomId = null
                            connectedUserId = null
                            uiClient = null
                            busy = true
                            status = "Signed out."
                            scope.launch {
                                try { client.disconnectUser() } finally { busy = false }
                            }
                        },
                        enabled = !busy,
                    ) {
                        Text("Log out")
                    }
                }
                // The released package pages the inbox itself: each row's preview, activity time and
                // unread badge or mark-unread dot come from the SDK, and live activity keeps them fresh.
                ConvoKitConversationList(
                    client = activeClient,
                    onConversationSelected = { openRoomId = it.id },
                    modifier = Modifier.weight(1f),
                    onController = { listController = it },
                    inboxItem = { conversation, summary, _, onClick ->
                        LiveInboxRow(
                            conversation = conversation,
                            summary = summary,
                            currentUserId = connectedUserId,
                            onClick = onClick,
                            onMarkUnread = { scope.launch { listController?.markUnread(conversation.id) } },
                        )
                    },
                )
            }
        } else {
            // The package's default rows and composer carry the 0.8.0 edit and delete surface: own
            // confirmed rows offer a long-press menu while the session's role allows it, deletes
            // confirm through the built-in dialog, and the composer saves with the snapshot revision.
            // They carry the 0.9.0 reply surface the same way, and the SDK-backed component owns
            // the rest of it: the batched quoted previews, the jumped window and its way back to
            // the latest all come from the controller, so the live example binds nothing new.
            ConvoKitConversation(
                client = activeClient,
                conversationId = activeRoom,
                modifier = Modifier.fillMaxSize().padding(systemPadding),
                onBack = { openRoomId = null },
                onAddAttachment = {
                    Toast.makeText(context, "Connect your app's file picker here", Toast.LENGTH_SHORT).show()
                },
                onAttachmentClick = { _, media ->
                    Toast.makeText(context, media.name ?: "Attachment", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/**
 * The package's default row (preview, activity time, numeric badge or the private-marker dot)
 * beside a per-row menu. "Mark unread" calls `ConvoKitConversationListController.markUnread`,
 * which marks the room for this user only and patches the row's summary so the dot shows at
 * once; opening the room later clears the marker through the room's own acknowledgements.
 */
@Composable
private fun LiveInboxRow(
    conversation: Conversation,
    summary: InboxSummary?,
    currentUserId: String?,
    onClick: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        DefaultConversationItem(
            conversation = conversation,
            onClick = onClick,
            modifier = Modifier.weight(1f),
            summary = summary,
            currentUserId = currentUserId,
        )
        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.semantics { contentDescription = "Conversation actions" },
            ) {
                Text("\u22EE", style = MaterialTheme.typography.titleMedium)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Mark unread") },
                    onClick = {
                        menuOpen = false
                        onMarkUnread()
                    },
                )
            }
        }
    }
}

private class DemoApi {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    fun close() {
        httpClient.dispatcher.cancelAll()
        httpClient.connectionPool.evictAll()
        httpClient.dispatcher.executorService.shutdown()
    }

    suspend fun issueUserToken(appUserId: String): String {
        val response = postJson(
            url = "${BuildConfig.DEMO_BACKEND_URL}/api/auth/token".toHttpUrl(),
            body = buildJsonObject { put("appUserId", appUserId) }.toString(),
        )
        return response["data"]?.jsonObject?.get("token")?.jsonPrimitive?.content
            ?: error("The demo token endpoint returned no token")
    }

    suspend fun joinChatroom(chatroomId: String, appUserId: String) {
        val url = BuildConfig.DEMO_BACKEND_URL.toHttpUrl().newBuilder()
            .addPathSegments("api/chatrooms")
            .addPathSegment(chatroomId)
            .addPathSegment("join")
            .build()
        postJson(url, buildJsonObject { put("appUserId", appUserId) }.toString())
    }

    private suspend fun postJson(url: okhttp3.HttpUrl, body: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("x-client-id", BuildConfig.CONVOKIT_CLIENT_ID)
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Demo backend failed (${response.code})")
            json.parseToJsonElement(responseBody).jsonObject
        }
    }
}
