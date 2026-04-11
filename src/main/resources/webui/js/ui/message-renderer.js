import { renderMarkdown } from '../core/markdown.js';

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

function appendMessageCard(ui, msg) {
  const info = msg.info || msg;
  const msgRole = info.role || 'assistant';
  const msgId = info.id || msg.id || '';
  const parts = msg.parts || [];

  const card = document.createElement('div');
  card.className = `message ${msgRole}`;
  card.dataset.messageId = msgId;

  const roleEl = document.createElement('div');
  roleEl.className = 'message-role';
  roleEl.textContent = msgRole === 'user' ? 'You' : `AgentelliJ - ${ui.agentName}`;
  card.appendChild(roleEl);

  const content = document.createElement('div');
  content.className = 'message-content';

  for (const part of parts) {
    const el = renderPart(ui, part);
    if (el) content.appendChild(el);
  }

  if (content.children.length === 0 && content.innerHTML === '' && msg.content) {
    content.innerHTML = renderMarkdown(msg.content);
  }

  card.appendChild(content);
  ui.messagesList.appendChild(card);
}

function renderPart(ui, part) {
  if (!part) return null;
  const type = part.type || '';

  const hidden = new Set(['step-start', 'step-finish', 'step_start', 'step_finish']);
  if (hidden.has(type)) return null;

  if (type === 'text' && part.text) {
    const span = document.createElement('span');
    span.innerHTML = renderMarkdown(part.text);
    return span;
  }

  if (type === 'tool-invocation' || type === 'tool_use' || type === 'tool') {
    return createToolCallCard(ui, part);
  }

  if (type === 'thinking' || type === 'reasoning') {
    return createThinkingCard(part);
  }

  if (type === 'tool_result' || type === 'tool-result') {
    return createToolResultCard(part);
  }

  if (type === 'file') {
    return createFilePart(part);
  }

  if (type === 'subtask') {
    return createSubtaskPart(part);
  }

  if (type === 'patch') {
    return createPatchPart(part);
  }

  if (type === 'snapshot') {
    return null;
  }

  if (type) {
    return createGenericPartCard(part);
  }

  return null;
}

function toolHint(part) {
  const input = part.input || part.args || part.arguments;
  if (!input) return '';
  const obj = typeof input === 'string' ? (() => { try { return JSON.parse(input); } catch { return null; } })() : input;
  if (!obj || typeof obj !== 'object') return '';
  const val = obj.filePath || obj.path || obj.file || obj.command || obj.cmd
           || obj.query || obj.pattern || obj.url || obj.description;
  if (!val || typeof val !== 'string') return '';
  const short = val.length > 60 ? `...${val.slice(-57)}` : val;
  return escapeHtml(short);
}

function buildToolBody(part) {
  const body = document.createElement('div');
  body.className = 'part-body';
  const input = part.input || part.args || part.arguments;
  if (input) {
    const formatted = typeof input === 'string' ? input : JSON.stringify(input, null, 2);
    body.innerHTML = `<pre class="part-pre">${escapeHtml(formatted)}</pre>`;
  }
  const output = part.output || part.result || part.content;
  if (output) {
    const formatted = typeof output === 'string' ? output : JSON.stringify(output, null, 2);
    body.innerHTML += `<div class="part-label">Result</div><pre class="part-pre">${escapeHtml(formatted)}</pre>`;
  }
  return body.innerHTML ? body : null;
}

function toolSummaryHtml(icon, toolName, hint) {
  const hintHtml = hint ? ` <span class="tool-call-hint">${hint}</span>` : '';
  return `<span class="tool-call-status">${icon}</span> <span class="tool-call-name">${toolName}</span>${hintHtml}`;
}

