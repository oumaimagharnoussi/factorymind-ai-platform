import { Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { AssetApi } from '../../core/api/asset-api';
import { Factory, ProductionLine } from '../../core/api/models';
import { StatusChip } from '../../shared/status-chip';

interface FactoryWithLines extends Factory {
  lines: ProductionLine[];
}

@Component({
  selector: 'app-factory-list',
  imports: [RouterLink, MatCardModule, MatIconModule, MatProgressBarModule, StatusChip],
  template: `
    <header class="page-header">
      <div>
        <h1>Factories</h1>
        <p class="subtitle">Sites and their production lines.</p>
      </div>
    </header>

    @if (data.isLoading()) {
      <mat-progress-bar mode="indeterminate" />
    }
    @if (data.error()) {
      <div class="error-banner" role="alert">Factories could not be loaded.</div>
    }

    <section class="factories">
      @for (f of factories(); track f.id) {
        <mat-card>
          <mat-card-header>
            <mat-icon mat-card-avatar>factory</mat-icon>
            <mat-card-title>{{ f.name }}</mat-card-title>
            <mat-card-subtitle>{{ f.location }} · {{ f.timezone }}</mat-card-subtitle>
          </mat-card-header>
          <mat-card-content>
            <ul class="lines">
              @for (l of f.lines; track l.id) {
                <li>
                  <span>{{ l.name }}</span>
                  <span class="count">{{ l.assetCount }} machines</span>
                  <app-status-chip [value]="l.status" />
                </li>
              } @empty {
                <li class="empty">No production line yet.</li>
              }
            </ul>
            <a class="link" routerLink="/assets">View machines</a>
          </mat-card-content>
        </mat-card>
      }
    </section>
  `,
  styles: `
    .factories {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
      gap: 16px;
    }
    .lines {
      list-style: none;
      padding: 0;
      margin: 8px 0 12px;
    }
    .lines li {
      display: grid;
      grid-template-columns: 1fr auto auto;
      gap: 12px;
      align-items: center;
      padding: 8px 0;
      border-bottom: 1px solid var(--fm-border);
    }
    .count,
    .empty {
      color: var(--fm-text-muted);
      font-size: 13px;
    }
  `,
})
export class FactoryList {
  private readonly api = inject(AssetApi);

  protected readonly data = rxResource({
    stream: () => forkJoin({ factories: this.api.factories(), lines: this.api.lines() }),
  });

  protected readonly factories = computed<FactoryWithLines[]>(() => {
    const data = this.data.value();
    if (!data) return [];
    return data.factories.map((f) => ({ ...f, lines: data.lines.filter((l) => l.factoryId === f.id) }));
  });
}
