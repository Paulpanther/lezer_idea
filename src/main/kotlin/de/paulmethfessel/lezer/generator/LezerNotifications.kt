package de.paulmethfessel.lezer.generator

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager

/** The notification group of the generator, registered in plugin.xml. */
object LezerNotifications {
    fun group(): NotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Lezer Generator")
}
