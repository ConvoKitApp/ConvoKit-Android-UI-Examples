# ConvoKit Android UI examples

A public native Android application showing how to use the compiled
`app.convokit:convokit-android-ui` Jetpack Compose package from
<https://maven.convokit.app>.

This repository contains demo integration code only. The reusable UI package
implementation stays in its private source repository.

## Component configurations

| Standard components | Branded support | Compact operations |
| --- | --- | --- |
| ![Standard components](doc/screenshots/standard-components.png) | ![Branded support](doc/screenshots/branded-support.png) | ![Compact operations](doc/screenshots/compact-operations.png) |

The app includes four navigable examples:

- **Standard** uses the Material 3 defaults with inbox previews, unread badges,
  the mark-unread dot, media, read receipts, typing, pagination, the composer,
  and the long-press edit and delete actions on your own messages.
- **Branded** applies a custom palette and replaces the list row (through the
  `inboxItem` slot, reading each row's `InboxSummary`, including `isUnread`),
  header, media wrapper, read receipt, and composer styling; the branded
  composer still saves an edit and reads `Save` while one is in progress.
- **Compact** changes sizing tokens and replaces the complete message row,
  which shows its own `Edited` label from `Message.isEdited`.
- **Live** connects the real SDK, joins a room through an application backend,
  and renders the SDK-backed inbox list (previews, activity times, unread
  badges and the mark-unread dot come from the package) with a per-row
  "Mark unread" menu before the SDK-backed conversation component, whose
  default rows and composer edit and delete the user's own messages.

The showcase screens pass real `InboxSummary` values to the controlled
`ConvoKitConversationListView` through its `summaries` and `currentUserId`
parameters, so the default row derives the preview line, the `activityAt` time
and the unread badge exactly as an SDK-backed list would. One fixture room is
fully read but carries the user's private "mark unread" marker (`isUnread` with
`unreadCount` 0), so its row shows the numberless dot next to the numeric and
capped (`99+`) badges of the other rooms. Every fixture `Message` carries its
`revision`; one of Maya's rows is already edited (`revision` 1), so the
`Edited` label is visible before any interaction.

## Run

Open the repository in Android Studio and run the `app` configuration, or:

```bash
./gradlew :app:installDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Package setup

The project resolves the private implementation's compiled artifacts from the
public, unauthenticated ConvoKit Maven feed:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.convokit.app/")
    }
}

dependencies {
    implementation("app.convokit:convokit-android-ui:0.8.0")
}
```

The UI artifact exposes the compatible core SDK transitively. An application
may also declare `app.convokit:convokit-android:0.8.0` explicitly.

## Live room example

The checked-in defaults use the deliberately open ConvoKit demo broker at
<https://convokit-open-chatroom.vercel.app>. Enter a unique demo user ID and an
existing room ID. The broker grants demo membership and returns the short-lived
token used by the Android SDK.

Joining by room ID is intentionally application/backend logic, not a UI SDK
component. Real products must decide who is allowed to join, how invite codes
map to rooms, and how their users are authenticated.

Once authorized, create the adapter **after** `connectUser()` succeeds. Retain it
for that login and create a new one on every subsequent login, even when the
public user ID is unchanged. Clear the chat UI on logout and disconnect the SDK
when leaving the live example. `LiveChatScreen.kt` demonstrates this ownership.
Then render the SDK-backed inbox, which pages `listInbox` itself and shows each
room's preview, activity time and unread badge, and open the selected room:

```kotlin
val uiClient = DefaultConvoKitUiClient(connectedSdk)
var openRoomId by remember { mutableStateOf<String?>(null) }

when (val id = openRoomId) {
    null -> ConvoKitConversationList(client = uiClient, onConversationSelected = { openRoomId = it.id })
    else -> ConvoKitConversation(client = uiClient, conversationId = id, onBack = { openRoomId = null })
}
```

### Mark unread

Since 0.7.0 a user can mark a room unread for themselves. The marker is
private (other members never see it), never invents a count, and shows as a
numberless dot named `Unread` on the package's default row while `unreadCount`
is 0; a numeric or capped badge still wins when there are unread messages. The
package ships no row affordance, so the live example keeps the default row and
adds a per-row menu through the `inboxItem` slot; the menu's "Mark unread"
action calls `markUnread` on the list controller handed over by
`onController`, which patches that row's summary so the dot appears at once:

