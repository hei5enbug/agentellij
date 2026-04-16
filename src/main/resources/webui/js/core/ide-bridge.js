export class IdeBridgeClient {
  constructor(baseUrl, token) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.token = token;
    this.eventSource = null;
    this._handlers = null;
    this._msgId = 0;
    this._reconnectTimer = null;
    this._reconnectDelay = 2000;
    this._maxReconnectDelay = 30000;
  }

  connect(handlers) {
    this._handlers = handlers;
    this._reconnectDelay = 2000;
    this._doConnect();
  }

  _doConnect() {
    if (this.eventSource) this.eventSource.close();

    const url = `${this.baseUrl}/events?token=${encodeURIComponent(this.token)}`;
    this.eventSource = new EventSource(url);

    this.eventSource.addEventListener('connected', () => {
      this._reconnectDelay = 2000;
      this._handlers?.onConnected?.();
    });

    this.eventSource.addEventListener('message', (event) => {
      try {
        this._dispatch(JSON.parse(event.data));
      } catch (e) {
        console.warn('IdeBridge SSE parse error:', e);
      }
    });

    this.eventSource.onerror = () => {
      this.eventSource.close();
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

  _dispatch(data) {
    const h = this._handlers;
    if (!h) return;

    const type = data?.type;
    const payload = data?.payload || {};

    switch (type) {
      case 'insertPaths':
        h.onInsertPaths?.(payload.paths || []);
        break;
      case 'updateOpenedFiles':
        h.onUpdateOpenedFiles?.(payload.openedFiles || [], payload.currentFile);
        break;
    }
  }

  disconnect() {
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

  async _send(type, payload = {}) {
    const id = `msg_${++this._msgId}`;
    await fetch(`${this.baseUrl}/send?token=${encodeURIComponent(this.token)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ type, id, payload }),
    });
  }

  async openFile(path, line) {
    const payload = { path };
    if (line != null) payload.line = line;
    return this._send('openFile', payload);
  }

  async openUrl(url) {
    return this._send('openUrl', { url });
  }

  async reloadPath(path) {
    return this._send('reloadPath', { path });
  }
}
