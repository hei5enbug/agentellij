export const state = {
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

export const MAX_CACHED_SESSIONS = 20;

export function trimMessageCache() {
  if (state.messages.size <= MAX_CACHED_SESSIONS) return;
  for (const [id] of state.messages) {
    if (id === state.currentSessionId) continue;
    state.messages.delete(id);
    if (state.messages.size <= MAX_CACHED_SESSIONS) break;
  }
}

export function removeSessionFromState(sessionId) {
  state.sessions = state.sessions.filter(s => s.id !== sessionId);
  state.messages.delete(sessionId);
}
