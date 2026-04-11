import { state } from '../core/state.js';

export function createConfigController({ api, ui }) {
  async function loadConfig() {
    try {
      const [providerData, agents, config, commands] = await Promise.all([
        api.getProviders(),
        api.getAgents(),
        api.getConfig().catch(() => null),
        api.getCommands().catch(() => []),
      ]);

      const models = [];
      const connected = new Set(providerData?.connected || []);
      for (const provider of (providerData?.all || [])) {
        if (!connected.has(provider.id)) continue;
        for (const [modelId, model] of Object.entries(provider.models || {})) {
          models.push({
            providerID: provider.id,
            providerName: provider.name || provider.id,
            modelID: modelId,
            name: model.name || modelId,
            variants: model.variants ? Object.keys(model.variants) : [],
            contextLimit: model.limit?.context || 0,
          });
        }
      }
      state.allModels = models;
      state.agents = Array.isArray(agents) ? agents : [];

      if (models.length > 0) {
        ui.renderModelList(models, '');
        ui.renderVariantList([], '');
      }

      const defaultAgent = config?.default_agent || 'build';
      const primaryAgents = state.agents.filter((a) => a.mode !== 'subagent');
      if (primaryAgents.length > 0) {
        const agentObj = primaryAgents.find((a) => a.name === defaultAgent) || primaryAgents[0];
        state.selectedAgent = agentObj.name;
        ui.setAgentName(agentObj.name);
        ui.renderAgentList(primaryAgents, state.selectedAgent);
        applyAgentDefaults(agentObj, models);
      }

      const slashCommands = (Array.isArray(commands) ? commands : []).map((cmd) => ({
        name: `/${cmd.name}`,
        description: cmd.description || '',
      }));
      ui.setSlashCommands(slashCommands);
    } catch (e) {
      console.warn('Failed to load config:', e);
    }
  }

  function handleModelChange(value) {
    const [providerID, ...rest] = value.split('/');
    const modelID = rest.join('/');
    state.selectedModel = { providerID, modelID };

    const model = state.allModels.find((m) => m.providerID === providerID && m.modelID === modelID);
    state.currentModelVariants = model?.variants || [];
    state.selectedVariant = state.currentModelVariants[0] || '';
    ui.renderVariantList(state.currentModelVariants, state.selectedVariant);
  }

  function handleVariantChange(value) {
    state.selectedVariant = value;
  }

  function handleAgentChange(value) {
    state.selectedAgent = value;
    ui.setAgentName(value);
    const agentObj = state.agents.find((a) => a.name === value);
    if (agentObj) applyAgentDefaults(agentObj, state.allModels);
  }

  function applyAgentDefaults(agentObj, models) {
    const agentModel = agentObj.model;
    if (!agentModel?.providerID || !agentModel?.modelID) return;

    const modelKey = `${agentModel.providerID}/${agentModel.modelID}`;
    const match = models.find((m) => `${m.providerID}/${m.modelID}` === modelKey);

    if (match) {
      state.selectedModel = { providerID: match.providerID, modelID: match.modelID };
      state.currentModelVariants = match.variants;
      state.selectedVariant = agentObj.variant || match.variants[0] || '';
      ui.renderModelList(models, modelKey);
      ui.renderVariantList(match.variants, state.selectedVariant);
    } else {
      state.selectedModel = { providerID: agentModel.providerID, modelID: agentModel.modelID };
      state.selectedVariant = agentObj.variant || '';
      ui.renderVariantList(agentObj.variant ? [agentObj.variant] : [], state.selectedVariant);
    }
  }

  function refreshContextUsage(messages) {
    if (!messages || messages.length === 0) {
      ui.updateContextUsage(0, 0);
      return;
    }

    const lastAssistant = [...messages].reverse().find((m) => {
      const info = m.info || m;
      return info.role === 'assistant' && info.tokens;
    });

    if (!lastAssistant) {
      ui.updateContextUsage(0, 0);
      return;
    }

    const info = lastAssistant.info || lastAssistant;
    const tokens = info.tokens || {};
    const cache = tokens.cache || {};
    const total =
      (tokens.input || 0) +
      (tokens.output || 0) +
      (tokens.reasoning || 0) +
      (cache.read || 0) +
      (cache.write || 0);

    const model = state.allModels.find(
      (m) => m.providerID === info.providerID && m.modelID === info.modelID,
    );
    const limit = model?.contextLimit || 0;

    ui.updateContextUsage(total, limit);
  }

  return {
    loadConfig,
    handleModelChange,
    handleVariantChange,
    handleAgentChange,
    refreshContextUsage,
  };
}
