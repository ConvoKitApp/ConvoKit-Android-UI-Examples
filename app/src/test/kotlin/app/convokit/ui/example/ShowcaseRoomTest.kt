package app.convokit.ui.example

import app.convokit.sdk.Message
import app.convokit.ui.ConvoKitReplyPreview
import app.convokit.ui.ConvoKitWindowMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The showcase fixture is the only piece of the example that has behavior of its own; the package
 * owns everything else. These cover what the screens claim: a quote is re-read rather than copied,
 * a reference outlives the message it points at, and a jump out of the loaded window replaces that
 * window and offers the way back.
 */
class ShowcaseRoomTest {
    @Test fun `a quoted message inside the window resolves from the room`() {
        val preview = resolved(ShowcaseRoom(), "m1")
        assertEquals("alex", preview.senderId)
        assertEquals(row("m1").text, preview.text)
        assertFalse(preview.textTruncated)
    }

    @Test fun `a quoted message outside the loaded window still resolves`() {
        val room = ShowcaseRoom()
        assertFalse(room.messages.any { it.id == "a2" })
        assertEquals(row("a2").text, resolved(room, "a2").text)
    }

    @Test fun `a quoted message that is gone is the terminal unavailable state`() {
        assertSame(ConvoKitReplyPreview.Unavailable, ShowcaseRoom().replyPreviews["m0"])
    }

    @Test fun `the preview map is bounded by the rendered window`() {
        val room = ShowcaseRoom()
        // `m5` quotes `a6`, which the fixture withholds, so that id is deliberately not a key.
        assertEquals(setOf("m1", "a2", "m0"), room.replyPreviews.keys)
        // The jumped window holds no replies at all, so it resolves nothing.
        assertEquals(emptyMap<String, ConvoKitReplyPreview>(), room.jumpTo("a2").replyPreviews)
    }

    @Test fun `editing a quoted message updates the quote and never moves the reference`() {
        val room = ShowcaseRoom().saveEdit(row("m1"), "The updated empty state shipped.")
        assertEquals("The updated empty state shipped.", resolved(room, "m1").text)
        assertEquals(1, resolved(room, "m1").revision)
        assertEquals("m1", room.messages.first { it.id == "m2" }.replyToMessageId)
    }

    @Test fun `deleting a quoted message keeps the reference and degrades the quote`() {
        val room = ShowcaseRoom().delete(row("m1"))
        assertSame(ConvoKitReplyPreview.Unavailable, room.replyPreviews["m1"])
        assertEquals("m1", room.messages.first { it.id == "m2" }.replyToMessageId)
    }

    @Test fun `an unresolved reference is a missing key, not the unavailable state`() {
        val room = ShowcaseRoom()
        val quoting = room.messages.first { it.id == "m5" }
        assertEquals("a6", quoting.replyToMessageId)
        assertFalse(room.replyPreviews.containsKey("a6"))
        assertNull(room.replyPreviews["a6"])
        // The parent is still in the room, so the jump affordance survives the unresolved state.
        val jumped = room.jumpTo("a6")
        assertEquals(ConvoKitWindowMode.JUMPED, jumped.windowMode)
        assertEquals("a6", jumped.highlightedMessageId)
        assertTrue(jumped.messages.any { it.id == "a6" })
    }

    @Test fun `deleting a withheld parent resolves it to the terminal unavailable state`() {
        val room = ShowcaseRoom().delete(row("a6"))
        assertSame(ConvoKitReplyPreview.Unavailable, room.replyPreviews["a6"])
        assertEquals("a6", room.messages.first { it.id == "m5" }.replyToMessageId)
    }

    @Test fun `a jump inside the window only highlights and scrolls`() {
        val room = ShowcaseRoom().jumpTo("m1")
        assertEquals(ConvoKitWindowMode.LIVE, room.windowMode)
        assertEquals(showcaseMessages, room.messages)
        assertEquals("m1", room.highlightedMessageId)
        assertEquals("m1", room.scrollTarget)
        assertTrue(room.suppressPagination)
    }

    @Test fun `a jump outside the window replaces it and offers the way back`() {
        val room = ShowcaseRoom().jumpTo("a2")
        assertEquals(ConvoKitWindowMode.JUMPED, room.windowMode)
        assertEquals(showcaseWindowSize, room.messages.size)
        assertTrue(room.messages.any { it.id == "a2" })
        assertTrue(room.hasNewerMessages)
        assertEquals("a2", room.highlightedMessageId)
    }

