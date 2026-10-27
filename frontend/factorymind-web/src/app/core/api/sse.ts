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
