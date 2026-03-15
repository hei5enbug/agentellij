import { renderMarkdown } from './markdown.js';

export class ChatUI {
  constructor(container) {
    this.container = container;
    this.messagesList = container.querySelector('#messages-list');
    this.emptyState = container.querySelector('#empty-state');
    this.sessionSelect = container.querySelector('#session-select');
    this.btnNewSession = container.querySelector('#btn-new-session');
    this.btnSend = container.querySelector('#btn-send');
    this.btnAbort = container.querySelector('#btn-abort');
    this.promptInput = container.querySelector('#prompt-input');
    this.contextBar = container.querySelector('#context-bar');
    this.contextFiles = container.querySelector('#context-files');
    this.statusDot = container.querySelector('#connection-status');
    this.messagesContainer = container.querySelector('#messages');
    this.modelDropdown = container.querySelector('#model-dropdown');
    this.variantDropdown = container.querySelector('#variant-dropdown');
    this.agentDropdown = container.querySelector('#agent-dropdown');

    this._callbacks = {};
    this._userScrolledUp = false;

    this._setupInputHandlers();
    this._setupScrollDetection();
  }

  _setupInputHandlers() {
    this.promptInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
        e.preventDefault();
        this._callbacks.onSend?.();
      }
    });

    this.promptInput.addEventListener('input', () => {
      this.promptInput.style.height = 'auto';
      const lineHeight = parseFloat(getComputedStyle(this.promptInput).lineHeight) || 20;
      const maxHeight = lineHeight * 20;
      this.promptInput.style.height = Math.min(this.promptInput.scrollHeight, maxHeight) + 'px';
    });

    this.btnSend.addEventListener('click', () => this._callbacks.onSend?.());
    this.btnAbort.addEventListener('click', () => this._callbacks.onAbort?.());
    this.btnNewSession.addEventListener('click', () => this._callbacks.onNewSession?.());
    this.sessionSelect.addEventListener('change', (e) => {
      this._callbacks.onSessionSwitch?.(e.target.value);
    });
    this._initDropdown(this.modelDropdown, (val) => this._callbacks.onModelChange?.(val));
    this._initDropdown(this.variantDropdown, (val) => this._callbacks.onVariantChange?.(val));
    this._initDropdown(this.agentDropdown, (val) => this._callbacks.onAgentChange?.(val));

    document.addEventListener('click', (e) => {
      container.querySelectorAll('.dropdown.open').forEach((dd) => {
        if (!dd.contains(e.target)) dd.classList.remove('open');
      });
    });

    this.messagesList.addEventListener('click', (e) => {
      const fileLink = e.target.closest('.file-link');
      if (fileLink) {
        e.preventDefault();
        const path = fileLink.dataset.path;
        const line = fileLink.dataset.line ? parseInt(fileLink.dataset.line) : undefined;
        this._callbacks.onFileClick?.(path, line);
      }
    });
  }

  _setupScrollDetection() {
    this.messagesContainer.addEventListener('scroll', () => {
      const { scrollTop, scrollHeight, clientHeight } = this.messagesContainer;
      this._userScrolledUp = (scrollHeight - scrollTop - clientHeight) > 50;
    });
  }

  onSend(cb)          { this._callbacks.onSend = cb; }
  onAbort(cb)         { this._callbacks.onAbort = cb; }
  onNewSession(cb)    { this._callbacks.onNewSession = cb; }
  onSessionSwitch(cb) { this._callbacks.onSessionSwitch = cb; }
  onFileClick(cb)     { this._callbacks.onFileClick = cb; }
  onModelChange(cb)   { this._callbacks.onModelChange = cb; }
  onVariantChange(cb) { this._callbacks.onVariantChange = cb; }
  onAgentChange(cb)   { this._callbacks.onAgentChange = cb; }

  renderSessionList(sessions, currentId) {
    this.sessionSelect.innerHTML = '';
    if (!sessions || sessions.length === 0) {
      this.sessionSelect.innerHTML = '<option value="">No sessions</option>';
      return;
    }
    sessions.forEach((s) => {
      const opt = document.createElement('option');
      opt.value = s.id;
      opt.textContent = s.title || s.id.substring(0, 8);
      if (s.id === currentId) opt.selected = true;
      this.sessionSelect.appendChild(opt);
    });
  }

  _initDropdown(dropdown, onChange) {
    const trigger = dropdown.querySelector('.dropdown-trigger');
    const searchInput = dropdown.querySelector('.dropdown-search');

    trigger.addEventListener('click', (e) => {
      e.stopPropagation();
      const wasOpen = dropdown.classList.contains('open');
      this.container.querySelectorAll('.dropdown.open').forEach((dd) => dd.classList.remove('open'));
      if (!wasOpen) {
        dropdown.classList.add('open');
        if (searchInput) {
          searchInput.value = '';
          this._filterDropdownItems(dropdown, '');
          setTimeout(() => searchInput.focus(), 0);
        }
      }
    });

    if (searchInput) {
      searchInput.addEventListener('input', () => {
        this._filterDropdownItems(dropdown, searchInput.value);
      });
      searchInput.addEventListener('click', (e) => e.stopPropagation());
      searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') dropdown.classList.remove('open');
      });
    }

    dropdown._onChange = onChange;
  }

  _filterDropdownItems(dropdown, query) {
    const q = query.toLowerCase().trim();
    const items = dropdown.querySelectorAll('.dropdown-item');
    const groups = dropdown.querySelectorAll('.dropdown-group-label');
    const seps = dropdown.querySelectorAll('.dropdown-separator');

    items.forEach((item) => {
      const text = (item.textContent || '').toLowerCase();
      const value = (item.dataset.value || '').toLowerCase();
      item.style.display = (!q || text.includes(q) || value.includes(q)) ? '' : 'none';
    });

    groups.forEach((g) => {
      let next = g.nextElementSibling;
      let hasVisible = false;
      while (next && !next.classList.contains('dropdown-group-label') && !next.classList.contains('dropdown-separator')) {
        if (next.classList.contains('dropdown-item') && next.style.display !== 'none') hasVisible = true;
        next = next.nextElementSibling;
      }
      g.style.display = hasVisible ? '' : 'none';
    });

    seps.forEach((s) => { s.style.display = q ? 'none' : ''; });
  }

  _renderDropdown(dropdown, items, currentValue) {
    const menu = dropdown.querySelector('.dropdown-menu');
    const trigger = dropdown.querySelector('.dropdown-trigger');
    menu.innerHTML = '';

    let selectedLabel = dropdown.dataset.label || '';
    items.forEach((item) => {
      const el = document.createElement('div');
      el.className = 'dropdown-item' + (item.value === currentValue ? ' selected' : '') + (item.disabled ? ' disabled' : '');
      el.dataset.value = item.value;
      el.textContent = item.label;
      if (item.value === currentValue) selectedLabel = item.label;
      el.addEventListener('click', () => {
        if (item.disabled) return;
        menu.querySelectorAll('.dropdown-item').forEach((i) => i.classList.remove('selected'));
        el.classList.add('selected');
        trigger.textContent = item.label;
        dropdown.classList.remove('open');
        dropdown._onChange?.(item.value);
      });
      menu.appendChild(el);
    });
    trigger.textContent = selectedLabel;
  }

  renderModelList(models, currentValue) {
    const groups = {};
    models.forEach((m) => {
      const key = m.providerName || m.providerID;
      if (!groups[key]) groups[key] = [];
      groups[key].push(m);
    });

    const itemsContainer = this.modelDropdown.querySelector('.dropdown-items');
    const trigger = this.modelDropdown.querySelector('.dropdown-trigger');
    itemsContainer.innerHTML = '';

    let selectedLabel = 'Model';

    for (const [providerName, providerModels] of Object.entries(groups)) {
      const groupLabel = document.createElement('div');
      groupLabel.className = 'dropdown-group-label';
      groupLabel.textContent = providerName;
      itemsContainer.appendChild(groupLabel);

      providerModels.forEach((m) => {
        const value = `${m.providerID}/${m.modelID}`;
        const el = document.createElement('div');
        el.className = 'dropdown-item' + (value === currentValue ? ' selected' : '');
        el.dataset.value = value;
        el.textContent = m.name || m.modelID;
        if (value === currentValue) selectedLabel = el.textContent;
        el.addEventListener('click', () => {
          itemsContainer.querySelectorAll('.dropdown-item').forEach((i) => i.classList.remove('selected'));
          el.classList.add('selected');
          trigger.textContent = el.textContent;
          this.modelDropdown.classList.remove('open');
          this.modelDropdown._onChange?.(value);
        });
        itemsContainer.appendChild(el);
      });
    }

    trigger.textContent = selectedLabel;
  }

  renderVariantList(variants, currentValue) {
    const items = (!variants || variants.length === 0)
      ? [{ value: '', label: 'Default' }]
      : variants.map((v) => ({ value: v, label: v }));
    this._renderDropdown(this.variantDropdown, items, currentValue);
  }

  renderAgentList(agents, currentValue) {
    const items = agents.map((a) => ({ value: a.name, label: a.name }));
    this._renderDropdown(this.agentDropdown, items, currentValue);
  }

  renderMessages(messages) {
    this.messagesList.innerHTML = '';
    if (!messages || messages.length === 0) {
      this.emptyState.classList.remove('hidden');
      return;
    }
    this.emptyState.classList.add('hidden');
    messages.forEach((msg) => this._appendMessageCard(msg));
    this.scrollToBottom();
  }

  _appendMessageCard(msg) {
    const info = msg.info || msg;
    const msgRole = info.role || 'assistant';
    const msgId = info.id || msg.id || '';
    const parts = msg.parts || [];

    const card = document.createElement('div');
    card.className = `message ${msgRole}`;
    card.dataset.messageId = msgId;

    const roleEl = document.createElement('div');
    roleEl.className = 'message-role';
    roleEl.textContent = msgRole === 'user' ? 'You' : 'Assistant';
    card.appendChild(roleEl);

    const content = document.createElement('div');
    content.className = 'message-content';

    for (const part of parts) {
      if (part.type === 'text' && part.text) {
        content.innerHTML += renderMarkdown(part.text);
      } else if (part.type === 'tool-invocation' || part.type === 'tool_use' || part.type === 'tool') {
        content.appendChild(this._createToolCallCard(part));
      }
    }

    if (content.innerHTML === '' && msg.content) {
      content.innerHTML = renderMarkdown(msg.content);
    }

    card.appendChild(content);
    this.messagesList.appendChild(card);
  }

  _createToolCallCard(part) {
    const card = document.createElement('div');
    const status = part.state || part.status || 'pending';
    card.className = `tool-call ${status}`;
    card.dataset.partId = part.id || '';

    const statusIcon = { pending: '\u23F3', running: '\uD83D\uDD04', completed: '\u2705', error: '\u274C' };

    card.innerHTML = `
      <div class="tool-call-header">
        <span class="tool-call-status">${statusIcon[status] || '\u23F3'}</span>
        <span class="tool-call-name">${this._escapeHtml(part.toolName || part.name || 'tool')}</span>
      </div>
    `;
    return card;
  }

  renderAssistantMessage(messageId, parts) {
    this.emptyState.classList.add('hidden');

    let card = this.messagesList.querySelector(`[data-message-id="${messageId}"]`);
    if (!card) {
      card = document.createElement('div');
      card.className = 'message assistant';
      card.dataset.messageId = messageId;

      const role = document.createElement('div');
      role.className = 'message-role';
      role.textContent = 'Assistant';
      card.appendChild(role);

      const content = document.createElement('div');
      content.className = 'message-content';
      card.appendChild(content);

      this.messagesList.appendChild(card);
    }

    const content = card.querySelector('.message-content');
    content.classList.remove('streaming-cursor');
    content.innerHTML = '';

    for (const part of parts) {
      if (part.type === 'text' && part.text) {
        content.innerHTML += renderMarkdown(part.text);
      } else if (part.type === 'tool-invocation' || part.type === 'tool_use') {
        content.appendChild(this._createToolCallCard(part));
      }
    }

    card._rawText = null;
    if (!this._userScrolledUp) this.scrollToBottom();
  }

  appendStreamDelta(messageId, partId, delta) {
    this.emptyState.classList.add('hidden');

    let card = this.messagesList.querySelector(`[data-message-id="${messageId}"]`);
    if (!card) {
      card = document.createElement('div');
      card.className = 'message assistant';
      card.dataset.messageId = messageId;

      const role = document.createElement('div');
      role.className = 'message-role';
      role.textContent = 'Assistant';
      card.appendChild(role);

      const content = document.createElement('div');
      content.className = 'message-content streaming-cursor';
      card.appendChild(content);

      this.messagesList.appendChild(card);
    }

    const content = card.querySelector('.message-content');
    if (!card._rawText) card._rawText = '';
    card._rawText += delta;
    content.innerHTML = renderMarkdown(card._rawText);
    content.classList.add('streaming-cursor');

    if (!this._userScrolledUp) this.scrollToBottom();
  }

  finalizeMessage(messageId, message) {
    const card = this.messagesList.querySelector(`[data-message-id="${messageId}"]`);
    if (card) {
      const content = card.querySelector('.message-content');
      content.classList.remove('streaming-cursor');
      if (message?.parts) {
        content.innerHTML = '';
        message.parts.forEach((part) => {
          if (part.type === 'text') {
            content.innerHTML += renderMarkdown(part.text || '');
          } else if (part.type === 'tool-invocation' || part.type === 'tool_use') {
            content.appendChild(this._createToolCallCard(part));
          }
        });
      }
      card._rawText = null;
    }
    if (!this._userScrolledUp) this.scrollToBottom();
  }

  updateToolCallStatus(messageId, partId, status) {
    const card = this.messagesList.querySelector(`[data-message-id="${messageId}"]`);
    if (!card) return;
    const toolCard = card.querySelector(`[data-part-id="${partId}"]`);
    if (!toolCard) return;

    const statusIcon = { pending: '\u23F3', running: '\uD83D\uDD04', completed: '\u2705', error: '\u274C' };
    toolCard.className = `tool-call ${status}`;
    const statusEl = toolCard.querySelector('.tool-call-status');
    if (statusEl) statusEl.textContent = statusIcon[status] || '\u23F3';
  }

  showContextFiles(files) {
    if (!files || files.length === 0) {
      this.contextBar.classList.add('hidden');
      return;
    }
    this.contextBar.classList.remove('hidden');
    this.contextFiles.innerHTML = '';
    files.forEach((f) => {
      const el = document.createElement('span');
      el.className = 'context-file';
      const name = f.split('/').pop();
      el.innerHTML = `${this._escapeHtml(name)} <span class="context-file-remove" data-path="${this._escapeHtml(f)}">\u00D7</span>`;
      el.querySelector('.context-file-remove').addEventListener('click', () => {
        this._callbacks.onRemoveContextFile?.(f);
      });
      this.contextFiles.appendChild(el);
    });
  }

  showConnectionStatus(status) {
    this.statusDot.className = `status-dot ${status}`;
    this.statusDot.title = status === 'connected' ? 'Connected' :
                           status === 'connecting' ? 'Connecting...' : 'Disconnected';
  }

  showError(message) {
    const existing = this.messagesContainer.querySelector('.error-banner');
    if (existing) existing.remove();

    const banner = document.createElement('div');
    banner.className = 'error-banner';
    banner.textContent = message;
    this.messagesContainer.insertBefore(banner, this.messagesContainer.firstChild);
    setTimeout(() => banner.remove(), 10000);
  }

  scrollToBottom() {
    requestAnimationFrame(() => {
      this.messagesContainer.scrollTop = this.messagesContainer.scrollHeight;
    });
  }

  getInputText()       { return this.promptInput.value; }
  clearInput()         { this.promptInput.value = ''; this.promptInput.style.height = 'auto'; }
  focusInput()         { this.promptInput.focus(); }

  setInputEnabled(enabled) {
    this.promptInput.disabled = !enabled;
    this.btnSend.disabled = !enabled;
  }

  setStreaming(streaming) {
    this.btnSend.classList.toggle('hidden', streaming);
    this.btnAbort.classList.toggle('hidden', !streaming);
    this.setInputEnabled(!streaming);
  }

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
}
