/**
 * useNotificationSocket.test.ts
 * Tests: Normal | Boundary | Exception Handling
 *
 * Strategy: the hook is thin wiring around @stomp/stompjs Client.
 * We mock the Client class so each test gets a fresh instance whose
 * callbacks we can inspect, without fighting vi.mock hoisting or
 * vi.clearAllMocks() wiping mockImplementation.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';

// ── Per-test state container — shared between factory and test body ───────────
// We use a plain object so the reference is stable across the hoisting boundary.
const stomp = {
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn(),
  active: false,
  onConnect: null as (() => void) | null,
  onStompError: null as (() => void) | null,
  subscribeCallback: null as ((msg: { body: string }) => void) | null,
};

vi.mock('sockjs-client', () => ({ default: vi.fn(() => ({})) }));

vi.mock('../../core/tokenService', () => ({
  default: { getToken: vi.fn(() => 'mock-token') },
}));

// The Client constructor captures the config callbacks into `stomp` so tests
// can trigger them directly. Must use `function` (not arrow) because the hook
// calls `new Client(...)` — arrow functions cannot be constructors.
vi.mock('@stomp/stompjs', () => ({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  Client: vi.fn(function (this: unknown, config: {
    onConnect?: () => void;
    onStompError?: () => void;
  }) {
    stomp.onConnect = config.onConnect ?? null;
    stomp.onStompError = config.onStompError ?? null;
    stomp.subscribe.mockImplementation(
      (_topic: string, cb: (msg: { body: string }) => void) => {
        stomp.subscribeCallback = cb;
      }
    );
    return {
      activate: stomp.activate,
      deactivate: stomp.deactivate,
      subscribe: stomp.subscribe,
      get active() { return stomp.active; },
    };
  }),
}));

import useNotificationSocket from '../../shared/hooks/useNotificationSocket';

// ── Reset only our local state, not the Client mock itself ───────────────────
beforeEach(() => {
  stomp.activate.mockReset();
  stomp.deactivate.mockReset();
  stomp.subscribe.mockReset();
  stomp.active = false;
  stomp.onConnect = null;
  stomp.onStompError = null;
  stomp.subscribeCallback = null;
  // Re-attach subscribe implementation after reset
  stomp.subscribe.mockImplementation(
    (_topic: string, cb: (msg: { body: string }) => void) => {
      stomp.subscribeCallback = cb;
    }
  );
});

// ── Helpers ──────────────────────────────────────────────────────────────────
const connect = () => { if (stomp.onConnect) stomp.onConnect(); };
const stompError = () => { if (stomp.onStompError) stomp.onStompError(); };
const push = (payload: object) => {
  if (stomp.subscribeCallback)
    stomp.subscribeCallback({ body: JSON.stringify(payload) });
};

const mockNotification = {
  id: 1,
  userId: 1,
  message: 'Test notification',
  isRead: false,
  createdAt: '2024-01-01',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('useNotificationSocket – Normal', () => {
  it('activates the STOMP client when a valid userId is provided', () => {
    renderHook(() => useNotificationSocket(1, vi.fn()));
    expect(stomp.activate).toHaveBeenCalledTimes(1);
  });

  it('calls onNotification with the parsed payload on an incoming message', () => {
    const onNotification = vi.fn();
    renderHook(() => useNotificationSocket(1, onNotification));
    connect();
    push(mockNotification);
    expect(onNotification).toHaveBeenCalledWith(mockNotification);
  });

  it('delivers multiple messages in order', () => {
    const onNotification = vi.fn();
    renderHook(() => useNotificationSocket(1, onNotification));
    connect();
    push({ ...mockNotification, id: 1 });
    push({ ...mockNotification, id: 2 });
    expect(onNotification).toHaveBeenCalledTimes(2);
    expect(onNotification.mock.calls[0][0].id).toBe(1);
    expect(onNotification.mock.calls[1][0].id).toBe(2);
  });

  it('deactivates the client on unmount when the client is active', () => {
    stomp.active = true;
    const { unmount } = renderHook(() => useNotificationSocket(1, vi.fn()));
    unmount();
    expect(stomp.deactivate).toHaveBeenCalledTimes(1);
  });

  it('uses the latest onNotification callback via ref (stale-closure guard)', () => {
    const firstCb = vi.fn();
    const secondCb = vi.fn();
    const { rerender } = renderHook(
      ({ cb }: { cb: typeof firstCb }) => useNotificationSocket(1, cb),
      { initialProps: { cb: firstCb } }
    );
    connect();
    rerender({ cb: secondCb });
    push(mockNotification);
    expect(secondCb).toHaveBeenCalledWith(mockNotification);
    expect(firstCb).not.toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('useNotificationSocket – Boundary', () => {
  it('does not activate the STOMP client when userId is undefined', () => {
    renderHook(() => useNotificationSocket(undefined, vi.fn()));
    expect(stomp.activate).not.toHaveBeenCalled();
  });

  it('does not activate when userId is 0 (falsy)', () => {
    renderHook(() => useNotificationSocket(0, vi.fn()));
    expect(stomp.activate).not.toHaveBeenCalled();
  });

  it('does not call onNotification before onConnect fires', () => {
    const onNotification = vi.fn();
    renderHook(() => useNotificationSocket(1, onNotification));
    // connect() NOT called — subscription never wired up
    expect(onNotification).not.toHaveBeenCalled();
  });

  it('does not throw when onStompError fires', () => {
    renderHook(() => useNotificationSocket(1, vi.fn()));
    expect(() => act(() => stompError())).not.toThrow();
  });

  it('does not call deactivate on unmount when client is not yet active', () => {
    stomp.active = false;
    const { unmount } = renderHook(() => useNotificationSocket(1, vi.fn()));
    unmount();
    expect(stomp.deactivate).not.toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('useNotificationSocket – Exception Handling', () => {
  it('silently ignores malformed (non-JSON) message bodies', () => {
    const onNotification = vi.fn();
    renderHook(() => useNotificationSocket(1, onNotification));
    connect();
    if (stomp.subscribeCallback)
      stomp.subscribeCallback({ body: 'not-valid-json{{{' });
    expect(onNotification).not.toHaveBeenCalled();
  });

  it('silently ignores an empty message body', () => {
    const onNotification = vi.fn();
    renderHook(() => useNotificationSocket(1, onNotification));
    connect();
    if (stomp.subscribeCallback)
      stomp.subscribeCallback({ body: '' });
    expect(onNotification).not.toHaveBeenCalled();
  });

  it('does not throw when the hook unmounts without ever connecting', () => {
    const { unmount } = renderHook(() => useNotificationSocket(1, vi.fn()));
    expect(() => unmount()).not.toThrow();
  });

  it('handles userId changing from a value to undefined gracefully', () => {
    const { rerender } = renderHook(
      ({ uid }: { uid: number | undefined }) =>
        useNotificationSocket(uid, vi.fn()),
      { initialProps: { uid: 1 as number | undefined } }
    );
    expect(() => rerender({ uid: undefined })).not.toThrow();
  });
});
