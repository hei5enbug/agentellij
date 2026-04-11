export function setSlashCommands(ui, commands) {
  ui._slashCommands = commands || [];
}

export function checkSlashCommand(ui) {
  const text = ui.promptInput.textContent;
  if (text.startsWith('/') && !text.includes('\n')) {
    const query = text.substring(1).toLowerCase();
    showSlashPopup(ui, query);
  } else {
    hideSlashPopup(ui);
  }
}

export function showSlashPopup(ui, query) {
  const filtered = ui._slashCommands.filter((cmd) => {
    const name = cmd.name.toLowerCase().substring(1);
    const desc = (cmd.description || '').toLowerCase();
    return name.includes(query) || desc.includes(query);
  });

  if (filtered.length === 0) {
    hideSlashPopup(ui);
    return;
  }

  ui.slashPopupList.innerHTML = '';
  filtered.forEach((cmd, i) => {
    const item = document.createElement('div');
    item.className = `slash-popup-item${i === 0 ? ' active' : ''}`;
    const nameEl = document.createElement('span');
    nameEl.className = 'slash-popup-name';
    nameEl.textContent = cmd.name;
    const descEl = document.createElement('span');
    descEl.className = 'slash-popup-desc';
    descEl.textContent = cmd.description || '';
    item.appendChild(nameEl);
    item.appendChild(descEl);

    item.addEventListener('click', () => {
      ui._slashActiveIndex = i;
      slashSelect(ui);
    });
    item.addEventListener('mouseenter', () => {
      ui.slashPopupList.querySelectorAll('.slash-popup-item.active').forEach((el) => { el.classList.remove('active'); });
      item.classList.add('active');
      ui._slashActiveIndex = i;
    });
    ui.slashPopupList.appendChild(item);
  });

  ui._slashActiveIndex = 0;
  ui._slashFiltered = filtered;
  ui._slashVisible = true;
  ui.slashPopup.classList.remove('hidden');
}

export function hideSlashPopup(ui) {
  ui._slashVisible = false;
  ui._slashFiltered = [];
  ui.slashPopup.classList.add('hidden');
}

export function slashNavigate(ui, direction) {
  const items = ui.slashPopupList.querySelectorAll('.slash-popup-item');
  if (items.length === 0) return;
  items[ui._slashActiveIndex]?.classList.remove('active');
  ui._slashActiveIndex = (ui._slashActiveIndex + direction + items.length) % items.length;
  items[ui._slashActiveIndex]?.classList.add('active');
  items[ui._slashActiveIndex]?.scrollIntoView({ block: 'nearest' });
}

export function slashSelect(ui) {
  const cmd = ui._slashFiltered[ui._slashActiveIndex];
  if (!cmd) return;

  ui.promptInput.textContent = `${cmd.name} `;
  const range = document.createRange();
  const sel = window.getSelection();
  range.selectNodeContents(ui.promptInput);
  range.collapse(false);
  sel.removeAllRanges();
  sel.addRange(range);

  hideSlashPopup(ui);
}
