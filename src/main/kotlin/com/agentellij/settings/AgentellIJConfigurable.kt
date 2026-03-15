package com.agentellij.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JTextField

class AgentellIJConfigurable : Configurable {
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var customArgsField: JTextField? = null

    override fun getDisplayName(): String = "AgentellIJ"

    override fun createComponent(): JComponent {
        agentPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
                    .withTitle("Select Agent Binary")
                    .withDescription("Path to the agent executable")
            )
        }
        customArgsField = JTextField()

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("Agent binary path:", agentPathField!!)
            .addTooltip("Leave empty to use 'opencode' from PATH. For other agents, specify the full path.")
            .addLabeledComponent("Additional arguments:", customArgsField!!)
            .addTooltip("Extra arguments appended after the agent binary (space-separated)")
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = AgentellIJSettings.getInstance()
        return agentPathField?.text != settings.state.agentPath ||
                customArgsField?.text != settings.state.customArgs
    }

    override fun apply() {
        val settings = AgentellIJSettings.getInstance()
        settings.state.agentPath = agentPathField?.text?.trim() ?: ""
        settings.state.customArgs = customArgsField?.text?.trim() ?: ""
    }

    override fun reset() {
        val settings = AgentellIJSettings.getInstance()
        agentPathField?.text = settings.state.agentPath
        customArgsField?.text = settings.state.customArgs
    }

    override fun disposeUIResources() {
        agentPathField = null
        customArgsField = null
    }
}
