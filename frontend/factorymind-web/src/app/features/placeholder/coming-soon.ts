import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

/** Stands in for screens planned in later phases of the roadmap. Title and phase come from route data. */
@Component({
  selector: 'app-coming-soon',
  imports: [MatCardModule, MatIconModule],
  template: `
    <header class="page-header">
      <div>
        <h1>{{ title() }}</h1>
        <p class="subtitle">Planned for {{ phase() }}.</p>
      </div>
    </header>
    <mat-card>
      <mat-card-content class="body">
        <mat-icon>construction</mat-icon>
        <p>{{ description() }}</p>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .body {
      display: flex;
      gap: 16px;
      align-items: center;
      color: var(--fm-text-muted);
    }
  `,
})
export class ComingSoon {
  readonly title = input('');
  readonly phase = input('');
  readonly description = input('');
}
