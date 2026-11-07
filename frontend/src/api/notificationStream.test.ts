import { parseEvents } from './notificationStream'

describe('parseEvents', () => {
  it('reads named events and ignores comments', () => {
    const { events, rest } = parseEvents('event:unread\ndata:{"count":3}\n\n:ping\n\nevent:unread\ndata:{"count":0}\n\n')
    expect(events).toEqual([
      { event: 'unread', data: '{"count":3}' },
      { event: 'unread', data: '{"count":0}' },
    ])
    expect(rest).toBe('')
  })

  it('keeps an unfinished event for the next chunk', () => {
    const first = parseEvents('event:unread\ndata:{"cou')
    expect(first.events).toEqual([])
    const second = parseEvents(first.rest + 'nt":7}\n\n')
    expect(second.events).toEqual([{ event: 'unread', data: '{"count":7}' }])
  })

  it('accepts Windows line endings', () => {
    expect(parseEvents('event:unread\r\ndata:{"count":1}\r\n\r\n').events).toEqual([{ event: 'unread', data: '{"count":1}' }])
  })
})
