# ADR 0006: Maintenance copilot with retrieval-augmented generation

- Status: accepted
- Date: 2026-10-30

## Context

The phase 5 assistant (ADR 0005) answers from live data: risks, alerts, work orders. The question a technician
asks next is "what do I do about it?", and that answer is in the machine manuals and plant procedures, not in
the data. A language model alone would answer from memory: plausible steps, invented intervals, wrong torque
values, no safety steps. The answer has to come from the plant's own documents and show where it comes from.

## Decision

### Knowledge base (ai-insights-service, insights_db)

- **Documents:** 9 built-in Markdown files in `src/main/resources/knowledge` (6 machine manuals for the demo
  machines, 3 plant procedures: lockout/tagout, vibration alert, temperature alert), loaded on start and saved
  again only when their SHA-256 changes. Engineers add their own: Markdown (front matter optional), text, or PDF
  with a text layer (Spring AI's PDFBox reader, one section per page). The demo manuals are illustrative content
  written for FactoryMind, not the manufacturers' manuals.
- **Scope by machine model:** a manual declares the models it covers (`machines: HP-400`), matched with the model
  field of asset-service; a procedure covers every machine. A question about M-102 searches only the HP-400
  manual and the procedures, so the copilot cannot answer with the compressor's oil change interval.
- **Passages:** sections by heading (`##`, `###`), then about 1,000 characters, never across a heading, so every
  passage cites as "HP-400 manual › Troubleshooting › Oil overheats". Paragraphs stay whole when they fit.
  Each passage is embedded together with its document title and section (contextual embedding).

### Retrieval: hybrid, fused by rank

- **Keywords:** Postgres full-text search on a generated `tsvector` column (section title weighted above text),
  GIN index, words joined with OR and ranked by `ts_rank_cd`. It finds exact terms that embeddings miss: alarm
  codes (E-217), part numbers, valve names.
- **Meaning:** pgvector, cosine distance on an HNSW index, 768 dimensions (nomic-embed-text through Ollama).
  It finds "the press runs hot" for "oil overheats".
- **Reciprocal rank fusion** (k = 60) merges the two top-20 lists. It uses ranks only, so the incomparable scores
  of `ts_rank_cd` and cosine distance never need weights. Each source in the answer says how it was found
  ("words #1 · meaning #3").
- **Embeddings are optional.** Anthropic has no embedding model and the default setup has no Ollama, so
  `AI_EMBEDDING_PROVIDER=none` gives keyword search alone and the copilot still works. With `ollama`, a
  background job embeds every passage without an embedding of the current model: new documents, a change of
  model (the model name is stored with each vector), or Ollama coming up after the service. Until then search
  falls back to keywords.
- **Plain SQL, not Spring AI's `VectorStore`:** the store needs full-text search next to vectors, documents with
  their own lifecycle (replace, delete, scope by machine), a schema owned by Flyway like every other table, and
  must run with no embedding model at all. Spring AI still provides the `EmbeddingModel`, the chat client, tool
  calling and the PDF reader.

### Generation

- The copilot finds the machine (selected on screen, or a single machine code named in the question), retrieves
  5 passages, and sends them numbered to the language model with a system prompt: cite [n], never invent steps,
  limits or part numbers, say when the sources do not cover it, begin with safety steps when the answer means
  working on the machine.
- **Tools:** the phase 5 plant tools (risks, machine details, alerts, work orders) and `searchManuals`, so the
  model can search again: in English when the question is in French (the manuals are in English), or for the
  cause it found in live data. Every search adds to the same numbered list, so [3] means one passage everywhere.
- The answer comes back with every passage read and a `cited` flag parsed from its [n] markers; the screen shows
  cited passages under the answer and the others folded.
- **Conversation:** the client sends the last turns with each question; the server keeps nothing. Citation
  numbers are stripped from earlier answers, since they pointed to earlier passages.
- **No language model, or it fails:** the copilot returns the passages it found (`PASSAGES_ONLY`), a search
  engine over the manuals instead of an error.
- The phase 5 `POST /ask` and `GET /assistant` endpoints are replaced by `/copilot/ask` and `/copilot`; reports
  stay where they were.

### Access

Every role asks the copilot: operators at the machine are the first readers of procedures. Adding or removing
documents is for ENGINEER and ADMIN, because what the copilot answers from is their responsibility. Built-in
manuals cannot be deleted through the API (they come back on each start).

### Infrastructure

Postgres runs on `pgvector/pgvector:0.8.1-pg17` (Postgres 17 + the extension); Flyway creates the extension.
Existing volumes from the Alpine image should be recreated once (different text collation library).
The Angular screen `/copilot` has two tabs: Ask (machine picker, conversation, clickable [n] markers that
highlight the source, link to the document) and Knowledge base (documents, passages as the copilot reads them,
upload). The risk center links to it with the machine selected.

## Consequences

- Answers can be checked: each claim points to a passage the reader can open.
- Retrieval quality is visible and testable without a language model: integration tests run the real SQL on
  pgvector with a hashing stand-in for the embedding model.
- Keyword search is English only (`to_tsvector('english')`); a French question relies on embeddings or on the
  model calling `searchManuals` in English.
- Not done yet: re-ranking with a cross-encoder, evaluation set of question/expected passage pairs, OCR for
  scanned PDFs, per-document permissions, streaming answers.
