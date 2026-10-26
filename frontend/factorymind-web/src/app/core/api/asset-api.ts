import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Asset,
  AssetQuery,
  AssetStatus,
  AssetSummary,
  CreateAssetRequest,
  CreateSensorRequest,
  Factory,
  Page,
  ProductionLine,
  Sensor,
} from './models';

/** All calls go through the API gateway; in dev, proxy.conf.json forwards /api to it. */
@Injectable({ providedIn: 'root' })
export class AssetApi {
  private readonly http = inject(HttpClient);

  factories(): Observable<Factory[]> {
    return this.http.get<Factory[]>('/api/factories');
  }

  lines(factoryId?: string): Observable<ProductionLine[]> {
    const params = factoryId ? new HttpParams().set('factoryId', factoryId) : undefined;
    return this.http.get<ProductionLine[]>('/api/production-lines', { params });
  }

  assets(query: AssetQuery): Observable<Page<Asset>> {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.size ?? 20)
      .set('sort', 'assetCode,asc');
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.status) params = params.set('status', query.status);
    if (query.criticality) params = params.set('criticality', query.criticality);
    if (query.factoryId) params = params.set('factoryId', query.factoryId);
    return this.http.get<Page<Asset>>('/api/assets', { params });
  }

  asset(id: string): Observable<Asset> {
    return this.http.get<Asset>(`/api/assets/${id}`);
  }

  summary(): Observable<AssetSummary> {
    return this.http.get<AssetSummary>('/api/assets/summary');
  }

  createAsset(request: CreateAssetRequest): Observable<Asset> {
    return this.http.post<Asset>('/api/assets', request);
  }

  changeStatus(id: string, status: AssetStatus): Observable<Asset> {
    return this.http.patch<Asset>(`/api/assets/${id}/status`, { status });
  }

  sensors(assetId: string): Observable<Sensor[]> {
    return this.http.get<Sensor[]>(`/api/assets/${assetId}/sensors`);
  }

  addSensor(assetId: string, request: CreateSensorRequest): Observable<Sensor> {
    return this.http.post<Sensor>(`/api/assets/${assetId}/sensors`, request);
  }
}
