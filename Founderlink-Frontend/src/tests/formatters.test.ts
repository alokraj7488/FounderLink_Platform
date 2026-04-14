/**
 * formatters.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, afterEach } from 'vitest';
import { formatCurrency, formatDate, formatRelativeTime } from '../shared/utils/formatters';

afterEach(() => vi.useRealTimers());

// ─────────────────────────────────────────────────────────────────────────────
// 1. formatCurrency
// ─────────────────────────────────────────────────────────────────────────────
describe('formatCurrency – Normal', () => {
  it('formats a number with the ₹ symbol', () => {
    const result = formatCurrency(10000);
    expect(result).toMatch(/^₹/);
  });

  it('formats 1000 correctly for en-IN locale', () => {
    expect(formatCurrency(1000)).toBe('₹1,000');
  });

  it('accepts a numeric string', () => {
    expect(formatCurrency('50000')).toBe('₹50,000');
  });
});

describe('formatCurrency – Boundary', () => {
  it('formats 0 without sign issues', () => {
    expect(formatCurrency(0)).toBe('₹0');
  });

  it('formats negative numbers', () => {
    const result = formatCurrency(-500);
    expect(result).toContain('₹');
    expect(result).toContain('500');
  });

  it('formats very large numbers', () => {
    const result = formatCurrency(1000000000);
    expect(result).toMatch(/^₹/);
    expect(result).toContain('1');
  });
});

describe('formatCurrency – Exception Handling', () => {
  it('handles NaN input gracefully (returns ₹NaN)', () => {
    const result = formatCurrency(NaN);
    expect(result).toContain('₹');
  });

  it('handles Infinity without throwing', () => {
    expect(() => formatCurrency(Infinity)).not.toThrow();
  });

  it('handles a non-numeric string by converting via Number()', () => {
    // Number('abc') = NaN
    const result = formatCurrency('abc' as unknown as number);
    expect(result).toContain('₹');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. formatDate
// ─────────────────────────────────────────────────────────────────────────────
describe('formatDate – Normal', () => {
  it('formats a valid ISO date string', () => {
    const result = formatDate('2024-01-15T10:00:00Z');
    expect(result).toContain('2024');
    expect(result).toContain('Jan');
  });

  it('returns a non-empty string for any valid date', () => {
    expect(formatDate('2023-06-30')).toBeTruthy();
  });
});

describe('formatDate – Boundary', () => {
  it('formats the Unix epoch correctly', () => {
    const result = formatDate('1970-01-01T00:00:00Z');
    expect(result).toContain('1970');
  });

  it('formats a future year date', () => {
    const result = formatDate('2099-06-15T12:00:00Z');
    expect(result).toContain('2099');
  });
});

describe('formatDate – Exception Handling', () => {
  it('handles an invalid date string without throwing (returns Invalid Date)', () => {
    expect(() => formatDate('not-a-date')).not.toThrow();
  });

  it('handles empty string without throwing', () => {
    expect(() => formatDate('')).not.toThrow();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. formatRelativeTime
// ─────────────────────────────────────────────────────────────────────────────
describe('formatRelativeTime – Normal', () => {
  it('returns "just now" for a date less than 1 minute ago', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T12:00:30Z'));
    expect(formatRelativeTime('2024-01-01T12:00:00Z')).toBe('just now');
  });

  it('returns minutes ago for 5 minutes ago', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T12:05:00Z'));
    expect(formatRelativeTime('2024-01-01T12:00:00Z')).toBe('5m ago');
  });

  it('returns hours ago for 3 hours ago', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T15:00:00Z'));
    expect(formatRelativeTime('2024-01-01T12:00:00Z')).toBe('3h ago');
  });

  it('returns a formatted date for dates older than 24 hours', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-03T12:00:00Z'));
    const result = formatRelativeTime('2024-01-01T12:00:00Z');
    expect(result).toContain('2024');
  });
});

describe('formatRelativeTime – Boundary', () => {
  it('returns "1m ago" for exactly 60 seconds ago', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T12:01:00Z'));
    expect(formatRelativeTime('2024-01-01T12:00:00Z')).toBe('1m ago');
  });

  it('returns "1h ago" for exactly 60 minutes ago', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2024-01-01T13:00:00Z'));
    expect(formatRelativeTime('2024-01-01T12:00:00Z')).toBe('1h ago');
  });
});

describe('formatRelativeTime – Exception Handling', () => {
  it('handles invalid date string without throwing', () => {
    expect(() => formatRelativeTime('invalid')).not.toThrow();
  });

  it('handles empty string without throwing', () => {
    expect(() => formatRelativeTime('')).not.toThrow();
  });
});
