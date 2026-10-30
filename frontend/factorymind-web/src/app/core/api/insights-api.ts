import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CopilotAnswer,
  CopilotStatus,
  CopilotTurn,
  DocumentKind,
  KnowledgeDocument,
  KnowledgeDocumentDetail,
  MachineReport,
  MachineRisk,
  ModelCard,
  RiskPoint,
} from './models';

/**
 * ai-insights-service: failure risks from the Python model, reports from the language model, and the
 * maintenance copilot that answers from the machine manuals.
 */
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

  report(assetId: string): Observable<MachineReport> {
    return this.http.post<MachineReport>(`/api/insights/assets/${assetId}/report`, null);
  }

  copilot(): Observable<CopilotStatus> {
    return this.http.get<CopilotStatus>('/api/insights/copilot');
  }

  /** The server keeps no conversation: the earlier turns travel with each question. */
  askCopilot(
    question: string,
    assetId: string | null,
    history: CopilotTurn[],
  ): Observable<CopilotAnswer> {
    return this.http.post<CopilotAnswer>('/api/insights/copilot/ask', {
      question,
      assetId,
      history,
    });
  }

  documents(): Observable<KnowledgeDocument[]> {
    return this.http.get<KnowledgeDocument[]>('/api/insights/knowledge/documents');
  }

  document(id: string): Observable<KnowledgeDocumentDetail> {
    return this.http.get<KnowledgeDocumentDetail>(`/api/insights/knowledge/documents/${id}`);
  }

  uploadDocument(
    file: File,
    options: { title?: string; kind?: DocumentKind; machines?: string[] },
  ): Observable<KnowledgeDocumentDetail> {
    const form = new FormData();
    form.append('file', file);
    if (options.title?.trim()) form.append('title', options.title.trim());
    if (options.kind) form.append('kind', options.kind);
    if (options.machines?.length) form.append('machines', options.machines.join(','));
    return this.http.post<KnowledgeDocumentDetail>('/api/insights/knowledge/documents', form);
  }

  deleteDocument(id: string): Observable<void> {
    return this.http.delete<void>(`/api/insights/knowledge/documents/${id}`);
  }
}
