package com.agentellij.settings

import com.agentellij.backend.AgentProfile
import com.agentellij.backend.AgentProfileResolver
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JTextField

class AgentellIJConfigurable : Configurable {
    private var agentComboBox: ComboBox<AgentOption>? = null
    private var modeComboBox: ComboBox<String>? = null
    private var agentPathField: TextFieldWithBrowseButton? = null
    private var customArgsField: JTextField? = null
    private var agentPathSelectionState: AgentPathSelectionState? = null

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

        agentComboBox?.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                val selectedOption = event.item as? AgentOption ?: return@addItemListener
                val nextPath = agentPathSelectionState?.selectAgent(
                    agentId = selectedOption.id,
                    currentPath = agentPathField?.text.orEmpty()
                ) ?: AgentellIJSettings.getInstance().getAgentPath(selectedOption.id)
                agentPathField?.text = nextPath
            }
        }

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
        val agentPaths = agentPathSelectionState?.snapshot(
            selectedAgentId = selectedAgentId,
            currentPath = agentPathField?.text.orEmpty()
        ) ?: mapOf(selectedAgentId to agentPathField?.text.orEmpty())
        return selectedAgentId != settings.getActiveAgent() ||
                selectedMode != settings.getMode() ||
                agentPaths.any { (agentId, path) -> path != settings.getAgentPath(agentId) } ||
                customArgsField?.text != settings.state.customArgs
    }

    override fun apply() {
        val settings = AgentellIJSettings.getInstance()
        val selectedAgentId = getSelectedAgentId()
        val selectedProfile = AgentModePolicy.resolveProfile(selectedAgentId, AgentProfileResolver.allProfiles())
        settings.state.activeAgent = selectedAgentId
        settings.state.mode = AgentModePolicy.normalizeModeForProfile(getSelectedMode(), selectedProfile)
        val agentPaths = agentPathSelectionState?.snapshot(
            selectedAgentId = selectedAgentId,
            currentPath = agentPathField?.text.orEmpty()
        ) ?: mapOf(selectedAgentId to agentPathField?.text.orEmpty())
        agentPaths.forEach { (agentId, path) -> settings.setAgentPath(agentId, path.trim()) }
        settings.state.customArgs = customArgsField?.text?.trim() ?: ""
    }

    override fun reset() {
        val settings = AgentellIJSettings.getInstance()
        val profile = AgentProfileResolver.resolve()
        agentPathSelectionState = AgentPathSelectionState(
            profiles = AgentProfileResolver.allProfiles(),
            selectedAgentId = profile.id,
            pathProvider = settings::getAgentPath
        )
        agentComboBox?.selectedItem = AgentOption(profile.id, profile.displayName)
        modeComboBox?.selectedItem = modeToLabel(AgentModePolicy.normalizeModeForProfile(settings.getMode(), profile))
        agentPathField?.text = agentPathSelectionState?.currentPath().orEmpty()
        customArgsField?.text = settings.state.customArgs
    }

    override fun disposeUIResources() {
        agentComboBox = null
        modeComboBox = null
        agentPathField = null
        customArgsField = null
        agentPathSelectionState = null
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

internal class AgentPathSelectionState(
    profiles: List<AgentProfile>,
    selectedAgentId: String,
    pathProvider: (String) -> String
) {
    private val pathsByAgentId = profiles.associate { profile -> profile.id to pathProvider(profile.id) }.toMutableMap()
    private var currentAgentId = selectedAgentId

    fun currentPath(): String = pathsByAgentId[currentAgentId].orEmpty()

    fun selectAgent(agentId: String, currentPath: String): String {
        pathsByAgentId[currentAgentId] = currentPath
        currentAgentId = agentId
        return pathsByAgentId[agentId].orEmpty()
    }

    fun snapshot(selectedAgentId: String, currentPath: String): Map<String, String> {
        pathsByAgentId[selectedAgentId] = currentPath
        currentAgentId = selectedAgentId
        return pathsByAgentId.toMap()
    }
}
