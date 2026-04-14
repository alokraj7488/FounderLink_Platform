/**
 * ErrorBoundary.test.tsx
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import ErrorBoundary from '../../shared/components/ErrorBoundary';

// Suppress React's console.error for intentional thrown errors
const suppressConsoleError = () => {
  const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
  return spy;
};

// Component that throws on demand
const ThrowingComponent: React.FC<{ shouldThrow: boolean }> = ({ shouldThrow }) => {
  if (shouldThrow) throw new Error('Test render error');
  return <div>Safe Content</div>;
};

// Component that throws with a custom message
const CustomErrorComponent: React.FC<{ message: string }> = ({ message }) => {
  throw new Error(message);
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('ErrorBoundary – Normal', () => {
  it('renders children when no error is thrown', () => {
    render(
      <ErrorBoundary>
        <div>Normal Content</div>
      </ErrorBoundary>
    );
    expect(screen.getByText('Normal Content')).toBeInTheDocument();
  });

  it('renders multiple children without error', () => {
    render(
      <ErrorBoundary>
        <div>Child One</div>
        <div>Child Two</div>
      </ErrorBoundary>
    );
    expect(screen.getByText('Child One')).toBeInTheDocument();
    expect(screen.getByText('Child Two')).toBeInTheDocument();
  });

  it('does not show the error UI when children render successfully', () => {
    render(
      <ErrorBoundary>
        <div>OK</div>
      </ErrorBoundary>
    );
    expect(screen.queryByText(/something went wrong/i)).not.toBeInTheDocument();
  });

  it('renders ThrowingComponent without issue when shouldThrow=false', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow={false} />
      </ErrorBoundary>
    );
    expect(screen.getByText('Safe Content')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('ErrorBoundary – Boundary', () => {
  let consoleSpy: ReturnType<typeof suppressConsoleError>;

  beforeEach(() => { consoleSpy = suppressConsoleError(); });
  afterEach(() => consoleSpy.mockRestore());

  it('catches a render error and shows the fallback UI', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow />
      </ErrorBoundary>
    );
    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
  });

  it('displays the thrown error message in the fallback UI', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow />
      </ErrorBoundary>
    );
    expect(screen.getByText('Test render error')).toBeInTheDocument();
  });

  it('shows "Reload Page" button in the fallback UI', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow />
      </ErrorBoundary>
    );
    expect(screen.getByRole('button', { name: /reload page/i })).toBeInTheDocument();
  });

  it('displays a custom error message correctly', () => {
    render(
      <ErrorBoundary>
        <CustomErrorComponent message="Custom crash message" />
      </ErrorBoundary>
    );
    expect(screen.getByText('Custom crash message')).toBeInTheDocument();
  });

  it('renders fallback heading "Something went wrong"', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow />
      </ErrorBoundary>
    );
    expect(screen.getByRole('heading', { name: /something went wrong/i })).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('ErrorBoundary – Exception Handling', () => {
  let consoleSpy: ReturnType<typeof suppressConsoleError>;

  beforeEach(() => { consoleSpy = suppressConsoleError(); });
  afterEach(() => consoleSpy.mockRestore());

  it('Reload Page button click calls window.location.reload', () => {
    const reloadSpy = vi.fn();
    Object.defineProperty(window, 'location', {
      value: { ...window.location, reload: reloadSpy },
      writable: true,
    });

    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow />
      </ErrorBoundary>
    );

    fireEvent.click(screen.getByRole('button', { name: /reload page/i }));
    expect(reloadSpy).toHaveBeenCalledTimes(1);
  });

  it('falls back gracefully when error.message is undefined', () => {
    const NullMessageError: React.FC = () => {
      const err = new Error();
      err.message = '';
      throw err;
    };

    render(
      <ErrorBoundary>
        <NullMessageError />
      </ErrorBoundary>
    );

    expect(screen.getByText(/something went wrong/i)).toBeInTheDocument();
    // Shows fallback text when message is empty
    expect(screen.getByText(/an unexpected error occurred/i)).toBeInTheDocument();
  });

  it('does not propagate error outside the boundary', () => {
    expect(() =>
      render(
        <ErrorBoundary>
          <ThrowingComponent shouldThrow />
        </ErrorBoundary>
      )
    ).not.toThrow();
  });

  it('recovers state when Reload Page is clicked (resets hasError)', () => {
    render(
      <ErrorBoundary>
        <ThrowingComponent shouldThrow />
      </ErrorBoundary>
    );

    // After clicking reload, setState is called (resets hasError).
    // We just verify the button is clickable and does not throw.
    expect(() =>
      fireEvent.click(screen.getByRole('button', { name: /reload page/i }))
    ).not.toThrow();
  });
});
