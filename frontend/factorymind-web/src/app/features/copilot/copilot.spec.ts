import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InsightsApi } from '../../core/api/insights-api';
import { CopilotAnswer, CopilotSource } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { Copilot } from './copilot';

function source(number: number, cited: boolean, section: string): CopilotSource {
  return {
    number,
    documentId: `doc-${number}`,
    documentTitle: 'Schuler HP-400 hydraulic press',
    kind: 'MANUAL',
    section,
    page: null,
    excerpt: `Passage ${number}.`,
    cited,
    keywordRank: 1,
    vectorRank: number,
  };
}

function answer(overrides: Partial<CopilotAnswer> = {}): CopilotAnswer {
  return {
    answer: 'Lock out the press first [2], then check the cooler fan [1].',
    mode: 'GENERATED',
    generatedBy: 'anthropic:claude-sonnet-5-5',
    assetId: 'id-102',
    machine: 'M-102 Hydraulic Press',
    retrieval: 'HYBRID',
    sources: [
      source(1, true, 'Troubleshooting'),
      source(2, true, 'Safety'),
      source(3, false, 'Alarm codes'),
    ],
    notice: null,
    answeredAt: '2026-10-30T10:00:00Z',
    ...overrides,
  };
}

describe('Copilot', () => {
  const askCopilot = vi.fn();

  function render() {
    TestBed.configureTestingModule({
      providers: [
        { provide: Auth, useValue: { hasAnyRole: () => false } },
        {
          provide: AssetDirectory,
          useValue: { list: () => [], code: () => 'M-102', name: () => 'Hydraulic Press' },
        },
        {
          provide: InsightsApi,
          useValue: {
            copilot: () =>
              of({
                chatModel: 'anthropic:claude-sonnet-5-5',
                knowledge: {
                  retrieval: 'HYBRID',
                  embeddingModel: 'ollama:nomic-embed-text',
                  documents: 9,
                  chunks: 60,
                  embeddedChunks: 60,
                },
              }),
            askCopilot,
            documents: () => of([]),
            document: () => of(null),
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(Copilot);
    fixture.detectChanges();
    return fixture;
  }

  beforeEach(() => askCopilot.mockReset());

  it('says what the copilot runs on', () => {
    const page: HTMLElement = render().nativeElement;

    expect(page.querySelector('.engine')?.textContent).toContain('anthropic:claude-sonnet-5-5');
    expect(page.querySelector('.engine')?.textContent).toContain(
      'Hybrid search · ollama:nomic-embed-text',
    );
  });

  it('shows the answer with clickable citations, cited passages first, and sends the conversation back', () => {
    askCopilot.mockReturnValue(of(answer()));
    const fixture = render();

    fixture.componentInstance['ask']('M-102 runs hot');
    fixture.detectChanges();

    const page: HTMLElement = fixture.nativeElement;
    expect([...page.querySelectorAll('.answer .cite')].map((c) => c.textContent)).toEqual([
      '2',
      '1',
    ]);
    const cited = page.querySelectorAll('.answer > .sources .source');
    expect(cited.length).toBe(2);
    expect(page.querySelector('.also-read summary')?.textContent).toContain('1 more passages read');

    (page.querySelector('.answer .cite') as HTMLElement).click();
    fixture.detectChanges();
    expect(page.querySelector('#source-0-2')?.classList).toContain('highlighted');

    fixture.componentInstance['ask']('And on M-104?');
    expect(askCopilot).toHaveBeenLastCalledWith('And on M-104?', null, [
      { role: 'USER', content: 'M-102 runs hot' },
      { role: 'ASSISTANT', content: answer().answer },
    ]);
  });

  it('without a language model, lists the passages found and keeps them out of the history', () => {
    askCopilot.mockReturnValue(
      of(
        answer({
          mode: 'PASSAGES_ONLY',
          generatedBy: 'search',
          answer: 'These passages match best:',
        }),
      ),
    );
    const fixture = render();

    fixture.componentInstance['ask']('E-217?');
    fixture.detectChanges();

    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('.answer')?.classList).toContain('passages-only');
    expect(page.querySelectorAll('.answer > .sources .source').length).toBe(3);

    fixture.componentInstance['ask']('next');
    expect(askCopilot).toHaveBeenLastCalledWith('next', null, []);
  });
});
