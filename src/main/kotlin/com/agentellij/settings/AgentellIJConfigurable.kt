package com.agentellij.settings

import com.agentellij.backend.AgentProfileResolver
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JTextField

class AgentellIJConfigurable : Configurable {
    private var agentComboBox: ComboBox<String>? = null
    private var modeComboBox: ComboBox<String>? = null
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var customArgsField: JTextField? = null

    override fun getDisplayName(): String = "AgentellIJ"

    override fun createComponent(): JComponent {
        val agentLabels = AgentProfileResolver.allProfiles().map { it.displayName }.toTypedArray()
        agentComboBox = ComboBox(agentLabels)
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
            .addLabeledComponent("Agent:", agentComboBox!!)
            .addTooltip("Select which AI agent to use")
            .addLabeledComponent("Mode:", modeComboBox!!)
            .addTooltip("Choose between the embedded web UI and terminal wrapper modes (availability depends on agent)")
            .addLabeledComponent("Agent binary path:", agentPathField!!)
            .addTooltip("Leave empty to auto-detect from PATH or common install locations")
            .addLabeledComponent("Additional arguments:", customArgsField!!)
            .addTooltip("Extra arguments appended after the agent binary (space-separated)")
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = AgentellIJSettings.getInstance()
        val selectedAgentId = getSelectedAgentId()
        return selectedAgentId != settings.getActiveAgent() ||
                getSelectedMode() != settings.getMode() ||
                agentPathField?.text != settings.getAgentPath(selectedAgentId) ||
                customArgsField?.text != settings.state.customArgs
    }

    override fun apply() {
        val settings = AgentellIJSettings.getInstance()
        val selectedAgentId = getSelectedAgentId()
        settings.state.activeAgent = selectedAgentId
        settings.state.mode = AgentellIJSettings.normalizeMode(getSelectedMode())
        settings.setAgentPath(selectedAgentId, agentPathField?.text?.trim() ?: "")
        settings.state.customArgs = customArgsField?.text?.trim() ?: ""
    }

    override fun reset() {
        val settings = AgentellIJSettings.getInstance()
        val profile = AgentProfileResolver.resolve()
        agentComboBox?.selectedItem = profile.displayName
        modeComboBox?.selectedItem = modeToLabel(settings.getMode())
        agentPathField?.text = settings.getAgentPath(profile.id)
        customArgsField?.text = settings.state.customArgs
    }

    override fun disposeUIResources() {
        agentComboBox = null
        modeComboBox = null
        agentPathField = null
        customArgsField = null
    }

    private fun getSelectedAgentId(): String {
        val selectedLabel = agentComboBox?.selectedItem as? String ?: return "opencode"
        return AgentProfileResolver.allProfiles()
            .find { it.displayName == selectedLabel }?.id ?: "opencode"
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
        private const val TUI_MODE_LABEL = "TUI mode — terminal wrapper"
    }
}
