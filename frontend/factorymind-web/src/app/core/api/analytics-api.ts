import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AnalyticsMonth,
  AnalyticsOverview,
  AnalyticsStatus,
  AssetReliability,
  EtlRun,
} from './models';

/** analytics-service: KPIs from the warehouse that also feeds the Power BI reports. */
@Injectable({ providedIn: 'root' })
export class AnalyticsApi {
  private readonly http = inject(HttpClient);

  overview(from: string, to: string, factory: string | null): Observable<AnalyticsOverview> {
    return this.http.get<AnalyticsOverview>('/api/analytics/overview', {
      params: period(from, to, factory),
    });
  }

  monthly(months: number, factory: string | null): Observable<AnalyticsMonth[]> {
    let params = new HttpParams().set('months', months);
    if (factory) params = params.set('factory', factory);
    return this.http.get<AnalyticsMonth[]>('/api/analytics/monthly', { params });
  }

  assets(from: string, to: string, factory: string | null): Observable<AssetReliability[]> {
    return this.http.get<AssetReliability[]>('/api/analytics/assets', {
      params: period(from, to, factory),
    });
  }

  status(): Observable<AnalyticsStatus> {
    return this.http.get<AnalyticsStatus>('/api/analytics/status');
  }

  /** Loads the warehouse now (ADMIN). */
  refresh(): Observable<EtlRun> {
    return this.http.post<EtlRun>('/api/analytics/refresh', null);
  }
}

function period(from: string, to: string, factory: string | null): HttpParams {
  let params = new HttpParams().set('from', from).set('to', to);
  if (factory) params = params.set('factory', factory);
  return params;
}
