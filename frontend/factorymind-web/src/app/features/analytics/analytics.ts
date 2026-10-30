import { DatePipe, DecimalPipe, PercentPipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AnalyticsApi } from '../../core/api/analytics-api';
import { AnalyticsMonth } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { describeError } from '../../core/http/problem';
import { MonthBar, MonthBars } from '../../shared/month-bars';
import { StatusChip } from '../../shared/status-chip';

const MONTHS = 6;

/**
 * Reliability and maintenance KPIs from the analytics warehouse: the same data and definitions as the Power BI
 * reports, for people without Power BI. The full reports open from the button when one is published.
 */
@Component({
  selector: 'app-analytics',
  imports: [
    DatePipe,
    DecimalPipe,
    PercentPipe,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
    MonthBars,
    StatusChip,
  ],
  templateUrl: './analytics.html',
  styleUrl: './analytics.scss',
})
export class Analytics {
  private readonly api = inject(AnalyticsApi);
  protected readonly auth = inject(Auth);

  protected readonly days = signal(90);
  protected readonly factory = signal<string | null>(null);
  private readonly period = computed(() => {
    const to = new Date();
    const from = new Date(to.getTime() - (this.days() - 1) * 86_400_000);
    return { from: isoDay(from), to: isoDay(to), factory: this.factory() };
  });

  protected readonly status = rxResource({ stream: () => this.api.status() });
  protected readonly overview = rxResource({
    params: () => this.period(),
    stream: ({ params: p }) => this.api.overview(p.from, p.to, p.factory),
  });
  protected readonly assets = rxResource({
    params: () => this.period(),
    stream: ({ params: p }) => this.api.assets(p.from, p.to, p.factory),
  });
  protected readonly months = rxResource({
    params: () => this.factory(),
    stream: ({ params: factory }) => this.api.monthly(MONTHS, factory),
  });

  protected readonly availabilityBars = computed(() =>
    this.bars((m) => (m.availability == null ? null : m.availability * 100)),
  );
  protected readonly failureBars = computed(() => this.bars((m) => m.failures));
  protected readonly costBars = computed(() => this.bars((m) => m.maintenanceCost));
  protected readonly percent = (v: number) => `${v.toFixed(1)} %`;
  protected readonly count = (v: number) => String(v);
  protected readonly euros = (v: number) => `${Math.round(v).toLocaleString('en')} €`;

  protected readonly refreshing = signal(false);
  protected readonly refreshError = signal<string | null>(null);
  protected readonly canRefresh = computed(() => this.auth.hasAnyRole('ADMIN'));

  protected readonly errorMessage = computed(() => {
    const error = this.overview.error() ?? this.status.error();
    return error ? describeError(error) : null;
  });

  protected setFactory(factory: string | null): void {
    this.factory.set(factory || null);
  }

  protected refresh(): void {
    this.refreshing.set(true);
    this.refreshError.set(null);
    this.api.refresh().subscribe({
      next: () => {
        this.refreshing.set(false);
        this.status.reload();
        this.overview.reload();
        this.assets.reload();
        this.months.reload();
      },
      error: (error) => {
        this.refreshing.set(false);
        this.refreshError.set(describeError(error));
      },
    });
  }

  private bars(value: (m: AnalyticsMonth) => number | null): MonthBar[] {
    return (this.months.value() ?? []).map((m) => ({ month: m.month, value: value(m) }));
  }
}

function isoDay(date: Date): string {
  return date.toISOString().slice(0, 10);
}
