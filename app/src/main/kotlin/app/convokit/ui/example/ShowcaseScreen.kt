package app.convokit.ui.example

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.convokit.sdk.Conversation
import app.convokit.sdk.InboxSummary
import app.convokit.sdk.Message
import app.convokit.sdk.MessageReactionSummary
import app.convokit.sdk.ReactionSummary
import app.convokit.sdk.ReactionUser
import app.convokit.sdk.ReactionUsersPage
import app.convokit.ui.ConvoKitReplyPreview
import app.convokit.ui.ConvoKitWindowMode
import app.convokit.ui.components.ConvoKitConversationListView
import app.convokit.ui.components.ConvoKitConversationView
import app.convokit.ui.components.ConvoKitImageLoader
import app.convokit.ui.components.ConvoKitMessageItemScope
import app.convokit.ui.components.ConvoKitReactionBar
import app.convokit.ui.components.ConversationHeaderContent
import app.convokit.ui.components.DefaultComposer
import app.convokit.ui.components.DefaultMediaBlock
import app.convokit.ui.components.InboxItemContent
import app.convokit.ui.components.MessageItemContent
import app.convokit.ui.components.ReadReceiptContent
import app.convokit.ui.isConvoKitPending
import app.convokit.ui.theme.ConvoKitTheme
import app.convokit.ui.theme.ConvoKitUiColors
import app.convokit.ui.theme.ConvoKitUiDimensions
import kotlinx.coroutines.delay

internal enum class ShowcaseVariant {
    STANDARD,
    BRANDED,
    COMPACT,
    QUOTED,
}

private data class ShowcaseSpec(
    val title: String,
    val description: String,
    val props: List<String>,
    val colors: ConvoKitUiColors,
    val dimensions: ConvoKitUiDimensions = ConvoKitUiDimensions(),
)

