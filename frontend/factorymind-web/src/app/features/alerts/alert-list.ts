import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime, defer } from 'rxjs';
import { AlertApi } from '../../core/api/alert-api';
import { AssetDirectory } from '../../core/api/asset-directory';
import { Alert, AlertQuery, AlertSeverity, AlertStatus } from '../../core/api/models';
import { stayConnected } from '../../core/api/sse';
import { Auth } from '../../core/auth/auth';
import { sensorLabel } from '../../shared/labels';
import { StatusChip } from '../../shared/status-chip';

type View = 'unresolved' | 'resolved' | 'all';

const VIEWS: Record<View, AlertStatus[]> = {
  unresolved: ['OPEN', 'ACKNOWLEDGED'],
  resolved: ['RESOLVED'],
  all: [],
};

/**
 * Alerts raised by alert-service once a sensor has stayed outside its limits long enough. The list
 * refreshes itself from the alert stream, so a new alert shows up without reloading the page.
 */
@Component({
  selector: 'app-alert-list',
  imports: [
    DatePipe,
    DecimalPipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
    StatusChip,
  ],
  templateUrl: './alert-list.html',
  styleUrl: './alert-list.scss',
})
export class AlertList {
  private readonly api = inject(AlertApi);
  protected readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  protected readonly sensorLabel = sensorLabel;
  protected readonly columns = ['severity', 'machine', 'reading', 'raised', 'state', 'workOrder', 'actions'];

  protected readonly view = signal<View>('unresolved');
  protected readonly severity = signal<AlertSeverity | null>(null);
  protected readonly page = signal({ index: 0, size: 20 });
  protected readonly live = signal(false);

  /** The alert whose resolution note is being written, if any. */
  protected readonly resolving = signal<Alert | null>(null);
  protected resolutionNote = '';

  protected readonly summary = rxResource({ stream: () => this.api.summary() });
  protected readonly alerts = rxResource({
    params: (): AlertQuery => ({
      statuses: VIEWS[this.view()],
      severity: this.severity(),
      page: this.page().index,
      size: this.page().size,
    }),
    stream: ({ params }) => this.api.alerts(params),
  });

  constructor() {
    defer(() => this.api.stream(() => this.live.set(true)))
      .pipe(
        stayConnected(() => this.live.set(false)),
        // A machine going bad changes several alerts at once: refresh once for the burst.
        debounceTime(400),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.alerts.reload();
        this.summary.reload();
      });
  }

  protected setView(view: View): void {
    this.view.set(view);
    this.page.update((page) => ({ ...page, index: 0 }));
  }

  protected setSeverity(severity: AlertSeverity | null): void {
    this.severity.set(severity);
    this.page.update((page) => ({ ...page, index: 0 }));
  }

  protected onPage(event: PageEvent): void {
    this.page.set({ index: event.pageIndex, size: event.pageSize });
  }

  protected acknowledge(alert: Alert): void {
    this.api.acknowledge(alert.id).subscribe(() => this.refresh());
  }

  protected startResolving(alert: Alert): void {
    this.resolutionNote = '';
    this.resolving.set(alert);
  }

  protected resolve(): void {
    const alert = this.resolving();
    if (!alert) return;
    this.api.resolve(alert.id, this.resolutionNote.trim() || null).subscribe(() => {
      this.resolving.set(null);
      this.refresh();
    });
  }

  /** Query parameters that pre-fill the work order form from this alert. */
  protected workOrderParams(alert: Alert): Record<string, string> {
    return {
      alertId: alert.id,
      assetId: alert.assetId,
      title: `${alert.severity === 'CRITICAL' ? 'Critical' : 'High'} ${sensorLabel(alert.sensorType).toLowerCase()} on ${this.assets.code(alert.assetId)}`,
      priority: alert.severity === 'CRITICAL' ? 'URGENT' : 'HIGH',
    };
  }

  private refresh(): void {
    this.alerts.reload();
    this.summary.reload();
  }
}
