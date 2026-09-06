package app.convokit.ui.example

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.convokit.sdk.Message
import app.convokit.ui.components.ConvoKitConversationListView
import app.convokit.ui.components.ConvoKitConversationView
import app.convokit.ui.components.ConvoKitImageLoader
import app.convokit.ui.components.ConversationHeaderContent
import app.convokit.ui.components.ConversationItemContent
import app.convokit.ui.components.DefaultComposer
import app.convokit.ui.components.DefaultMediaBlock
import app.convokit.ui.components.MessageItemContent
import app.convokit.ui.components.ReadReceiptContent
import app.convokit.ui.isConvoKitPending
import app.convokit.ui.theme.ConvoKitTheme
import app.convokit.ui.theme.ConvoKitUiColors
import app.convokit.ui.theme.ConvoKitUiDimensions

internal enum class ShowcaseVariant {
    STANDARD,
    BRANDED,
    COMPACT,
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
    var messages by remember(variant) { mutableStateOf(showcaseMessages) }
    val context = LocalContext.current
    val imageBytes = remember { context.resources.openRawResource(R.raw.convokit_sample).use { it.readBytes() } }
    val imageLoader = remember(imageBytes) { ConvoKitImageLoader { imageBytes } }

    val customHeader: ConversationHeaderContent? = if (variant == ShowcaseVariant.BRANDED) {
        { conversation, _, onRefresh -> BrandedHeader(conversation.displayTitle, onRefresh) }
    } else {
        null
    }
    val customConversationItem: ConversationItemContent? = if (variant == ShowcaseVariant.BRANDED) {
        { conversation, _, onClick -> BrandedConversationItem(conversation.displayTitle, conversation.description.orEmpty(), onClick) }
    } else {
        null
    }
    val customMessage: MessageItemContent? = if (variant == ShowcaseVariant.COMPACT) {
        { message, _, mine, sender, readers -> CompactMessage(message, mine, sender?.name, readers.size) }
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
                modifier = Modifier.fillMaxWidth().height(if (variant == ShowcaseVariant.COMPACT) 156.dp else 190.dp),
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
                        conversations = showcaseConversations.take(2),
                        onConversationSelected = { selected = it },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        itemSpacing = if (variant == ShowcaseVariant.COMPACT) 4.dp else 8.dp,
                        conversationItem = customConversationItem,
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
                    messages = messages,
                    currentUserId = currentUserId,
                    onSendMessage = { text ->
                        messages = messages + Message(
                            id = "local-${messages.size}",
                            conversationId = selected.id,
                            senderId = currentUserId,
                            text = text,
                            media = emptyList(),
                            createdAt = showcaseInstant(59),
                            updatedAt = null,
                        )
                    },
                    readAtByUserId = mapOf("alex" to showcaseInstant(40)),
                    typingUserIds = if (variant == ShowcaseVariant.STANDARD) setOf("alex") else emptySet(),
                    displayNameForUser = { id -> if (id == "alex") "Alex Rivera" else id },
                    reverseMessages = true,
                    messageContentPadding = PaddingValues(
                        horizontal = if (variant == ShowcaseVariant.COMPACT) 10.dp else 14.dp,
                        vertical = 10.dp,
                    ),
                    headerContent = customHeader,
                    messageItem = customMessage,
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
                            Surface(color = Color(0xFFF8F5FF)) {
                                DefaultComposer(text, onTextChange, sending, send, attachment)
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
private fun BrandedConversationItem(title: String, detail: String, onClick: () -> Unit) {
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
                Text(title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(detail, color = Color(0xFF746B82), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

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
                if (mine) {
                    Text(
                        if (message.isConvoKitPending) "SENDING…" else if (readerCount > 0) "READ" else "SENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFAAD8CC),
                    )
                }
            }
        }
    }
}

private fun specFor(variant: ShowcaseVariant): ShowcaseSpec = when (variant) {
    ShowcaseVariant.STANDARD -> ShowcaseSpec(
        title = "Standard components",
        description = "Material 3 defaults with media, read receipts, typing state, pagination, and a text composer.",
        props = listOf("defaults", "read receipts", "media"),
        colors = ConvoKitUiColors.light(),
    )
    ShowcaseVariant.BRANDED -> ShowcaseSpec(
        title = "Branded support",
        description = "The same components configured through color tokens and targeted content slots.",
        props = listOf("theme tokens", "header slot", "media slot"),
        colors = ConvoKitUiColors.light().copy(
            primary = Color(0xFF68479D),
            outgoingBubble = Color(0xFF68479D),
            readReceipt = Color(0xFFE0D0FF),
            background = Color(0xFFF5F1FA),
        ),
    )
    ShowcaseVariant.COMPACT -> ShowcaseSpec(
        title = "Compact operations",
        description = "Dense list spacing and a full message-row replacement for operational workflows.",
        props = listOf("compact sizing", "message slot", "custom rows"),
        colors = ConvoKitUiColors.light().copy(
            primary = Color(0xFF284A40),
            outgoingBubble = Color(0xFF263A35),
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