function createToolCallCard(ui, part) {
  void ui;
  const status = part.state || part.status || 'pending';
  const statusIcon = { pending: '\u23F3', running: '\uD83D\uDD04', completed: '\u2705', error: '\u274C' };
  const toolName = escapeHtml(part.toolName || part.name || 'tool');
  const icon = statusIcon[status] || '\u23F3';
  const hint = toolHint(part);

  const body = buildToolBody(part);

  if (!body) {
    const card = document.createElement('div');
    card.className = `part-collapsible tool-call ${status}`;
    card.dataset.partId = part.id || '';
    const header = document.createElement('div');
    header.className = 'part-summary no-expand';
    header.innerHTML = toolSummaryHtml(icon, toolName, hint);
    card.appendChild(header);
    return card;
  }

  const details = document.createElement('details');
  details.className = `part-collapsible tool-call ${status}`;
  details.dataset.partId = part.id || '';
  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  summary.innerHTML = toolSummaryHtml(icon, toolName, hint);
  details.appendChild(summary);
  details.appendChild(body);
  return details;
}

function createThinkingCard(part) {
  const text = part.thinking || part.text || part.content || '';

  const details = document.createElement('details');
  details.className = 'part-collapsible part-thinking';

  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  summary.textContent = '\uD83D\uDCA1 Thinking';
  details.appendChild(summary);

  if (text) {
    const body = document.createElement('div');
    body.className = 'part-body';
    body.innerHTML = renderMarkdown(text);
    details.appendChild(body);
  }

  return details;
}

function createToolResultCard(part) {
  const content = part.content || part.output || part.text || '';

  const details = document.createElement('details');
  details.className = 'part-collapsible part-tool-result';

  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  summary.textContent = '\uD83D\uDCCB Tool Result';
  details.appendChild(summary);

  if (content) {
    const body = document.createElement('div');
    body.className = 'part-body';
    const formatted = typeof content === 'string' ? content : JSON.stringify(content, null, 2);
    body.innerHTML = `<pre class="part-pre">${escapeHtml(formatted)}</pre>`;
    details.appendChild(body);
  }

  return details;
}

function createFilePart(part) {
  const name = part.filename || part.name || part.path || 'file';
  const mime = part.mimeType || part.mediaType || '';
  const url = part.url || part.data || '';

  const details = document.createElement('details');
  details.className = 'part-collapsible part-file';

  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  summary.innerHTML = `\uD83D\uDCC4 <span class="tool-call-name">${escapeHtml(name)}</span>`;
  if (mime) summary.innerHTML += ` <span class="tool-call-hint">${escapeHtml(mime)}</span>`;
  details.appendChild(summary);

  if (url || part.content || part.text) {
    const body = document.createElement('div');
    body.className = 'part-body';
    const content = part.content || part.text || url;
    const formatted = typeof content === 'string' ? content : JSON.stringify(content, null, 2);
    body.innerHTML = `<pre class="part-pre">${escapeHtml(formatted)}</pre>`;
    details.appendChild(body);
  }

  return details;
}

function createSubtaskPart(part) {
  const agent = part.agent || 'subtask';
  const desc = part.description || '';
  const prompt = part.prompt || '';

  const details = document.createElement('details');
  details.className = 'part-collapsible part-subtask';

  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  summary.innerHTML = `\uD83E\uDD16 <span class="tool-call-name">${escapeHtml(agent)}</span>`;
  if (desc) summary.innerHTML += ` <span class="tool-call-hint">${escapeHtml(desc)}</span>`;
  details.appendChild(summary);

  if (prompt) {
    const body = document.createElement('div');
    body.className = 'part-body';
    body.innerHTML = renderMarkdown(prompt);
    details.appendChild(body);
  }

  return details;
}

function createPatchPart(part) {
  const filePath = part.filePath || part.path || part.file || '';
  const diff = part.content || part.patch || part.diff || part.text || '';

  const details = document.createElement('details');
  details.className = 'part-collapsible part-patch';

  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  const label = filePath ? filePath : 'patch';
  summary.innerHTML = `\u270F\uFE0F <span class="tool-call-name">${escapeHtml(label)}</span>`;
  details.appendChild(summary);

  if (diff) {
    const body = document.createElement('div');
    body.className = 'part-body';
    body.innerHTML = `<pre class="part-pre">${escapeHtml(diff)}</pre>`;
    details.appendChild(body);
  }

  return details;
}