@Composable
internal fun ShowcaseScreen(variant: ShowcaseVariant, systemPadding: PaddingValues) {
    val spec = specFor(variant)
    var selected by remember(variant) { mutableStateOf(showcaseConversations.first()) }
    // One fixture room stands in for the SDK-backed controller the Live tab gets: the loaded
    // window over a longer history, edit mode, the reply target, the quoted previews and the
    // jumped-window mode are all host state here, and the controlled view below is a pure function
    // of them and its callbacks. Edit mode has worked that way since 0.8.0: while `editingMessage`
    // is set the package shows the banner, prefills the composer silently and routes the one
    // submit handed to the default and custom composers to `onSaveEdit` instead of `onSendMessage`.
    var room by remember(variant) { mutableStateOf(ShowcaseRoom()) }
    var reactionSummaries by remember(variant) { mutableStateOf<Map<String, MessageReactionSummary>>(mapOf(
        showcaseMessages.last().id to MessageReactionSummary(
            showcaseMessages.last().id,
            listOf(ReactionSummary("❤️", 2, false)),
            false,
        ),
    )) }
    // The highlight belongs to whoever owns the state, so a controlled host clears it itself; the
    // package's own controller clears its own after about two seconds.
    LaunchedEffect(room.highlightedMessageId) {
        if (room.highlightedMessageId != null) {
            delay(2_000)
            room = room.clearHighlight()
        }
    }
    val context = LocalContext.current
    val imageBytes = remember { context.resources.openRawResource(R.raw.convokit_sample).use { it.readBytes() } }
    val imageLoader = remember(imageBytes) { ConvoKitImageLoader { imageBytes } }

    val customHeader: ConversationHeaderContent? = if (variant == ShowcaseVariant.BRANDED) {
        { conversation, _, onRefresh -> BrandedHeader(conversation.displayTitle, onRefresh) }
    } else {
        null
    }
    val customInboxItem: InboxItemContent? = if (variant == ShowcaseVariant.BRANDED) {
        { conversation, summary, _, onClick -> BrandedConversationItem(conversation, summary, onClick) }
    } else {
        null
    }
    val customMessage: MessageItemContent? = if (variant == ShowcaseVariant.COMPACT) {
        { message, _, mine, sender, readers -> CompactMessage(message, mine, sender?.name, readers.size) }
    } else {
        null
    }
    // `messageItem` above keeps its five parameters for ever. A row that needs the 0.9.0 reply
    // members takes the `messageItemScope` slot instead, which carries those five unchanged plus
    // the quoted preview, the reply action, the jump target and the highlight; supplying both on
    // one view is allowed and the scope wins.
    val scopedMessage: (@Composable (ConvoKitMessageItemScope) -> Unit)? = if (variant == ShowcaseVariant.QUOTED) {
        { scope -> QuotedMessage(scope) }
    } else {
        null
    }
    val customReceipt: ReadReceiptContent? = if (variant == ShowcaseVariant.BRANDED) {
        { _, readers -> Text("Seen by ${readers.size}", color = Color(0xFF6F54A6), style = MaterialTheme.typography.labelSmall) }
    } else {
        null
    }

    ConvoKitTheme(colors = spec.colors, dimensions = spec.dimensions) {
        Column(
            modifier = Modifier.fillMaxSize().padding(systemPadding).background(spec.colors.background).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(spec.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(spec.description, color = spec.colors.mutedText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                spec.props.take(3).forEach { prop -> AssistChip(onClick = {}, label = { Text(prop) }) }
            }
            Surface(
                // Tall enough for all three showcase rows (marked-unread dot, unread, and capped `99+`) without scrolling.
                modifier = Modifier.fillMaxWidth().height(if (variant == ShowcaseVariant.COMPACT) 210.dp else 268.dp),
                shape = RoundedCornerShape(18.dp),
                color = spec.colors.surface,
                tonalElevation = 2.dp,
            ) {
                Column {
                    Text(
                        "CONVERSATION LIST",
                        color = spec.colors.mutedText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                    ConvoKitConversationListView(
                        conversations = showcaseConversations,
                        onConversationSelected = { selected = it },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        itemSpacing = if (variant == ShowcaseVariant.COMPACT) 4.dp else 8.dp,
                        summaries = showcaseSummaries,
                        currentUserId = currentUserId,
                        inboxItem = customInboxItem,
                    )
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(18.dp),
                color = spec.colors.surface,
                tonalElevation = 2.dp,
            ) {
                ConvoKitConversationView(
                    conversation = selected,
                    messages = room.messages,
                    currentUserId = currentUserId,
                    onSendMessage = { text -> room = room.send(text, selected.id) },
                    // The fixture stands in for the backend and the SDK-backed controller: the
                    // package's default rows offer `Edit message` / `Delete message` to Maya's
                    // confirmed rows because these callbacks are bound, and the composer saves
                    // through `onSaveEdit`. A save lands on the row and bumps its `revision`,
                    // which is what renders `Edited`; an empty save clears the caption of a row
                    // that keeps an attachment (the package refuses it for text-only rows).
                    editingMessage = room.editingMessage,
                    onEditMessage = { room = room.startEditing(it) },
                    onSaveEdit = { message, text, complete ->
                        room = room.saveEdit(message, text)
                        complete(true)
                    },
                    onCancelEdit = { room = room.cancelEditing() },
                    onDeleteMessage = { message, complete ->
                        room = room.delete(message)
                        complete(true)
                    },
                    // Replying and jumping are the same kind of pure function of props (0.9.0).
                    // Every row the session may quote offers `Reply`, which sets `replyTarget` and
                    // raises the cancellable strip above the composer; a row whose
                    // `replyToMessageId` is set renders a quoted block from
                    // `replyPreviewByMessageId` and activates `onJumpToMessage`. `m3` quotes a row
                    // outside the loaded window, so activating its quote replaces the window with
                    // a slice centred on the target, highlights it and offers `Jump to latest`
                    // back; deleting a quoted row leaves the reference and degrades the quote.
                    replyTarget = room.replyTarget,
                    onReplyToMessage = { room = room.startReplying(it) },
                    onCancelReply = { room = room.cancelReplying() },
                    replyPreviewByMessageId = room.replyPreviews,
                    reactionSummaries = reactionSummaries,
                    onToggleReaction = { message, emoji ->
                        val previous = reactionSummaries[message.id]?.reactions.orEmpty()
                        val found = previous.find { it.emoji == emoji }
                        val count = (found?.count ?: 0) + if (found?.reactedByMe == true) -1 else 1
                        val updated = previous.filterNot { it.emoji == emoji } +
                            if (count > 0) listOf(ReactionSummary(emoji, count, found?.reactedByMe != true)) else emptyList()
                        reactionSummaries = reactionSummaries + (message.id to MessageReactionSummary(message.id, updated, false))
                        true
                    },
                    onListReactionUsers = { message, emoji, cursor ->
                        ReactionUsersPage(
                            if (cursor != null) emptyList() else buildList {
                                if (reactionSummaries[message.id]?.reactions?.any { it.emoji == emoji && it.reactedByMe } == true) {
                                    add(ReactionUser(currentUserId, "Maya", null, showcaseInstant(40)))
                                }
                                add(ReactionUser("alex", "Alex", null, showcaseInstant(40)))
                            },
                            null,
                        )
                    },
                    onJumpToMessage = { room = room.jumpTo(it) },
                    highlightedMessageId = room.highlightedMessageId,
                    hasNewerMessages = room.hasNewerMessages,
                    onLoadNewer = if (room.windowMode == ConvoKitWindowMode.JUMPED) ({ room = room.loadNewer() }) else null,
                    onReturnToLatest = if (room.windowMode == ConvoKitWindowMode.JUMPED) ({ room = room.returnToLatest() }) else null,
                    scrollTarget = room.scrollTarget,
                    suppressPagination = room.suppressPagination,
                    onScrollTargetHandled = { room = room.scrollHandled() },
                    readAtByUserId = mapOf("alex" to showcaseInstant(40)),
                    typingUserIds = if (variant == ShowcaseVariant.STANDARD) setOf("alex") else emptySet(),
                    displayNameForUser = ::showcaseDisplayName,
                    reverseMessages = true,
                    messageContentPadding = PaddingValues(
                        horizontal = if (variant == ShowcaseVariant.COMPACT) 10.dp else 14.dp,
                        vertical = 10.dp,
                    ),
                    headerContent = customHeader,
                    messageItem = customMessage,
                    messageItemScope = scopedMessage,
                    mediaContent = if (variant == ShowcaseVariant.BRANDED) {
                        { media, message, _, fallback ->
                            Surface(color = Color(0xFFF5F0FF), shape = RoundedCornerShape(12.dp)) {
                                Box(Modifier.padding(3.dp)) { fallback() }
                            }
                        }
                    } else {
                        null
                    },
                    readReceiptContent = customReceipt,
                    composerContent = if (variant == ShowcaseVariant.BRANDED) {
                        { text, onTextChange, sending, send, attachment ->
                            // The slot keeps its five parameters: `send` already saves while a
                            // message is being edited, and the host passes its own edit state on
                            // so the branded composer reads `Save` and allows an empty caption.
                            // The cancellable reply strip needs nothing here either: the view
                            // draws it above whichever composer is in the slot.
                            Surface(color = Color(0xFFF8F5FF)) {
                                DefaultComposer(
                                    text,
                                    onTextChange,
                                    sending,
                                    send,
                                    attachment,
                                    editing = room.editingMessage != null,
                                    allowEmpty = room.editingMessage?.media?.isNotEmpty() == true,
                                )
                            }
                        }
                    } else {
                        null
                    },
                    onAddAttachment = {},
                    onAttachmentClick = { _, _ -> },
                    imageLoader = imageLoader,
                )
            }
        }
    }
}

@Composable
private fun BrandedHeader(title: String, onRefresh: () -> Unit) {
    Surface(color = Color(0xFF5E3F95), contentColor = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("NORTHSTAR SUPPORT", style = MaterialTheme.typography.labelSmall, color = Color(0xFFDCCBFF))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            AssistChip(onClick = onRefresh, label = { Text("Sync") })
        }
    }
}

@Composable
private fun BrandedConversationItem(conversation: Conversation, summary: InboxSummary?, onClick: () -> Unit) {
    // The inbox summary carries the latest message and unread state; the row only presents them.
    // `isUnread` also covers the user's private "mark unread" marker, which never invents a count.
    val detail = summary?.latestMessage?.text?.takeIf(String::isNotBlank) ?: conversation.description.orEmpty()
    val unread = summary?.takeIf { it.isUnread || it.unreadCount > 0 || it.unreadCountCapped }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF8F5FF),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(5.dp).height(38.dp).background(Color(0xFF7457A8), RoundedCornerShape(5.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(conversation.displayTitle, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(detail, color = Color(0xFF746B82), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (unread != null) {
                Spacer(Modifier.width(10.dp))
                if (unread.unreadCount > 0 || unread.unreadCountCapped) {
                    Text(
                        "${unread.unreadCount}${if (unread.unreadCountCapped) "+" else ""} new",
                        color = Color(0xFF68479D),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    // Marked unread without unread messages: a dot, never "0 new".
                    Box(
                        Modifier
                            .semantics { contentDescription = "Unread" }
                            .size(8.dp)
                            .background(Color(0xFF68479D), CircleShape),
                    )
                }
            }
        }
    }
}

/**
 * A complete row replacement. The `messageItem` slot keeps its five parameters, so the row
 * derives the edited state from `Message.isEdited` (`revision > 0`) itself; the package's
 * long-press actions belong to its default row, and a custom row supplies its own affordance
 * (through `onController` in an SDK-backed host).
 *
 * This row is unchanged in 0.9.0: the slot's arity is frozen for ever, so a host never has to
 * migrate. A row that wants the reply members takes `messageItemScope` instead, which the Quoted
 * tab shows.
 */
@Composable
private fun CompactMessage(message: Message, mine: Boolean, sender: String?, readerCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (mine) Color(0xFF263A35) else Color(0xFFE8EEEC),
            contentColor = if (mine) Color.White else Color(0xFF17231F),
            shape = RoundedCornerShape(9.dp),
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp).fillMaxWidth(0.88f)) {
                if (!mine) Text(sender ?: message.senderId, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                message.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                message.media.forEach { media -> DefaultMediaBlock(media, message) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (mine) {
                        Text(
                            if (message.isConvoKitPending) "SENDING…" else if (readerCount > 0) "READ" else "SENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFAAD8CC),
                        )
                    }
                    if (message.isEdited) {
                        Text(
                            "EDITED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (mine) Color(0xFFAAD8CC) else Color(0xFF5F726C),
                            modifier = Modifier.semantics { contentDescription = "Edited" },
                        )
                    }
                }
            }
        }
    }
}

/**
 * A complete row replacement through the 0.9.0 `messageItemScope` slot.
 * [ConvoKitMessageItemScope] carries the five arguments of the frozen `messageItem` lambda
 * unchanged (`message`, `chronologicalIndex`, `isCurrentUser`, `sender`, `readerIds`) plus the
 * reply members, so this row renders its own quoted block from `replyPreview`, offers `Reply`
 * while `canReply` holds, activates `jumpToReplyTarget`, and tints the row a jump just landed on
 * from `isHighlighted`. A custom row never repeats the eligibility rule: `reply` is already null
 * for a pending row and for a read-only session, and `jumpToReplyTarget` only for a row that
 * quotes nothing.
 */
@Composable
private fun QuotedMessage(scope: ConvoKitMessageItemScope) {
    val mine = scope.isCurrentUser
    val bubble = when {
        scope.isHighlighted -> Color(0xFFFFE3A3)
        mine -> Color(0xFF2F3B63)
        else -> Color(0xFFE9ECF6)
    }
    val content = if (mine && !scope.isHighlighted) Color.White else Color(0xFF1B2138)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Surface(color = bubble, contentColor = content, shape = RoundedCornerShape(11.dp)) {
            Column(Modifier.padding(horizontal = 11.dp, vertical = 8.dp).fillMaxWidth(0.88f)) {
                if (!mine) {
                    Text(
                        scope.sender?.name ?: scope.message.senderId,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (scope.isReply) {
                    QuotedBlock(scope.replyPreview, scope.jumpToReplyTarget, content)
                    Spacer(Modifier.height(5.dp))
                }
                scope.message.text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                scope.message.media.forEach { media -> DefaultMediaBlock(media, scope.message) }
                scope.listReactionUsers?.let { listUsers ->
                    ConvoKitReactionBar(scope.reactionSummary, scope.toggleReaction, listUsers)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (mine) {
                        Text(
                            if (scope.message.isConvoKitPending) {
                                "SENDING…"
                            } else if (scope.readerIds.isEmpty()) {
                                "SENT"
                            } else {
                                "READ"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.7f),
                        )
                    }
                    if (scope.message.isEdited) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "EDITED",
                            style = MaterialTheme.typography.labelSmall,
                            color = content.copy(alpha = 0.7f),
                            modifier = Modifier.semantics { contentDescription = "Edited" },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    scope.reply?.let { reply ->
                        TextButton(
                            onClick = reply,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("Reply", style = MaterialTheme.typography.labelSmall, color = content)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The quoted block a custom row draws for itself. Three branches and no fourth: a resolved
 * preview, the terminal `Original message unavailable` copy, and a reference the host has not
 * resolved yet, which shows the block without quoted text rather than claiming the message is
 * gone. `ReplyPreview.senderId` is the quoted author, the identity `Message.senderId` also
 * carries, so the same resolver names both.
 */
@Composable
private fun QuotedBlock(preview: ConvoKitReplyPreview?, onActivate: (() -> Unit)?, content: Color) {
    val base = Modifier.fillMaxWidth().semantics { contentDescription = "Quoted message" }
    Surface(
        modifier = if (onActivate == null) base else base.clickable(onClickLabel = "Go to quoted message", onClick = onActivate),
        color = content.copy(alpha = 0.10f),
        contentColor = content,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(Modifier.padding(vertical = 5.dp).padding(start = 7.dp, end = 9.dp)) {
            Box(Modifier.width(3.dp).height(26.dp).background(content.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(7.dp))
            Column(Modifier.weight(1f)) {
                val resolved = (preview as? ConvoKitReplyPreview.Resolved)?.preview
                if (resolved != null) {
                    Text(
                        showcaseDisplayName(resolved.senderId),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        resolved.text?.takeIf(String::isNotBlank)?.let { if (resolved.textTruncated) "$it…" else it }
                            ?: "Attachment",
                        color = content.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (preview === ConvoKitReplyPreview.Unavailable) {
                    Text(
                        "Original message unavailable",
                        color = content.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** The room's display names, used for message authors and for quoted authors alike. */
private fun showcaseDisplayName(id: String): String = if (id == "alex") "Alex Rivera" else id

private fun specFor(variant: ShowcaseVariant): ShowcaseSpec = when (variant) {
    ShowcaseVariant.STANDARD -> ShowcaseSpec(
        title = "Standard components",
        description = "Material 3 defaults with inbox previews, unread badges and the mark-unread dot, media, read receipts, typing state, pagination, a text composer, and long-press reply, edit and delete actions.",
        props = listOf("defaults", "unread badges", "quoted replies"),
        colors = ConvoKitUiColors.light(),
    )
    ShowcaseVariant.BRANDED -> ShowcaseSpec(
        title = "Branded support",
        description = "The same components configured through color tokens, including the jump highlight, and targeted content slots.",
        props = listOf("theme tokens", "inbox slot", "media slot"),
        colors = ConvoKitUiColors.light().copy(
            primary = Color(0xFF68479D),
            outgoingBubble = Color(0xFF68479D),
            readReceipt = Color(0xFFE0D0FF),
            background = Color(0xFFF5F1FA),
            badge = Color(0xFF68479D),
            highlight = Color(0xFF68479D).copy(alpha = 0.24f),
        ),
    )
    ShowcaseVariant.QUOTED -> ShowcaseSpec(
        title = "Quoted replies",
        description = "A full row replacement through the message scope, which carries the frozen slot's five arguments plus the quoted preview, the reply action, the jump target and the highlight.",
        props = listOf("message scope", "quoted block", "jump target"),
        colors = ConvoKitUiColors.light().copy(
            primary = Color(0xFF2F3B63),
            outgoingBubble = Color(0xFF2F3B63),
            badge = Color(0xFF2F3B63),
            background = Color(0xFFF2F4FA),
        ),
    )
    ShowcaseVariant.COMPACT -> ShowcaseSpec(
        title = "Compact operations",
        description = "Dense list spacing and a full message-row replacement, through the frozen five-parameter slot, for operational workflows.",
        props = listOf("compact sizing", "message slot", "custom rows"),
        colors = ConvoKitUiColors.light().copy(
            primary = Color(0xFF284A40),
            outgoingBubble = Color(0xFF263A35),
            badge = Color(0xFF284A40),
        ),
        dimensions = ConvoKitUiDimensions(
            cornerRadius = 10.dp,
            compactCornerRadius = 8.dp,
            avatarSize = 36.dp,
            contentPadding = 10.dp,
            itemSpacing = 4.dp,
        ),
    )
}
