import { DatePipe, NgTemplateOutlet } from '@angular/common';
import {
  Component,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InsightsApi } from '../../core/api/insights-api';
import { CopilotAnswer, CopilotSource, CopilotTurn } from '../../core/api/models';
import { describeError } from '../../core/http/problem';
import { renderCitations, renderMarkdown } from '../../shared/markdown';
import { KnowledgeBase } from './knowledge-base';

interface Exchange {
  question: string;
  answer?: CopilotAnswer;
  error?: string;
}

/** Answered exchanges sent back with each question, so "and on M-104?" is understood. */
const HISTORY_EXCHANGES = 4;

/**
 * The maintenance copilot: questions answered from the plant's manuals and procedures (retrieval-augmented
 * generation in ai-insights-service), each answer citing the passages it used. A second tab manages the
 * knowledge base it reads.
 */
@Component({
  selector: 'app-copilot',
  imports: [
    DatePipe,
    NgTemplateOutlet,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTabsModule,
    MatTooltipModule,
    KnowledgeBase,
  ],
  templateUrl: './copilot.html',
  styleUrl: './copilot.scss',
})
export class Copilot {
  private readonly api = inject(InsightsApi);
  protected readonly assets = inject(AssetDirectory);

  /** ?asset=<id>, from the "Ask the copilot" button of the risk center. */
  readonly asset = input<string>();

  protected readonly status = rxResource({ stream: () => this.api.copilot() });

  protected readonly tab = signal(0);
  protected readonly openDocument = signal<string | null>(null);

  protected readonly assetId = signal<string | null>(null);
  protected question = '';
  protected readonly exchanges = signal<Exchange[]>([]);
  protected readonly asking = signal(false);
  /** "2:3" = source 3 of exchange 2, highlighted after a click on its citation. */
  protected readonly highlighted = signal<string | null>(null);

  private readonly conversation = viewChild<ElementRef<HTMLElement>>('conversation');

  protected readonly suggestions = computed(() => {
    const code = this.assetId() ? this.assets.code(this.assetId()!) : 'M-102';
    return [
      `${code} is overheating: what should I check first?`,
      `How do I lock out ${code} safely before maintenance?`,
      'Vibration is at 5 mm/s on a motor: is it serious?',
      'What does alarm E-217 mean?',
    ];
  });

  constructor() {
    effect(() => {
      const asset = this.asset();
      if (asset) this.assetId.set(asset);
    });
  }

  protected ask(text: string = this.question): void {
    const question = text.trim();
    if (!question || this.asking()) return;
    const history = this.history();
    this.question = '';
    this.asking.set(true);
    this.exchanges.update((list) => [...list, { question }]);
    this.scrollToEnd();
    const settle = (result: Partial<Exchange>) => {
      this.exchanges.update((list) => [...list.slice(0, -1), { ...list.at(-1)!, ...result }]);
      this.asking.set(false);
      this.scrollToEnd();
    };
    this.api.askCopilot(question, this.assetId(), history).subscribe({
      next: (answer) => settle({ answer }),
      error: (error) => settle({ error: describeError(error) }),
    });
  }

  protected newConversation(): void {
    this.exchanges.set([]);
    this.highlighted.set(null);
  }

  protected answerHtml(answer: CopilotAnswer): string {
    return renderCitations(renderMarkdown(answer.answer));
  }

  protected excerptHtml(source: CopilotSource): string {
    return renderMarkdown(source.excerpt);
  }

  protected cited(answer: CopilotAnswer): CopilotSource[] {
    return answer.mode === 'GENERATED' ? answer.sources.filter((s) => s.cited) : answer.sources;
  }

  protected alsoRead(answer: CopilotAnswer): CopilotSource[] {
    return answer.mode === 'GENERATED' ? answer.sources.filter((s) => !s.cited) : [];
  }

  /** A click on a citation marker in an answer highlights and shows the passage it points to. */
  protected onAnswerClick(event: MouseEvent, index: number): void {
    const target = event.target as HTMLElement | null;
    if (!target?.classList.contains('cite')) return;
    const key = `${index}:${target.textContent?.trim()}`;
    this.highlighted.set(key);
    setTimeout(() =>
      document
        .getElementById(`source-${key.replace(':', '-')}`)
        ?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' }),
    );
  }

  protected openInKnowledgeBase(documentId: string): void {
    this.openDocument.set(documentId);
    this.tab.set(1);
  }

  protected location(source: CopilotSource): string {
    const page =
      source.page !== null && !source.section?.startsWith('Page ') ? ` (page ${source.page})` : '';
    return (source.section ?? '') + page;
  }

  /** How the passage was found, for the curious: "words #1 · meaning #3". */
  protected foundBy(source: CopilotSource): string {
    const ways = [];
    if (source.keywordRank !== null) ways.push(`words #${source.keywordRank}`);
    if (source.vectorRank !== null) ways.push(`meaning #${source.vectorRank}`);
    return ways.join(' · ');
  }

  protected generatedByLabel(answer: CopilotAnswer): string {
    return answer.mode === 'GENERATED' ? answer.generatedBy : 'search only';
  }

  private history(): CopilotTurn[] {
    return this.exchanges()
      .filter((e) => e.answer?.mode === 'GENERATED')
      .slice(-HISTORY_EXCHANGES)
      .flatMap((e) => [
        { role: 'USER' as const, content: e.question },
        { role: 'ASSISTANT' as const, content: e.answer!.answer.slice(0, 4000) },
      ]);
  }

  private scrollToEnd(): void {
    setTimeout(() => {
      const element = this.conversation()?.nativeElement;
      element?.lastElementChild?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
    });
  }
}
