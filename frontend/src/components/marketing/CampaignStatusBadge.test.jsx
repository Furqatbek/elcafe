import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import CampaignStatusBadge, { CAMPAIGN_STATUS_COLORS } from './CampaignStatusBadge';

describe('CampaignStatusBadge', () => {
  it('shows the status label', () => {
    render(<CampaignStatusBadge status="SENDING" />);
    expect(screen.getByText('SENDING')).toBeInTheDocument();
  });

  it('applies the colour mapped to the status', () => {
    render(<CampaignStatusBadge status="COMPLETED" />);
    expect(screen.getByText('COMPLETED')).toHaveClass('bg-green-500');
  });

  it('maps every campaign lifecycle status to a colour', () => {
    // Mirrors the backend CampaignStatus enum shared by SMS and Telegram.
    ['DRAFT', 'SCHEDULED', 'SENDING', 'PAUSED', 'COMPLETED', 'CANCELLED'].forEach((status) => {
      expect(CAMPAIGN_STATUS_COLORS[status]).toBeTruthy();
    });
  });
});
