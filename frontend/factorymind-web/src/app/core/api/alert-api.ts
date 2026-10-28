import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { Auth } from '../auth/auth';
import { Alert, AlertEvent, AlertQuery, AlertSummary, Page } from './models';
import { eventStream } from './sse';

@Injectable({ providedIn: 'root' })
export class AlertApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(Auth);

  /** Most recently raised first. */
  alerts(query: AlertQuery = {}): Observable<Page<Alert>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    for (const status of query.statuses ?? []) params = params.append('status', status);
    if (query.severity) params = params.set('severity', query.severity);
    if (query.assetId) params = params.set('assetId', query.assetId);
    return this.http.get<Page<Alert>>('/api/alerts', { params });
  }

  alert(id: string): Observable<Alert> {
    return this.http.get<Alert>(`/api/alerts/${id}`);
  }

  summary(): Observable<AlertSummary> {
    return this.http.get<AlertSummary>('/api/alerts/summary');
  }

  acknowledge(id: string): Observable<Alert> {
    return this.http.post<Alert>(`/api/alerts/${id}/acknowledge`, null);
  }

  resolve(id: string, note: string | null): Observable<Alert> {
    return this.http.post<Alert>(`/api/alerts/${id}/resolve`, { note });
  }

  /** Every change of any alert, as it happens. Completes every 15 minutes; resubscribe. */
  stream(onOpen?: () => void): Observable<AlertEvent> {
    return eventStream('/api/alerts/stream', () => this.auth.token(), onOpen).pipe(
      filter((message) => message.event === 'alert'),
      map((message) => JSON.parse(message.data) as AlertEvent),
    );
  }
}
