import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import SystemUsers from './SystemUsers';
import { restaurantAPI, systemUserAPI } from '../services/api';

// Pins the SUPER_ADMIN-only restaurant binding UI (how a restaurant's FIRST admin is attached):
// the selector/column exist only for the platform operator, and an empty selection sends NO
// restaurantId key (backend: platform account on create, unchanged binding on edit).

// Benign never-resolving defaults — see PlatformConsole.test.jsx for why (restoreAllMocks +
// cold-runner async leak).
vi.mock('../services/api', () => {
  const pending = () => vi.fn(() => new Promise(() => {}));
  return {
    systemUserAPI: { getAll: pending(), create: pending(), update: pending(), delete: pending() },
    restaurantAPI: { getAll: pending() },
  };
});

const auth = vi.hoisted(() => ({ user: { role: 'SUPER_ADMIN' } }));
vi.mock('../store/authStore', () => ({
  useAuthStore: (selector) => selector({ user: auth.user }),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, defaultValue) => (typeof defaultValue === 'string' ? defaultValue : key),
  }),
}));

// Radix Select needs jsdom APIs we don't polyfill; render items as plain divs.
vi.mock('../components/ui/select', () => ({
  Select: ({ children }) => <div>{children}</div>,
  SelectTrigger: ({ children }) => <div>{children}</div>,
  SelectValue: ({ placeholder }) => <span>{placeholder}</span>,
  SelectContent: ({ children }) => <div>{children}</div>,
  SelectItem: ({ children }) => <div>{children}</div>,
}));
vi.mock('../components/ui/dialog', () => ({
  Dialog: ({ open, children }) => (open ? <div role="dialog">{children}</div> : null),
  DialogContent: ({ children }) => <div>{children}</div>,
  DialogHeader: ({ children }) => <div>{children}</div>,
  DialogTitle: ({ children }) => <h2>{children}</h2>,
  DialogFooter: ({ children }) => <div>{children}</div>,
}));

const usersEnvelope = (users) => ({ data: { data: users } });

describe('SystemUsers restaurant binding', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auth.user = { role: 'SUPER_ADMIN' };
    systemUserAPI.getAll.mockResolvedValue(usersEnvelope([
      { id: 1, email: 'a@t.co', firstName: 'A', lastName: 'B', phone: '', role: 'ADMIN', active: true, restaurantId: 3 },
    ]));
    restaurantAPI.getAll.mockResolvedValue({ data: { data: [{ id: 3, name: 'Kofi House' }] } });
  });

  it('SUPER_ADMIN sees the restaurant column and the binding selector in the create dialog', async () => {
    render(<SystemUsers />);

    expect(await screen.findByText('Kofi House')).toBeInTheDocument(); // column resolves id -> name
    expect(screen.getByText('Restaurant')).toBeInTheDocument();

    fireEvent.click(screen.getByText('Add User'));
    expect(await screen.findByText('— Platform (no restaurant)')).toBeInTheDocument();
    // The restaurant appears as a selectable option inside the dialog too.
    expect(screen.getAllByText('Kofi House').length).toBeGreaterThan(1);
  });

  it('a tenant admin gets neither the column nor the selector, and no restaurant fetch happens', async () => {
    auth.user = { role: 'ADMIN' };
    render(<SystemUsers />);

    await screen.findByText('a@t.co');
    expect(screen.queryByText('Restaurant')).not.toBeInTheDocument();
    expect(restaurantAPI.getAll).not.toHaveBeenCalled();

    fireEvent.click(screen.getByText('Add User'));
    expect(screen.queryByText('— Platform (no restaurant)')).not.toBeInTheDocument();
  });

  it('creating with no restaurant selected omits the restaurantId key entirely', async () => {
    systemUserAPI.create.mockResolvedValue({ data: { data: {} } });
    const { container } = render(<SystemUsers />);
    await screen.findByText('a@t.co');

    fireEvent.click(screen.getByText('Add User'));
    const dialog = screen.getByRole('dialog');
    const inputs = dialog.querySelectorAll('input');
    // Order in the form: firstName, lastName, email, password (inside PasswordInput), phone.
    fireEvent.change(inputs[0], { target: { value: 'New' } });
    fireEvent.change(inputs[1], { target: { value: 'Admin' } });
    fireEvent.change(inputs[2], { target: { value: 'new@t.co' } });
    fireEvent.change(inputs[3], { target: { value: 'Passw0rd!' } });

    // t('common.create') has no default string, so the mock returns the key itself.
    fireEvent.click(screen.getByText('common.create'));

    await waitFor(() => expect(systemUserAPI.create).toHaveBeenCalledTimes(1));
    const payload = systemUserAPI.create.mock.calls[0][0];
    expect(payload).not.toHaveProperty('restaurantId');
    expect(payload.email).toBe('new@t.co');
    expect(container).toBeTruthy();
  });
});
