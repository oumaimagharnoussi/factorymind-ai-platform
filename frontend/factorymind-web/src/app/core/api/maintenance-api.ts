import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateWorkOrderRequest,
  MaintenanceSummary,
  Page,
  WorkOrder,
  WorkOrderQuery,
  WorkOrderStatus,
  WorkOrderSummary,
} from './models';

@Injectable({ providedIn: 'root' })
export class MaintenanceApi {
  private readonly http = inject(HttpClient);

  /** Newest first. */
  workOrders(query: WorkOrderQuery = {}): Observable<Page<WorkOrderSummary>> {
    let params = new HttpParams().set('page', query.page ?? 0).set('size', query.size ?? 20);
    for (const status of query.statuses ?? []) params = params.append('status', status);
    if (query.priority) params = params.set('priority', query.priority);
    if (query.assetId) params = params.set('assetId', query.assetId);
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    return this.http.get<Page<WorkOrderSummary>>('/api/maintenance/work-orders', { params });
  }

  workOrder(id: string): Observable<WorkOrder> {
    return this.http.get<WorkOrder>(`/api/maintenance/work-orders/${id}`);
  }

  summary(): Observable<MaintenanceSummary> {
    return this.http.get<MaintenanceSummary>('/api/maintenance/summary');
  }

  create(request: CreateWorkOrderRequest): Observable<WorkOrder> {
    return this.http.post<WorkOrder>('/api/maintenance/work-orders', request);
  }

  changeStatus(id: string, status: WorkOrderStatus, note: string | null): Observable<WorkOrder> {
    return this.http.patch<WorkOrder>(`/api/maintenance/work-orders/${id}/status`, { status, note });
  }

  /** Takes the part out of stock (inventory-service) and books it on the work order. */
  usePart(id: string, partId: string, quantity: number): Observable<WorkOrder> {
    return this.http.post<WorkOrder>(`/api/maintenance/work-orders/${id}/parts`, { partId, quantity });
  }

  returnPart(id: string, lineId: string): Observable<WorkOrder> {
    return this.http.delete<WorkOrder>(`/api/maintenance/work-orders/${id}/parts/${lineId}`);
  }
}
