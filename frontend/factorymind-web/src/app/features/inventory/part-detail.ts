import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { InventoryApi } from '../../core/api/inventory-api';
import { StockMovement } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { humanize } from '../../shared/labels';
import { StatusChip } from '../../shared/status-chip';

type StockForm = 'receive' | 'count' | null;

@Component({
  selector: 'app-part-detail',
  imports: [
    CurrencyPipe,
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatTableModule,
    StatusChip,
  ],
  templateUrl: './part-detail.html',
  styleUrl: './inventory.scss',
})
export class PartDetail {
  private readonly api = inject(InventoryApi);
  protected readonly auth = inject(Auth);

  /** Bound from the :id route parameter. */
  readonly id = input.required<string>();

  protected readonly humanize = humanize;
  protected readonly columns = ['performedAt', 'type', 'change', 'after', 'reference', 'by'];

  protected readonly page = signal(0);
  protected readonly part = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.part(params) });
  protected readonly movements = rxResource({
    params: () => ({ id: this.id(), page: this.page() }),
    stream: ({ params }) => this.api.movements(params.id, params.page),
  });

  protected readonly openForm = signal<StockForm>(null);
  protected readonly saving = signal(false);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly receipt = this.fb.group({
    quantity: [1, [Validators.required, Validators.min(1)]],
    reference: ['', Validators.maxLength(60)],
    unitCost: [null as number | null, Validators.min(0)],
  });
  protected readonly count = this.fb.group({
    countedQuantity: [0, [Validators.required, Validators.min(0)]],
    note: ['', [Validators.required, Validators.maxLength(500)]],
  });

  protected show(form: StockForm): void {
    if (form === 'count') {
      this.count.reset({ countedQuantity: this.part.value()?.quantityOnHand ?? 0, note: '' });
    } else {
      this.receipt.reset();
    }
    this.openForm.set(form);
  }

  protected receive(): void {
    if (this.receipt.invalid) return;
    const { quantity, reference, unitCost } = this.receipt.getRawValue();
    this.save(this.api.receive(this.id(), quantity, reference || null, unitCost));
  }

  protected adjust(): void {
    if (this.count.invalid) {
      this.count.markAllAsTouched();
      return;
    }
    const { countedQuantity, note } = this.count.getRawValue();
    this.save(this.api.adjust(this.id(), countedQuantity, note));
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
  }

  private save(call: Observable<StockMovement>): void {
    this.saving.set(true);
    call.subscribe({
      next: () => {
        this.saving.set(false);
        this.openForm.set(null);
        this.part.reload();
        this.page.set(0);
        this.movements.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
