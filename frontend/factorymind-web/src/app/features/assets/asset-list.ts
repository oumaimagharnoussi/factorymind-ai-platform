import { Component, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { AssetApi } from '../../core/api/asset-api';
import { Auth } from '../../core/auth/auth';
import { ASSET_STATUSES, AssetQuery, AssetStatus, CRITICALITIES, Criticality } from '../../core/api/models';
import { StatusChip } from '../../shared/status-chip';

@Component({
  selector: 'app-asset-list',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatTableModule,
    MatPaginatorModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatProgressBarModule,
    StatusChip,
  ],
  templateUrl: './asset-list.html',
  styleUrl: './asset-list.scss',
})
export class AssetList {
  private readonly api = inject(AssetApi);
  protected readonly auth = inject(Auth);

  protected readonly statuses = ASSET_STATUSES;
  protected readonly criticalities = CRITICALITIES;
  protected readonly columns = ['assetCode', 'name', 'location', 'criticality', 'status'];

  protected readonly filters = inject(NonNullableFormBuilder).group({
    q: '',
    status: null as AssetStatus | null,
    criticality: null as Criticality | null,
    factoryId: null as string | null,
  });

  protected readonly query = signal<AssetQuery>({ page: 0, size: 20 });

  protected readonly factories = rxResource({ stream: () => this.api.factories() });
  protected readonly assets = rxResource({ params: () => this.query(), stream: ({ params }) => this.api.assets(params) });

  constructor() {
    this.filters.valueChanges.pipe(debounceTime(300), takeUntilDestroyed()).subscribe((value) =>
      this.query.update((current) => ({ ...current, ...value, page: 0 })),
    );
  }

  protected onPage(event: PageEvent): void {
    this.query.update((current) => ({ ...current, page: event.pageIndex, size: event.pageSize }));
  }

  protected clearFilters(): void {
    this.filters.reset();
  }
}
