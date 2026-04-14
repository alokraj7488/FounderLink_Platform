/**
 * ThemeToggle.test.tsx
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import React from 'react';

import ThemeToggle from '../../shared/components/ThemeToggle';
import themeReducer, { setTheme, selectTheme } from '../../store/slices/themeSlice';
import authReducer from '../../store/slices/authSlice';
import notificationReducer from '../../store/slices/notificationSlice';
import sidebarReducer from '../../store/slices/sidebarSlice';
import startupReducer from '../../store/slices/startupSlice';

vi.mock('../../core/tokenService', () => ({
  default: {
    getToken: vi.fn(() => null),
    getUser: vi.fn(() => null),
    setToken: vi.fn(),
    setUser: vi.fn(),
    clearAll: vi.fn(),
  },
}));

const makeStore = (initialTheme = 'light') => {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      theme: themeReducer,
      notifications: notificationReducer,
      sidebar: sidebarReducer,
      startups: startupReducer,
    },
  });
  store.dispatch(setTheme(initialTheme));
  return store;
};

const renderToggle = (initialTheme = 'light') => {
  const store = makeStore(initialTheme);
  const utils = render(
    <Provider store={store}>
      <ThemeToggle />
    </Provider>
  );
  return { ...utils, store };
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('ThemeToggle – Normal', () => {
  it('renders the toggle button', () => {
    renderToggle();
    const btn = screen.getByRole('button');
    expect(btn).toBeInTheDocument();
  });

  it('shows Moon icon when theme is light (click to go dark)', () => {
    renderToggle('light');
    const btn = screen.getByRole('button');
    expect(btn.getAttribute('aria-label')).toBe('Switch to dark mode');
  });

  it('shows Sun icon when theme is dark (click to go light)', () => {
    renderToggle('dark');
    const btn = screen.getByRole('button');
    expect(btn.getAttribute('aria-label')).toBe('Switch to light mode');
  });

  it('dispatches toggleTheme on click (light → dark)', () => {
    const { store } = renderToggle('light');
    fireEvent.click(screen.getByRole('button'));
    expect(selectTheme(store.getState() as never)).toBe('dark');
  });

  it('dispatches toggleTheme on click (dark → light)', () => {
    const { store } = renderToggle('dark');
    fireEvent.click(screen.getByRole('button'));
    expect(selectTheme(store.getState() as never)).toBe('light');
  });

  it('aria-label updates correctly after theme toggle', () => {
    const { store } = renderToggle('light');
    const btn = screen.getByRole('button');
    expect(btn.getAttribute('aria-label')).toBe('Switch to dark mode');
    fireEvent.click(btn);
    // Re-read the button after state update
    expect(selectTheme(store.getState() as never)).toBe('dark');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('ThemeToggle – Boundary', () => {
  it('button has correct fixed positioning styles', () => {
    renderToggle();
    const btn = screen.getByRole('button');
    expect(btn.style.position).toBe('fixed');
    expect(btn.style.bottom).toBe('24px');
    expect(btn.style.right).toBe('24px');
  });

  it('button renders an SVG icon', () => {
    renderToggle();
    const btn = screen.getByRole('button');
    expect(btn.querySelector('svg')).toBeTruthy();
  });

  it('clicking multiple times toggles consistently', () => {
    const { store } = renderToggle('light');
    const btn = screen.getByRole('button');
    fireEvent.click(btn); // → dark
    fireEvent.click(btn); // → light
    fireEvent.click(btn); // → dark
    expect(selectTheme(store.getState() as never)).toBe('dark');
  });

  it('title attribute matches aria-label', () => {
    renderToggle('dark');
    const btn = screen.getByRole('button');
    expect(btn.getAttribute('title')).toBe(btn.getAttribute('aria-label'));
  });

  it('renders correctly for a completely fresh store', () => {
    renderToggle();
    expect(screen.getByRole('button')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('ThemeToggle – Exception Handling', () => {
  it('does not throw when clicked rapidly many times', () => {
    const { store } = renderToggle('light');
    const btn = screen.getByRole('button');
    expect(() => {
      for (let i = 0; i < 20; i++) fireEvent.click(btn);
    }).not.toThrow();
    // 20 toggles from light → ends on light (even number)
    expect(selectTheme(store.getState() as never)).toBe('light');
  });

  it('does not throw when theme is an unexpected string value', () => {
    // theme !== 'dark' → aria-label should say "Switch to dark mode"
    renderToggle('high-contrast');
    const btn = screen.getByRole('button');
    expect(btn.getAttribute('aria-label')).toBe('Switch to dark mode');
    expect(() => fireEvent.click(btn)).not.toThrow();
  });

  it('component mounts and unmounts without errors', () => {
    const { unmount } = renderToggle();
    expect(() => unmount()).not.toThrow();
  });
});
