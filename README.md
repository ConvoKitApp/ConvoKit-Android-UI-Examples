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
  the mark-unread dot, media, read receipts, typing, pagination, and the
  composer.
- **Branded** applies a custom palette and replaces the list row (through the
  `inboxItem` slot, reading each row's `InboxSummary`, including `isUnread`),
  header, media wrapper, read receipt, and composer styling.
- **Compact** changes sizing tokens and replaces the complete message row.
- **Live** connects the real SDK, joins a room through an application backend,
  and renders the SDK-backed inbox list (previews, activity times, unread
  badges and the mark-unread dot come from the package) with a per-row
  "Mark unread" menu before the SDK-backed conversation component.

The showcase screens pass real `InboxSummary` values to the controlled
`ConvoKitConversationListView` through its `summaries` and `currentUserId`
parameters, so the default row derives the preview line, the `activityAt` time
and the unread badge exactly as an SDK-backed list would. One fixture room is
fully read but carries the user's private "mark unread" marker (`isUnread` with
`unreadCount` 0), so its row shows the numberless dot next to the numeric and
capped (`99+`) badges of the other rooms.

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
    implementation("app.convokit:convokit-android-ui:0.7.0")
}
```

The UI artifact exposes the compatible core SDK transitively. An application
may also declare `app.convokit:convokit-android:0.7.0` explicitly.

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

The client ID is public. Never put a ConvoKit client secret in an APK,
`BuildConfig`, resource, manifest, or mobile request. Production token endpoints
must authenticate the host application's user and derive the app-user ID on the
server.

## Documentation

See the [Android UI documentation](https://convokit.app/docs/android-ui) and
[native Android SDK documentation](https://convokit.app/docs/android-sdk).
