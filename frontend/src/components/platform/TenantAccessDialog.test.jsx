import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import TenantAccessDialog from './TenantAccessDialog';
import { systemUserAPI } from '../../services/api';

// Pins the per-tenant access panel: it lists only THIS restaurant's users, grants access by creating
// a system user bound to the restaurant, and revokes via soft-deactivate.

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (k, d) => d || k }) }));
vi.mock('../../services/api', () => ({
  systemUserAPI: { getAll: vi.fn(), create: vi.fn(), delete: vi.fn(), update: vi.fn() },
}));
vi.mock('../../lib/errors', () => ({ notifyError: vi.fn(), notifySuccess: vi.fn() }));
vi.mock('../../utils/passwordGenerator', () => ({ generatePassword: () => 'Temp1234!abcd' }));

const TENANT = { restaurantId: 5, name: 'Kofe House' };

describe('TenantAccessDialog', () => {
  beforeEach(() => vi.clearAllMocks());

  it('lists only the selected restaurant’s users', async () => {
    systemUserAPI.getAll.mockResolvedValue({ data: { data: [
      { id: 1, restaurantId: 5, firstName: 'Ali', lastName: 'Valiev', email: 'ali@kofe.uz', role: 'OWNER', active: true },
      { id: 2, restaurantId: 9, firstName: 'Other', lastName: 'Tenant', email: 'x@other.uz', role: 'OWNER', active: true },
    ] } });

    render(<TenantAccessDialog tenant={TENANT} onOpenChange={vi.fn()} />);

    expect(await screen.findByText('ali@kofe.uz')).toBeInTheDocument();
    expect(screen.queryByText('x@other.uz')).not.toBeInTheDocument(); // other tenant filtered out
  });

  it('grants access by creating a user bound to the restaurant, then refetches', async () => {
    systemUserAPI.getAll.mockResolvedValue({ data: { data: [] } });
    systemUserAPI.create.mockResolvedValue({});

    render(<TenantAccessDialog tenant={TENANT} onOpenChange={vi.fn()} />);
    await screen.findByText('No accounts yet — grant access below.');

    fireEvent.click(screen.getByRole('button', { name: /Add owner or employee/ }));
    fireEvent.change(screen.getByPlaceholderText(/First name/), { target: { value: 'Nodir' } });
    fireEvent.change(screen.getByPlaceholderText(/Last name/), { target: { value: 'Karimov' } });
    fireEvent.change(screen.getByPlaceholderText(/Email/), { target: { value: 'nodir@kofe.uz' } });
    fireEvent.click(screen.getByRole('button', { name: 'Grant access' }));

    await waitFor(() => expect(systemUserAPI.create).toHaveBeenCalledTimes(1));
    expect(systemUserAPI.create).toHaveBeenCalledWith(expect.objectContaining({
      email: 'nodir@kofe.uz', firstName: 'Nodir', lastName: 'Karimov', role: 'MANAGER', restaurantId: 5,
    }));
    // refetch after a successful grant
    await waitFor(() => expect(systemUserAPI.getAll).toHaveBeenCalledTimes(2));
  });

  it('revokes access via soft-deactivate after confirmation', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    systemUserAPI.getAll.mockResolvedValue({ data: { data: [
      { id: 7, restaurantId: 5, firstName: 'Ali', lastName: 'Valiev', email: 'ali@kofe.uz', role: 'OWNER', active: true },
    ] } });
    systemUserAPI.delete.mockResolvedValue({});

    render(<TenantAccessDialog tenant={TENANT} onOpenChange={vi.fn()} />);
    await screen.findByText('ali@kofe.uz');

    fireEvent.click(screen.getByRole('button', { name: /Revoke/ }));

    await waitFor(() => expect(systemUserAPI.delete).toHaveBeenCalledWith(7));
  });
});
