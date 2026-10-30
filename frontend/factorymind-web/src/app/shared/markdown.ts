/**
 * The small part of Markdown that reports and answers use: headings, paragraphs, bullet and numbered
 * lists, **bold** and `code`. Everything is HTML-escaped first, so text from a language model can never
 * inject markup; Angular sanitizes the result again when it is bound with [innerHTML].
 */
export function renderMarkdown(markdown: string): string {
  const html: string[] = [];
  let list: 'ul' | 'ol' | null = null;
  let paragraph: string[] = [];

  const flushParagraph = () => {
    if (paragraph.length) html.push(`<p>${paragraph.join(' ')}</p>`);
    paragraph = [];
  };
  const closeList = () => {
    if (list) html.push(`</${list}>`);
    list = null;
  };

  for (const raw of markdown.split(/\r?\n/)) {
    const line = inline(escape(raw.trim()));
    const heading = /^(#{1,4})\s+(.*)$/.exec(line);
    const bullet = /^[-*]\s+(.*)$/.exec(line);
    const numbered = /^\d+[.)]\s+(.*)$/.exec(line);
    if (!line) {
      flushParagraph();
      closeList();
    } else if (heading) {
      flushParagraph();
      closeList();
      // Reports sit inside a card: their headings start at h3.
      const level = Math.min(heading[1].length + 2, 6);
      html.push(`<h${level}>${heading[2]}</h${level}>`);
    } else if (bullet || numbered) {
      flushParagraph();
      const kind = bullet ? 'ul' : 'ol';
      if (list !== kind) {
        closeList();
        html.push(`<${kind}>`);
        list = kind;
      }
      html.push(`<li>${(bullet ?? numbered)![1]}</li>`);
    } else {
      closeList();
      paragraph.push(line);
    }
  }
  flushParagraph();
  closeList();
  return html.join('');
}

/**
 * Turns the copilot's source references, "[2]", into superscript markers the page can make clickable
 * (it reads the number from the marker's text). Apply to the output of renderMarkdown.
 */
export function renderCitations(html: string): string {
  return html.replace(/\[(\d{1,2})\]/g, '<sup class="cite" title="Show source $1">$1</sup>');
}

function escape(text: string): string {
  return text
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}

function inline(text: string): string {
  return text
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>');
}
