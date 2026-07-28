import React from 'react';
import { Badge } from '../ui/badge';

/**
 * Campaign lifecycle status → badge colour, shared by the SMS and Telegram marketing pages. Both had
 * this exact map and badge copy-pasted, so a colour tweak had to be made in two places and could
 * drift; now it lands once.
 *
 * Instagram's campaign badges use a different palette (light chips with coloured text) and are
 * intentionally NOT routed through here — see InstagramMarketing.
 */
export const CAMPAIGN_STATUS_COLORS = {
  DRAFT: 'bg-gray-500',
  SCHEDULED: 'bg-blue-500',
  SENDING: 'bg-yellow-500',
  PAUSED: 'bg-orange-500',
  COMPLETED: 'bg-green-500',
  CANCELLED: 'bg-red-500',
};

/** A pill showing a campaign's status, coloured by {@link CAMPAIGN_STATUS_COLORS}. */
export default function CampaignStatusBadge({ status }) {
  return <Badge className={CAMPAIGN_STATUS_COLORS[status]}>{status}</Badge>;
}