```kotlin
var listController by remember(uiClient) { mutableStateOf<ConvoKitConversationListController?>(null) }

ConvoKitConversationList(
    client = uiClient,
    onConversationSelected = { openRoomId = it.id },
    onController = { listController = it },
    inboxItem = { conversation, summary, _, onClick ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            DefaultConversationItem(conversation, onClick, Modifier.weight(1f), summary = summary, currentUserId = userId)
            // A host DropdownMenu (see LiveInboxRow in LiveChatScreen.kt) whose item runs:
            RowMenu("Mark unread") { scope.launch { listController?.markUnread(conversation.id) } }
        }
    },
)
```

Opening the room clears the marker through its read acknowledgements:
`ConvoKitConversation` captures the membership's `privateStateVersion` when it
opens and sends it with every acknowledgement of that open (an empty marked
room issues one versioned clear instead), so a mark made on another device
after the open survives. Other devices refresh their lists through the
package's `inboxActivity` signal. `LiveChatScreen.kt` demonstrates the
complete flow.

### Edit and delete your own messages

Since 0.8.0 a user can edit the text of, or delete, a message they sent. Both
go through the core's author-tier routes, which need the 0.8 backend; an
older backend answers an uncoded 404 that only surfaces as
`ConversationState.error`. Every `Message` carries a `revision` (0 on
creation, +1 per author or administrative edit) and `isEdited`
(`revision > 0`); the package's default rows show `Edited` beside the time.
Unlike mark unread, the package ships the affordance itself, so the live
example keeps `ConvoKitConversation` unchanged: the current user's confirmed
rows offer a long press (accessible action `Message actions`) opening
`Edit message` / `Delete message`, deleting asks `Delete this message?`
(`Delete` named `Confirm delete`, `Cancel` named `Cancel delete`) unless
`confirmDelete` answers instead, and the composer turns into edit mode with a
banner (`Editing message`, `Cancel` named `Cancel editing`) and a `Save`
button (named `Save message`). The controller sends the **snapshot's**
`revision`; a 409 `REVISION_CONFLICT` reloads the row once and keeps the
draft and edit mode so the user can review the changed content and save
again, and failures never evict a row. Foreign rows, pending rows and rows
under a `READ` role render exactly as in 0.7.0.

The showcase screens exercise the same surface through the controlled
`ConvoKitConversationView`, which is a pure function of `editingMessage` and
the callbacks; with none of them bound the view is the 0.7.0 one. The fixture
stands in for the backend: a save lands on the row and bumps its `revision`,
which is what renders `Edited`, and a delete removes the row
(`ShowcaseScreen.kt`):

```kotlin
var messages by remember { mutableStateOf(showcaseMessages) }
var editingMessage by remember { mutableStateOf<Message?>(null) }

ConvoKitConversationView(
    conversation = selected,
    messages = messages,
    currentUserId = currentUserId,
    onSendMessage = { text -> /* append a local row with revision = 0 */ },
    editingMessage = editingMessage,
    onEditMessage = { editingMessage = it },
    onSaveEdit = { message, text, complete ->
        // `text` is trimmed; "" clears the caption of a row that keeps an attachment.
        messages = messages.map { if (it.id == message.id) it.copy(text = text.ifEmpty { null }, revision = it.revision + 1) else it }
        editingMessage = null
        complete(true)
    },
    onCancelEdit = { editingMessage = null },
    onDeleteMessage = { message, complete ->
        messages = messages.filterNot { it.id == message.id }
        complete(true)
    },
)
```

The `composerContent` and `messageItem` slots keep their parameters: the one
`send` handed to a custom composer already saves while `editingMessage` is
set, so the branded composer only passes the host's edit state on to
`DefaultComposer(editing = editingMessage != null, allowEmpty = …)` to read
`Save` and allow an empty caption, and the compact custom row shows its own
`Edited` label from `Message.isEdited`. Deleting removes the message and its
attachment records for every member and cannot be undone; files already
received or downloaded cannot be retracted.

The client ID is public. Never put a ConvoKit client secret in an APK,
`BuildConfig`, resource, manifest, or mobile request. Production token endpoints
must authenticate the host application's user and derive the app-user ID on the
server.

## Documentation

See the [Android UI documentation](https://convokit.app/docs/android-ui) and
[native Android SDK documentation](https://convokit.app/docs/android-sdk).
