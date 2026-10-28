import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Observable, map } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InventoryApi } from '../../core/api/inventory-api';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import { WorkOrder } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { humanize } from '../../shared/labels';
import { StatusChip } from '../../shared/status-chip';
import { StatusAction, statusActions } from './work-order-actions';

const ACTIVITY_ICONS: Record<string, string> = {
  CREATED: 'add_circle',
  UPDATED: 'edit',
  STATUS_CHANGED: 'sync_alt',
  ALERT_LINKED: 'notifications_active',
  PART_USED: 'inventory_2',
  PART_RETURNED: 'undo',
};

@Component({
  selector: 'app-work-order-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    StatusChip,
  ],
  templateUrl: './work-order-detail.html',
  styleUrl: './work-order-detail.scss',
})
export class WorkOrderDetail {
  private readonly api = inject(MaintenanceApi);
  private readonly inventory = inject(InventoryApi);
  protected readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly humanize = humanize;
  protected readonly activityIcons = ACTIVITY_ICONS;
  protected readonly partColumns = ['part', 'quantity', 'cost', 'issued', 'actions'];

  protected readonly workOrder = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.workOrder(params) });
  protected readonly canRun = computed(() => this.auth.hasAnyRole('ADMIN', 'ENGINEER'));
  protected readonly actions = computed(() => {
    const w = this.workOrder.value();
    return w && this.canRun() ? statusActions(w.status, w.nextStatuses) : [];
  });
  protected readonly closed = computed(() => {
    const status = this.workOrder.value()?.status;
    return status === 'COMPLETED' || status === 'CANCELLED';
  });

  /** The status change waiting for its note, if it needs one. */
  protected readonly pending = signal<StatusAction | null>(null);
  protected note = '';
  protected readonly busy = signal(false);

  protected readonly showPartForm = signal(false);
  protected partId: string | null = null;
  protected quantity = 1;
  protected readonly parts = rxResource({
    params: () => (this.showPartForm() ? true : undefined),
    stream: () => this.inventory.parts({ size: 200 }).pipe(map((page) => page.content)),
  });

  protected choose(action: StatusAction): void {
    if (action.note === 'none') {
      this.move(action, null);
    } else {
      this.note = '';
      this.pending.set(action);
    }
  }

  protected confirm(): void {
    const action = this.pending();
    if (!action || (action.note === 'required' && !this.note.trim())) return;
    this.move(action, this.note.trim() || null);
  }

  protected usePart(): void {
    if (!this.partId || this.quantity < 1) return;
    this.run(this.api.usePart(this.id(), this.partId, this.quantity), () => {
      this.showPartForm.set(false);
      this.partId = null;
      this.quantity = 1;
    });
  }

  protected returnPart(lineId: string): void {
    this.run(this.api.returnPart(this.id(), lineId));
  }

  private move(action: StatusAction, note: string | null): void {
    this.run(this.api.changeStatus(this.id(), action.status, note), () => this.pending.set(null));
  }

  private run(call: Observable<WorkOrder>, done?: () => void): void {
    this.busy.set(true);
    call.subscribe({
      next: (updated: WorkOrder) => {
        this.busy.set(false);
        this.workOrder.set(updated);
        done?.();
      },
      error: () => this.busy.set(false),
    });
  }
}
