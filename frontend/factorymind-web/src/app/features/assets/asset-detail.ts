import { DatePipe } from '@angular/common';
import { Component, inject, input, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { AssetApi } from '../../core/api/asset-api';
import { ASSET_STATUSES, AssetStatus, SENSOR_TYPES, SensorType } from '../../core/api/models';
import { StatusChip } from '../../shared/status-chip';

const DEFAULT_UNITS: Record<SensorType, string> = {
  TEMPERATURE: '°C',
  VIBRATION: 'mm/s',
  PRESSURE: 'bar',
  ROTATION_SPEED: 'rpm',
};

@Component({
  selector: 'app-asset-detail',
  imports: [
    DatePipe,
    RouterLink,
    ReactiveFormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    MatTableModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatProgressBarModule,
    StatusChip,
  ],
  templateUrl: './asset-detail.html',
  styleUrl: './asset-detail.scss',
})
export class AssetDetail {
  private readonly api = inject(AssetApi);

  /** Bound from the :id route parameter (withComponentInputBinding). */
  readonly id = input.required<string>();

  protected readonly statuses = ASSET_STATUSES;
  protected readonly sensorTypes = SENSOR_TYPES;
  protected readonly sensorColumns = ['sensorType', 'unit', 'warningThreshold', 'criticalThreshold'];

  protected readonly asset = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.asset(params) });
  protected readonly sensors = rxResource({ params: () => this.id(), stream: ({ params }) => this.api.sensors(params) });

  protected readonly showSensorForm = signal(false);
  protected readonly saving = signal(false);

  protected readonly sensorForm = inject(NonNullableFormBuilder).group({
    sensorType: ['VIBRATION' as SensorType, Validators.required],
    unit: ['mm/s', [Validators.required, Validators.maxLength(20)]],
    warningThreshold: [0, Validators.required],
    criticalThreshold: [0, Validators.required],
  });

  constructor() {
    this.sensorForm.controls.sensorType.valueChanges.pipe(takeUntilDestroyed()).subscribe((type) =>
      this.sensorForm.controls.unit.setValue(DEFAULT_UNITS[type]),
    );
  }

  protected changeStatus(status: AssetStatus): void {
    this.api.changeStatus(this.id(), status).subscribe((updated) => this.asset.set(updated));
  }

  protected addSensor(): void {
    if (this.sensorForm.invalid) return;
    this.saving.set(true);
    this.api.addSensor(this.id(), this.sensorForm.getRawValue()).subscribe({
      next: () => {
        this.saving.set(false);
        this.showSensorForm.set(false);
        this.sensors.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
