import {
  initDropdown as _initDropdown,
  renderAgentList as _renderAgentList,
  renderModelList as _renderModelList,
  renderVariantList as _renderVariantList
} from './dropdowns.js';
import {
  clearInput as _clearInput,
  focusInput as _focusInput,
  getInputText as _getInputText,
  insertChipAtCursor as _insertChipAtCursor,
  setInputEnabled as _setInputEnabled,
  setStreaming as _setStreaming,
  setupInputHandlers
} from './input.js';
import {
  appendStreamDelta as _appendStreamDelta,
  finalizeMessage as _finalizeMessage,
  renderAssistantMessage as _renderAssistantMessage,
  renderMessages as _renderMessages,
  updateToolCallStatus as _updateToolCallStatus
} from './message-renderer.js';
import {
  getCheckedSessionIds as _getCheckedSessionIds,
  renderSessionList as _renderSessionList,
  updateBulkBar as _updateBulkBar
} from './session-list.js';
import { setSlashCommands as _setSlashCommands } from './slash-commands.js';

export class ChatUI {
  constructor(container, options = {}) {
    this.container = container;
    this.agentName = options.agentName || 'Agent';
    this.profileName = options.agentName || 'Agent';
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

    this.slashPopup = container.querySelector('#slash-popup');
    this.slashPopupList = container.querySelector('#slash-popup-list');

    this._callbacks = {};
    this._userScrolledUp = false;
    this._slashCommands = [];
    this._slashFiltered = [];
    this._slashActiveIndex = 0;
    this._slashVisible = false;

    setupInputHandlers(this);
    this._setupScrollDetection();
  }

  _setupScrollDetection() {
    this.messagesContainer.addEventListener('scroll', () => {
      const { scrollTop, scrollHeight, clientHeight } = this.messagesContainer;
      this._userScrolledUp = (scrollHeight - scrollTop - clientHeight) > 50;
    });
  }

  onSend(cb) { this._callbacks.onSend = cb; }
  onAbort(cb) { this._callbacks.onAbort = cb; }
  onNewSession(cb) { this._callbacks.onNewSession = cb; }
  onSessionSwitch(cb) { this._callbacks.onSessionSwitch = cb; }
  onFileClick(cb) { this._callbacks.onFileClick = cb; }
  onDeleteSession(cb) { this._callbacks.onDeleteSession = cb; }
  onBulkDeleteSessions(cb) { this._callbacks.onBulkDeleteSessions = cb; }
  onModelChange(cb) { this._callbacks.onModelChange = cb; }
  onVariantChange(cb) { this._callbacks.onVariantChange = cb; }
  onAgentChange(cb) { this._callbacks.onAgentChange = cb; }

  renderSessionList(sessions, currentId) { _renderSessionList(this, sessions, currentId); }
  _getCheckedSessionIds() { return _getCheckedSessionIds(this); }
  _updateBulkBar() { _updateBulkBar(this); }
  _initDropdown(dropdown, onChange) { _initDropdown(this, dropdown, onChange); }
  renderModelList(models, currentValue) { _renderModelList(this, models, currentValue); }
  renderVariantList(variants, currentValue) { _renderVariantList(this, variants, currentValue); }
  renderAgentList(agents, currentValue) { _renderAgentList(this, agents, currentValue); }
  renderMessages(messages) { _renderMessages(this, messages); }
  renderAssistantMessage(messageId, parts) { _renderAssistantMessage(this, messageId, parts); }
  appendStreamDelta(messageId, partId, delta) { _appendStreamDelta(this, messageId, partId, delta); }
  finalizeMessage(messageId, message) { _finalizeMessage(this, messageId, message); }
  updateToolCallStatus(messageId, partId, status, part) { _updateToolCallStatus(this, messageId, partId, status, part); }
  insertChipAtCursor(path) { _insertChipAtCursor(this, path); }
  getInputText() { return _getInputText(this); }
  clearInput() { _clearInput(this); }
  focusInput() { _focusInput(this); }
  setInputEnabled(enabled) { _setInputEnabled(this, enabled); }
  setStreaming(streaming) { _setStreaming(this, streaming); }
  setSlashCommands(commands) { _setSlashCommands(this, commands); }

  setAgentName(name) {
    this.agentName = name || this.profileName;
  }

  updateContextUsage(current, max) {
    const el = this.container.querySelector('#context-usage');
    const bar = this.container.querySelector('#context-bar');
    const fill = bar?.querySelector('.context-bar-fill');
    if (!el) return;

    if (!current && !max) {
      el.textContent = '— / —';
      el.className = 'context-usage';
      el.title = 'Context window usage';
      if (fill) fill.style.width = '0%';
      if (bar) bar.className = 'context-bar';
      return;
    }

    const fmt = (n) => {
      if (n >= 1000000) return `${(n / 1000000).toFixed(1)}M`;
      if (n >= 1000) return `${Math.round(n / 1000)}k`;
      return n.toString();
    };

    if (max > 0) {
      const pct = Math.round((current / max) * 100);
      el.textContent = `${fmt(current)} / ${fmt(max)}`;
      el.title = `Context: ${current.toLocaleString()} / ${max.toLocaleString()} tokens (${pct}%)`;
      const level = pct > 90 ? ' danger' : pct > 75 ? ' warning' : '';
      el.className = `context-usage${level}`;
      if (fill) fill.style.width = `${Math.min(pct, 100)}%`;
      if (bar) bar.className = `context-bar${level}`;
    } else {
      el.textContent = fmt(current);
      el.title = `${current.toLocaleString()} tokens used`;
      el.className = 'context-usage';
      if (fill) fill.style.width = '0%';
      if (bar) bar.className = 'context-bar';
    }
  }

  showConnectionStatus(status) {
    this.statusDot.className = `status-dot ${status}`;
    this.statusDot.title = status === 'connected' ? 'Connected'
      : status === 'connecting' ? 'Connecting...'
        : 'Disconnected';
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

  _escapeHtml(str) {
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }
}
