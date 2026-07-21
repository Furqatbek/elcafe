import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import TenantOnboardingWizard from './TenantOnboardingWizard';
import { restaurantAPI, systemUserAPI } from '../../services/api';

// Pins the onboarding orchestration: since there is no combined backend endpoint, the wizard must
// create the restaurant first, then create the owner bound to the *returned* restaurant id.

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (k, d) => d || k }) }));
vi.mock('../../services/api', () => ({
  restaurantAPI: { create: vi.fn() },
  systemUserAPI: { create: vi.fn() },
}));
vi.mock('../../lib/errors', () => ({ notifyError: vi.fn(), notifySuccess: vi.fn(), notifyWarning: vi.fn() }));
vi.mock('../../utils/passwordGenerator', () => ({ generatePassword: () => 'Temp1234!abcd' }));

describe('TenantOnboardingWizard', () => {
  beforeEach(() => vi.clearAllMocks());

  it('creates the restaurant, then the owner bound to the returned restaurant id', async () => {
    restaurantAPI.create.mockResolvedValue({ data: { data: { id: 42 } } });
    systemUserAPI.create.mockResolvedValue({});
    const onOpenChange = vi.fn();
    const onComplete = vi.fn();

    render(<TenantOnboardingWizard open onOpenChange={onOpenChange} onComplete={onComplete} />);

    // Step 1 — restaurant (textboxes: name, address, city, phone, email)
    let boxes = screen.getAllByRole('textbox');
    fireEvent.change(boxes[0], { target: { value: 'Kofe House' } });
    fireEvent.change(boxes[1], { target: { value: '12 Main St' } });
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    // Step 2 — owner (textboxes: firstName, lastName, email, phone; password is prefilled)
    boxes = screen.getAllByRole('textbox');
    fireEvent.change(boxes[0], { target: { value: 'Ali' } });
    fireEvent.change(boxes[1], { target: { value: 'Valiev' } });
    fireEvent.change(boxes[2], { target: { value: 'ali@kofe.uz' } });
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));

    // Step 3 — no employees, just create
    fireEvent.click(screen.getByRole('button', { name: 'Create tenant' }));

    await waitFor(() => expect(systemUserAPI.create).toHaveBeenCalledTimes(1));

    expect(restaurantAPI.create).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'Kofe House', address: '12 Main St' }),
    );
    expect(systemUserAPI.create).toHaveBeenCalledWith(expect.objectContaining({
      email: 'ali@kofe.uz', firstName: 'Ali', lastName: 'Valiev', role: 'OWNER', restaurantId: 42,
    }));
    expect(onComplete).toHaveBeenCalled();
    expect(onOpenChange).toHaveBeenCalledWith(false); // wizard closes on success
  });

  it('keeps the tenant and hands off when the owner step fails (no rollback)', async () => {
    restaurantAPI.create.mockResolvedValue({ data: { data: { id: 42 } } });
    systemUserAPI.create.mockRejectedValue(new Error('email exists'));
    const onComplete = vi.fn();

    render(<TenantOnboardingWizard open onOpenChange={vi.fn()} onComplete={onComplete} />);

    let boxes = screen.getAllByRole('textbox');
    fireEvent.change(boxes[0], { target: { value: 'Kofe House' } });
    fireEvent.change(boxes[1], { target: { value: '12 Main St' } });
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    boxes = screen.getAllByRole('textbox');
    fireEvent.change(boxes[0], { target: { value: 'Ali' } });
    fireEvent.change(boxes[1], { target: { value: 'Valiev' } });
    fireEvent.change(boxes[2], { target: { value: 'ali@kofe.uz' } });
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    fireEvent.click(screen.getByRole('button', { name: 'Create tenant' }));

    // restaurant was created; owner failed → we still refresh the list (tenant is visible) rather
    // than orphan-retry, and don't attempt a rollback.
    await waitFor(() => expect(onComplete).toHaveBeenCalled());
    expect(restaurantAPI.create).toHaveBeenCalledTimes(1);
    expect(systemUserAPI.create).toHaveBeenCalledTimes(1);
  });
});
