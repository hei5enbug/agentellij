export class IdeBridgeClient {
  constructor(baseUrl, token) {
    this.baseUrl = baseUrl.replace(/\/$/, '');
    this.token = token;
    this.eventSource = null;
    this._handlers = null;
    this._msgId = 0;
  }

  connect(handlers) {
    this._handlers = handlers;

    const url = `${this.baseUrl}/events?token=${encodeURIComponent(this.token)}`;
    this.eventSource = new EventSource(url);

    this.eventSource.addEventListener('connected', () => {
      handlers.onConnected?.();
    });

    this.eventSource.addEventListener('message', (event) => {
      try {
        this._dispatch(JSON.parse(event.data));
      } catch (e) {
        console.warn('IdeBridge SSE parse error:', e);
      }
    });

    this.eventSource.onerror = () => {};
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
