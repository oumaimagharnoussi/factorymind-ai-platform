import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  AssistantAnswer,
  AssistantStatus,
  MachineReport,
  MachineRisk,
  ModelCard,
  RiskPoint,
} from './models';

/** ai-insights-service: failure risks from the Python model, and reports and answers from the language model. */
@Injectable({ providedIn: 'root' })
export class InsightsApi {
  private readonly http = inject(HttpClient);

  /** Latest risk of every scored machine, highest first. */
  risks(): Observable<MachineRisk[]> {
    return this.http.get<MachineRisk[]>('/api/insights/risks');
  }

  history(assetId: string, minutes = 60): Observable<RiskPoint[]> {
    const params = new HttpParams().set('minutes', minutes);
    return this.http.get<RiskPoint[]>(`/api/insights/risks/${assetId}/history`, { params });
  }

  modelCard(): Observable<ModelCard> {
    return this.http.get<ModelCard>('/api/insights/model');
  }

  assistant(): Observable<AssistantStatus> {
    return this.http.get<AssistantStatus>('/api/insights/assistant');
  }

  report(assetId: string): Observable<MachineReport> {
    return this.http.post<MachineReport>(`/api/insights/assets/${assetId}/report`, null);
  }

  ask(question: string): Observable<AssistantAnswer> {
    return this.http.post<AssistantAnswer>('/api/insights/ask', { question });
  }
}
