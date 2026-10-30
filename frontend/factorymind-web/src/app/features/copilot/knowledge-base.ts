import { DatePipe } from '@angular/common';
import { Component, computed, inject, input, model, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { of } from 'rxjs';
import { AssetDirectory } from '../../core/api/asset-directory';
import { InsightsApi } from '../../core/api/insights-api';
import { CopilotStatus, DocumentKind, KnowledgeDocument } from '../../core/api/models';
import { Auth } from '../../core/auth/auth';
import { describeError } from '../../core/http/problem';
import { renderMarkdown } from '../../shared/markdown';
import { StatusChip } from '../../shared/status-chip';

/**
 * What the copilot reads: the built-in manuals and procedures, and the documents engineers add. Selecting a
 * document shows it cut into passages, the way the copilot searches it.
 */
@Component({
  selector: 'app-knowledge-base',
  imports: [
    DatePipe,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTooltipModule,
    StatusChip,
  ],
  templateUrl: './knowledge-base.html',
  styleUrl: './knowledge-base.scss',
})
export class KnowledgeBase {
  private readonly api = inject(InsightsApi);
  private readonly assets = inject(AssetDirectory);
  protected readonly auth = inject(Auth);

  readonly status = input<CopilotStatus | null>(null);
  readonly selected = model<string | null>(null);
  /** A document was added or removed. */
  readonly changed = output<void>();

  protected readonly canEdit = computed(() => this.auth.hasAnyRole('ADMIN', 'ENGINEER'));
  protected readonly documents = rxResource({ stream: () => this.api.documents() });
  protected readonly detail = rxResource({
    params: () => this.selected(),
    stream: ({ params: id }) => (id ? this.api.document(id) : of(null)),
  });

  /** Models of the plant's machines, to say which machines a new document is about. */
  protected readonly models = computed(() => {
    const byModel = new Map<string, string[]>();
    for (const asset of this.assets.list()) {
      if (asset.model)
        byModel.set(asset.model, [...(byModel.get(asset.model) ?? []), asset.assetCode]);
    }
    return [...byModel.entries()]
      .map(([model, codes]) => ({ model, codes: codes.join(', ') }))
      .sort((a, b) => a.model.localeCompare(b.model));
  });

  protected readonly kinds: DocumentKind[] = ['MANUAL', 'PROCEDURE'];
  protected file: File | null = null;
  protected title = '';
  protected kind: DocumentKind = 'MANUAL';
  protected machines: string[] = [];
  protected readonly uploading = signal(false);
  protected readonly message = signal<{ text: string; error: boolean } | null>(null);

  protected pick(event: Event): void {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
    this.message.set(null);
  }

  protected upload(input: HTMLInputElement): void {
    if (!this.file || this.uploading()) return;
    this.uploading.set(true);
    this.message.set(null);
    this.api
      .uploadDocument(this.file, { title: this.title, kind: this.kind, machines: this.machines })
      .subscribe({
        next: (detail) => {
          this.uploading.set(false);
          this.message.set({
            text: `"${detail.document.title}" added: ${detail.document.chunks} passages.`,
            error: false,
          });
          this.file = null;
          this.title = '';
          this.machines = [];
          input.value = '';
          this.documents.reload();
          this.selected.set(detail.document.id);
          this.changed.emit();
        },
        error: (error) => {
          this.uploading.set(false);
          this.message.set({ text: describeError(error), error: true });
        },
      });
  }

  protected remove(document: KnowledgeDocument): void {
    if (!window.confirm(`Remove "${document.title}" from the knowledge base?`)) return;
    this.api.deleteDocument(document.id).subscribe({
      next: () => {
        if (this.selected() === document.id) this.selected.set(null);
        this.documents.reload();
        this.changed.emit();
      },
      error: (error) => this.message.set({ text: describeError(error), error: true }),
    });
  }

  protected machinesLabel(document: KnowledgeDocument): string {
    if (!document.machineModels.length) return 'Every machine';
    return document.machineModels
      .map((model) => {
        const known = this.models().find((m) => m.model.toLowerCase() === model);
        return known ? `${known.model} (${known.codes})` : model;
      })
      .join(', ');
  }

  protected embedding(document: KnowledgeDocument): string | null {
    const status = this.status();
    if (!status?.knowledge.embeddingModel) return null;
    return document.embeddedChunks === document.chunks
      ? 'embedded'
      : `${document.embeddedChunks}/${document.chunks} embedded`;
  }

  protected chunkHtml(content: string): string {
    return renderMarkdown(content);
  }
}
