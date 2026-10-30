import { Component, computed, input } from '@angular/core';

export interface MonthBar {
  /** 2026-10 */
  month: string;
  value: number | null;
}

const WIDTH = 320;
const HEIGHT = 120;
const BASE = 100;
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/**
 * One measure per month as thin bars from a zero baseline, the latest one labelled. Hovering a bar shows its
 * value; the same numbers are in the table below the charts.
 */
@Component({
  selector: 'app-month-bars',
  template: `
    <svg [attr.viewBox]="'0 0 ' + width + ' ' + height" role="img" [attr.aria-label]="label()">
      <line class="base" x1="0" [attr.x2]="width" [attr.y1]="base" [attr.y2]="base" />
      @for (bar of bars(); track bar.month) {
        <g>
          <title>{{ bar.monthLabel }}: {{ bar.text }}</title>
          <rect
            class="hit"
            [attr.x]="bar.x - bar.slot / 2"
            y="0"
            [attr.width]="bar.slot"
            [attr.height]="base"
          />
          @if (bar.h > 0) {
            <path class="bar" [class.latest]="bar.latest" [attr.d]="bar.path" />
          }
          <text class="month" [attr.x]="bar.x" [attr.y]="base + 14">{{ bar.monthLabel }}</text>
          @if (bar.latest) {
            <text class="value" [attr.x]="bar.x" [attr.y]="base - bar.h - 5">{{ bar.text }}</text>
          }
        </g>
      }
    </svg>
  `,
  styles: `
    :host {
      display: block;
    }
    svg {
      width: 100%;
      height: auto;
      overflow: visible;
    }
    .base {
      stroke: var(--fm-border);
      stroke-width: 1;
    }
    .hit {
      fill: transparent;
    }
    .bar {
      fill: var(--fm-accent);
      opacity: 0.55;
    }
    .bar.latest {
      opacity: 1;
    }
    g:hover .bar {
      opacity: 1;
    }
    text {
      font-size: 10px;
      text-anchor: middle;
      fill: var(--fm-text-muted);
      font-variant-numeric: tabular-nums;
    }
    .value {
      fill: var(--mat-sys-on-surface);
      font-weight: 600;
    }
  `,
})
export class MonthBars {
  readonly data = input.required<MonthBar[]>();
  readonly label = input.required<string>();
  readonly format = input<(value: number) => string>((v) => String(v));

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;
  protected readonly base = BASE;

  protected readonly bars = computed(() => {
    const data = this.data();
    const max = Math.max(...data.map((d) => d.value ?? 0), 0) || 1;
    const slot = WIDTH / Math.max(data.length, 1);
    const barWidth = Math.min(28, slot * 0.6);
    return data.map((d, i) => {
      const x = slot * i + slot / 2;
      const h = ((d.value ?? 0) / max) * (BASE - 18);
      return {
        month: d.month,
        monthLabel: MONTHS[Number(d.month.slice(5, 7)) - 1] ?? d.month,
        x,
        h,
        slot,
        latest: i === data.length - 1,
        text: d.value == null ? 'no data' : this.format()(d.value),
        path: roundedTop(x - barWidth / 2, BASE - h, barWidth, h, Math.min(4, h)),
      };
    });
  });
}

/** A bar with 4px rounded corners at the data end and a square base on the axis. */
function roundedTop(x: number, y: number, w: number, h: number, r: number): string {
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`;
}
