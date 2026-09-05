package app.convokit.ui.example

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.convokit.sdk.ConvoKitClient
import app.convokit.sdk.ConvoKitException
import app.convokit.sdk.TokenProvider
import app.convokit.ui.client.DefaultConvoKitUiClient
import app.convokit.ui.components.ConvoKitConversation
import app.convokit.ui.theme.ConvoKitTheme
import kotlinx.coroutines.Dispatchers
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
    val uiClient = remember(client) { DefaultConvoKitUiClient(client) }
    var userId by remember { mutableStateOf("convokit_open_maya") }
    var roomId by remember { mutableStateOf("") }
    var connectedRoomId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("Enter a user ID and an existing chatroom ID.") }
    var busy by remember { mutableStateOf(false) }

    ConvoKitTheme {
        val activeRoom = connectedRoomId
        if (activeRoom == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(systemPadding).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Live SDK example", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(
                    "Room joining remains an application/backend concern; the reusable UI starts after the core SDK is connected.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = userId,
                    onValueChange = { userId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Demo user ID") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = roomId,
                    onValueChange = { roomId = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Chatroom ID") },
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
                                runCatching {
                                    demoApi.joinChatroom(nextRoom, nextUser)
                                    client.connectUser(nextUser)
                                    client.getConversation(nextRoom)
                                }.onSuccess {
                                    connectedRoomId = nextRoom
                                    status = "Connected"
                                }.onFailure { cause ->
                                    status = when (cause) {
                                        is ConvoKitException -> cause.message ?: cause.code
                                        else -> cause.message ?: "Could not connect"
                                    }
                                }
                                busy = false
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
        } else {
            ConvoKitConversation(
                client = uiClient,
                conversationId = activeRoom,
                modifier = Modifier.fillMaxSize().padding(systemPadding),
                onBack = {
                    connectedRoomId = null
                    scope.launch { client.disconnectUser() }
                },
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

private class DemoApi {
    private val httpClient = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

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
