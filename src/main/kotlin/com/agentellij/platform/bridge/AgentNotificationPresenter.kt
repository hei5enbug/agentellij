package com.agentellij.platform.bridge

import com.agentellij.core.bridge.AgentNotificationEvent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.SystemNotifications

/** Shows both AgentellIJ's IDE balloon and the host operating system's native popup. */
internal object AgentNotificationPresenter {
    private const val GROUP_ID = "AgentellIJ"

    fun show(project: Project, displayName: String, event: AgentNotificationEvent) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater

            val title = "$displayName ${event.titleSuffix}"
            Notification(GROUP_ID, title, event.message, NotificationType.INFORMATION).notify(project)
            AlwaysSystemNotification.notify(GROUP_ID, title, event.message)
        }
    }
}

/**
 * IntelliJ's public system-notification facade deliberately suppresses OS popups while
 * the IDE is active. The platform notifier behind that facade has no such restriction,
 * so use it directly and fall back to the public facade if its internal shape changes.
 */
private object AlwaysSystemNotification {
    private val LOG = Logger.getInstance(AlwaysSystemNotification::class.java)

    fun notify(name: String, title: String, message: String) {
        try {
            val facade = SystemNotifications.getInstance()
            val provider = facade.javaClass.getDeclaredMethod("getPlatformNotifier")
            check(provider.trySetAccessible()) { "Platform notifier provider is inaccessible" }
            val notifier = provider.invoke(null) ?: return
            val notify = notifier.javaClass.getDeclaredMethod(
                "notify",
                String::class.java,
                String::class.java,
                String::class.java
            )
            check(notify.trySetAccessible()) { "Platform notifier is inaccessible" }
            notify.invoke(notifier, name, title, message)
        } catch (failure: Throwable) {
            LOG.warn("Could not bypass active-IDE suppression for an OS notification", failure)
            SystemNotifications.getInstance().notify(name, title, message)
        }
    }
}
