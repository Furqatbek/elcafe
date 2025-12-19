import { format } from 'date-fns';

const PrintReceipt = (order, onPrint) => {
  const printWindow = window.open('', '_blank');
  const receiptHTML = generateReceiptHTML(order);

  printWindow.document.write(receiptHTML);
  printWindow.document.close();

  // Wait for content to load then print
  printWindow.onload = () => {
    printWindow.focus();
    printWindow.print();
    printWindow.close();
  };

  if (onPrint) onPrint();
};

const generateReceiptHTML = (order) => {
    const currentDate = format(new Date(), 'dd/MM/yyyy HH:mm:ss');
    const cashier = order.createdBy || 'System';

    return `
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <title>Receipt - ${order.orderNumber}</title>
  <link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700&display=swap" rel="stylesheet">
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    @page {
      size: A5;
      margin: 0;
    }

    @media print {
      body {
        margin: 0;
        padding: 0;
        -webkit-print-color-adjust: exact;
        print-color-adjust: exact;
      }

      .no-print {
        display: none !important;
      }
    }

    body {
      font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
      font-size: 14px;
      line-height: 1.6;
      background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
      padding: 20px;
      color: #2d3748;
    }

    .receipt {
      max-width: 400px;
      margin: 0 auto;
      background: white;
      box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
      border-radius: 16px;
      overflow: hidden;
    }

    /* Header */
    .header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      text-align: center;
      padding: 30px 20px;
      position: relative;
    }

    .header::after {
      content: '';
      position: absolute;
      bottom: -20px;
      left: 0;
      right: 0;
      height: 20px;
      background: white;
      border-radius: 20px 20px 0 0;
    }

    .brand-name {
      font-size: 36px;
      font-weight: 700;
      letter-spacing: 3px;
      margin-bottom: 8px;
      text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.2);
    }

    .restaurant-name {
      font-size: 14px;
      font-weight: 400;
      opacity: 0.95;
      letter-spacing: 1px;
    }

    /* Content */
    .content {
      padding: 30px 25px;
    }

    /* Subheader */
    .subheader {
      background: #f7fafc;
      padding: 15px;
      border-radius: 10px;
      margin-bottom: 20px;
      border-left: 4px solid #667eea;
    }

    .subheader-line {
      display: flex;
      justify-content: space-between;
      margin: 5px 0;
      font-size: 13px;
      color: #4a5568;
    }

    .subheader-line .label {
      font-weight: 600;
      color: #2d3748;
    }

    .order-badge {
      display: inline-block;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 6px 16px;
      border-radius: 20px;
      font-weight: 600;
      font-size: 14px;
      margin-bottom: 10px;
    }

    /* Customer Info */
    .customer-info {
      background: #edf2f7;
      padding: 15px;
      border-radius: 10px;
      margin-bottom: 20px;
      border-left: 4px solid #48bb78;
    }

    .customer-info-title {
      font-weight: 600;
      color: #2d3748;
      margin-bottom: 8px;
      font-size: 14px;
    }

    .customer-info-line {
      font-size: 13px;
      color: #4a5568;
      margin: 4px 0;
    }

    /* Items Section */
    .items-section {
      margin: 20px 0;
    }

    .items-title {
      font-size: 16px;
      font-weight: 600;
      color: #2d3748;
      margin-bottom: 12px;
      padding-bottom: 8px;
      border-bottom: 2px solid #e2e8f0;
    }

    .item-row {
      display: flex;
      justify-content: space-between;
      align-items: start;
      padding: 12px 0;
      border-bottom: 1px solid #edf2f7;
    }

    .item-row:last-child {
      border-bottom: 2px solid #cbd5e0;
    }

    .item-details {
      flex: 1;
    }

    .item-name {
      font-weight: 600;
      color: #2d3748;
      font-size: 14px;
      margin-bottom: 2px;
    }

    .item-variant {
      font-size: 12px;
      color: #718096;
      font-style: italic;
    }

    .item-qty {
      background: #edf2f7;
      padding: 4px 12px;
      border-radius: 6px;
      font-weight: 600;
      color: #4a5568;
      font-size: 13px;
      margin: 0 10px;
      white-space: nowrap;
    }

    .item-price {
      font-weight: 600;
      color: #2d3748;
      font-size: 14px;
      min-width: 70px;
      text-align: right;
    }

    /* Totals */
    .totals {
      margin: 20px 0;
      padding: 15px;
      background: #f7fafc;
      border-radius: 10px;
    }

    .total-line {
      display: flex;
      justify-content: space-between;
      margin: 8px 0;
      font-size: 14px;
    }

    .total-line .label {
      color: #4a5568;
      font-weight: 500;
    }

    .total-line .amount {
      font-weight: 600;
      color: #2d3748;
    }

    .total-line.grand-total {
      margin-top: 15px;
      padding-top: 15px;
      border-top: 2px solid #cbd5e0;
      font-size: 18px;
    }

    .total-line.grand-total .label {
      color: #2d3748;
      font-weight: 700;
    }

    .total-line.grand-total .amount {
      color: #667eea;
      font-weight: 700;
      font-size: 22px;
    }

    /* Payment Info */
    .payment-info {
      background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
      color: white;
      padding: 12px 15px;
      border-radius: 10px;
      margin: 15px 0;
      text-align: center;
    }

    .payment-method {
      font-weight: 600;
      font-size: 14px;
    }

    /* Notes */
    .notes-section {
      background: #fff5f5;
      border-left: 4px solid #fc8181;
      padding: 12px 15px;
      border-radius: 10px;
      margin: 15px 0;
    }

    .notes-title {
      font-weight: 600;
      color: #c53030;
      margin-bottom: 5px;
      font-size: 13px;
    }

    .notes-content {
      color: #742a2a;
      font-size: 13px;
      font-style: italic;
    }

    /* Footer */
    .footer {
      background: #2d3748;
      color: white;
      text-align: center;
      padding: 25px 20px;
      margin-top: 20px;
    }

    .contact-info {
      margin: 10px 0;
      font-size: 13px;
      opacity: 0.9;
    }

    .contact-info div {
      margin: 5px 0;
    }

    .thank-you {
      font-size: 20px;
      font-weight: 700;
      margin: 15px 0 8px 0;
      color: #ffd700;
    }

    .visit-again {
      font-size: 14px;
      font-style: italic;
      opacity: 0.9;
    }

    .timestamp {
      margin-top: 15px;
      font-size: 11px;
      opacity: 0.7;
      border-top: 1px solid rgba(255, 255, 255, 0.2);
      padding-top: 10px;
    }

    /* Print button */
    .print-button {
      position: fixed;
      top: 20px;
      right: 20px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      border: none;
      padding: 12px 24px;
      border-radius: 8px;
      font-weight: 600;
      font-size: 14px;
      cursor: pointer;
      box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
      transition: transform 0.2s;
    }

    .print-button:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(102, 126, 234, 0.5);
    }

    @media print {
      .print-button {
        display: none;
      }

      body {
        background: white;
        padding: 0;
      }

      .receipt {
        box-shadow: none;
        border-radius: 0;
        max-width: 100%;
      }
    }
  </style>
</head>
<body>
  <!-- Print Button -->
  <button class="print-button no-print" onclick="window.print()">🖨️ Print Receipt</button>

  <div class="receipt">
    <!-- Header -->
    <div class="header">
      <div class="brand-name">LaCasa</div>
      <div class="restaurant-name">${order.restaurant?.name || 'Restaurant'}</div>
    </div>

    <!-- Content -->
    <div class="content">
      <!-- Order Badge -->
      <div style="text-align: center; margin-bottom: 20px;">
        <span class="order-badge">Order #${order.orderNumber}</span>
      </div>

      <!-- Subheader Info -->
      <div class="subheader">
        <div class="subheader-line">
          <span class="label">Date & Time:</span>
          <span>${currentDate}</span>
        </div>
        <div class="subheader-line">
          <span class="label">Cashier:</span>
          <span>${cashier}</span>
        </div>
        ${order.orderType ? `
        <div class="subheader-line">
          <span class="label">Order Type:</span>
          <span>${order.orderType.replace(/_/g, ' ')}</span>
        </div>
        ` : ''}
        ${order.diningTable ? `
        <div class="subheader-line">
          <span class="label">Table:</span>
          <span>${order.diningTable.tableNumber}${order.diningTable.section ? ' - ' + order.diningTable.section : ''}</span>
        </div>
        ` : ''}
      </div>

      <!-- Customer Info -->
      ${order.deliveryInfo ? `
      <div class="customer-info">
        <div class="customer-info-title">👤 Customer Information</div>
        <div class="customer-info-line">
          <strong>Name:</strong> ${order.deliveryInfo.contactName || 'N/A'}
        </div>
        <div class="customer-info-line">
          <strong>Phone:</strong> ${order.deliveryInfo.contactPhone || 'N/A'}
        </div>
        ${order.deliveryInfo.address ? `
        <div class="customer-info-line">
          <strong>Address:</strong> ${order.deliveryInfo.address}, ${order.deliveryInfo.city || ''}
        </div>
        ` : ''}
      </div>
      ` : ''}

      <!-- Items Section -->
      <div class="items-section">
        <div class="items-title">📋 Order Items</div>
        ${(order.items || []).map(item => `
        <div class="item-row">
          <div class="item-details">
            <div class="item-name">${item.productName}</div>
            ${item.variantName ? `<div class="item-variant">${item.variantName}</div>` : ''}
          </div>
          <div class="item-qty">×${item.quantity}</div>
          <div class="item-price">$${(item.totalPrice || 0).toFixed(2)}</div>
        </div>
        `).join('')}
      </div>

      <!-- Totals -->
      <div class="totals">
        <div class="total-line">
          <span class="label">Subtotal</span>
          <span class="amount">$${(order.subtotal || 0).toFixed(2)}</span>
        </div>

        ${order.tax ? `
        <div class="total-line">
          <span class="label">Tax</span>
          <span class="amount">$${(order.tax || 0).toFixed(2)}</span>
        </div>
        ` : ''}

        ${order.deliveryFee && order.deliveryFee > 0 ? `
        <div class="total-line">
          <span class="label">Delivery Fee</span>
          <span class="amount">$${(order.deliveryFee || 0).toFixed(2)}</span>
        </div>
        ` : ''}

        ${order.discount && order.discount > 0 ? `
        <div class="total-line">
          <span class="label">Discount</span>
          <span class="amount">-$${(order.discount || 0).toFixed(2)}</span>
        </div>
        ` : ''}

        <div class="total-line grand-total">
          <span class="label">TOTAL</span>
          <span class="amount">$${(order.total || 0).toFixed(2)}</span>
        </div>
      </div>

      <!-- Payment Info -->
      ${order.payment ? `
      <div class="payment-info">
        <div class="payment-method">
          💳 ${order.payment.method || 'Payment'} • ${order.payment.status || 'Status'}
        </div>
      </div>
      ` : ''}

      <!-- Notes -->
      ${order.customerNotes ? `
      <div class="notes-section">
        <div class="notes-title">📝 Special Notes</div>
        <div class="notes-content">${order.customerNotes}</div>
      </div>
      ` : ''}
    </div>

    <!-- Footer -->
    <div class="footer">
      <div class="contact-info">
        <div>📞 +1 (555) 123-4567</div>
        <div>✉️ info@lacasa.com</div>
        <div>🌐 www.lacasa.com</div>
      </div>

      <div class="thank-you">★ Thank You! ★</div>
      <div class="visit-again">We appreciate your business!</div>

      <div class="timestamp">${currentDate}</div>
    </div>
  </div>
</body>
</html>
    `;
};

export default PrintReceipt;
