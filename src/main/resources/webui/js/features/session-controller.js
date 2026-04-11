import { removeSessionFromState, state, trimMessageCache } from '../core/state.js';

export function createSessionController({ api, ui, refreshContextUsage }) {
  async function handleNewSession() {
    try {
      const session = await api.createSession();
      state.sessions.unshift(session);
      state.currentSessionId = session.id;
      state.messages.set(session.id, []);
      ui.renderSessionList(state.sessions, state.currentSessionId);
      ui.renderMessages([]);
      ui.focusInput();
    } catch (_) {
      ui.showError('Failed to create session');
    }
  }

  async function handleSessionSwitch(sessionId) {
    if (sessionId === state.currentSessionId) return;
    state.currentSessionId = sessionId;

    if (state.isStreaming) {
      state.isStreaming = false;
      ui.setStreaming(false);
    }

    if (state.messages.has(sessionId)) {
      ui.renderMessages(state.messages.get(sessionId));
      refreshContextUsage(state.messages.get(sessionId));
    } else {
      try {
        const messages = await api.getSessionMessages(sessionId);
        state.messages.set(sessionId, messages || []);
        ui.renderMessages(messages || []);
        refreshContextUsage(messages || []);
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
    trimMessageCache();
    ui.focusInput();
  }

  async function handleDeleteSession(sessionId) {
    if (!confirm('Delete this session?')) return;
    try {
      await api.deleteSession(sessionId);
      removeSessionFromState(sessionId);
      await switchAfterDelete();
      ui.renderSessionList(state.sessions, state.currentSessionId);
    } catch (_) {
      ui.showError('Failed to delete session');
    }
  }

  async function handleBulkDeleteSessions(sessionIds) {
    if (!sessionIds || sessionIds.length === 0) return;
    const count = sessionIds.length;
    if (!confirm(`Delete ${count} session${count > 1 ? 's' : ''}?`)) return;

    let failed = 0;
    for (const id of sessionIds) {
      try {
        await api.deleteSession(id);
        removeSessionFromState(id);
      } catch (_) {
        failed++;
      }
    }
    await switchAfterDelete();
    ui.renderSessionList(state.sessions, state.currentSessionId);
    if (failed > 0) ui.showError(`Failed to delete ${failed} session(s)`);
  }

  async function switchAfterDelete() {
    if (state.currentSessionId && !state.sessions.find(s => s.id === state.currentSessionId)) {
      if (state.sessions.length > 0) {
        await handleSessionSwitch(state.sessions[0].id);
      } else {
        state.currentSessionId = null;
        ui.renderMessages([]);
        refreshContextUsage([]);
      }
    }
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

  return {
    handleNewSession,
    handleSessionSwitch,
    handleDeleteSession,
    handleBulkDeleteSessions,
    loadSessions,
  };
}
