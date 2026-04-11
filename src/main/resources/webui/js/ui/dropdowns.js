export function initDropdown(ui, dropdown, onChange) {
  const trigger = dropdown.querySelector('.dropdown-trigger');
  const searchInput = dropdown.querySelector('.dropdown-search');

  trigger.addEventListener('click', (e) => {
    e.stopPropagation();
    const wasOpen = dropdown.classList.contains('open');
    ui.container.querySelectorAll('.dropdown.open').forEach((dd) => {
      dd.classList.remove('open');
    });
    if (!wasOpen) {
      dropdown.classList.add('open');
      if (searchInput) {
        searchInput.value = '';
        filterDropdownItems(dropdown, '');
        setTimeout(() => searchInput.focus(), 0);
      }
    }
  });

  if (searchInput) {
    searchInput.addEventListener('input', () => {
      filterDropdownItems(dropdown, searchInput.value);
    });
    searchInput.addEventListener('click', (e) => e.stopPropagation());
    searchInput.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') dropdown.classList.remove('open');
    });
  }

  dropdown._onChange = onChange;
}

export function filterDropdownItems(dropdown, query) {
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

export function renderDropdown(dropdown, items, currentValue) {
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
      menu.querySelectorAll('.dropdown-item').forEach((i) => {
        i.classList.remove('selected');
      });
      el.classList.add('selected');
      trigger.textContent = item.label;
      dropdown.classList.remove('open');
      dropdown._onChange?.(item.value);
    });
    menu.appendChild(el);
  });
  trigger.textContent = selectedLabel;
}

export function renderModelList(ui, models, currentValue) {
  const groups = {};
  models.forEach((m) => {
    const key = m.providerName || m.providerID;
    if (!groups[key]) groups[key] = [];
    groups[key].push(m);
  });

  const itemsContainer = ui.modelDropdown.querySelector('.dropdown-items');
  const trigger = ui.modelDropdown.querySelector('.dropdown-trigger');
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
        itemsContainer.querySelectorAll('.dropdown-item').forEach((i) => {
          i.classList.remove('selected');
        });
        el.classList.add('selected');
        trigger.textContent = el.textContent;
        ui.modelDropdown.classList.remove('open');
        ui.modelDropdown._onChange?.(value);
      });
      itemsContainer.appendChild(el);
    });
  }

  trigger.textContent = selectedLabel;
}

export function renderVariantList(ui, variants, currentValue) {
  const items = (!variants || variants.length === 0)
    ? [{ value: '', label: 'Default' }]
    : variants.map((v) => ({ value: v, label: v }));
  renderDropdown(ui.variantDropdown, items, currentValue);
}

export function renderAgentList(ui, agents, currentValue) {
  const items = agents.map((a) => ({ value: a.name, label: a.name }));
  renderDropdown(ui.agentDropdown, items, currentValue);
}
