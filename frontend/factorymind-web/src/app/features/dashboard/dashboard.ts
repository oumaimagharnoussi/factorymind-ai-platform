import { DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { AssetApi } from '../../core/api/asset-api';
import { CRITICALITIES } from '../../core/api/models';
import { describeError } from '../../core/http/problem';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-dashboard',
  imports: [DecimalPipe, RouterLink, MatCardModule, MatIconModule, MatButtonModule, MatProgressBarModule, StatusChip],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(AssetApi);

  protected readonly criticalities = CRITICALITIES;

  protected readonly summary = rxResource({ stream: () => this.api.summary() });

  /** Machines an engineer should look at first: anything in WARNING or STOPPED. */
  protected readonly attention = rxResource({
    stream: () =>
      forkJoin([
        this.api.assets({ status: 'STOPPED', size: 10 }),
        this.api.assets({ status: 'WARNING', size: 10 }),
      ]).pipe(map(([stopped, warning]) => [...stopped.content, ...warning.content])),
  });

  /** Share of machines currently able to produce. A proxy until downtime events exist (telemetry phase). */
  protected readonly operationalRate = computed(() => {
    const summary = this.summary.value();
    if (!summary || summary.total === 0) return 0;
    return (summary.byStatus.OPERATIONAL + summary.byStatus.WARNING) / summary.total;
  });

  protected readonly errorMessage = computed(() => {
    const error = this.summary.error() ?? this.attention.error();
    return error ? describeError(error) : null;
  });

  protected share(count: number): number {
    const total = this.summary.value()?.total ?? 0;
    return total === 0 ? 0 : (count / total) * 100;
  }
}
