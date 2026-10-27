import { SseParser } from './sse';

describe('SseParser', () => {
  it('reads named events and skips comments', () => {
    const parser = new SseParser();

    const messages = parser.push(
      ':connected\n\nevent:reading\ndata:{"value":1}\n\n:ping\n\nevent: status\ndata: {"a":2}\n\n',
    );

    expect(messages).toEqual([
      { event: 'reading', data: '{"value":1}' },
      { event: 'status', data: '{"a":2}' },
    ]);
  });

  it('waits for the rest of a message split across chunks', () => {
    const parser = new SseParser();

    expect(parser.push('event:reading\ndata:{"val')).toEqual([]);
    expect(parser.push('ue":42}\n')).toEqual([]);
    expect(parser.push('\n')).toEqual([{ event: 'reading', data: '{"value":42}' }]);
  });
});
