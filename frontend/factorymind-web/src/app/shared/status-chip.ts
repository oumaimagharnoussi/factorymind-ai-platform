import { Component, computed, input } from '@angular/core';

/** Coloured pill for status-like enum values: machines, sensors, alerts, work orders and stock. */
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

  protected readonly label = computed(() => this.value().replaceAll('_', ' ').toLowerCase());

  protected readonly tone = computed(() => {
    switch (this.value()) {
      case 'OPERATIONAL':
      case 'RUNNING':
      case 'NORMAL':
      case 'LOW':
      case 'RESOLVED':
      case 'COMPLETED':
      case 'RECEIPT':
      case 'RETURN':
        return 'chip ok';
      case 'WARNING':
      case 'HIGH':
      case 'ON_HOLD':
      case 'LOW_STOCK':
      case 'ACKNOWLEDGED':
        return 'chip warn';
      case 'STOPPED':
      case 'CRITICAL':
      case 'URGENT':
      case 'OUT_OF_STOCK':
        return 'chip bad';
      case 'MAINTENANCE':
      case 'MEDIUM':
      case 'OPEN':
      case 'IN_PROGRESS':
      case 'ISSUE':
        return 'chip info';
      default:
        return 'chip muted';
    }
  });
}
