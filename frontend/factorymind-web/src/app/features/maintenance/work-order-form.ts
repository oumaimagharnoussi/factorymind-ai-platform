import { Component, OnInit, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { AssetDirectory } from '../../core/api/asset-directory';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import { WORK_ORDER_PRIORITIES, WORK_ORDER_TYPES, WorkOrderPriority, WorkOrderType } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { humanize } from '../../shared/labels';

/**
 * Request a maintenance job. Opened from an alert, the machine, alert, title and priority come pre-filled
 * from the query string (bound to the inputs below by withComponentInputBinding).
 */
@Component({
  selector: 'app-work-order-form',
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
  templateUrl: './work-order-form.html',
  styles: `
    .form {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 4px 16px;
      max-width: 760px;
    }
    .wide {
      grid-column: 1 / -1;
    }
    .from-alert {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 16px;
      color: var(--fm-bad);
    }
    .buttons {
      display: flex;
      gap: 12px;
      margin-top: 8px;
    }
  `,
})
export class WorkOrderForm implements OnInit {
  private readonly api = inject(MaintenanceApi);
  private readonly router = inject(Router);
  protected readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  readonly alertId = input<string>();
  readonly assetId = input<string>();
  readonly title = input<string>();
  readonly priority = input<WorkOrderPriority>();

  protected readonly types = WORK_ORDER_TYPES;
  protected readonly priorities = WORK_ORDER_PRIORITIES;
  protected readonly humanize = humanize;
  protected readonly saving = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    assetId: ['', Validators.required],
    type: ['CORRECTIVE' as WorkOrderType, Validators.required],
    priority: ['MEDIUM' as WorkOrderPriority, Validators.required],
    title: ['', [Validators.required, Validators.maxLength(160)]],
    description: ['', Validators.maxLength(2000)],
    assignee: ['', Validators.maxLength(100)],
    dueDate: [''],
  });

  ngOnInit(): void {
    this.form.patchValue({
      assetId: this.assetId() ?? '',
      title: this.title() ?? '',
      priority: this.priority() ?? 'MEDIUM',
    });
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .create({
        ...value,
        alertId: this.alertId() ?? null,
        description: value.description || null,
        assignee: value.assignee || null,
        dueDate: value.dueDate || null,
      })
      .subscribe({
        next: (workOrder) => this.router.navigate(['/maintenance/orders', workOrder.id]),
        error: () => this.saving.set(false),
      });
  }
}
