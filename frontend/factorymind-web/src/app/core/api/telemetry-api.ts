import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Auth } from '../auth/auth';
import { ReadingPoint, SensorSnapshot, SensorType, TelemetryEvent } from './models';
import { SseParser } from './sse';

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

  /**
   * Live readings and status changes. The browser's EventSource cannot send an Authorization header,
   * so the stream is read with fetch. It completes when the server closes it (every 15 minutes);
   * callers resubscribe, which also picks up a fresh token.
   */
  stream(): Observable<TelemetryEvent> {
    return new Observable<TelemetryEvent>((subscriber) => {
      const abort = new AbortController();
      (async () => {
        const token = await this.auth.token();
        const response = await fetch('/api/telemetry/stream', {
          headers: {
            Accept: 'text/event-stream',
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          signal: abort.signal,
        });
        if (!response.ok || !response.body) {
          throw new Error(`Live stream refused (${response.status})`);
        }
        const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
        const parser = new SseParser();
        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          for (const message of parser.push(value)) {
            if (message.event === 'reading' || message.event === 'status') {
              subscriber.next({
                type: message.event,
                data: JSON.parse(message.data),
              } as TelemetryEvent);
            }
          }
        }
        subscriber.complete();
      })().catch((error: unknown) => {
        if (!abort.signal.aborted) subscriber.error(error);
      });
      return () => abort.abort();
    });
  }
}
