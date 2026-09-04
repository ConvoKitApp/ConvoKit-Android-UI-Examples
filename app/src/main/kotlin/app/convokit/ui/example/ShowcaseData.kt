package app.convokit.ui.example

import app.convokit.sdk.ContactMedia
import app.convokit.sdk.Conversation
import app.convokit.sdk.FileMedia
import app.convokit.sdk.ImageMedia
import app.convokit.sdk.LocationMedia
import app.convokit.sdk.Message
import app.convokit.sdk.Participant
import kotlinx.datetime.Instant

internal const val currentUserId = "maya"

internal val showcaseConversations = listOf(
    conversation("design", "Design review", "Alex shared an image", 34, "alex", "Alex Rivera"),
    conversation("support", "Customer support", "Taylor: The fix is live", 27, "taylor", "Taylor Kim"),
    conversation("launch", "Launch room", "Sam: Checklist attached", 18, "sam", "Sam Patel"),
)

internal val showcaseMessages = listOf(
    message("m1", "alex", "The updated empty state is ready for review.", 22),
    message(
        "m2",
        currentUserId,
        "Looks good. I tightened the copy and spacing.",
        24,
        listOf(ContactMedia(name = "Jordan Lee", email = "jordan@example.com")),
    ),
    message("m3", "alex", "Perfect—adding it to the release notes.", 26),
    message(
        "m4",
        currentUserId,
        "Here is the signed-off handoff.",
        28,
        listOf(LocationMedia(latitude = 48.8566, longitude = 2.3522, name = "Paris office")),
    ),
    message(
        "m5",
        "alex",
        "Here is the final component preview.",
        30,
        listOf(ImageMedia(url = "fixture://convokit", name = "component-preview.png", size = 48_200)),
    ),
    message(
        "m6",
        currentUserId,
        "The signed-off handoff is attached.",
        32,
        listOf(FileMedia(url = "fixture://handoff", name = "handoff.pdf", size = 834_220)),
    ),
)

internal fun conversation(
    id: String,
    title: String,
    description: String,
    minute: Int,
    partnerId: String,
    partnerName: String,
): Conversation = Conversation(
    id = id,
    title = title,
    imageUrl = null,
    appId = "showcase",
    displayTitle = title,
    description = description,
    participants = listOf(
        participant(currentUserId, "Maya Chen", 32),
        participant(partnerId, partnerName, 33),
    ),
    createdAt = showcaseInstant(0),
    updatedAt = showcaseInstant(minute),
)

internal fun participant(id: String, name: String, readMinute: Int): Participant = Participant(
    id = "participant-$id",
    appUserId = id,
    name = name,
    imageUrl = null,
    role = "member",
    lastReadAt = showcaseInstant(readMinute),
)

internal fun message(
    id: String,
    senderId: String,
    text: String?,
    minute: Int,
    media: List<app.convokit.sdk.MessageMedia> = emptyList(),
): Message = Message(
    id = id,
    conversationId = "design",
    senderId = senderId,
    text = text,
    media = media,
    createdAt = showcaseInstant(minute),
    updatedAt = null,
)

internal fun showcaseInstant(minute: Int): Instant =
    Instant.parse("2026-09-04T09:${minute.toString().padStart(2, '0')}:00Z")
