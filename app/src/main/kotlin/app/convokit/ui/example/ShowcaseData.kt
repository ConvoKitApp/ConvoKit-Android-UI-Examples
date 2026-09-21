package app.convokit.ui.example

import app.convokit.sdk.ContactMedia
import app.convokit.sdk.Conversation
import app.convokit.sdk.FileMedia
import app.convokit.sdk.ImageMedia
import app.convokit.sdk.InboxSummary
import app.convokit.sdk.LocationMedia
import app.convokit.sdk.Message
import app.convokit.sdk.Participant
import app.convokit.sdk.ReadPosition
import kotlinx.datetime.Instant

internal const val currentUserId = "maya"

internal val showcaseConversations = listOf(
    conversation("design", "Design review", "Weekly component review", 34, "alex" to "Alex Rivera"),
    conversation("support", "Customer support", "Tier 2 escalations", 27, "taylor" to "Taylor Kim", "jordan" to "Jordan Lee"),
    conversation("launch", "Launch room", "Release coordination", 18, "sam" to "Sam Patel", "alex" to "Alex Rivera"),
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

/**
 * The inbox state a `listInbox` page would carry for [showcaseConversations]: the default row
 * derives the preview line, the activity time and the unread badge from these values. `design`
 * is fully read but carries Maya's private "mark unread" marker (`isUnread` with a zero count),
 * so its row shows the numberless dot; `support` and `launch` keep the numeric and capped badges.
 */
internal val showcaseSummaries: Map<String, InboxSummary> = mapOf(
    "design" to InboxSummary(
        latestMessage = showcaseMessages.last(),
        unreadCount = 0,
        readPosition = ReadPosition(messageId = "m6", createdAt = showcaseInstant(32)),
        lastReadAt = showcaseInstant(33),
        activityAt = showcaseInstant(32),
        unreadMarkedAt = showcaseInstant(35),
        privateStateVersion = 1,
        isUnread = true,
    ),
    "support" to InboxSummary(
        latestMessage = message("s1", "taylor", "The fix is live", 27, conversationId = "support"),
        unreadCount = 2,
        readPosition = ReadPosition(messageId = "s0", createdAt = showcaseInstant(20)),
        lastReadAt = showcaseInstant(21),
        activityAt = showcaseInstant(27),
    ),
    "launch" to InboxSummary(
        latestMessage = message(
            "l1",
            "sam",
            null,
            18,
            listOf(FileMedia(url = "fixture://checklist", name = "checklist.pdf", size = 120_400)),
            conversationId = "launch",
        ),
        unreadCount = 1000,
        unreadCountCapped = true,
        activityAt = showcaseInstant(18),
    ),
)

internal fun conversation(
    id: String,
    title: String,
    description: String,
    minute: Int,
    vararg partners: Pair<String, String>,
): Conversation = Conversation(
    id = id,
    title = title,
    imageUrl = null,
    appId = "showcase",
    displayTitle = title,
    description = description,
    participants = listOf(participant(currentUserId, "Maya Chen", 32)) +
        partners.map { (partnerId, partnerName) -> participant(partnerId, partnerName, 33) },
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
    conversationId: String = "design",
): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    text = text,
    media = media,
    createdAt = showcaseInstant(minute),
    updatedAt = null,
)

internal fun showcaseInstant(minute: Int): Instant =
    Instant.parse("2026-09-04T09:${minute.toString().padStart(2, '0')}:00Z")
