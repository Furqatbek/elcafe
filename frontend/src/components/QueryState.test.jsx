import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import QueryState from './QueryState';

// Pins EH-2.4: the three states (loading / error+retry / empty) and that children render only when
// the fetch succeeded with data.

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (k, d) => d || k }),
}));
vi.mock('../lib/errors', () => ({ errorMessage: (e) => e.message || 'err' }));

describe('QueryState', () => {
  it('shows the loading state', () => {
    render(<QueryState loading><div>content</div></QueryState>);
    expect(screen.getByText('Loading…')).toBeInTheDocument();
    expect(screen.queryByText('content')).not.toBeInTheDocument();
  });

  it('shows the localized error and a working retry button', () => {
    const onRetry = vi.fn();
    render(
      <QueryState loading={false} error={{ message: 'Boom' }} onRetry={onRetry}>
        <div>content</div>
      </QueryState>,
    );
    expect(screen.getByText('Boom')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Try again'));
    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(screen.queryByText('content')).not.toBeInTheDocument();
  });

  it('shows the empty state with a custom message', () => {
    render(
      <QueryState loading={false} error={null} empty emptyMessage="No users">
        <div>content</div>
      </QueryState>,
    );
    expect(screen.getByText('No users')).toBeInTheDocument();
  });

  it('renders children on success with data', () => {
    render(<QueryState loading={false} error={null} empty={false}><div>content</div></QueryState>);
    expect(screen.getByText('content')).toBeInTheDocument();
  });
});
