export class OpenCodeApi {
  constructor(baseUrl) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.eventSource = null;
    this._reconnectTimer = null;
    this._reconnectDelay = 1000;
    this._maxReconnectDelay = 30000;
    this._handlers = null;
  }

  async _fetch(path, options = {}) {
    const url = `${this.baseUrl}${path}`;
    const res = await fetch(url, {
      headers: { 'Content-Type': 'application/json', ...options.headers },
      ...options,
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`API ${options.method || 'GET'} ${path}: ${res.status} ${body}`);
    }
    const ct = res.headers.get('content-type') || '';
    return ct.includes('application/json') ? res.json() : null;
  }

  async _post(path, body) {
    return this._fetch(path, {
      method: 'POST',
      body: body != null ? JSON.stringify(body) : undefined,
    });
  }

  async health() {
    return this._fetch('/global/health');
  }

  async createSession(opts = {}) {
    return this._post('/session', opts);
  }

  async listSessions() {
    return this._fetch('/session');
  }

  async getSessionMessages(sessionId) {
    return this._fetch(`/session/${sessionId}/message`);
  }

  async sendPrompt(sessionId, parts) {
    return this._post(`/session/${sessionId}/message`, { parts });
  }

  async sendPromptWithConfig(sessionId, parts, { model, variant, agent } = {}) {
    const body = { parts };
    if (model) body.model = model;
    if (variant) body.variant = variant;
    if (agent) body.agent = agent;
    return this._post(`/session/${sessionId}/message`, body);
  }

  async abortPrompt(sessionId) {
    return this._post(`/session/${sessionId}/abort`, {});
  }

  async getProviders() {
    return this._fetch('/provider');
  }

  async getAgents() {
    return this._fetch('/agent');
  }

  async getConfig() {
    return this._fetch('/config');
  }

  connectEvents(handlers) {
    this._handlers = handlers;
    this._reconnectDelay = 1000;
    this._doConnect();
  }

  _doConnect() {
    if (this.eventSource) this.eventSource.close();

    this.eventSource = new EventSource(`${this.baseUrl}/global/event`);

    this.eventSource.onopen = () => {
      this._reconnectDelay = 1000;
      this._handlers?.onConnected?.();
    };

    this.eventSource.onmessage = (event) => {
      try {
        this._dispatchEvent(JSON.parse(event.data));
      } catch (e) {
        console.warn('SSE parse error:', e, event.data);
      }
    };

    this.eventSource.onerror = (err) => {
      this.eventSource.close();
      this._handlers?.onError?.(err);
      this._scheduleReconnect();
    };
  }

  _scheduleReconnect() {
    if (this._reconnectTimer) return;
    this._reconnectTimer = setTimeout(() => {
      this._reconnectTimer = null;
      this._reconnectDelay = Math.min(this._reconnectDelay * 2, this._maxReconnectDelay);
      this._doConnect();
    }, this._reconnectDelay);
  }

  _dispatchEvent(data) {
    const h = this._handlers;
    if (!h) return;

    const payload = data?.payload || data;
    const type = payload?.type;
    const props = payload?.properties || {};

    switch (type) {
      case 'message.part.delta':
        h.onMessageDelta?.(props.sessionID, props.messageID, props.partID, props.delta);
        break;
      case 'message.part.updated':
        h.onMessagePartUpdated?.(props.sessionID, props.messageID, props.part);
        break;
      case 'message.updated': {
        const info = props.info || {};
        h.onMessageUpdated?.(info.sessionID, info.id, info);
        break;
      }
      case 'session.updated': {
        const info = props.info || {};
        h.onSessionUpdated?.(info.id, info);
        break;
      }
      case 'server.heartbeat':
      case 'server.connected':
      case 'session.status':
      case 'session.diff':
      case 'session.idle':
        break;
      default:
        console.debug('Unknown SSE event:', type);
    }
  }

  disconnectEvents() {
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }
    this._handlers = null;
  }
}