    @Test fun `reporting the scroll releases the hold and leaves the highlight alone`() {
        val room = ShowcaseRoom().jumpTo("m1").scrollHandled()
        assertNull(room.scrollTarget)
        assertFalse(room.suppressPagination)
        assertEquals("m1", room.highlightedMessageId)
    }

    @Test fun `clearing the highlight ends it without touching the window`() {
        val room = ShowcaseRoom().jumpTo("a2").clearHighlight()
        assertNull(room.highlightedMessageId)
        assertEquals(ConvoKitWindowMode.JUMPED, room.windowMode)
        assertTrue(room.messages.any { it.id == "a2" })
    }

    @Test fun `a jump to a message that is gone leaves the window untouched`() {
        val room = ShowcaseRoom()
        assertEquals(room, room.jumpTo("m0"))
    }

    @Test fun `paging a jumped window to the tail does not make it live`() {
        var room = ShowcaseRoom().jumpTo("a2")
        while (room.hasNewerMessages) room = room.loadNewer()
        assertEquals(ConvoKitWindowMode.JUMPED, room.windowMode)
        assertEquals(showcaseHistory, room.messages)
    }

    @Test fun `returning to the latest restores the live window and carries the highlight`() {
        val room = ShowcaseRoom().jumpTo("a2").scrollHandled().returnToLatest()
        assertEquals(ConvoKitWindowMode.LIVE, room.windowMode)
        assertEquals(showcaseMessages, room.messages)
        assertFalse(room.hasNewerMessages)
        assertEquals("a2", room.highlightedMessageId)
    }

    @Test fun `sending a reply stamps the target on the row and clears the strip`() {
        val room = ShowcaseRoom().startReplying(row("m1")).send("On it.", "design")
        assertNull(room.replyTarget)
        assertEquals("m1", room.messages.last().replyToMessageId)
        assertEquals("On it.", room.messages.last().text)
    }

    @Test fun `sending from a jumped window returns to the latest and keeps the quote`() {
        val room = ShowcaseRoom().jumpTo("a2").startReplying(row("a2")).send("Still owed.", "design")
        assertEquals(ConvoKitWindowMode.LIVE, room.windowMode)
        assertEquals("a2", room.messages.last().replyToMessageId)
        assertTrue(room.messages.containsAll(showcaseMessages))
    }

    @Test fun `a plain send carries no reference`() {
        assertNull(ShowcaseRoom().send("Morning.", "design").messages.last().replyToMessageId)
    }

    @Test fun `cancelling a reply drops the target and sends nothing`() {
        val room = ShowcaseRoom().startReplying(row("m1")).cancelReplying()
        assertNull(room.replyTarget)
        assertEquals(showcaseMessages, room.messages)
        assertEquals(showcaseHistory, room.history)
    }

    @Test fun `deleting the row being replied to clears the strip`() {
        val room = ShowcaseRoom().startReplying(row("m4")).delete(row("m4"))
        assertNull(room.replyTarget)
        assertFalse(room.messages.any { it.id == "m4" })
    }

    @Test fun `a send after a delete never reuses a message id`() {
        val sent = ShowcaseRoom().send("Morning.", "design")
        val room = sent.delete(row("m4")).send("On it.", "design")
        assertEquals(room.messages.size, room.messages.distinctBy(Message::id).size)
        assertEquals(room.history.size, room.history.distinctBy(Message::id).size)
    }

    @Test fun `replying and editing are mutually exclusive`() {
        val replying = ShowcaseRoom().startEditing(row("m2")).startReplying(row("m1"))
        assertNull(replying.editingMessage)
        assertEquals("m1", replying.replyTarget?.id)
        val editing = replying.startEditing(row("m2"))
        assertNull(editing.replyTarget)
        assertEquals("m2", editing.editingMessage?.id)
    }

    private fun row(id: String): Message = showcaseHistory.first { it.id == id }

    private fun resolved(room: ShowcaseRoom, parentId: String) =
        (room.replyPreviews.getValue(parentId) as ConvoKitReplyPreview.Resolved).preview
}
