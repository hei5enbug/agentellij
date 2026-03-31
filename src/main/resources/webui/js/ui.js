import { renderMarkdown } from './markdown.js';

export class ChatUI {
  constructor(container) {
    this.container = container;
    this.messagesList = container.querySelector('#messages-list');
    this.emptyState = container.querySelector('#empty-state');
    this.sessionDropdown = container.querySelector('#session-dropdown');
    this.sessionTrigger = container.querySelector('#session-trigger');
    this.sessionPanel = container.querySelector('#session-panel');
    this.sessionList = container.querySelector('#session-list');
    this.sessionBulkBar = container.querySelector('#session-bulk-bar');
    this.sessionBulkDelete = container.querySelector('#session-bulk-delete');
    this.btnNewSession = container.querySelector('#btn-new-session');
    this.btnSend = container.querySelector('#btn-send');
    this.btnAbort = container.querySelector('#btn-abort');
    this.promptInput = container.querySelector('#prompt-input');
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
      } else if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        document.execCommand('insertLineBreak');
      }
    });

    this.promptInput.addEventListener('input', () => {
      this.promptInput.style.height = 'auto';
      const lineHeight = parseFloat(getComputedStyle(this.promptInput).lineHeight) || 20;
      const maxHeight = lineHeight * 10;
      const newHeight = Math.min(this.promptInput.scrollHeight, maxHeight);
      this.promptInput.style.height = newHeight + 'px';
      this.promptInput.style.overflowY = this.promptInput.scrollHeight > maxHeight ? 'auto' : 'hidden';
    });

    this.btnSend.addEventListener('click', () => this._callbacks.onSend?.());
    this.btnAbort.addEventListener('click', () => this._callbacks.onAbort?.());
    this.btnNewSession.addEventListener('click', () => this._callbacks.onNewSession?.());
    this.sessionTrigger.addEventListener('click', (e) => {
      e.stopPropagation();
      this.sessionDropdown.classList.toggle('open');
    });

    this.sessionList.addEventListener('click', (e) => {
      const checkbox = e.target.closest('.session-item-check');
      if (checkbox) {
        e.stopPropagation();
        this._updateBulkBar();
        return;
      }

      const deleteBtn = e.target.closest('.session-item-delete');
      if (deleteBtn) {
        e.stopPropagation();
        const sessionId = deleteBtn.closest('.session-item').dataset.sessionId;
        this._callbacks.onDeleteSession?.(sessionId);
        return;
      }

      const item = e.target.closest('.session-item');
      if (item) {
        e.stopPropagation();
        const sessionId = item.dataset.sessionId;
        this._callbacks.onSessionSwitch?.(sessionId);
        this.sessionDropdown.classList.remove('open');
      }
    });

    this.sessionBulkDelete.addEventListener('click', (e) => {
      e.stopPropagation();
      const ids = this._getCheckedSessionIds();
      if (ids.length > 0) {
        this._callbacks.onBulkDeleteSessions?.(ids);
      }
    });
    this._initDropdown(this.modelDropdown, (val) => this._callbacks.onModelChange?.(val));
    this._initDropdown(this.variantDropdown, (val) => this._callbacks.onVariantChange?.(val));
    this._initDropdown(this.agentDropdown, (val) => this._callbacks.onAgentChange?.(val));

    document.addEventListener('click', (e) => {
      this.container.querySelectorAll('.dropdown.open').forEach((dd) => {
        if (!dd.contains(e.target)) dd.classList.remove('open');
      });
      if (this.sessionDropdown && !this.sessionDropdown.contains(e.target)) {
        this.sessionDropdown.classList.remove('open');
      }
    });

    this.messagesList.addEventListener('click', (e) => {
      const copyBtn = e.target.closest('.btn-copy');
      if (copyBtn) {
        const pre = copyBtn.closest('.code-header')?.nextElementSibling;
        const code = pre?.querySelector('code');
        if (code) {
          navigator.clipboard.writeText(code.textContent).then(() => {
            copyBtn.textContent = 'Copied!';
            setTimeout(() => { copyBtn.textContent = 'Copy'; }, 2000);
          }).catch(() => {});
        }
        return;
      }

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
  onDeleteSession(cb) { this._callbacks.onDeleteSession = cb; }
  onBulkDeleteSessions(cb) { this._callbacks.onBulkDeleteSessions = cb; }
  onModelChange(cb)   { this._callbacks.onModelChange = cb; }
  onVariantChange(cb) { this._callbacks.onVariantChange = cb; }
  onAgentChange(cb)   { this._callbacks.onAgentChange = cb; }

  renderSessionList(sessions, currentId) {
    this.sessionList.innerHTML = '';
    this.sessionBulkBar.classList.add('hidden');

    if (!sessions || sessions.length === 0) {
      this.sessionTrigger.textContent = 'No sessions';
      this.sessionList.innerHTML = '<div class="session-empty">No sessions</div>';
      return;
    }
    const current = sessions.find(s => s.id === currentId);
    this.sessionTrigger.textContent = current ? (current.title || current.id.substring(0, 8)) : sessions[0].title || sessions[0].id.substring(0, 8);

    sessions.forEach((s) => {
      const item = document.createElement('div');
      item.className = 'session-item' + (s.id === currentId ? ' active' : '');
      item.dataset.sessionId = s.id;
      item.innerHTML = `
          <input type="checkbox" class="session-item-check" title="Select for deletion">
          <span class="session-item-title">${this._escapeHtml(s.title || s.id.substring(0, 8))}</span>
          <button class="session-item-delete" title="Delete session">&times;</button>
      `;
      this.sessionList.appendChild(item);
    });
  }

  _getCheckedSessionIds() {
    return [...this.sessionList.querySelectorAll('.session-item-check:checked')]
      .map(cb => cb.closest('.session-item').dataset.sessionId);
  }

  _updateBulkBar() {
    const count = this._getCheckedSessionIds().length;
    if (count > 0) {
      this.sessionBulkBar.classList.remove('hidden');
      this.sessionBulkDelete.textContent = `Delete (${count})`;
    } else {
      this.sessionBulkBar.classList.add('hidden');
    }
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

    if (!card._rawText) card._rawText = '';
    card._rawText += delta;

    if (!card._renderPending) {
      card._renderPending = true;
      requestAnimationFrame(() => {
        card._renderPending = false;
        const content = card.querySelector('.message-content');
        if (content && card._rawText) {
          content.innerHTML = renderMarkdown(card._rawText);
          content.classList.add('streaming-cursor');
        }
        if (!this._userScrolledUp) this.scrollToBottom();
      });
    }
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

  insertChipAtCursor(path) {
    const chip = document.createElement('span');
    chip.className = 'context-chip';
    chip.contentEditable = 'false';
    chip.dataset.path = path;
    chip.title = path;
    const name = path.split('/').pop();
    chip.innerHTML = `${this._escapeHtml(name)}<span class="context-chip-remove">&times;</span>`;
    chip.querySelector('.context-chip-remove').addEventListener('click', () => chip.remove());

    const sel = window.getSelection();
    if (sel.rangeCount > 0 && this.promptInput.contains(sel.anchorNode)) {
      const range = sel.getRangeAt(0);
      range.deleteContents();
      range.insertNode(chip);
      const space = document.createTextNode('\u00A0');
      chip.after(space);
      range.setStartAfter(space);
      range.setEndAfter(space);
      sel.removeAllRanges();
      sel.addRange(range);
    } else {
      this.promptInput.appendChild(chip);
      const space = document.createTextNode('\u00A0');
      this.promptInput.appendChild(space);
    }
  }

  updateContextUsage(current, max) {
    const el = this.container.querySelector('#context-usage');
    if (!el) return;

    if (!current && !max) {
      el.textContent = '— / —';
      el.className = 'context-usage';
      el.title = 'Context window usage';
      return;
    }

    const fmt = (n) => {
      if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
      if (n >= 1000) return Math.round(n / 1000) + 'k';
      return n.toString();
    };

    if (max > 0) {
      const pct = Math.round((current / max) * 100);
      el.textContent = `${fmt(current)} / ${fmt(max)}`;
      el.title = `Context: ${current.toLocaleString()} / ${max.toLocaleString()} tokens (${pct}%)`;
      el.className = 'context-usage' + (pct > 90 ? ' danger' : pct > 75 ? ' warning' : '');
    } else {
      el.textContent = fmt(current);
      el.title = `${current.toLocaleString()} tokens used`;
      el.className = 'context-usage';
    }
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

  getInputText() {
    let text = '';
    const walk = (node) => {
      if (node.nodeType === Node.TEXT_NODE) {
        text += node.textContent;
      } else if (node.classList?.contains('context-chip')) {
        text += `@${node.dataset.path}`;
      } else if (node.tagName === 'BR') {
        text += '\n';
      } else {
        node.childNodes.forEach(walk);
        if (node.tagName === 'DIV' || node.tagName === 'P') text += '\n';
      }
    };
    this.promptInput.childNodes.forEach(walk);
    return text;
  }

  clearInput() {
    this.promptInput.innerHTML = '';
    this.promptInput.style.height = 'auto';
  }

  focusInput() { this.promptInput.focus(); }

  setInputEnabled(enabled) {
    this.promptInput.contentEditable = enabled ? 'true' : 'false';
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
