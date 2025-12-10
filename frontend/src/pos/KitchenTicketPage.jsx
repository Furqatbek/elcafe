import React, { useEffect } from 'react';
import KitchenTicket from './components/KitchenTicket';

/**
 * KitchenTicketPage - Standalone page for printing kitchen tickets
 * Opens in new window, auto-prints
 */
const KitchenTicketPage = () => {
  useEffect(() => {
    // Auto-print after a short delay to ensure content is loaded
    const timer = setTimeout(() => {
      window.print();
    }, 500);

    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="min-h-screen bg-white">
      <KitchenTicket />
    </div>
  );
};

export default KitchenTicketPage;
