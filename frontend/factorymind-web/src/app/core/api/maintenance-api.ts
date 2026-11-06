import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  CreateWorkOrderRequest,
  MaintenancePlan,
  MaintenanceSummary,
  Page,
  PlanRequest,
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
    if (query.planId) params = params.set('planId', query.planId);
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

  /** Preventive maintenance plans, soonest due first. */
  plans(assetId?: string | null): Observable<MaintenancePlan[]> {
    const params = assetId ? new HttpParams().set('assetId', assetId) : undefined;
    return this.http.get<MaintenancePlan[]>('/api/maintenance/plans', { params });
  }

  plan(id: string): Observable<MaintenancePlan> {
    return this.http.get<MaintenancePlan>(`/api/maintenance/plans/${id}`);
  }

  createPlan(request: PlanRequest): Observable<MaintenancePlan> {
    return this.http.post<MaintenancePlan>('/api/maintenance/plans', request);
  }

  updatePlan(id: string, request: PlanRequest): Observable<MaintenancePlan> {
    return this.http.put<MaintenancePlan>(`/api/maintenance/plans/${id}`, request);
  }

  pausePlan(id: string): Observable<MaintenancePlan> {
    return this.http.post<MaintenancePlan>(`/api/maintenance/plans/${id}/pause`, null);
  }

  resumePlan(id: string): Observable<MaintenancePlan> {
    return this.http.post<MaintenancePlan>(`/api/maintenance/plans/${id}/resume`, null);
  }

  /** Opens the plan's next work order now instead of waiting for its lead time. */
  openPlanWorkOrder(id: string): Observable<WorkOrderSummary> {
    return this.http.post<WorkOrderSummary>(`/api/maintenance/plans/${id}/work-orders`, null);
  }
}
