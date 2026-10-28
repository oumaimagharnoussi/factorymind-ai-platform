import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, filter, map } from 'rxjs';
import { Auth } from '../auth/auth';
import { ReadingPoint, SensorSnapshot, SensorType, TelemetryEvent } from './models';
import { eventStream } from './sse';

export interface ReadingQuery {
  assetId?: string;
  sensorType?: SensorType;
  minutes?: number;
}

@Injectable({ providedIn: 'root' })
export class TelemetryApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(Auth);

  latest(): Observable<SensorSnapshot[]> {
    return this.http.get<SensorSnapshot[]>('/api/telemetry/latest');
  }

  readings(query: ReadingQuery = {}): Observable<ReadingPoint[]> {
    let params = new HttpParams().set('minutes', query.minutes ?? 15);
    if (query.assetId) params = params.set('assetId', query.assetId);
    if (query.sensorType) params = params.set('sensorType', query.sensorType);
    return this.http.get<ReadingPoint[]>('/api/telemetry/readings', { params });
  }

  /** Live readings and status changes; completes every 15 minutes (see {@link eventStream}). */
  stream(): Observable<TelemetryEvent> {
    return eventStream('/api/telemetry/stream', () => this.auth.token()).pipe(
      filter((message) => message.event === 'reading' || message.event === 'status'),
      map((message) => ({ type: message.event, data: JSON.parse(message.data) }) as TelemetryEvent),
    );
  }
}
