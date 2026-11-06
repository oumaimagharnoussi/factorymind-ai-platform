import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import { MaintenancePlan } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { humanize } from '../../shared/labels';
import { scheduleLabel, stateLabel, todayUtc } from '../../shared/plan-schedule';
import { StatusChip } from '../../shared/status-chip';

/** One maintenance plan: its rule, what the planner does next, and the work orders it opened. */
@Component({
  selector: 'app-plan-detail',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    StatusChip,
  ],
  templateUrl: './plan-detail.html',
  styleUrls: ['./maintenance.scss', './work-order-detail.scss'],
})
export class PlanDetail {
  private readonly api = inject(MaintenanceApi);
  private readonly router = inject(Router);
  protected readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  readonly id = input.required<string>();

  protected readonly humanize = humanize;
  protected readonly scheduleLabel = scheduleLabel;
  protected readonly today = todayUtc();
  protected readonly busy = signal(false);
  protected readonly historyColumns = ['code', 'status', 'due', 'completed'];

  protected readonly plan = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.plan(params) });
  protected readonly history = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.workOrders({ planId: params, size: 10 }),
  });

  protected stateText(plan: MaintenancePlan): string {
    return stateLabel(plan, this.today);
  }

  protected openNow(): void {
    this.busy.set(true);
    this.api.openPlanWorkOrder(this.id()).subscribe({
      next: (workOrder) => this.router.navigate(['/maintenance/orders', workOrder.id]),
      error: () => this.busy.set(false),
    });
  }

  protected pause(): void {
    this.apply(this.api.pausePlan(this.id()));
  }

  protected resume(): void {
    this.apply(this.api.resumePlan(this.id()));
  }

  private apply(call: Observable<MaintenancePlan>): void {
    this.busy.set(true);
    call.subscribe({
      next: (plan) => {
        this.plan.set(plan);
        this.busy.set(false);
      },
      error: () => this.busy.set(false),
    });
  }
}
