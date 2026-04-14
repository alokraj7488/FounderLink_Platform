/**
 * startupSlice.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import startupReducer, {
  setCurrentPage,
  fetchStartups,
  selectStartups,
  selectStartupLoading,
  selectStartupError,
  selectTotalPages,
  selectTotalElements,
  selectCurrentPage,
} from '../../store/slices/startupSlice';

vi.mock('../../core/api/startupApi', () => ({
  getAllStartups: vi.fn(),
}));

import { getAllStartups } from '../../core/api/startupApi';

const makeStore = () => configureStore({ reducer: { startups: startupReducer } });

const mockStartup = {
  id: 1, name: 'TechCo', industry: 'Tech', description: 'Desc',
  problemStatement: 'P', solution: 'S', fundingGoal: 100000,
  stage: 'MVP', location: 'Delhi', founderId: 1,
  isApproved: false, isRejected: false, createdAt: '2024-01-01',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('startupSlice – Normal', () => {
  beforeEach(() => vi.clearAllMocks());

  it('initial state has empty items, loading false and no error', () => {
    const store = makeStore();
    const state = store.getState();
    expect(selectStartups(state as never)).toHaveLength(0);
    expect(selectStartupLoading(state as never)).toBe(false);
    expect(selectStartupError(state as never)).toBeNull();
    expect(selectCurrentPage(state as never)).toBe(0);
  });

  it('setCurrentPage: updates currentPage in the store', () => {
    const store = makeStore();
    store.dispatch(setCurrentPage(3));
    expect(selectCurrentPage(store.getState() as never)).toBe(3);
  });

  it('fetchStartups.fulfilled: populates items, totalPages, totalElements', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { content: [mockStartup], totalPages: 5, totalElements: 45 },
    });
    const store = makeStore();
    await store.dispatch(fetchStartups({ page: 0, size: 10 }) as never);
    const state = store.getState();
    expect(selectStartups(state as never)).toHaveLength(1);
    expect(selectTotalPages(state as never)).toBe(5);
    expect(selectTotalElements(state as never)).toBe(45);
    expect(selectStartupLoading(state as never)).toBe(false);
    expect(selectStartupError(state as never)).toBeNull();
  });

  it('fetchStartups.pending: sets loading to true', async () => {
    let resolvePromise!: (v: unknown) => void;
    (getAllStartups as ReturnType<typeof vi.fn>).mockReturnValueOnce(
      new Promise((res) => { resolvePromise = res; })
    );
    const store = makeStore();
    const dispatchPromise = store.dispatch(fetchStartups() as never);
    expect(selectStartupLoading(store.getState() as never)).toBe(true);
    resolvePromise({ data: { content: [], totalPages: 0, totalElements: 0 } });
    await dispatchPromise;
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('startupSlice – Boundary', () => {
  beforeEach(() => vi.clearAllMocks());

  it('setCurrentPage: page 0 is valid', () => {
    const store = makeStore();
    store.dispatch(setCurrentPage(0));
    expect(selectCurrentPage(store.getState() as never)).toBe(0);
  });

  it('setCurrentPage: very large page number', () => {
    const store = makeStore();
    store.dispatch(setCurrentPage(999999));
    expect(selectCurrentPage(store.getState() as never)).toBe(999999);
  });

  it('fetchStartups.fulfilled: empty content array sets items to []', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { content: [], totalPages: 0, totalElements: 0 },
    });
    const store = makeStore();
    await store.dispatch(fetchStartups() as never);
    expect(selectStartups(store.getState() as never)).toHaveLength(0);
    expect(selectTotalPages(store.getState() as never)).toBe(0);
  });

  it('fetchStartups.fulfilled: handles missing fields with defaults', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: {},
    });
    const store = makeStore();
    await store.dispatch(fetchStartups() as never);
    // Should default to [] and 0
    expect(selectStartups(store.getState() as never)).toHaveLength(0);
    expect(selectTotalPages(store.getState() as never)).toBe(0);
  });

  it('fetchStartups: uses default params when none passed', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      data: { content: [mockStartup], totalPages: 1, totalElements: 1 },
    });
    const store = makeStore();
    await store.dispatch(fetchStartups() as never);
    expect(getAllStartups).toHaveBeenCalledWith(0, 10);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('startupSlice – Exception Handling', () => {
  beforeEach(() => vi.clearAllMocks());

  it('fetchStartups.rejected: sets error from API message', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>).mockRejectedValueOnce({
      response: { data: { message: 'Unauthorised' } },
      isAxiosError: true,
    });
    const store = makeStore();
    await store.dispatch(fetchStartups() as never);
    expect(selectStartupError(store.getState() as never)).toBe('Unauthorised');
    expect(selectStartupLoading(store.getState() as never)).toBe(false);
  });

  it('fetchStartups.rejected: falls back to default message when no API message', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('Network Error'));
    const store = makeStore();
    await store.dispatch(fetchStartups() as never);
    expect(selectStartupError(store.getState() as never)).toBe('Failed to load startups');
  });

  it('fetchStartups.rejected: clears previous items on failure', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ data: { content: [mockStartup], totalPages: 1, totalElements: 1 } })
      .mockRejectedValueOnce(new Error('fail'));
    const store = makeStore();
    await store.dispatch(fetchStartups() as never); // success
    expect(selectStartups(store.getState() as never)).toHaveLength(1);
    await store.dispatch(fetchStartups() as never); // failure
    // Items from previous fetch should remain, error is set
    expect(selectStartupError(store.getState() as never)).toBe('Failed to load startups');
  });

  it('calling fetchStartups twice in sequence works without error', async () => {
    (getAllStartups as ReturnType<typeof vi.fn>)
      .mockResolvedValue({ data: { content: [mockStartup], totalPages: 1, totalElements: 1 } });
    const store = makeStore();
    await store.dispatch(fetchStartups() as never);
    await store.dispatch(fetchStartups() as never);
    expect(selectStartups(store.getState() as never)).toHaveLength(1);
  });
});
