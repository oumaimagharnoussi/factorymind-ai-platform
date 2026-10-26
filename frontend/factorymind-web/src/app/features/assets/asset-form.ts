import { Component, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { AssetApi } from '../../core/api/asset-api';
import { CRITICALITIES, Criticality } from '../../core/api/models';

@Component({
  selector: 'app-asset-form',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
  ],
  templateUrl: './asset-form.html',
  styles: `
    .form {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 4px 16px;
      max-width: 760px;
    }
    .buttons {
      display: flex;
      gap: 12px;
      margin-top: 8px;
    }
  `,
})
export class AssetForm {
  private readonly api = inject(AssetApi);
  private readonly router = inject(Router);

  protected readonly criticalities = CRITICALITIES;
  protected readonly saving = signal(false);

  protected readonly form = inject(NonNullableFormBuilder).group({
    assetCode: ['', [Validators.required, Validators.maxLength(40), Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9-]*$/)]],
    name: ['', [Validators.required, Validators.maxLength(120)]],
    factoryId: ['', Validators.required],
    lineId: ['', Validators.required],
    manufacturer: [''],
    model: [''],
    criticality: ['MEDIUM' as Criticality, Validators.required],
    installedAt: [''],
  });

  private readonly factoryId = toSignal(this.form.controls.factoryId.valueChanges, { initialValue: '' });

  protected readonly factories = rxResource({ stream: () => this.api.factories() });
  /** Lines depend on the chosen factory, so the line list reloads whenever the factory changes. */
  protected readonly lines = rxResource({
    params: () => this.factoryId(),
    stream: ({ params }) => (params ? this.api.lines(params) : of([])),
  });

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const { factoryId: _factory, installedAt, ...value } = this.form.getRawValue();
    this.saving.set(true);
    this.api.createAsset({ ...value, installedAt: installedAt || null }).subscribe({
      next: (asset) => this.router.navigate(['/assets', asset.id]),
      error: () => this.saving.set(false),
    });
  }
}
