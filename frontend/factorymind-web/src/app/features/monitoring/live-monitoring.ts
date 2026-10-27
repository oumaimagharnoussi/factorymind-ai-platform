import { DatePipe, DecimalPipe, LowerCasePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { concat, defer, forkJoin, ignoreElements, map, repeat, retry, tap, timer } from 'rxjs';
import { AssetApi } from '../../core/api/asset-api';
import { Asset, SensorStatusChange } from '../../core/api/models';
import { TelemetryApi } from '../../core/api/telemetry-api';
import { describeError } from '../../core/http/problem';
import { Sparkline } from '../../shared/sparkline';
import { StatusChip } from '../../shared/status-chip';
import { SensorView, TelemetryBoard, severity, worst } from './telemetry-board';

type Connection = 'connecting' | 'live' | 'reconnecting';

const MAX_CHANGES = 15;
const SENSOR_LABELS: Record<string, string> = {
  TEMPERATURE: 'Temperature',
  VIBRATION: 'Vibration',
  PRESSURE: 'Pressure',
  ROTATION_SPEED: 'Rotation speed',
};

interface MachineView {
  assetId: string;
  asset: Asset | undefined;
  status: ReturnType<typeof worst>;
  sensors: SensorView[];
}

/**
 * Every sensor of the plant, live: first the latest values and 10 minutes of history over REST,
 * then each new reading from the telemetry stream as soon as Kafka delivers it.
 */
@Component({
  selector: 'app-live-monitoring',
  imports: [
    DatePipe,
    DecimalPipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatIconModule,
    MatProgressBarModule,
    Sparkline,
    StatusChip,
  ],
  templateUrl: './live-monitoring.html',
  styleUrl: './live-monitoring.scss',
})
export class LiveMonitoring {
  private readonly telemetry = inject(TelemetryApi);
  private readonly assetApi = inject(AssetApi);

  protected readonly labels = SENSOR_LABELS;
  protected readonly board = signal<TelemetryBoard | null>(null);
  protected readonly changes = signal<SensorStatusChange[]>([]);
  protected readonly connection = signal<Connection>('connecting');
  protected readonly error = signal<string | null>(null);

  private readonly assets = rxResource({
    stream: () =>
      this.assetApi
        .assets({ size: 200 })
        .pipe(map((page) => new Map(page.content.map((a) => [a.id, a])))),
  });

  /** Machines with a problem first, then by machine code. */
  protected readonly machines = computed<MachineView[]>(() => {
    const board = this.board();
    if (!board) return [];
    const assets = this.assets.value();
    return [...board.byAsset()]
      .map(([assetId, sensors]) => ({
        assetId,
        asset: assets?.get(assetId),
        status: worst(sensors.map((view) => view.snapshot.status)),
        sensors,
      }))
      .sort(
        (a, b) =>
          severity(b.status) - severity(a.status) || this.code(a).localeCompare(this.code(b)),
      );
  });

  protected readonly counts = computed(() => {
    const board = this.board();
    return {
      normal: board?.count('NORMAL') ?? 0,
      warning: board?.count('WARNING') ?? 0,
      critical: board?.count('CRITICAL') ?? 0,
    };
  });

  constructor() {
    const initial = forkJoin([
      this.telemetry.latest(),
      this.telemetry.readings({ minutes: 10 }),
    ]).pipe(
      tap(([latest, history]) => this.board.set(TelemetryBoard.from(latest, history))),
      ignoreElements(),
    );
    const live = defer(() => this.telemetry.stream()).pipe(
      tap({ subscribe: () => this.connection.set('connecting') }),
      // The server ends each stream after 15 minutes; open the next one straight away.
      repeat({ delay: 500 }),
      retry({
        delay: (_error, attempt) => {
          this.connection.set('reconnecting');
          return timer(Math.min(1000 * 2 ** attempt, 15_000));
        },
      }),
    );

    concat(initial, live)
      .pipe(takeUntilDestroyed())
      .subscribe({
        next: (event) => {
          this.connection.set('live');
          if (event.type === 'reading') {
            this.board.update((board) => (board ?? TelemetryBoard.from([], [])).apply(event.data));
          } else if (event.data.previousStatus !== null) {
            this.changes.update((changes) => [event.data, ...changes].slice(0, MAX_CHANGES));
          }
        },
        error: (error: unknown) => this.error.set(describeError(error)),
      });
  }

  protected code(machine: { assetId: string; asset?: Asset }): string {
    return machine.asset?.assetCode ?? machine.assetId.slice(0, 8);
  }

  protected machineCode(assetId: string): string {
    return this.assets.value()?.get(assetId)?.assetCode ?? assetId.slice(0, 8);
  }
}
