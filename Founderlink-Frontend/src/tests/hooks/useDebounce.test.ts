/**
 * useDebounce.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useDebounce from '../../shared/hooks/useDebounce';

afterEach(() => vi.useRealTimers());

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('useDebounce – Normal', () => {
  it('returns the initial value immediately before the delay elapses', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useDebounce('hello', 400));
    expect(result.current).toBe('hello');
  });

  it('returns the updated value after the delay elapses', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 400),
      { initialProps: { value: 'initial' } }
    );
    rerender({ value: 'updated' });
    expect(result.current).toBe('initial');
    act(() => { vi.advanceTimersByTime(400); });
    expect(result.current).toBe('updated');
  });

  it('debounces rapid successive changes and settles on the last value', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 400),
      { initialProps: { value: 'a' } }
    );
    rerender({ value: 'b' });
    rerender({ value: 'c' });
    rerender({ value: 'final' });
    expect(result.current).toBe('a');
    act(() => { vi.advanceTimersByTime(400); });
    expect(result.current).toBe('final');
  });

  it('works with number type values', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: number }) => useDebounce(value, 300),
      { initialProps: { value: 1 } }
    );
    rerender({ value: 99 });
    act(() => { vi.advanceTimersByTime(300); });
    expect(result.current).toBe(99);
  });

  it('works with object type values', async () => {
    vi.useFakeTimers();
    const initial = { query: '' };
    const updated = { query: 'search' };
    const { result, rerender } = renderHook(
      ({ value }: { value: typeof initial }) => useDebounce(value, 500),
      { initialProps: { value: initial } }
    );
    rerender({ value: updated });
    act(() => { vi.advanceTimersByTime(500); });
    expect(result.current).toEqual({ query: 'search' });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('useDebounce – Boundary', () => {
  it('uses the default delay of 400ms when no delay is provided', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value),
      { initialProps: { value: 'start' } }
    );
    rerender({ value: 'end' });
    act(() => { vi.advanceTimersByTime(399); });
    expect(result.current).toBe('start');
    act(() => { vi.advanceTimersByTime(1); });
    expect(result.current).toBe('end');
  });

  it('does not update before delay when delay is very large', () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 99999),
      { initialProps: { value: 'old' } }
    );
    rerender({ value: 'new' });
    act(() => { vi.advanceTimersByTime(1000); });
    expect(result.current).toBe('old');
  });

  it('updates immediately when delay is 0ms', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 0),
      { initialProps: { value: 'old' } }
    );
    rerender({ value: 'new' });
    act(() => { vi.advanceTimersByTime(0); });
    expect(result.current).toBe('new');
  });

  it('returns empty string value correctly', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 200),
      { initialProps: { value: 'text' } }
    );
    rerender({ value: '' });
    act(() => { vi.advanceTimersByTime(200); });
    expect(result.current).toBe('');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('useDebounce – Exception Handling', () => {
  it('does not update stale value if component unmounts before delay', () => {
    vi.useFakeTimers();
    const { result, rerender, unmount } = renderHook(
      ({ value }: { value: string }) => useDebounce(value, 400),
      { initialProps: { value: 'initial' } }
    );
    rerender({ value: 'new' });
    unmount();
    // Timer fires after unmount — should not cause errors
    expect(() => act(() => { vi.advanceTimersByTime(400); })).not.toThrow();
    // Last snapshot before unmount should still be the pre-debounce value
    expect(result.current).toBe('initial');
  });

  it('handles undefined value without throwing', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useDebounce(undefined, 300));
    act(() => { vi.advanceTimersByTime(300); });
    expect(result.current).toBeUndefined();
  });

  it('handles null value without throwing', () => {
    vi.useFakeTimers();
    const { result } = renderHook(() => useDebounce(null, 300));
    act(() => { vi.advanceTimersByTime(300); });
    expect(result.current).toBeNull();
  });

  it('resets correctly when delay changes mid-flight', async () => {
    vi.useFakeTimers();
    const { result, rerender } = renderHook(
      ({ value, delay }: { value: string; delay: number }) => useDebounce(value, delay),
      { initialProps: { value: 'a', delay: 500 } }
    );
    rerender({ value: 'b', delay: 500 });
    act(() => { vi.advanceTimersByTime(200); });
    rerender({ value: 'b', delay: 100 });
    act(() => { vi.advanceTimersByTime(100); });
    expect(result.current).toBe('b');
  });
});