function createGenericPartCard(part) {
  const details = document.createElement('details');
  details.className = 'part-collapsible part-generic';

  const summary = document.createElement('summary');
  summary.className = 'part-summary';
  summary.textContent = part.type;
  details.appendChild(summary);

  const body = document.createElement('div');
  body.className = 'part-body';
  const raw = { ...part };
  delete raw.type;
  body.innerHTML = `<pre class="part-pre">${escapeHtml(JSON.stringify(raw, null, 2))}</pre>`;
  details.appendChild(body);

  return details;
}

export function renderMessages(ui, messages) {
  ui.messagesList.innerHTML = '';
  if (!messages || messages.length === 0) {
    ui.emptyState.classList.remove('hidden');
    return;
  }
  ui.emptyState.classList.add('hidden');
  messages.forEach((msg) => {
    appendMessageCard(ui, msg);
  });
  ui.scrollToBottom();
}

export function renderAssistantMessage(ui, messageId, parts) {
  ui.emptyState.classList.add('hidden');

  let card = ui.messagesList.querySelector(`[data-message-id="${messageId}"]`);
  if (!card) {
    card = document.createElement('div');
    card.className = 'message assistant';
    card.dataset.messageId = messageId;

    const role = document.createElement('div');
    role.className = 'message-role';
    role.textContent = `AgentelliJ - ${ui.agentName}`;
    card.appendChild(role);

    const content = document.createElement('div');
    content.className = 'message-content';
    card.appendChild(content);

    ui.messagesList.appendChild(card);
  }

  const content = card.querySelector('.message-content');
  content.classList.remove('streaming-cursor');
  content.innerHTML = '';

  for (const part of parts) {
    const el = renderPart(ui, part);
    if (el) content.appendChild(el);
  }

  card._rawText = null;
  if (!ui._userScrolledUp) ui.scrollToBottom();
}

export function appendStreamDelta(ui, messageId, partId, delta) {
  void partId;
  ui.emptyState.classList.add('hidden');

  let card = ui.messagesList.querySelector(`[data-message-id="${messageId}"]`);
  if (!card) {
    card = document.createElement('div');
    card.className = 'message assistant';
    card.dataset.messageId = messageId;

    const role = document.createElement('div');
    role.className = 'message-role';
    role.textContent = `AgentelliJ - ${ui.agentName}`;
    card.appendChild(role);

    const content = document.createElement('div');
    content.className = 'message-content streaming-cursor';
    card.appendChild(content);

    ui.messagesList.appendChild(card);
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
      if (!ui._userScrolledUp) ui.scrollToBottom();
    });
  }
}

export function finalizeMessage(ui, messageId, message) {
  const card = ui.messagesList.querySelector(`[data-message-id="${messageId}"]`);
  if (card) {
    const content = card.querySelector('.message-content');
    content.classList.remove('streaming-cursor');
    if (message?.parts) {
      content.innerHTML = '';
      message.parts.forEach((part) => {
        const el = renderPart(ui, part);
        if (el) content.appendChild(el);
      });
    }
    card._rawText = null;
  }
  if (!ui._userScrolledUp) ui.scrollToBottom();
}

export function updateToolCallStatus(ui, messageId, partId, status, part) {
  const card = ui.messagesList.querySelector(`[data-message-id="${messageId}"]`);
  if (!card) return;
  const toolCard = card.querySelector(`[data-part-id="${partId}"]`);
  if (!toolCard) return;

  const statusIcon = { pending: '\u23F3', running: '\uD83D\uDD04', completed: '\u2705', error: '\u274C' };
  const statusEl = toolCard.querySelector('.tool-call-status');
  if (statusEl) statusEl.textContent = statusIcon[status] || '\u23F3';

  if (part && toolCard.tagName !== 'DETAILS') {
    const body = buildToolBody(part);
    if (body) {
      const details = document.createElement('details');
      details.className = toolCard.className;
      details.dataset.partId = partId;
      const summary = document.createElement('summary');
      summary.className = 'part-summary';
      summary.innerHTML = toolCard.querySelector('.part-summary')?.innerHTML || '';
      details.appendChild(summary);
      details.appendChild(body);
      toolCard.replaceWith(details);
      return;
    }
  }

  toolCard.className = `part-collapsible tool-call ${status}`;
}
