import { checkSlashCommand, slashNavigate, slashSelect, hideSlashPopup } from './slash-commands.js';

function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

export function setupInputHandlers(ui) {
  ui.promptInput.addEventListener('keydown', (e) => {
    if (ui._slashVisible) {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        slashNavigate(ui, 1);
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        slashNavigate(ui, -1);
        return;
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault();
        slashSelect(ui);
        return;
      }
      if (e.key === 'Escape') {
        e.preventDefault();
        hideSlashPopup(ui);
        return;
      }
    }

    if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
      e.preventDefault();
      ui._callbacks.onSend?.();
    } else if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      document.execCommand('insertLineBreak');
    }
  });

  ui.promptInput.addEventListener('input', () => {
    ui.promptInput.style.height = 'auto';
    const lineHeight = parseFloat(getComputedStyle(ui.promptInput).lineHeight) || 20;
    const maxHeight = lineHeight * 10;
    const newHeight = Math.min(ui.promptInput.scrollHeight, maxHeight);
    ui.promptInput.style.height = `${newHeight}px`;
    ui.promptInput.style.overflowY = ui.promptInput.scrollHeight > maxHeight ? 'auto' : 'hidden';

    checkSlashCommand(ui);
  });

  ui.btnSend.addEventListener('click', () => ui._callbacks.onSend?.());
  ui.btnAbort.addEventListener('click', () => ui._callbacks.onAbort?.());
  ui.btnNewSession.addEventListener('click', () => ui._callbacks.onNewSession?.());
  ui.sessionTrigger.addEventListener('click', (e) => {
    e.stopPropagation();
    ui.sessionDropdown.classList.toggle('open');
  });

  ui.sessionList.addEventListener('click', (e) => {
    const checkbox = e.target.closest('.session-item-check');
    if (checkbox) {
      e.stopPropagation();
      ui._updateBulkBar();
      return;
    }

    const deleteBtn = e.target.closest('.session-item-delete');
    if (deleteBtn) {
      e.stopPropagation();
      const sessionId = deleteBtn.closest('.session-item').dataset.sessionId;
      ui._callbacks.onDeleteSession?.(sessionId);
      return;
    }

    const item = e.target.closest('.session-item');
    if (item) {
      e.stopPropagation();
      const sessionId = item.dataset.sessionId;
      ui._callbacks.onSessionSwitch?.(sessionId);
      ui.sessionDropdown.classList.remove('open');
    }
  });

  ui.sessionBulkDelete.addEventListener('click', (e) => {
    e.stopPropagation();
    const ids = ui._getCheckedSessionIds();
    if (ids.length > 0) {
      ui._callbacks.onBulkDeleteSessions?.(ids);
    }
  });
  ui._initDropdown(ui.modelDropdown, (val) => ui._callbacks.onModelChange?.(val));
  ui._initDropdown(ui.variantDropdown, (val) => ui._callbacks.onVariantChange?.(val));
  ui._initDropdown(ui.agentDropdown, (val) => ui._callbacks.onAgentChange?.(val));

  document.addEventListener('click', (e) => {
    ui.container.querySelectorAll('.dropdown.open').forEach((dd) => {
      if (!dd.contains(e.target)) dd.classList.remove('open');
    });
    if (ui.sessionDropdown && !ui.sessionDropdown.contains(e.target)) {
      ui.sessionDropdown.classList.remove('open');
    }
  });

  ui.messagesList.addEventListener('click', (e) => {
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
      const line = fileLink.dataset.line ? parseInt(fileLink.dataset.line, 10) : undefined;
      ui._callbacks.onFileClick?.(path, line);
    }
  });
}

export function insertChipAtCursor(ui, path) {
  const chip = document.createElement('span');
  chip.className = 'context-chip';
  chip.contentEditable = 'false';
  chip.dataset.path = path;
  chip.title = path;
  const name = path.split('/').pop();
  chip.innerHTML = `${escapeHtml(name)}<span class="context-chip-remove">&times;</span>`;
  chip.querySelector('.context-chip-remove').addEventListener('click', () => chip.remove());

  const sel = window.getSelection();
  if (sel.rangeCount > 0 && ui.promptInput.contains(sel.anchorNode)) {
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
    ui.promptInput.appendChild(chip);
    const space = document.createTextNode('\u00A0');
    ui.promptInput.appendChild(space);
  }
}

export function getInputText(ui) {
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
  ui.promptInput.childNodes.forEach(walk);
  return text;
}

export function clearInput(ui) {
  ui.promptInput.innerHTML = '';
  ui.promptInput.style.height = 'auto';
}

export function focusInput(ui) { ui.promptInput.focus(); }

export function setInputEnabled(ui, enabled) {
  ui.promptInput.contentEditable = enabled ? 'true' : 'false';
  ui.btnSend.disabled = !enabled;
}

export function setStreaming(ui, streaming) {
  ui.btnSend.classList.toggle('hidden', streaming);
  ui.btnAbort.classList.toggle('hidden', !streaming);
  setInputEnabled(ui, !streaming);
}
