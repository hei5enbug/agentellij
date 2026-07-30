package com.agentellij.platform.config

import com.agentellij.core.agent.AgentCatalog as CatalogAgentProfileResolver
import com.agentellij.core.settings.ActiveAgentSelector as CoreAgentProfileResolver
import com.agentellij.core.settings.AgentModePolicy
import com.agentellij.core.settings.AgentPathEditState
import com.agentellij.core.settings.SettingsFormPolicy
import com.agentellij.core.settings.SettingsFormValues
import com.agentellij.platform.config.AgentellIJSettings
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
    private var agentPathEditState: AgentPathEditState? = null

    override fun getDisplayName(): String = "AgentellIJ"

    override fun createComponent(): JComponent {
        val agentOptions = CatalogAgentProfileResolver.allProfiles()
            .map { AgentOption(it.id, it.displayName) }
            .toTypedArray()
        agentComboBox = ComboBox(agentOptions)
        modeComboBox = ComboBox(arrayOf(SettingsFormPolicy.GUI_MODE_LABEL, SettingsFormPolicy.TUI_MODE_LABEL))
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
                onAgentSelected(selectedOption.id)
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

    override fun isModified(): Boolean =
        SettingsFormPolicy.isModified(shown = shownValues(), stored = storedValues())

    override fun apply() {
        val settings = AgentellIJSettings.getInstance()
        val shown = shownValues()
        val profile = AgentModePolicy.resolveProfile(shown.agentId, CatalogAgentProfileResolver.allProfiles())
        val toSave = SettingsFormPolicy.normalizeForSave(shown, profile)

        settings.state.activeAgent = toSave.agentId
        settings.state.mode = toSave.mode
        toSave.agentPaths.forEach { (agentId, path) -> settings.setAgentPath(agentId, path) }
        settings.state.customArgs = toSave.customArgs
    }

    override fun reset() {
        val settings = AgentellIJSettings.getInstance()
        val activeAgentId = settings.getActiveAgent()
        val profile = CoreAgentProfileResolver.resolveProfile(
            activeAgentId = activeAgentId,
            settingsPath = settings.getAgentPath(activeAgentId),
            agentellijBin = System.getenv("AGENTELLIJ_BIN"),
            profiles = CatalogAgentProfileResolver.allProfiles()
        )
        agentPathEditState = AgentPathEditState.initial(
            agentIds = agentIds(),
            selectedAgentId = profile.id,
            pathProvider = settings::getAgentPath
        )
        agentComboBox?.selectedItem = AgentOption(profile.id, profile.displayName)
        modeComboBox?.selectedItem =
            SettingsFormPolicy.labelForMode(AgentModePolicy.normalizeModeForProfile(settings.getMode(), profile))
        agentPathField?.text = agentPathEditState?.currentPath().orEmpty()
        applyAgentPathFieldState(profile.id)
        customArgsField?.text = settings.state.customArgs
    }

    override fun disposeUIResources() {
        agentComboBox = null
        modeComboBox = null
        agentPathField = null
        customArgsField = null
        agentPathEditState = null
    }

    private fun onAgentSelected(agentId: String) {
        val result = agentPathEditState?.selectAgent(agentId, agentPathField?.text.orEmpty())
        if (result != null) {
            agentPathEditState = result.state
            agentPathField?.text = result.pathToShow
        } else {
            agentPathField?.text = AgentellIJSettings.getInstance().getAgentPath(agentId)
        }
        applyAgentPathFieldState(agentId)
    }

    private fun applyAgentPathFieldState(agentId: String) {
        val profile = AgentModePolicy.resolveProfile(agentId, CatalogAgentProfileResolver.allProfiles())
        val enabled = SettingsFormPolicy.pathFieldEnabled(profile)
        if (!enabled) agentPathField?.text = ""
        agentPathField?.isEnabled = enabled
    }

    /**
     * Reads the panel. Taking a snapshot also parks the text currently in the field, so
     * the edit state stays in step with what the user sees.
     */
    private fun shownValues(): SettingsFormValues {
        val selectedAgentId = selectedAgentId()
        val profile = AgentModePolicy.resolveProfile(selectedAgentId, CatalogAgentProfileResolver.allProfiles())
        val currentPath = agentPathField?.text.orEmpty()
        val snapshot = agentPathEditState?.snapshot(selectedAgentId, currentPath)
        if (snapshot != null) agentPathEditState = snapshot.state

        return SettingsFormValues(
            agentId = selectedAgentId,
            mode = AgentModePolicy.normalizeModeForProfile(selectedMode(), profile),
            agentPaths = snapshot?.paths ?: mapOf(selectedAgentId to currentPath),
            customArgs = customArgsField?.text.orEmpty()
        )
    }

    private fun storedValues(): SettingsFormValues {
        val settings = AgentellIJSettings.getInstance()
        return SettingsFormValues(
            agentId = settings.getActiveAgent(),
            mode = settings.getMode(),
            agentPaths = agentIds().associateWith(settings::getAgentPath),
            customArgs = settings.state.customArgs
        )
    }

    private fun agentIds(): List<String> = CatalogAgentProfileResolver.allProfiles().map { it.id }

    private fun selectedAgentId(): String =
        (agentComboBox?.selectedItem as? AgentOption)?.id ?: AgentModePolicy.DEFAULT_AGENT_ID

    private fun selectedMode(): String = SettingsFormPolicy.modeFromLabel(modeComboBox?.selectedItem as? String)

    private data class AgentOption(val id: String, val label: String) {
        override fun toString(): String = label
    }
}
