import { Component, computed, input } from '@angular/core';

const WIDTH = 160;
const HEIGHT = 36;

/** A small line chart of recent values, with the warning and critical limits as dashed lines. */
@Component({
  selector: 'app-sparkline',
  template: `
    <svg
      [attr.viewBox]="'0 0 ' + width + ' ' + height"
      preserveAspectRatio="none"
      role="img"
      [attr.aria-label]="label()"
    >
      @if (warningY(); as y) {
        <line class="limit warn" x1="0" [attr.x2]="width" [attr.y1]="y" [attr.y2]="y" />
      }
      @if (criticalY(); as y) {
        <line class="limit bad" x1="0" [attr.x2]="width" [attr.y1]="y" [attr.y2]="y" />
      }
      <polyline [attr.points]="line()" [class]="tone()" />
    </svg>
  `,
  styles: `
    :host {
      display: block;
    }
    svg {
      width: 100%;
      height: 36px;
      overflow: visible;
    }
    polyline {
      fill: none;
      stroke-width: 1.6;
      stroke-linejoin: round;
      vector-effect: non-scaling-stroke;
    }
    .limit {
      stroke-width: 1;
      stroke-dasharray: 3 3;
      vector-effect: non-scaling-stroke;
    }
    .NORMAL,
    .UNKNOWN {
      stroke: var(--fm-accent);
    }
    .WARNING,
    .warn {
      stroke: var(--fm-warn);
    }
    .CRITICAL,
    .bad {
      stroke: var(--fm-bad);
    }
  `,
})
export class Sparkline {
  readonly values = input.required<number[]>();
  readonly warning = input<number | null>(null);
  readonly critical = input<number | null>(null);
  readonly tone = input<string>('NORMAL');

  protected readonly width = WIDTH;
  protected readonly height = HEIGHT;

  /** Scale covers the values and both limits, so you can see how close a sensor is to its alarm. */
  private readonly range = computed(() => {
    const all = [...this.values(), this.warning() ?? NaN, this.critical() ?? NaN].filter(
      Number.isFinite,
    );
    const min = Math.min(...all, (this.warning() ?? Infinity) * 0.5);
    const max = Math.max(...all);
    const pad = (max - min) * 0.1 || 1;
    return { min: min - pad, max: max + pad };
  });

  protected readonly line = computed(() => {
    const values = this.values();
    const step = values.length > 1 ? WIDTH / (values.length - 1) : 0;
    return values
      .map((value, i) => `${(i * step).toFixed(1)},${this.y(value).toFixed(1)}`)
      .join(' ');
  });

  protected readonly warningY = computed(() => this.limitY(this.warning()));
  protected readonly criticalY = computed(() => this.limitY(this.critical()));

  protected readonly label = computed(() => {
    const values = this.values();
    return values.length
      ? `Last ${values.length} readings, latest ${values[values.length - 1]}`
      : 'No readings yet';
  });

  private limitY(limit: number | null): number | null {
    return limit === null ? null : this.y(limit);
  }

  private y(value: number): number {
    const { min, max } = this.range();
    return HEIGHT - ((value - min) / (max - min)) * HEIGHT;
  }
}
