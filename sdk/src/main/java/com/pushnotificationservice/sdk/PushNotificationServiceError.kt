package com.pushnotificationservice.sdk

sealed class PushNotificationServiceError(message: String) : Exception(message) {
    /** `onNewToken`/`unregister` called before `configure()`. */
    object NotConfigured : PushNotificationServiceError("PushNotificationService.configure() must be called first")
    /** The server returned a non-2xx status. */
    data class ServerError(val statusCode: Int) : PushNotificationServiceError("Server returned status $statusCode")
    /** The 2xx response body wasn't the expected shape. */
    object InvalidResponse : PushNotificationServiceError("Unexpected response shape")
    /** A transport-level failure (no connectivity, timeout, etc.). */
    object Transport : PushNotificationServiceError("Network request failed")
}
