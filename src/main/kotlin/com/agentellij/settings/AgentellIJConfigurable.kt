package com.agentellij.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JTextField

class AgentellIJConfigurable : Configurable {
    private var modeComboBox: ComboBox<String>? = null
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var customArgsField: JTextField? = null

    override fun getDisplayName(): String = "AgentellIJ"

    override fun createComponent(): JComponent {
        modeComboBox = ComboBox(arrayOf(GUI_MODE_LABEL, TUI_MODE_LABEL))
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
            .addLabeledComponent("Mode:", modeComboBox!!)
            .addTooltip("Choose between the embedded web UI and terminal wrapper modes")
            .addLabeledComponent("Agent binary path:", agentPathField!!)
            .addTooltip("Leave empty to auto-detect 'opencode' from PATH or common install locations. For other agents, specify the full path.")
            .addLabeledComponent("Additional arguments:", customArgsField!!)
            .addTooltip("Extra arguments appended after the agent binary (space-separated)")
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = AgentellIJSettings.getInstance()
        return getSelectedMode() != settings.getMode() ||
                agentPathField?.text != settings.state.agentPath ||
                customArgsField?.text != settings.state.customArgs
    }

    override fun apply() {
        val settings = AgentellIJSettings.getInstance()
        settings.state.mode = AgentellIJSettings.normalizeMode(getSelectedMode())
        settings.state.agentPath = agentPathField?.text?.trim() ?: ""
        settings.state.customArgs = customArgsField?.text?.trim() ?: ""
    }

    override fun reset() {
        val settings = AgentellIJSettings.getInstance()
        modeComboBox?.selectedItem = modeToLabel(settings.getMode())
        agentPathField?.text = settings.state.agentPath
        customArgsField?.text = settings.state.customArgs
    }

    override fun disposeUIResources() {
        modeComboBox = null
        agentPathField = null
        customArgsField = null
    }

    private fun getSelectedMode(): String =
        when (modeComboBox?.selectedItem as? String) {
            TUI_MODE_LABEL -> "tui"
            else -> "gui"
        }

    private fun modeToLabel(mode: String): String =
        when (AgentellIJSettings.normalizeMode(mode)) {
            "tui" -> TUI_MODE_LABEL
            else -> GUI_MODE_LABEL
        }

    companion object {
        private const val GUI_MODE_LABEL = "GUI mode — embedded web UI"
        private const val TUI_MODE_LABEL = "TUI mode — terminal wrapper for opencode"
    }
}
