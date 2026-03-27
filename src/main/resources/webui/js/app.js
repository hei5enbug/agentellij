import { OpenCodeApi } from './api.js';
import { IdeBridgeClient } from './ide-bridge.js';
import { ChatUI } from './ui.js';

const state = {
  sessions: [],
  currentSessionId: null,
  messages: new Map(),
  isStreaming: false,

  openFiles: [],
  currentFile: null,
  providers: [],
  allModels: [],
  agents: [],
  selectedModel: null,
  selectedVariant: '',
  selectedAgent: '',
  currentModelVariants: [],
};

let api, bridge, ui;

async function init() {
  const params = new URLSearchParams(window.location.search);
  const opencodeApiUrl = params.get('opencodeApi');
  const ideBridgeUrl = params.get('ideBridge');
  const ideBridgeToken = params.get('ideBridgeToken');

  if (!opencodeApiUrl) {
    return;
  }

  api = new OpenCodeApi(opencodeApiUrl);
  ui = new ChatUI(document.getElementById('app'));

  if (ideBridgeUrl && ideBridgeToken) {
    bridge = new IdeBridgeClient(ideBridgeUrl, ideBridgeToken);
  }

  wireUICallbacks();
  connectStreams();

  try {
    await loadSessions();
    await loadConfig();
  } catch (e) {
    ui.showError('Failed to connect to OpenCode. Is it running?');
  }
}

function wireUICallbacks() {
  ui.onSend(handleSend);
  ui.onAbort(handleAbort);
  ui.onNewSession(handleNewSession);
  ui.onSessionSwitch(handleSessionSwitch);
  ui.onFileClick(handleFileClick);
  ui.onModelChange(handleModelChange);
  ui.onVariantChange(handleVariantChange);
  ui.onAgentChange(handleAgentChange);

}

function connectStreams() {
  api.connectEvents({
    onConnected: () => {
      ui.showConnectionStatus('connected');
    },
    onMessageDelta: (sessionId, messageId, partId, delta) => {
      if (sessionId !== state.currentSessionId) return;
      state.isStreaming = true;
      ui.setStreaming(true);
      ui.appendStreamDelta(messageId, partId, delta);
    },
    onMessagePartUpdated: (sessionId, messageId, part) => {
      if (sessionId !== state.currentSessionId) return;
      const status = part?.state || part?.status || 'running';
      ui.updateToolCallStatus(messageId, part?.id, status);
    },
    onMessageUpdated: (sessionId, messageId, message) => {
      if (sessionId !== state.currentSessionId) return;
      if (message?.role !== 'assistant') return;
      if (!message?.time?.completed) return;
      state.isStreaming = false;
      ui.setStreaming(false);
      ui.finalizeMessage(messageId, message);
      ui.focusInput();
    },
    onSessionUpdated: (sessionId, session) => {
      const s = state.sessions.find((s) => s.id === sessionId);
      if (s && session?.title) {
        s.title = session.title;
        ui.renderSessionList(state.sessions, state.currentSessionId);
      }
    },
    onError: () => {
      ui.showConnectionStatus('connecting');
    },
  });

  if (bridge) {
    try {
      bridge.connect({
        onConnected: () => {},
        onInsertPaths: (paths) => {
          paths.forEach((p) => ui.insertChipAtCursor(p));
          ui.focusInput();
        },
        onUpdateOpenedFiles: (openedFiles, currentFile) => {
          state.openFiles = openedFiles;
          state.currentFile = currentFile;
        },
      });
    } catch (_) {}
  }

  ui.showConnectionStatus('connecting');
}

async function handleSend() {
  const text = ui.getInputText().trim();
  if (!text || state.isStreaming) return;

  if (!state.currentSessionId) {
    await handleNewSession();
    if (!state.currentSessionId) return;
  }

  const userMsg = { id: `local_${Date.now()}`, role: 'user', content: text };
  ui.renderMessages([...(state.messages.get(state.currentSessionId) || []), userMsg]);

  ui.clearInput();
  ui.setStreaming(true);
  state.isStreaming = true;

  try {
    const parts = [{ type: 'text', text }];
    const config = {};
    if (state.selectedModel) config.model = state.selectedModel;
    if (state.selectedVariant) config.variant = state.selectedVariant;
    if (state.selectedAgent) config.agent = state.selectedAgent;
    await api.sendPromptWithConfig(state.currentSessionId, parts, config);

    const messages = await api.getSessionMessages(state.currentSessionId);
    state.messages.set(state.currentSessionId, messages || []);
    ui.renderMessages(messages || []);

    state.isStreaming = false;
    ui.setStreaming(false);
    ui.focusInput();
  } catch (e) {
    ui.showError('Failed to send message: ' + e.message);
    ui.setStreaming(false);
    state.isStreaming = false;
  }
}

async function handleAbort() {
  if (!state.isStreaming) return;
  try {
    await api.abortPrompt(state.currentSessionId);
  } catch (_) {}
  state.isStreaming = false;
  ui.setStreaming(false);
}

async function handleNewSession() {
  try {
    const session = await api.createSession();
    state.sessions.unshift(session);
    state.currentSessionId = session.id;
    state.messages.set(session.id, []);
    ui.renderSessionList(state.sessions, state.currentSessionId);
    ui.renderMessages([]);
    ui.focusInput();
  } catch (e) {
    ui.showError('Failed to create session');
  }
}

async function handleSessionSwitch(sessionId) {
  if (sessionId === state.currentSessionId) return;
  state.currentSessionId = sessionId;

  if (state.messages.has(sessionId)) {
    ui.renderMessages(state.messages.get(sessionId));
  } else {
    try {
      const messages = await api.getSessionMessages(sessionId);
      state.messages.set(sessionId, messages || []);
      ui.renderMessages(messages || []);
    } catch (e) {
      if (e.message?.includes('404')) {
        state.sessions = state.sessions.filter((s) => s.id !== sessionId);
        ui.renderSessionList(state.sessions, null);
        ui.showError('Session not found. It may have been deleted.');
        state.currentSessionId = null;
      } else {
        ui.renderMessages([]);
      }
    }
  }
  ui.focusInput();
}

function handleFileClick(path, line) {
  if (bridge) bridge.openFile(path, line);
}

async function loadSessions() {
  try {
    await api.health();
  } catch (_) {
    ui.showError('Cannot connect to OpenCode. Make sure "opencode serve" is running.');
    ui.showConnectionStatus('error');
    setTimeout(loadSessions, 5000);
    return;
  }

  const sessions = await api.listSessions();
  state.sessions = Array.isArray(sessions) ? sessions : [];

  if (state.sessions.length > 0) {
    ui.renderSessionList(state.sessions, state.sessions[0].id);
    await handleSessionSwitch(state.sessions[0].id);
  } else {
    ui.renderSessionList([], null);
  }
}

async function loadConfig() {
  try {
    const [providerData, agents, config] = await Promise.all([
      api.getProviders(),
      api.getAgents(),
      api.getConfig().catch(() => null),
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
      ui.renderAgentList(primaryAgents, state.selectedAgent);
      applyAgentDefaults(agentObj, models);
    }
  } catch (_) {}
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

document.addEventListener('DOMContentLoaded', init);
