import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, booleanAttribute, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { InventoryApi } from '../../core/api/inventory-api';
import { PART_CATEGORIES, PartCategory, PartQuery } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { humanize } from '../../shared/labels';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-part-list',
  imports: [
    CurrencyPipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTableModule,
    StatusChip,
  ],
  templateUrl: './part-list.html',
  styleUrl: './inventory.scss',
})
export class PartList implements OnInit {
  private readonly api = inject(InventoryApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(Auth);

  /** `?lowStock=true` from the dashboard's "Parts to reorder" card. */
  readonly lowStock = input(false, { transform: booleanAttribute });

  protected readonly categories = PART_CATEGORIES;
  protected readonly humanize = humanize;
  protected readonly columns = ['partNumber', 'name', 'category', 'stock', 'unitCost', 'location'];

  protected readonly filters = inject(NonNullableFormBuilder).group({
    q: '',
    category: null as PartCategory | null,
    lowStock: false,
  });
  protected readonly query = signal<PartQuery>({ page: 0, size: 20 });

  protected readonly summary = rxResource({ stream: () => this.api.summary() });
  protected readonly parts = rxResource({ params: () => this.query(), stream: ({ params }) => this.api.parts(params) });

  protected readonly showForm = signal(false);
  protected readonly saving = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    partNumber: ['', [Validators.required, Validators.maxLength(40), Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9./-]*$/)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    category: ['MECHANICAL' as PartCategory, Validators.required],
    unit: ['pcs', [Validators.required, Validators.maxLength(20)]],
    minimumStock: [1, [Validators.required, Validators.min(0)]],
    unitCost: [0, [Validators.required, Validators.min(0)]],
    location: ['', Validators.maxLength(40)],
  });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) =>
      this.query.update((current) => ({ ...current, ...value, page: 0 })),
    );
  }

  ngOnInit(): void {
    if (this.lowStock()) {
      this.filters.patchValue({ lowStock: true }, { emitEvent: false });
      this.query.update((current) => ({ ...current, lowStock: true }));
    }
  }

  protected onPage(event: PageEvent): void {
    this.query.update((current) => ({ ...current, page: event.pageIndex, size: event.pageSize }));
  }

  protected create(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.api.create({ ...value, location: value.location || null }).subscribe({
      next: (part) => this.router.navigate(['/inventory', part.id]),
      error: () => this.saving.set(false),
    });
  }
}
