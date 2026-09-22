package app.convokit.ui.example

import app.convokit.sdk.Message
import app.convokit.sdk.ReplyPreview
import app.convokit.ui.ConvoKitReplyPreview
import app.convokit.ui.ConvoKitWindowMode

/**
 * The fixture the showcase screens drive the controlled `ConvoKitConversationView` with. It stands
 * in for the SDK-backed controller the Live tab gets from `ConvoKitConversation`, and it is
 * deliberately the smallest thing that makes the 0.9.0 surface real: one loaded [messages] window
 * over a longer [history], a reply target, a quoted-preview map, and a jump that replaces the
 * window instead of scrolling inside it.
 *
 * Quoted previews are **re-read, never copied**: [replyPreviews] resolves each rendered row's
 * `replyToMessageId` against [history] every time, so editing a quoted message updates the quote
 * and deleting one turns it into `ConvoKitReplyPreview.Unavailable` while the reference stays.
 * One parent is withheld through [unresolvedParentIds] so the map's third state — a **missing
 * key**, "not yet resolved" — is on screen beside the other two rather than only documented.
 */
internal data class ShowcaseRoom(
    val history: List<Message> = showcaseHistory,
    val messages: List<Message> = showcaseMessages,
    val windowMode: ConvoKitWindowMode = ConvoKitWindowMode.LIVE,
    val replyTarget: Message? = null,
    val editingMessage: Message? = null,
    val highlightedMessageId: String? = null,
    val scrollTarget: String? = null,
    val suppressPagination: Boolean = false,
    val unresolvedParentIds: Set<String> = showcaseUnresolvedParentIds,
    val nextLocalId: Int = 0,
) {
    /** Only a jumped window can have newer history; a live one is anchored at the newest row. */
    val hasNewerMessages: Boolean
        get() = windowMode == ConvoKitWindowMode.JUMPED && messages.lastOrNull()?.id != history.lastOrNull()?.id

    /**
     * One entry per distinct `replyToMessageId` among the rendered rows, so the map stays bounded
     * by the window exactly as a batched `getReplyPreviews` answer would. A parent that is no
     * longer in the room resolves to the terminal `Unavailable`; against a real backend that
     * answer arrives as the parent's absence from the batch, which is the only deletion signal.
     * An id in [unresolvedParentIds] gets **no entry at all**, which is the distinct third state:
     * its reply renders the bare reference with no quoted text, never the unavailable copy.
     */
    val replyPreviews: Map<String, ConvoKitReplyPreview>
        get() = messages.mapNotNull(Message::replyToMessageId)
            .distinct()
            .filterNot { it in unresolvedParentIds }
            .associateWith { parentId ->
                when (val parent = history.firstOrNull { it.id == parentId }) {
                    null -> ConvoKitReplyPreview.Unavailable
                    else -> ConvoKitReplyPreview.Resolved(previewOf(parent))
                }
            }

    /** Quoting and editing are mutually exclusive: one composer can only be in one mode. */
    fun startReplying(message: Message): ShowcaseRoom = copy(replyTarget = message, editingMessage = null)

    fun cancelReplying(): ShowcaseRoom = copy(replyTarget = null)

    fun startEditing(message: Message): ShowcaseRoom = copy(editingMessage = message, replyTarget = null)

    fun cancelEditing(): ShowcaseRoom = copy(editingMessage = null)

    /**
     * Appends a row as an optimistic send would, stamping [replyTarget] on it so its quoted block
     * renders before any acknowledgement, and clears the reply target. A send always lands on the
     * live tail, so a jumped window returns to the latest first; the quote survives that switch.
     *
     * The id comes from [nextLocalId], which only ever goes up. It must never be derived from the
     * size of a list [delete] can shrink: the list keys its rows by id, so a reused id is a
     * duplicate key rather than a second row.
     */
    fun send(text: String, conversationId: String): ShowcaseRoom {
        val live = returnToLatest()
        val sent = Message(
            id = "local-${live.nextLocalId}",
            conversationId = conversationId,
            senderId = currentUserId,
            text = text,
            media = emptyList(),
            createdAt = showcaseInstant(59),
            updatedAt = null,
            revision = 0,
            replyToMessageId = replyTarget?.id,
        )
        return live.copy(
            history = live.history + sent,
            messages = live.messages + sent,
            replyTarget = null,
            nextLocalId = live.nextLocalId + 1,
        )
    }

    /** A save lands on the row and bumps its `revision`; `copy` carries the reference along. */
    fun saveEdit(message: Message, text: String): ShowcaseRoom {
        val saved: (Message) -> Message = { row ->
            if (row.id == message.id) row.copy(text = text.ifEmpty { null }, revision = row.revision + 1) else row
        }
        return copy(history = history.map(saved), messages = messages.map(saved), editingMessage = null)
    }

    /**
     * Deleting a quoted message leaves every reply's reference in place; the quote degrades. A
     * delete is an explicit signal, so a parent that was being withheld stops being withheld and
     * resolves to the terminal `Unavailable` instead of staying unresolved.
     */
    fun delete(message: Message): ShowcaseRoom = copy(
        history = history.filterNot { it.id == message.id },
        messages = messages.filterNot { it.id == message.id },
        editingMessage = editingMessage?.takeIf { it.id != message.id },
        replyTarget = replyTarget?.takeIf { it.id != message.id },
        unresolvedParentIds = unresolvedParentIds - message.id,
    )

    /**
     * A target already in the window only highlights and scrolls. Anything else replaces the
     * window with one bounded slice centred on the target and switches to
     * [ConvoKitWindowMode.JUMPED], which is what [hasNewerMessages] and `Jump to latest` are for.
     * A target that is no longer in the room leaves the window untouched: its quote already reads
     * `Original message unavailable`, so there is nothing to go to and nothing to report.
     */
    fun jumpTo(messageId: String): ShowcaseRoom {
        if (messages.any { it.id == messageId }) return highlight(messageId)
        val index = history.indexOfFirst { it.id == messageId }
        if (index < 0) return this
        val lastStart = (history.size - showcaseWindowSize).coerceAtLeast(0)
        val start = (index - showcaseWindowSize / 2).coerceIn(0, lastStart)
        val window = history.subList(start, (start + showcaseWindowSize).coerceAtMost(history.size))
        return copy(messages = window, windowMode = ConvoKitWindowMode.JUMPED).highlight(messageId)
    }

    /** Pages a jumped window one step toward the live tail, as the list's newer edge asks. */
    fun loadNewer(): ShowcaseRoom {
        if (!hasNewerMessages) return this
        val next = history.indexOfFirst { it.id == messages.last().id } + 1
        return copy(messages = messages + history.subList(next, (next + showcaseWindowSize).coerceAtMost(history.size)))
    }

    /**
     * Drops a jumped window and goes back to the newest page. The highlighted row is carried
     * through, so a target still in that page re-anchors. A no-op in [ConvoKitWindowMode.LIVE].
     */
    fun returnToLatest(): ShowcaseRoom = when (windowMode) {
        ConvoKitWindowMode.LIVE -> this
        ConvoKitWindowMode.JUMPED -> copy(
            messages = history.takeLast(showcaseWindowSize),
            windowMode = ConvoKitWindowMode.LIVE,
        )
    }

    /** The list reports the scroll finished, or could not run, which releases both edges. */
    fun scrollHandled(): ShowcaseRoom = copy(scrollTarget = null, suppressPagination = false)

    /** The highlight expired; the package's own controller clears its own after about two seconds. */
    fun clearHighlight(): ShowcaseRoom = copy(highlightedMessageId = null)

    // Highlight and scroll one rendered row, holding both pagination edges until the view reports
    // the scroll handled, so the jump's own scroll cannot trigger a page load.
    private fun highlight(messageId: String): ShowcaseRoom =
        copy(highlightedMessageId = messageId, scrollTarget = messageId, suppressPagination = true)
}

/** Longest quoted excerpt the backend serves; a preview derived from a loaded row cuts the same. */
private const val replyPreviewTextLimit = 500

// The same projection the reply-preview route returns, so a quote derived from a loaded row and
// one resolved over the wire render identically.
private fun previewOf(parent: Message): ReplyPreview = ReplyPreview(
    id = parent.id,
    conversationId = parent.conversationId,
    senderId = parent.senderId,
    text = parent.text?.take(replyPreviewTextLimit),
    textTruncated = (parent.text?.length ?: 0) > replyPreviewTextLimit,
    createdAt = parent.createdAt,
    revision = parent.revision,
    mediaCount = parent.media.size,
)
