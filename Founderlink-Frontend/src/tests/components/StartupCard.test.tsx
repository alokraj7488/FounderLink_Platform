/**
 * StartupCard.test.tsx
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import React from 'react';

import StartupCard from '../../shared/components/StartupCard';
import authReducer, { setCredentials } from '../../store/slices/authSlice';
import themeReducer from '../../store/slices/themeSlice';
import notificationReducer from '../../store/slices/notificationSlice';
import sidebarReducer from '../../store/slices/sidebarSlice';
import startupReducer from '../../store/slices/startupSlice';
import type { Startup } from '../../types';

vi.mock('../../core/tokenService', () => ({
  default: {
    getToken: vi.fn(() => 'mock-token'),
    getUser: vi.fn(() => null),
    setToken: vi.fn(),
    setUser: vi.fn(),
    clearAll: vi.fn(),
  },
}));

// Mock useNavigate
const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const makeStore = () =>
  configureStore({
    reducer: {
      auth: authReducer,
      theme: themeReducer,
      notifications: notificationReducer,
      sidebar: sidebarReducer,
      startups: startupReducer,
    },
  });

const baseStartup: Startup = {
  id: 1,
  name: 'TestCo',
  industry: 'FinTech',
  description: 'A revolutionary fintech startup.',
  problemStatement: 'Payments are slow.',
  solution: 'We speed them up.',
  fundingGoal: 500000,
  stage: 'MVP',
  location: 'Mumbai',
  founderId: 10,
  isApproved: false,
  isRejected: false,
  createdAt: '2024-01-01',
};

const renderCard = (startup: Startup, role = 'ROLE_FOUNDER') => {
  const store = makeStore();
  store.dispatch(setCredentials({ token: 'tok', userId: 1, role, email: 'u@t.com', name: 'User' }));
  return render(
    <Provider store={store}>
      <MemoryRouter>
        <StartupCard startup={startup} />
      </MemoryRouter>
    </Provider>
  );
};

beforeEach(() => mockNavigate.mockClear());

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('StartupCard – Normal', () => {
  it('renders startup name', () => {
    renderCard(baseStartup);
    expect(screen.getByText('TestCo')).toBeInTheDocument();
  });

  it('renders startup description', () => {
    renderCard(baseStartup);
    expect(screen.getByText('A revolutionary fintech startup.')).toBeInTheDocument();
  });

  it('renders industry name', () => {
    renderCard(baseStartup);
    expect(screen.getByText('FinTech')).toBeInTheDocument();
  });

  it('renders location when provided', () => {
    renderCard(baseStartup);
    expect(screen.getByText('Mumbai')).toBeInTheDocument();
  });

  it('renders formatted fundingGoal', () => {
    renderCard(baseStartup);
    const expected = Number(500000).toLocaleString();
    expect(screen.getByText(expected)).toBeInTheDocument();
  });

  it('renders MVP stage badge', () => {
    renderCard(baseStartup);
    expect(screen.getByText('MVP')).toBeInTheDocument();
  });

  it('renders "Early Traction" label for EARLY_TRACTION stage', () => {
    renderCard({ ...baseStartup, stage: 'EARLY_TRACTION' });
    expect(screen.getByText('Early Traction')).toBeInTheDocument();
  });

  it('investor click: navigates to /investor/startups/:id', () => {
    renderCard(baseStartup, 'ROLE_INVESTOR');
    fireEvent.click(screen.getByText('TestCo').closest('div')!);
    expect(mockNavigate).toHaveBeenCalledWith('/investor/startups/1');
  });

  it('co-founder click: navigates to /cofounder/startups/:id', () => {
    renderCard(baseStartup, 'ROLE_COFOUNDER');
    fireEvent.click(screen.getByText('TestCo').closest('div')!);
    expect(mockNavigate).toHaveBeenCalledWith('/cofounder/startups/1');
  });

  it('founder click: navigates to /founder/startups/:id/edit', () => {
    renderCard(baseStartup, 'ROLE_FOUNDER');
    fireEvent.click(screen.getByText('TestCo').closest('div')!);
    expect(mockNavigate).toHaveBeenCalledWith('/founder/startups/1/edit');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('StartupCard – Boundary', () => {
  it('does not render location section when location is empty string', () => {
    const { container } = renderCard({ ...baseStartup, location: '' });
    // The MapPin icon span only renders when location is truthy
    const mapPinIcons = container.querySelectorAll('.lucide-map-pin');
    expect(mapPinIcons).toHaveLength(0);
  });

  it('renders IDEA stage with correct badge class', () => {
    const { container } = renderCard({ ...baseStartup, stage: 'IDEA' });
    const badge = container.querySelector('.badge-yellow');
    expect(badge).toBeInTheDocument();
    expect(badge?.textContent).toBe('IDEA');
  });

  it('renders SCALING stage badge', () => {
    const { container } = renderCard({ ...baseStartup, stage: 'SCALING' });
    const badge = container.querySelector('.badge-green');
    expect(badge).toBeInTheDocument();
  });

  it('renders unknown stage with default badge-blue class', () => {
    const { container } = renderCard({ ...baseStartup, stage: 'UNKNOWN_STAGE' });
    const badge = container.querySelector('.badge-blue');
    expect(badge).toBeInTheDocument();
    expect(badge?.textContent).toBe('UNKNOWN_STAGE');
  });

  it('renders fundingGoal of 0 without crashing', () => {
    renderCard({ ...baseStartup, fundingGoal: 0 });
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('renders a very long startup name without crashing', () => {
    renderCard({ ...baseStartup, name: 'A'.repeat(200) });
    expect(screen.getByText('A'.repeat(200))).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('StartupCard – Exception Handling', () => {
  it('renders without crashing when description is an empty string', () => {
    renderCard({ ...baseStartup, description: '' });
    expect(screen.getByText('TestCo')).toBeInTheDocument();
  });

  it('does not crash when fundingGoal is a string-typed number', () => {
    renderCard({ ...baseStartup, fundingGoal: '500000' as unknown as number });
    expect(screen.getByText('TestCo')).toBeInTheDocument();
  });

  it('navigation is only called once per click', () => {
    renderCard(baseStartup, 'ROLE_INVESTOR');
    fireEvent.click(screen.getByText('TestCo').closest('div')!);
    expect(mockNavigate).toHaveBeenCalledTimes(1);
  });

  it('renders correctly for a startup with all boolean flags true', () => {
    renderCard({ ...baseStartup, isApproved: true, isRejected: true });
    expect(screen.getByText('TestCo')).toBeInTheDocument();
  });
});
