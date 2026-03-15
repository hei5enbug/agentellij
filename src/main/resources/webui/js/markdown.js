import { marked } from '../vendor/marked.min.js';

marked.setOptions({
  gfm: true,
  breaks: true,
});

const renderer = new marked.Renderer();

renderer.code = function ({ text, lang }) {
  const language = lang || 'text';
  const escaped = escapeHtml(text);
  return `
    <div class="code-header">
      <span class="code-lang">${escapeHtml(language)}</span>
      <button class="btn-copy" onclick="navigator.clipboard.writeText(this.closest('.code-block-wrapper').querySelector('code').textContent)">Copy</button>
    </div>
    <pre class="code-block-wrapper"><code class="language-${escapeHtml(language)}">${escaped}</code></pre>
  `;
};

marked.use({ renderer });

export function renderMarkdown(text) {
  if (!text) return '';
  try {
    return sanitizeHtml(marked.parse(text));
  } catch (e) {
    console.error('Markdown render error:', e);
    return escapeHtml(text);
  }
}

function escapeHtml(str) {
  return str
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

function sanitizeHtml(html) {
  return html
    .replace(/<script\b[^<]*(?:(?!<\/script>)<[^<]*)*<\/script>/gi, '')
    .replace(/<iframe\b[^<]*(?:(?!<\/iframe>)<[^<]*)*<\/iframe>/gi, '')
    .replace(/\s+on\w+\s*=\s*["'][^"']*["']/gi, '');
}
