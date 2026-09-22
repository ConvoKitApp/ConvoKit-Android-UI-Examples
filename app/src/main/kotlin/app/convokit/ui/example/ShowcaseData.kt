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

/** How many rows one loaded window holds; the rest of [showcaseHistory] stays out of view. */
internal const val showcaseWindowSize = 6

/**
 * Room history older than the loaded window. A store holds one contiguous window anchored at the
 * newest message, so these rows are not rendered until something asks for them: `m3` quotes `a2`,
 * which is what makes activating that quote a real jump out of the window rather than a scroll.
 */
internal val showcaseArchive = listOf(
    message("a1", "alex", "Kicking off this week's component review.", 4),
    message("a2", currentUserId, "Here is the audit of the empty states we still owe.", 6),
    message("a3", "alex", "Thanks, I will take the first two.", 8),
    message("a4", currentUserId, "Splitting the rest with Jordan.", 10),
    message("a5", "alex", "Jordan is out until Thursday.", 12),
    message("a6", currentUserId, "Then we ship the first two and revisit.", 14),
)

/**
 * The loaded window. Every row carries the core's `revision` (0 on creation, +1 per edit);
 * `m2` is Maya's own edited row (`revision` 1), so the default row and the compact custom row
 * both show `Edited`, and its attachment keeps `Save` enabled while the caption is cleared.
 *
 * Four rows carry a `replyToMessageId`, between them covering every render branch of the quoted
 * block and both sources a resolved preview comes from: `m2` quotes `m1`, which is loaded, so its
 * preview is derived from the window and costs nothing; `m3` quotes `a2`, which is not loaded, so
 * activating its quote is a real jump out of the window; `m5` quotes `a6`, which
 * [showcaseUnresolvedParentIds] withholds, so it renders the bare reference with no quoted text;
 * and `m6` quotes `m0`, which was deleted, so it shows `Original message unavailable` and keeps
 * the reference. An edit never moves a reference: `m2` is still a reply to `m1` after it is saved.
 */
internal val showcaseMessages = listOf(
    message("m1", "alex", "The updated empty state is ready for review.", 22),
    message(
        "m2",
        currentUserId,
        "Looks good. I tightened the copy and spacing.",
        24,
        listOf(ContactMedia(name = "Jordan Lee", email = "jordan@example.com")),
        revision = 1,
        replyToMessageId = "m1",
    ),
    message("m3", "alex", "Perfect—adding it to the release notes.", 26, replyToMessageId = "a2"),
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
        replyToMessageId = "a6",
    ),
    message(
        "m6",
        currentUserId,
        "The signed-off handoff is attached.",
        32,
        listOf(FileMedia(url = "fixture://handoff", name = "handoff.pdf", size = 834_220)),
        replyToMessageId = "m0",
    ),
)

/** Everything the room holds, oldest first: the archive followed by the loaded window. */
internal val showcaseHistory = showcaseArchive + showcaseMessages

/**
 * Parents the fixture deliberately answers nothing for, so one rendered reply stays in the third
 * state of `replyPreviewByMessageId`: a **missing key**, "not yet resolved". It stands in for an
 * id a batched `getReplyPreviews` has not come back for yet — and, against an older backend, one
 * it never will. The reference and the jump affordance stay; only the quoted text is absent, so
 * the row never claims the message is gone. Deleting such a parent resolves it to the terminal
 * `Unavailable`, which is the one explicit signal that outranks the withholding.
 */
internal val showcaseUnresolvedParentIds: Set<String> = setOf("a6")

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
    revision: Int = 0,
    replyToMessageId: String? = null,
): Message = Message(
    id = id,
    conversationId = conversationId,
    senderId = senderId,
    text = text,
    media = media,
    createdAt = showcaseInstant(minute),
    updatedAt = null,
    revision = revision,
    replyToMessageId = replyToMessageId,
)

internal fun showcaseInstant(minute: Int): Instant =
    Instant.parse("2026-09-04T09:${minute.toString().padStart(2, '0')}:00Z")
