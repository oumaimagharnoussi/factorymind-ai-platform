import { Injectable, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { AssetApi } from './asset-api';
import { Asset } from './models';

/**
 * Alerts and work orders only carry an asset id (services never share tables). This loads the machine
 * list once and lets every screen show "M-104 Electric Motor Drive" instead of a UUID.
 */
@Injectable({ providedIn: 'root' })
export class AssetDirectory {
  private readonly api = inject(AssetApi);

  private readonly assets = rxResource({
    stream: () => this.api.assets({ size: 500 }).pipe(map((page) => page.content)),
  });

  readonly list = computed(() => this.assets.value() ?? []);
  private readonly byId = computed(() => new Map(this.list().map((asset) => [asset.id, asset])));

  get(id: string): Asset | undefined {
    return this.byId().get(id);
  }

  /** "M-104" when the machine is known, a short id otherwise. */
  code(id: string): string {
    return this.get(id)?.assetCode ?? id.slice(0, 8);
  }

  name(id: string): string {
    return this.get(id)?.name ?? '';
  }
}
