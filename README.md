# PushNotificationService Android SDK

[![Test](https://github.com/pushnotificationlabs/pushnotificationservice-android/actions/workflows/test.yml/badge.svg)](https://github.com/pushnotificationlabs/pushnotificationservice-android/actions/workflows/test.yml)

Kotlin library wrapping PushNotificationService.com's native device-token
REST API. Min SDK 23. No bundled networking/JSON/Firebase dependency — you
already have Firebase Messaging in your app to obtain the token; this SDK
only ever receives plain strings from it.

## Install

**Not yet published to Maven Central** — the coordinate below isn't resolvable
yet (a Sonatype OSSRH account still needs to be provisioned before the first
real `./gradlew publish`). Source and tests are complete and CI-verified; this
section will drop the caveat once the first release actually publishes.

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.pushnotificationservice:android-sdk:0.1.0")
}
```

## Request notification permission (Android 13+)

On API 33+ (Android 13+), posting a notification requires the runtime
`android.permission.POST_NOTIFICATIONS` permission — the host app must
request it explicitly (e.g. via `ActivityCompat.requestPermissions`) before
`onMessageReceived` will actually post anything:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE)
}
```

If the permission hasn't been granted, `onMessageReceived` silently no-ops
(logs a warning, does not throw) — there is no error surfaced to the caller.

## Register for push

```kotlin
// Application.onCreate()
PushNotificationService.configure(applicationContext, siteId = "YOUR_SITE_ID")

// Your FirebaseMessagingService subclass
override fun onNewToken(token: String) {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching { PushNotificationService.onNewToken(token) }
    }
}

override fun onMessageReceived(message: RemoteMessage) {
    PushNotificationService.onMessageReceived(
        context = applicationContext,
        title = message.notification?.title,
        body = message.notification?.body,
        image = message.notification?.imageUrl?.toString(),
        data = message.data,
    )
}
```

Note: this is only reached while your app is foregrounded — a backgrounded
app has its notification auto-displayed by the OS directly, with no app code
running (a permanent FCM/Android platform limitation, not something this SDK
can work around).

## Handle a tap

```kotlin
// The Activity your notification's PendingIntent launches
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    PushNotificationService.onNotificationOpened(this, intent)
}
```

By default this opens the tapped notification's (tracking) URL via an
`ACTION_VIEW` intent. To intercept instead:

```kotlin
PushNotificationService.onNotificationTapped = { uri ->
    // your own routing
}
```

## Unregister (e.g. on logout)

```kotlin
CoroutineScope(Dispatchers.IO).launch {
    runCatching { PushNotificationService.unregister() }
}
```

## Customize the notification channel

```kotlin
PushNotificationChannel.configureChannel(name = "Deals", importance = NotificationManager.IMPORTANCE_HIGH)
```

Call this before the first message arrives, or the SDK creates a default
channel ("Push notifications") on first use.

## License

MIT
