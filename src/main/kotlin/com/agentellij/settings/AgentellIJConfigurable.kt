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
    private var agentComboBox: ComboBox<AgentOption>? = null
    private var modeComboBox: ComboBox<String>? = null
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var customArgsField: JTextField? = null

    override fun getDisplayName(): String = "AgentellIJ"

    override fun createComponent(): JComponent {
        val agentOptions = AgentProfileResolver.allProfiles()
            .map { AgentOption(it.id, it.displayName) }
            .toTypedArray()
        agentComboBox = ComboBox(agentOptions)
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
            .addTooltip("Extra arguments appended after the agent binary; quotes and escaped spaces are preserved")
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        val settings = AgentellIJSettings.getInstance()
        val selectedAgentId = getSelectedAgentId()
        val selectedProfile = AgentModePolicy.resolveProfile(selectedAgentId, AgentProfileResolver.allProfiles())
        val selectedMode = AgentModePolicy.normalizeModeForProfile(getSelectedMode(), selectedProfile)
        return selectedAgentId != settings.getActiveAgent() ||
                selectedMode != settings.getMode() ||
                agentPathField?.text != settings.getAgentPath(selectedAgentId) ||
                customArgsField?.text != settings.state.customArgs
    }

    override fun apply() {
        val settings = AgentellIJSettings.getInstance()
        val selectedAgentId = getSelectedAgentId()
        val selectedProfile = AgentModePolicy.resolveProfile(selectedAgentId, AgentProfileResolver.allProfiles())
        settings.state.activeAgent = selectedAgentId
        settings.state.mode = AgentModePolicy.normalizeModeForProfile(getSelectedMode(), selectedProfile)
        settings.setAgentPath(selectedAgentId, agentPathField?.text?.trim() ?: "")
        settings.state.customArgs = customArgsField?.text?.trim() ?: ""
    }

    override fun reset() {
        val settings = AgentellIJSettings.getInstance()
        val profile = AgentProfileResolver.resolve()
        agentComboBox?.selectedItem = AgentOption(profile.id, profile.displayName)
        modeComboBox?.selectedItem = modeToLabel(AgentModePolicy.normalizeModeForProfile(settings.getMode(), profile))
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
        val selectedOption = agentComboBox?.selectedItem as? AgentOption
        return selectedOption?.id ?: AgentModePolicy.DEFAULT_AGENT_ID
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

    private data class AgentOption(val id: String, val label: String) {
        override fun toString(): String = label
    }
}
