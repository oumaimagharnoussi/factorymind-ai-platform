import { MonoTypeOperatorFunction, Observable, repeat, retry, timer } from 'rxjs';

export interface SseMessage {
  event: string;
  data: string;
}

/**
 * Splits a Server-Sent Events byte stream into messages. Chunks from the network can end anywhere,
 * so an incomplete message is kept until the next chunk completes it. Comment lines (": ping") are skipped.
 */
export class SseParser {
  private buffer = '';

  push(chunk: string): SseMessage[] {
    this.buffer += chunk.replace(/\r\n?/g, '\n');
    const blocks = this.buffer.split('\n\n');
    this.buffer = blocks.pop() ?? '';
    return blocks.map(parseBlock).filter((message): message is SseMessage => message !== null);
  }
}

function parseBlock(block: string): SseMessage | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    const colon = line.indexOf(':');
    if (colon === 0 || line === '') continue;
    const field = colon < 0 ? line : line.slice(0, colon);
    const value = colon < 0 ? '' : line.slice(colon + 1).replace(/^ /, '');
    if (field === 'event') event = value;
    if (field === 'data') data.push(value);
  }
  return data.length ? { event, data: data.join('\n') } : null;
}

/**
 * Opens a Server-Sent Events stream with a bearer token. The browser's EventSource cannot send an
 * Authorization header, so the stream is read with fetch. Completes when the server closes the stream
 * (every 15 minutes); callers resubscribe, which also picks up a fresh token. `onOpen` runs once the server
 * has accepted the stream, before any message, for screens whose events are rare.
 */
export function eventStream(
  url: string,
  token: () => Promise<string | undefined>,
  onOpen?: () => void,
): Observable<SseMessage> {
  return new Observable<SseMessage>((subscriber) => {
    const abort = new AbortController();
    (async () => {
      const bearer = await token();
      const response = await fetch(url, {
        headers: {
          Accept: 'text/event-stream',
          ...(bearer ? { Authorization: `Bearer ${bearer}` } : {}),
        },
        signal: abort.signal,
      });
      if (!response.ok || !response.body) {
        throw new Error(`Live stream refused (${response.status})`);
      }
      onOpen?.();
      const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
      const parser = new SseParser();
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        parser.push(value).forEach((message) => subscriber.next(message));
      }
      subscriber.complete();
    })().catch((error: unknown) => {
      if (!abort.signal.aborted) subscriber.error(error);
    });
    return () => abort.abort();
  });
}

/**
 * Keeps a live stream open for as long as the screen is: reopens it when the server ends it (every
 * 15 minutes) and retries with a growing delay, up to 15 s, when it fails.
 */
export function stayConnected<T>(onRetry?: () => void): MonoTypeOperatorFunction<T> {
  return (source) =>
    source.pipe(
      repeat({ delay: 500 }),
      retry({
        delay: (_error, attempt) => {
          onRetry?.();
          return timer(Math.min(1000 * 2 ** attempt, 15_000));
        },
      }),
    );
}
