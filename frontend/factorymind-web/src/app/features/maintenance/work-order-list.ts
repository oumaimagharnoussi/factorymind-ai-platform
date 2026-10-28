import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { MaintenanceApi } from '../../core/api/maintenance-api';
import {
  OPEN_WORK_ORDER_STATUSES,
  WORK_ORDER_PRIORITIES,
  WorkOrderPriority,
  WorkOrderQuery,
  WorkOrderStatus,
} from '../../core/api/models';
import { humanize } from '../../shared/labels';
import { StatusChip } from '../../shared/status-chip';

type View = 'open' | 'closed' | 'all';

const VIEWS: Record<View, WorkOrderStatus[]> = {
  open: OPEN_WORK_ORDER_STATUSES,
  closed: ['COMPLETED', 'CANCELLED'],
  all: [],
};

@Component({
  selector: 'app-work-order-list',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
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
  templateUrl: './work-order-list.html',
  styleUrl: './maintenance.scss',
})
export class WorkOrderList {
  private readonly api = inject(MaintenanceApi);
  protected readonly assets = inject(AssetDirectory);

  protected readonly priorities = WORK_ORDER_PRIORITIES;
  protected readonly humanize = humanize;
  protected readonly columns = ['code', 'machine', 'title', 'priority', 'status', 'assignee', 'due'];

  protected readonly view = signal<View>('open');
  protected readonly filters = inject(NonNullableFormBuilder).group({
    q: '',
    priority: null as WorkOrderPriority | null,
    assetId: null as string | null,
  });
  protected readonly query = signal<WorkOrderQuery>({ statuses: VIEWS.open, page: 0, size: 20 });

  protected readonly summary = rxResource({ stream: () => this.api.summary() });
  protected readonly workOrders = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.workOrders(params),
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) =>
      this.query.update((current) => ({ ...current, ...value, page: 0 })),
    );
  }

  protected setView(view: View): void {
    this.view.set(view);
    this.query.update((current) => ({ ...current, statuses: VIEWS[view], page: 0 }));
  }

  protected onPage(event: PageEvent): void {
    this.query.update((current) => ({ ...current, page: event.pageIndex, size: event.pageSize }));
  }
}
