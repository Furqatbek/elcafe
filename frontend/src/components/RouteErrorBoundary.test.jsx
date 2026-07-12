import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import RouteErrorBoundary from './RouteErrorBoundary';

// Pins EH-2.2: a crashing child renders the in-shell card (not a blank app), Try again clears it,
// and a resetKey change (route navigation) recovers automatically.

vi.mock('i18next', () => ({ default: { t: (k, o) => o?.defaultValue || k } }));

function Boom({ crash }) {
  if (crash) throw new Error('render exploded');
  return <div>healthy page</div>;
}

describe('RouteErrorBoundary', () => {
  beforeEach(() => vi.spyOn(console, 'error').mockImplementation(() => {}));

  it('renders children normally', () => {
    render(<RouteErrorBoundary resetKey="/a"><Boom crash={false} /></RouteErrorBoundary>);
    expect(screen.getByText('healthy page')).toBeInTheDocument();
  });

  it('catches a render crash and shows the card with Try again / Back', () => {
    render(<RouteErrorBoundary resetKey="/a"><Boom crash /></RouteErrorBoundary>);
    expect(screen.getByText('This page ran into a problem')).toBeInTheDocument();
    expect(screen.getByText('Try again')).toBeInTheDocument();
    expect(screen.getByText('Back')).toBeInTheDocument();
  });

  it('recovers when the route (resetKey) changes', () => {
    const { rerender } = render(
      <RouteErrorBoundary resetKey="/a"><Boom crash /></RouteErrorBoundary>,
    );
    expect(screen.getByText('This page ran into a problem')).toBeInTheDocument();
    rerender(<RouteErrorBoundary resetKey="/b"><Boom crash={false} /></RouteErrorBoundary>);
    expect(screen.getByText('healthy page')).toBeInTheDocument();
  });
});
