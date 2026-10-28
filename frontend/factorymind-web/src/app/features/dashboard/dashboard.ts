import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { forkJoin, map } from 'rxjs';
import { AlertApi } from '../../core/api/alert-api';
import { AssetApi } from '../../core/api/asset-api';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InventoryApi } from '../../core/api/inventory-api';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import { CRITICALITIES } from '../../core/api/models';
import { describeError } from '../../core/http/problem';
import { sensorLabel } from '../../shared/labels';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-dashboard',
  imports: [DatePipe, DecimalPipe, RouterLink, MatCardModule, MatIconModule, MatButtonModule, MatProgressBarModule, StatusChip],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly api = inject(AssetApi);
  private readonly alertApi = inject(AlertApi);
  private readonly maintenanceApi = inject(MaintenanceApi);
  private readonly inventoryApi = inject(InventoryApi);
  protected readonly assets = inject(AssetDirectory);

  protected readonly criticalities = CRITICALITIES;
  protected readonly sensorLabel = sensorLabel;

  protected readonly summary = rxResource({ stream: () => this.api.summary() });

  /** Machines an engineer should look at first: anything in WARNING or STOPPED. */
  protected readonly attention = rxResource({
    stream: () =>
      forkJoin([
        this.api.assets({ status: 'STOPPED', size: 10 }),
        this.api.assets({ status: 'WARNING', size: 10 }),
      ]).pipe(map(([stopped, warning]) => [...stopped.content, ...warning.content])),
  });

  // Operations row: each service is loaded on its own so one being down does not blank the dashboard.
  protected readonly alertSummary = rxResource({ stream: () => this.alertApi.summary() });
  protected readonly maintenanceSummary = rxResource({ stream: () => this.maintenanceApi.summary() });
  protected readonly inventorySummary = rxResource({ stream: () => this.inventoryApi.summary() });

  /** Most recently raised alerts that are not resolved yet. */
  protected readonly activeAlerts = rxResource({
    stream: () =>
      this.alertApi.alerts({ statuses: ['OPEN', 'ACKNOWLEDGED'], size: 6 }).pipe(map((page) => page.content)),
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
