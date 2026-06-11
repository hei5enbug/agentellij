package com.agentellij.ui

import com.agentellij.backend.AgentProfile
import com.agentellij.settings.AgentellIJConfigurable
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel

object AgentCliInstallPanel {
    fun showMissingCli(
        project: Project,
        mainPanel: JPanel,
        profile: AgentProfile,
        binary: String,
        retryAction: () -> Unit
    ) {
        val installCommand = profile.installCommandLabel
        mainPanel.removeAll()
        mainPanel.add(JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(16)
            add(
                JLabel(
                    "<html><center>" +
                        "${escapeHtml(profile.displayName)} is not installed or not on PATH:<br/>" +
                        "<code>${escapeHtml(binary)}</code>" +
                        installCommand?.let { "<br/><br/>Install command:<br/><code>${escapeHtml(it)}</code>" }.orEmpty() +
                        "<br/><br/>AgentellIJ will only run this command if you click Install." +
                        "</center></html>"
                ),
                BorderLayout.CENTER
            )
            add(JPanel(FlowLayout(FlowLayout.CENTER, 8, 0)).apply {
                if (installCommand != null) {
                    add(JButton("Install").apply {
                        addActionListener {
                            isEnabled = false
                            AgentCliInstaller.installWithUserConsent(
                                project = project,
                                profile = profile,
                                onSuccess = retryAction,
                                onFailure = { isEnabled = true }
                            )
                        }
                    })
                }
                add(JButton("Open Settings").apply {
                    addActionListener {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, AgentellIJConfigurable::class.java)
                    }
                })
                add(JButton("Retry").apply { addActionListener { retryAction() } })
            }, BorderLayout.SOUTH)
        }, BorderLayout.CENTER)
        mainPanel.revalidate()
        mainPanel.repaint()
    }
}
