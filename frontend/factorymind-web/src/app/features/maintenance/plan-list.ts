import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { AssetDirectory } from '../../core/api/asset-directory';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import { MaintenancePlan } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { scheduleLabel, stateLabel, todayUtc } from '../../shared/plan-schedule';
import { StatusChip } from '../../shared/status-chip';

type View = 'active' | 'all';

/** Preventive maintenance plans: what is repeated on each machine and when its next work order opens. */
@Component({
  selector: 'app-plan-list',
  imports: [
    DatePipe,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatProgressBarModule,
    MatTableModule,
    MatTooltipModule,
    StatusChip,
  ],
  templateUrl: './plan-list.html',
  styleUrl: './maintenance.scss',
})
export class PlanList {
  private readonly api = inject(MaintenanceApi);
  protected readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  protected readonly scheduleLabel = scheduleLabel;
  protected readonly today = todayUtc();
  protected readonly columns = ['code', 'machine', 'title', 'next', 'state', 'job'];

  protected readonly view = signal<View>('active');
  protected readonly plans = rxResource({ stream: () => this.api.plans() });

  protected readonly shown = computed(() =>
    (this.plans.value() ?? []).filter((p) => this.view() === 'all' || p.active),
  );
  protected readonly counts = computed(() => {
    const plans = (this.plans.value() ?? []).filter((p) => p.active);
    return {
      active: plans.length,
      jobsOpen: plans.filter((p) => p.state === 'JOB_OPEN').length,
      overdue: plans.filter((p) => p.currentJob?.overdue).length,
      next7Days: plans.filter((p) => p.state === 'SCHEDULED' && p.opensOn <= this.addDays(7)).length,
    };
  });

  protected stateText(plan: MaintenancePlan): string {
    return stateLabel(plan, this.today);
  }

  private addDays(days: number): string {
    return todayUtc(new Date(Date.parse(this.today) + days * 86_400_000));
  }
}
