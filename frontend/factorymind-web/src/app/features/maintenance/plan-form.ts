import { Component, OnInit, inject, input, signal } from '@angular/core';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { AssetDirectory } from '../../core/api/asset-directory';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import {
  PlanRequest,
  PlanSchedule,
  WORK_ORDER_PRIORITIES,
  WorkOrderPriority,
} from '../../core/api/models';
import { humanize } from '../../shared/labels';
import { SCHEDULE_HINTS, todayUtc } from '../../shared/plan-schedule';

/** The work order must open before it is due, so the lead time is shorter than the interval. */
export function leadShorterThanInterval(group: AbstractControl): ValidationErrors | null {
  const interval = Number(group.get('intervalDays')?.value);
  const lead = Number(group.get('leadDays')?.value);
  return lead >= interval ? { leadTooLong: true } : null;
}

/** Create a preventive maintenance plan, or edit one (route /maintenance/plans/:id/edit). */
@Component({
  selector: 'app-plan-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
  ],
  templateUrl: './plan-form.html',
  styles: `
    .form {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 4px 16px;
      max-width: 820px;
    }
    .wide {
      grid-column: 1 / -1;
    }
    .form-error {
      color: var(--fm-bad);
      margin: 0 0 12px;
    }
    .buttons {
      display: flex;
      gap: 12px;
      margin-top: 8px;
    }
  `,
})
export class PlanForm implements OnInit {
  private readonly api = inject(MaintenanceApi);
  private readonly router = inject(Router);
  protected readonly assets = inject(AssetDirectory);

  /** Route parameter when editing. */
  readonly id = input<string>();
  /** Query parameter to start a plan from a machine's page. */
  readonly assetId = input<string>();

  protected readonly priorities = WORK_ORDER_PRIORITIES;
  protected readonly hints = SCHEDULE_HINTS;
  protected readonly humanize = humanize;
  protected readonly today = todayUtc();
  protected readonly saving = signal(false);
  protected readonly loaded = signal(true);

  protected readonly form = inject(NonNullableFormBuilder).group(
    {
      assetId: ['', Validators.required],
      title: ['', [Validators.required, Validators.maxLength(160)]],
      instructions: ['', Validators.maxLength(2000)],
      schedule: ['FIXED' as PlanSchedule, Validators.required],
      intervalDays: [30, [Validators.required, Validators.min(1), Validators.max(3650)]],
      leadDays: [3, [Validators.required, Validators.min(0)]],
      priority: ['MEDIUM' as WorkOrderPriority, Validators.required],
      assignee: ['', Validators.maxLength(100)],
      nextDueDate: [this.today, Validators.required],
    },
    { validators: leadShorterThanInterval },
  );

  ngOnInit(): void {
    const id = this.id();
    if (!id) {
      this.form.patchValue({ assetId: this.assetId() ?? '' });
      return;
    }
    this.loaded.set(false);
    this.api.plan(id).subscribe((plan) => {
      this.form.patchValue({
        ...plan,
        instructions: plan.instructions ?? '',
        assignee: plan.assignee ?? '',
      });
      this.form.controls.assetId.disable();
      this.loaded.set(true);
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const request: PlanRequest = {
      ...value,
      intervalDays: Number(value.intervalDays),
      leadDays: Number(value.leadDays),
      instructions: value.instructions.trim() || null,
      assignee: value.assignee.trim() || null,
    };
    const id = this.id();
    this.saving.set(true);
    (id ? this.api.updatePlan(id, request) : this.api.createPlan(request)).subscribe({
      next: (plan) => this.router.navigate(['/maintenance/plans', plan.id]),
      error: () => this.saving.set(false),
    });
  }
}
