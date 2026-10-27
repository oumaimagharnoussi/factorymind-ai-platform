import { Component, computed, input } from '@angular/core';

/** Coloured pill for machine status, line status, criticality and sensor reading status values. */
@Component({
  selector: 'app-status-chip',
  template: `<span class="chip" [class]="tone()">{{ label() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 999px;
      font-size: 12px;
      font-weight: 600;
      letter-spacing: 0.02em;
      white-space: nowrap;
    }
    .ok { background: var(--fm-ok-bg); color: var(--fm-ok); }
    .warn { background: var(--fm-warn-bg); color: var(--fm-warn); }
    .bad { background: var(--fm-bad-bg); color: var(--fm-bad); }
    .info { background: var(--fm-info-bg); color: var(--fm-info); }
    .muted { background: var(--fm-muted-bg); color: var(--fm-muted); }
  `,
})
export class StatusChip {
  readonly value = input.required<string>();

  protected readonly label = computed(() => this.value().replace('_', ' ').toLowerCase());

  protected readonly tone = computed(() => {
    switch (this.value()) {
      case 'OPERATIONAL':
      case 'RUNNING':
      case 'NORMAL':
      case 'LOW':
        return 'chip ok';
      case 'WARNING':
      case 'HIGH':
        return 'chip warn';
      case 'STOPPED':
      case 'CRITICAL':
        return 'chip bad';
      case 'MAINTENANCE':
      case 'MEDIUM':
        return 'chip info';
      default:
        return 'chip muted';
    }
  });
}
