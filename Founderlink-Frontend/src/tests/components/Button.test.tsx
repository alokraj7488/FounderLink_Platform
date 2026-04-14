/**
 * Button.test.tsx
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import Button from '../../shared/components/Button';

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('Button – Normal', () => {
  it('renders children text', () => {
    render(<Button>Click Me</Button>);
    expect(screen.getByRole('button', { name: /click me/i })).toBeInTheDocument();
  });

  it('calls onClick handler when clicked', () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Submit</Button>);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('applies primary variant class by default', () => {
    render(<Button>Primary</Button>);
    expect(screen.getByRole('button').className).toContain('btn-primary');
  });

  it('applies secondary variant class', () => {
    render(<Button variant="secondary">Secondary</Button>);
    expect(screen.getByRole('button').className).toContain('btn-secondary');
  });

  it('applies danger variant class', () => {
    render(<Button variant="danger">Delete</Button>);
    expect(screen.getByRole('button').className).toContain('btn-danger');
  });

  it('applies success variant class', () => {
    render(<Button variant="success">Confirm</Button>);
    expect(screen.getByRole('button').className).toContain('btn-success');
  });

  it('renders leftIcon when not loading', () => {
    render(<Button leftIcon={<span data-testid="left-icon" />}>Save</Button>);
    expect(screen.getByTestId('left-icon')).toBeInTheDocument();
  });

  it('renders rightIcon when not loading', () => {
    render(<Button rightIcon={<span data-testid="right-icon" />}>Next</Button>);
    expect(screen.getByTestId('right-icon')).toBeInTheDocument();
  });

  it('applies fullWidth class when fullWidth=true', () => {
    render(<Button fullWidth>Full</Button>);
    expect(screen.getByRole('button').className).toContain('w-full');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('Button – Boundary', () => {
  it('isLoading=true: button is disabled', () => {
    render(<Button isLoading>Loading</Button>);
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('isLoading=true: spinner icon renders instead of leftIcon', () => {
    render(<Button isLoading leftIcon={<span data-testid="left-icon" />}>Save</Button>);
    expect(screen.queryByTestId('left-icon')).not.toBeInTheDocument();
    // Loader2 from lucide-react renders an svg
    expect(screen.getByRole('button').querySelector('svg')).toBeTruthy();
  });

  it('isLoading=true: rightIcon is hidden', () => {
    render(<Button isLoading rightIcon={<span data-testid="right-icon" />}>Submit</Button>);
    expect(screen.queryByTestId('right-icon')).not.toBeInTheDocument();
  });

  it('disabled=true: button is disabled and onClick is not called', () => {
    const onClick = vi.fn();
    render(<Button disabled onClick={onClick}>Disabled</Button>);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('sm size: applies small padding class', () => {
    render(<Button size="sm">Small</Button>);
    expect(screen.getByRole('button').className).toContain('!py-1.5');
  });

  it('lg size: applies large padding class', () => {
    render(<Button size="lg">Large</Button>);
    expect(screen.getByRole('button').className).toContain('!py-3');
  });

  it('renders with no children without crashing', () => {
    render(<Button />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('passes extra HTML attributes through to the button element', () => {
    render(<Button aria-label="close-dialog">×</Button>);
    expect(screen.getByRole('button', { name: 'close-dialog' })).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('Button – Exception Handling', () => {
  it('does not crash when onClick is undefined', () => {
    render(<Button>No Handler</Button>);
    expect(() => fireEvent.click(screen.getByRole('button'))).not.toThrow();
  });

  it('does not call onClick when both disabled and isLoading are true', () => {
    const onClick = vi.fn();
    render(<Button disabled isLoading onClick={onClick}>Busy</Button>);
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).not.toHaveBeenCalled();
  });

  it('custom className is appended correctly alongside variant class', () => {
    render(<Button className="my-custom-class">Styled</Button>);
    const btn = screen.getByRole('button');
    expect(btn.className).toContain('btn-primary');
    expect(btn.className).toContain('my-custom-class');
  });

  it('ghost variant renders without crashing', () => {
    render(<Button variant="ghost">Ghost</Button>);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('isLoading → not loading transition: renders children again', () => {
    const { rerender } = render(<Button isLoading>Save</Button>);
    expect(screen.getByRole('button')).toBeDisabled();
    rerender(<Button isLoading={false}>Save</Button>);
    expect(screen.getByRole('button')).not.toBeDisabled();
    expect(screen.getByRole('button')).toHaveTextContent('Save');
  });
});
