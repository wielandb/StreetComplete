@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package de.westnordost.streetcomplete.screens.main

import de.westnordost.streetcomplete.IosAppLinks
import platform.Foundation.NSSelectorFromString
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/** Expose the Objective-C base type: Swift cannot import Kotlin Objective-C subclasses. */
fun createQuestNotificationRouter(links: IosAppLinks): NSObject = IosQuestNotificationRouter(links)

/** Swift retains this from app initialization, before iOS delivers a cold-start response. */
private class IosQuestNotificationRouter(private val links: IosAppLinks) : NSObject(), UNUserNotificationCenterDelegateProtocol {
    private val previous = UNUserNotificationCenter.currentNotificationCenter().delegate

    init {
        UNUserNotificationCenter.currentNotificationCenter().delegate = this
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val uri = didReceiveNotificationResponse.notification.request.content.userInfo[QUEST_NOTIFICATION_URI] as? String
        if (uri != null) {
            dispatch_async(dispatch_get_main_queue()) {
                links.openUri(uri)
                withCompletionHandler()
            }
        } else if (previous?.respondsToSelector(NSSelectorFromString(
                "userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:",
            )) == true) {
            previous.userNotificationCenter(center, didReceiveNotificationResponse, withCompletionHandler)
        } else {
            withCompletionHandler()
        }
    }
}

internal const val QUEST_NOTIFICATION_URI = "scee-quest-uri"
