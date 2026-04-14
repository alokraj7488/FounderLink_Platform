/**
 * sidebarSlice.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import sidebarReducer, {
  toggleSidebar,
  setSidebar,
  selectSidebarOpen,
} from '../../store/slices/sidebarSlice';

const makeStore = () => configureStore({ reducer: { sidebar: sidebarReducer } });

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('sidebarSlice – Normal', () => {
  it('initial state: sidebar is open (true)', () => {
    const store = makeStore();
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });

  it('toggleSidebar: closes an open sidebar', () => {
    const store = makeStore();
    store.dispatch(toggleSidebar());
    expect(selectSidebarOpen(store.getState() as never)).toBe(false);
  });

  it('toggleSidebar: opens a closed sidebar', () => {
    const store = makeStore();
    store.dispatch(setSidebar(false));
    store.dispatch(toggleSidebar());
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });

  it('setSidebar(false): explicitly closes the sidebar', () => {
    const store = makeStore();
    store.dispatch(setSidebar(false));
    expect(selectSidebarOpen(store.getState() as never)).toBe(false);
  });

  it('setSidebar(true): explicitly opens the sidebar', () => {
    const store = makeStore();
    store.dispatch(setSidebar(false));
    store.dispatch(setSidebar(true));
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('sidebarSlice – Boundary', () => {
  it('double toggle returns to original open state', () => {
    const store = makeStore();
    store.dispatch(toggleSidebar()); // false
    store.dispatch(toggleSidebar()); // true
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });

  it('setSidebar(true) when already open is idempotent', () => {
    const store = makeStore();
    store.dispatch(setSidebar(true));
    store.dispatch(setSidebar(true));
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });

  it('setSidebar(false) when already closed is idempotent', () => {
    const store = makeStore();
    store.dispatch(setSidebar(false));
    store.dispatch(setSidebar(false));
    expect(selectSidebarOpen(store.getState() as never)).toBe(false);
  });

  it('setSidebar(false) then toggleSidebar opens the sidebar', () => {
    const store = makeStore();
    store.dispatch(setSidebar(false));
    store.dispatch(toggleSidebar());
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('sidebarSlice – Exception Handling', () => {
  it('odd number of toggles from open ends in closed state', () => {
    const store = makeStore(); // open = true
    for (let i = 0; i < 5; i++) store.dispatch(toggleSidebar());
    expect(selectSidebarOpen(store.getState() as never)).toBe(false);
  });

  it('even number of toggles from open ends in open state', () => {
    const store = makeStore(); // open = true
    for (let i = 0; i < 6; i++) store.dispatch(toggleSidebar());
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });

  it('interleaving setSidebar and toggleSidebar produces correct state', () => {
    const store = makeStore(); // true
    store.dispatch(setSidebar(false));   // false
    store.dispatch(toggleSidebar());     // true
    store.dispatch(setSidebar(false));   // false
    store.dispatch(toggleSidebar());     // true
    expect(selectSidebarOpen(store.getState() as never)).toBe(true);
  });

  it('rapid dispatches do not throw', () => {
    const store = makeStore();
    expect(() => {
      for (let i = 0; i < 100; i++) store.dispatch(toggleSidebar());
    }).not.toThrow();
  });
});
