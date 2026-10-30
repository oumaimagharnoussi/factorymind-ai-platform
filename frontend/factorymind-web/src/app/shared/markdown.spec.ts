import { renderCitations, renderMarkdown } from './markdown';

describe('renderMarkdown', () => {
  it('renders a report: heading, bold, numbered actions', () => {
    const html = renderMarkdown(
      '## M-102 Hydraulic press\n\n**Failure risk: HIGH** (87%).\n\n1. Inspect the cooling fan\n2. Check the oil level',
    );
    expect(html).toBe(
      '<h4>M-102 Hydraulic press</h4><p><strong>Failure risk: HIGH</strong> (87%).</p>' +
        '<ol><li>Inspect the cooling fan</li><li>Check the oil level</li></ol>',
    );
  });

  it('never lets markup through', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)> **<b>hi</b>**');
    expect(html).not.toContain('<img');
    expect(html).toContain('&lt;img src=x onerror=alert(1)&gt;');
    expect(html).toContain('<strong>&lt;b&gt;hi&lt;/b&gt;</strong>');
  });

  it('switches between bullet and numbered lists', () => {
    expect(renderMarkdown('- a\n- b\n1. c')).toBe(
      '<ul><li>a</li><li>b</li></ul><ol><li>c</li></ol>',
    );
  });

  it('marks the copilot citations', () => {
    expect(renderCitations(renderMarkdown('Lock out first [2].'))).toBe(
      '<p>Lock out first <sup class="cite" title="Show source 2">2</sup>.</p>',
    );
  });
});
