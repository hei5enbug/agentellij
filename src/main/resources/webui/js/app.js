import { OpenCodeApi } from './core/api.js';
import { IdeBridgeClient } from './core/ide-bridge.js';
import { ChatUI } from './ui/chat-ui.js';
import { state, trimMessageCache } from './core/state.js';
import { createSessionController } from './features/session-controller.js';
import { createConfigController } from './features/config-controller.js';

let api, bridge, ui;
let sessionCtrl, configCtrl;

async function init() {
  const params = new URLSearchParams(window.location.search);
  const opencodeApiUrl = params.get('opencodeApi');
  const ideBridgeUrl = params.get('ideBridge');
  const ideBridgeToken = params.get('ideBridgeToken');
  const agentName = params.get('agentName') || 'Agent';
  if (!opencodeApiUrl) return;
  api = new OpenCodeApi(opencodeApiUrl);
  ui = new ChatUI(document.getElementById('app'), { agentName });
  if (ideBridgeUrl && ideBridgeToken) bridge = new IdeBridgeClient(ideBridgeUrl, ideBridgeToken);
  configCtrl = createConfigController({ api, ui });
  sessionCtrl = createSessionController({ api, ui, refreshContextUsage: configCtrl.refreshContextUsage });
  wireUICallbacks();
  connectStreams();
  try {
    await sessionCtrl.loadSessions();
    await configCtrl.loadConfig();
  } catch (_) {
    ui.showError('Failed to connect to OpenCode. Is it running?');
  }
}

function wireUICallbacks() {
  ui.onSend(handleSend);
  ui.onAbort(handleAbort);
  ui.onNewSession(() => sessionCtrl.handleNewSession());
  ui.onSessionSwitch((id) => sessionCtrl.handleSessionSwitch(id));
  ui.onDeleteSession((id) => sessionCtrl.handleDeleteSession(id));
  ui.onBulkDeleteSessions((ids) => sessionCtrl.handleBulkDeleteSessions(ids));
  ui.onFileClick(handleFileClick);
  ui.onModelChange((val) => configCtrl.handleModelChange(val));
  ui.onVariantChange((val) => configCtrl.handleVariantChange(val));
  ui.onAgentChange((val) => configCtrl.handleAgentChange(val));
}

function connectStreams() {
  api.connectEvents({
    onConnected: () => ui.showConnectionStatus('connected'),
    onMessageDelta: (sessionId, messageId, partId, delta) => { if (sessionId !== state.currentSessionId) return; state.isStreaming = true; ui.setStreaming(true); ui.appendStreamDelta(messageId, partId, delta); },
    onMessagePartUpdated: (sessionId, messageId, part) => { if (sessionId !== state.currentSessionId) return; ui.updateToolCallStatus(messageId, part?.id, part?.state || part?.status || 'running', part); },
    onMessageUpdated: (sessionId, messageId, message) => {
      if (sessionId !== state.currentSessionId || message?.role !== 'assistant') return;
      if (message?.agent) ui.setAgentName(message.agent);
      if (!message?.time?.completed) return;
      ui.finalizeMessage(messageId, message);
      api.getSessionMessages(sessionId).then((messages) => {
        state.messages.set(sessionId, messages || []);
        configCtrl.refreshContextUsage(messages || []);
        trimMessageCache();
      }).catch(() => {});
    },
    onSessionUpdated: (sessionId, session) => {
      const current = state.sessions.find((s) => s.id === sessionId);
      if (!current || !session?.title) return;
      current.title = session.title;
      ui.renderSessionList(state.sessions, state.currentSessionId);
    },
    onSessionDeleted: (sessionId) => {
      state.sessions = state.sessions.filter((s) => s.id !== sessionId);
      state.messages.delete(sessionId);
      if (state.currentSessionId === sessionId) {
        if (state.sessions.length > 0) {
          sessionCtrl.handleSessionSwitch(state.sessions[0].id);
        } else {
          state.currentSessionId = null;
          ui.renderMessages([]);
          configCtrl.refreshContextUsage([]);
        }
      }
      ui.renderSessionList(state.sessions, state.currentSessionId);
    },
    onSessionStatus: (sessionId, status) => {
      if (sessionId !== state.currentSessionId) return;
      if (status?.type === 'busy') { state.isStreaming = true; ui.setStreaming(true); }
      else if (status?.type === 'idle') { state.isStreaming = false; ui.setStreaming(false); ui.focusInput(); }
    },
    onSessionIdle: (sessionId) => { if (sessionId !== state.currentSessionId) return; state.isStreaming = false; ui.setStreaming(false); ui.focusInput(); },
    onError: () => ui.showConnectionStatus('connecting'),
  });

  if (bridge) {
    try {
      bridge.connect({
        onConnected: () => {},
        onInsertPaths: (paths) => { paths.forEach((p) => { ui.insertChipAtCursor(p); }); ui.focusInput(); },
        onUpdateOpenedFiles: (openedFiles, currentFile) => { state.openFiles = openedFiles; state.currentFile = currentFile; },
      });
    } catch (_) {}
  }

  ui.showConnectionStatus('connecting');
}

async function handleSend() {
  const text = ui.getInputText().trim();
  if (!text || state.isStreaming) return;
  if (!state.currentSessionId) { await sessionCtrl.handleNewSession(); if (!state.currentSessionId) return; }
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
  } catch (e) {
    ui.showError(`Failed to send message: ${e.message}`);
    state.isStreaming = false;
    ui.setStreaming(false);
  }
}

async function handleAbort() {
  if (!state.isStreaming) return;
  try { await api.abortPrompt(state.currentSessionId); } catch (_) {}
  state.isStreaming = false;
  ui.setStreaming(false);
}

function handleFileClick(path, line) { if (bridge) bridge.openFile(path, line).catch(() => {}); }

window.addEventListener('beforeunload', () => {
  api?.disconnectEvents();
  bridge?.disconnect();
});

document.addEventListener('DOMContentLoaded', init);
