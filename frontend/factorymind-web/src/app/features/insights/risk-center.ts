import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { interval, of } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InsightsApi } from '../../core/api/insights-api';
import { AssistantAnswer, MachineReport, MachineRisk, RiskLevel } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { describeError } from '../../core/http/problem';
import { humanize, sensorLabel } from '../../shared/labels';
import { renderMarkdown } from '../../shared/markdown';
import { Sparkline } from '../../shared/sparkline';
import { StatusChip } from '../../shared/status-chip';

/** ai-insights-service scores every machine this often; the page follows. */
const REFRESH_MS = 10_000;

interface Exchange {
  question: string;
  answer?: AssistantAnswer;
  error?: string;
}

/**
 * Failure risk of every machine, from the scikit-learn model fed with live telemetry, with the reasons
 * behind each score, a report per machine and an assistant that answers from live plant data.
 */
@Component({
  selector: 'app-risk-center',
  imports: [
    DatePipe,
    DecimalPipe,
    PercentPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatTooltipModule,
    Sparkline,
    StatusChip,
  ],
  templateUrl: './risk-center.html',
  styleUrl: './risk-center.scss',
})
export class RiskCenter {
  private readonly api = inject(InsightsApi);
  protected readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  protected readonly sensorLabel = sensorLabel;
  protected readonly humanize = humanize;
  protected readonly canUseLanguageModel = computed(() =>
    this.auth.hasAnyRole('ADMIN', 'ENGINEER'),
  );
  protected readonly suggestions = [
    'Which machine needs attention first?',
    'Why is the riskiest machine at risk, and is someone already on it?',
    'Which unresolved alerts have no work order yet?',
  ];

  protected readonly risks = rxResource({ stream: () => this.api.risks() });
  protected readonly modelCard = rxResource({ stream: () => this.api.modelCard() });
  protected readonly assistant = rxResource({
    params: () => this.canUseLanguageModel(),
    stream: ({ params: allowed }) => (allowed ? this.api.assistant() : of(null)),
  });

  protected readonly selectedId = signal<string | null>(null);
  protected readonly selected = computed<MachineRisk | null>(() => {
    const risks = this.risks.value() ?? [];
    return risks.find((r) => r.assetId === this.selectedId()) ?? risks[0] ?? null;
  });
  protected readonly history = rxResource({
    params: () => this.selected()?.assetId,
    stream: ({ params: assetId }) => this.api.history(assetId, 30),
  });
  protected readonly historyValues = computed(() =>
    (this.history.value() ?? []).map((p) => p.riskScore * 100),
  );

  protected readonly counts = computed(() => {
    const counts: Record<RiskLevel, number> = { HIGH: 0, MEDIUM: 0, LOW: 0 };
    for (const risk of this.risks.value() ?? []) counts[risk.riskLevel]++;
    return counts;
  });

  protected readonly importances = computed(() => {
    const card = this.modelCard.value();
    if (!card) return [];
    return Object.entries(card.featureImportances)
      .map(([name, value]) => ({ name: humanize(name), value }))
      .sort((a, b) => b.value - a.value);
  });

  protected readonly report = signal<MachineReport | null>(null);
  protected readonly reportHtml = computed(() => {
    const report = this.report();
    return report && report.assetId === this.selected()?.assetId
      ? renderMarkdown(report.text)
      : null;
  });
  protected readonly writingReport = signal(false);
  protected readonly reportError = signal<string | null>(null);

  protected question = '';
  protected readonly exchanges = signal<Exchange[]>([]);
  protected readonly asking = signal(false);

  protected readonly errorMessage = computed(() => {
    const error = this.risks.error();
    return error ? describeError(error) : null;
  });

  constructor() {
    interval(REFRESH_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => {
        this.risks.reload();
        this.history.reload();
      });
  }

  protected select(risk: MachineRisk): void {
    this.selectedId.set(risk.assetId);
    this.reportError.set(null);
  }

  protected writeReport(assetId: string): void {
    this.writingReport.set(true);
    this.reportError.set(null);
    this.api.report(assetId).subscribe({
      next: (report) => {
        this.report.set(report);
        this.writingReport.set(false);
      },
      error: (error) => {
        this.reportError.set(describeError(error));
        this.writingReport.set(false);
      },
    });
  }

  protected ask(text: string = this.question): void {
    const question = text.trim();
    if (!question || this.asking()) return;
    this.question = '';
    this.asking.set(true);
    this.exchanges.update((list) => [{ question }, ...list]);
    const settle = (result: Partial<Exchange>) => {
      this.exchanges.update(([latest, ...rest]) => [{ ...latest, ...result }, ...rest]);
      this.asking.set(false);
    };
    this.api.ask(question).subscribe({
      next: (answer) => settle({ answer }),
      error: (error) => settle({ error: describeError(error) }),
    });
  }

  protected answerHtml(answer: AssistantAnswer): string {
    return renderMarkdown(answer.answer);
  }

  /** "about 25 min", "about 3.5 h", or a dash when the sensor is not heading for its limit. */
  protected timeToCritical(hours: number | null): string {
    if (hours === null) return '–';
    if (hours === 0) return 'now';
    return hours < 1
      ? `about ${Math.max(1, Math.round(hours * 60))} min`
      : `about ${hours.toFixed(1)} h`;
  }

  protected generatedByLabel(generatedBy: string): string {
    return generatedBy === 'rules' ? 'rule-based (no language model configured)' : generatedBy;
  }
}
