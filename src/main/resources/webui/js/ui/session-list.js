function escapeHtml(str) {
  const div = document.createElement('div');
  div.textContent = str;
  return div.innerHTML;
}

export function renderSessionList(ui, sessions, currentId) {
  ui.sessionList.innerHTML = '';
  ui.sessionBulkBar.classList.add('hidden');

  if (!sessions || sessions.length === 0) {
    ui.sessionTrigger.textContent = 'No sessions';
    ui.sessionList.innerHTML = '<div class="session-empty">No sessions</div>';
    return;
  }
  const current = sessions.find(s => s.id === currentId);
  ui.sessionTrigger.textContent = current ? (current.title || current.id.substring(0, 8)) : sessions[0].title || sessions[0].id.substring(0, 8);

  sessions.forEach((s) => {
    const item = document.createElement('div');
    item.className = 'session-item' + (s.id === currentId ? ' active' : '');
    item.dataset.sessionId = s.id;
    item.innerHTML = `
        <input type="checkbox" class="session-item-check" title="Select for deletion">
        <span class="session-item-title">${escapeHtml(s.title || s.id.substring(0, 8))}</span>
        <button class="session-item-delete" title="Delete session">&times;</button>
    `;
    ui.sessionList.appendChild(item);
  });
}

export function getCheckedSessionIds(ui) {
  return [...ui.sessionList.querySelectorAll('.session-item-check:checked')]
    .map(cb => cb.closest('.session-item').dataset.sessionId);
}

export function updateBulkBar(ui) {
  const count = getCheckedSessionIds(ui).length;
  if (count > 0) {
    ui.sessionBulkBar.classList.remove('hidden');
    ui.sessionBulkDelete.textContent = `Delete (${count})`;
  } else {
    ui.sessionBulkBar.classList.add('hidden');
  }
}
