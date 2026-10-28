import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CreatePartRequest, InventorySummary, Page, PartQuery, SparePart, StockMovement } from './models';

@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);

  parts(query: PartQuery = {}): Observable<Page<SparePart>> {
    let params = new HttpParams()
      .set('page', query.page ?? 0)
      .set('size', query.size ?? 20)
      .set('sort', 'partNumber,asc');
    if (query.q?.trim()) params = params.set('q', query.q.trim());
    if (query.category) params = params.set('category', query.category);
    if (query.lowStock) params = params.set('lowStock', true);
    return this.http.get<Page<SparePart>>('/api/inventory/parts', { params });
  }

  part(id: string): Observable<SparePart> {
    return this.http.get<SparePart>(`/api/inventory/parts/${id}`);
  }

  summary(): Observable<InventorySummary> {
    return this.http.get<InventorySummary>('/api/inventory/summary');
  }

  movements(partId: string, page = 0): Observable<Page<StockMovement>> {
    const params = new HttpParams().set('page', page).set('size', 20);
    return this.http.get<Page<StockMovement>>(`/api/inventory/parts/${partId}/movements`, { params });
  }

  create(request: CreatePartRequest): Observable<SparePart> {
    return this.http.post<SparePart>('/api/inventory/parts', request);
  }

  receive(partId: string, quantity: number, reference: string | null, unitCost: number | null): Observable<StockMovement> {
    return this.http.post<StockMovement>(`/api/inventory/parts/${partId}/receipts`, { quantity, reference, unitCost });
  }

  adjust(partId: string, countedQuantity: number, note: string): Observable<StockMovement> {
    return this.http.post<StockMovement>(`/api/inventory/parts/${partId}/adjustments`, { countedQuantity, note });
  }
}
