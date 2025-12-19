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
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }

    @page {
      size: 80mm auto;
      margin: 0;
    }

    @media print {
      body {
        margin: 0;
        padding: 0;
      }
      .no-print {
        display: none !important;
      }
    }

    body {
      font-family: Arial, Helvetica, sans-serif;
      font-size: 16px;
      line-height: 1.4;
      width: 80mm;
      margin: 0 auto;
      padding: 0;
      background: white;
      color: black;
    }

    .receipt {
      width: 100%;
      padding: 10mm 5mm;
    }

    /* Header */
    .header {
      text-align: center;
      margin-bottom: 15px;
      padding-bottom: 10px;
      border-bottom: 3px solid black;
    }

    .brand-name {
      font-size: 32px;
      font-weight: bold;
      letter-spacing: 4px;
      margin-bottom: 5px;
      text-transform: uppercase;
    }

    .restaurant-name {
      font-size: 18px;
      font-weight: bold;
      margin-top: 5px;
    }

    /* Subheader */
    .subheader {
      margin: 15px 0;
      padding: 10px;
      border: 2px solid black;
      background: white;
    }

    .subheader-line {
      display: flex;
      justify-content: space-between;
      margin: 5px 0;
      font-size: 16px;
      line-height: 1.6;
    }

    .subheader-line .label {
      font-weight: bold;
    }

    .subheader-line .value {
      text-align: right;
    }

    .order-number {
      text-align: center;
      font-size: 24px;
      font-weight: bold;
      margin: 15px 0;
      padding: 10px;
      border: 3px solid black;
    }

    /* Customer Info */
    .customer-info {
      margin: 15px 0;
      padding: 10px;
      border: 2px solid black;
    }

    .customer-info-title {
      font-weight: bold;
      font-size: 18px;
      margin-bottom: 8px;
      text-decoration: underline;
    }

    .customer-info-line {
      font-size: 16px;
      margin: 5px 0;
      line-height: 1.5;
    }

    /* Items Table */
    .items-section {
      margin: 15px 0;
    }

    .items-title {
      font-size: 20px;
      font-weight: bold;
      text-align: center;
      margin-bottom: 10px;
      padding: 8px;
      border: 3px solid black;
      background: white;
    }

    .items-table {
      width: 100%;
      border-collapse: collapse;
      margin: 10px 0;
    }

    .items-table th {
      border: 2px solid black;
      padding: 8px 4px;
      text-align: left;
      font-weight: bold;
      font-size: 16px;
      background: black;
      color: white;
    }

    .items-table th.qty {
      width: 15%;
      text-align: center;
    }

    .items-table th.name {
      width: 50%;
    }

    .items-table th.price {
      width: 35%;
      text-align: right;
    }

    .items-table td {
      border: 2px solid black;
      padding: 8px 4px;
      font-size: 16px;
      line-height: 1.5;
    }

    .items-table td.qty {
      text-align: center;
      font-weight: bold;
    }

    .items-table td.name {
      font-weight: bold;
    }

    .items-table td.price {
      text-align: right;
      font-weight: bold;
    }

    .item-variant {
      font-size: 14px;
      font-weight: normal;
      font-style: italic;
      margin-top: 2px;
    }

    /* Totals */
    .totals {
      margin: 15px 0;
      border: 3px solid black;
      padding: 10px;
    }

    .total-line {
      display: flex;
      justify-content: space-between;
      margin: 8px 0;
      font-size: 18px;
      padding: 5px 0;
    }

    .total-line .label {
      font-weight: bold;
    }

    .total-line .amount {
      font-weight: bold;
      text-align: right;
    }

    .total-line.subtotal {
      border-bottom: 1px solid black;
      padding-bottom: 8px;
    }

    .total-line.grand-total {
      border-top: 3px solid black;
      border-bottom: 3px solid black;
      padding: 12px 0;
      margin-top: 10px;
      font-size: 24px;
    }

    .total-line.grand-total .label {
      font-weight: bold;
      text-transform: uppercase;
    }

    .total-line.grand-total .amount {
      font-weight: bold;
      font-size: 28px;
    }

    /* Payment Info */
    .payment-info {
      margin: 15px 0;
      padding: 10px;
      border: 2px solid black;
      text-align: center;
      font-size: 18px;
      font-weight: bold;
    }

    /* Notes */
    .notes-section {
      margin: 15px 0;
      padding: 10px;
      border: 2px solid black;
    }

    .notes-title {
      font-weight: bold;
      font-size: 18px;
      margin-bottom: 5px;
    }

    .notes-content {
      font-size: 16px;
      font-style: italic;
      line-height: 1.5;
    }

    /* Footer */
    .footer {
      margin-top: 20px;
      padding-top: 15px;
      border-top: 3px solid black;
      text-align: center;
    }

    .contact-info {
      margin: 10px 0;
      font-size: 16px;
      line-height: 1.8;
    }

    .contact-info div {
      margin: 5px 0;
      font-weight: bold;
    }

    .thank-you {
      font-size: 24px;
      font-weight: bold;
      margin: 15px 0;
      padding: 10px;
      border: 3px solid black;
    }

    .visit-again {
      font-size: 18px;
      font-weight: bold;
      margin: 10px 0;
    }

    .timestamp {
      margin-top: 15px;
      font-size: 14px;
      padding-top: 10px;
      border-top: 2px solid black;
    }

    /* Print button */
    .print-button {
      position: fixed;
      top: 20px;
      right: 20px;
      background: black;
      color: white;
      border: 3px solid black;
      padding: 15px 30px;
      font-weight: bold;
      font-size: 16px;
      cursor: pointer;
      box-shadow: 0 4px 8px rgba(0, 0, 0, 0.3);
    }

    .print-button:hover {
      background: #333;
    }

    .print-button:active {
      background: #555;
    }
  </style>
</head>
<body>
  <!-- Print Button -->
  <button class="print-button no-print" onclick="window.print()">PRINT RECEIPT</button>

  <div class="receipt">
    <!-- Header -->
    <div class="header">
      <div class="brand-name">LaCasa</div>
      <div class="restaurant-name">${order.restaurant?.name || 'Restaurant'}</div>
    </div>

    <!-- Order Number -->
    <div class="order-number">
      ORDER #${order.orderNumber}
    </div>

    <!-- Subheader Info -->
    <div class="subheader">
      <div class="subheader-line">
        <span class="label">Date & Time:</span>
        <span class="value">${currentDate}</span>
      </div>
      <div class="subheader-line">
        <span class="label">Cashier:</span>
        <span class="value">${cashier}</span>
      </div>
      ${order.orderType ? `
      <div class="subheader-line">
        <span class="label">Type:</span>
        <span class="value">${order.orderType.replace(/_/g, ' ')}</span>
      </div>
      ` : ''}
      ${order.diningTable ? `
      <div class="subheader-line">
        <span class="label">Table:</span>
        <span class="value">${order.diningTable.tableNumber}${order.diningTable.section ? ' - ' + order.diningTable.section : ''}</span>
      </div>
      ` : ''}
    </div>

    <!-- Customer Info -->
    ${order.deliveryInfo ? `
    <div class="customer-info">
      <div class="customer-info-title">CUSTOMER INFORMATION</div>
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
      <div class="items-title">ORDER ITEMS</div>

      <table class="items-table">
        <thead>
          <tr>
            <th class="qty">QTY</th>
            <th class="name">ITEM</th>
            <th class="price">PRICE</th>
          </tr>
        </thead>
        <tbody>
          ${(order.items || []).map(item => `
          <tr>
            <td class="qty">${item.quantity}</td>
            <td class="name">
              ${item.productName}
              ${item.variantName ? `<div class="item-variant">(${item.variantName})</div>` : ''}
            </td>
            <td class="price">$${(item.totalPrice || 0).toFixed(2)}</td>
          </tr>
          `).join('')}
        </tbody>
      </table>
    </div>

    <!-- Totals -->
    <div class="totals">
      <div class="total-line subtotal">
        <span class="label">Subtotal:</span>
        <span class="amount">$${(order.subtotal || 0).toFixed(2)}</span>
      </div>

      ${order.tax ? `
      <div class="total-line">
        <span class="label">Tax:</span>
        <span class="amount">$${(order.tax || 0).toFixed(2)}</span>
      </div>
      ` : ''}

      ${order.deliveryFee && order.deliveryFee > 0 ? `
      <div class="total-line">
        <span class="label">Delivery Fee:</span>
        <span class="amount">$${(order.deliveryFee || 0).toFixed(2)}</span>
      </div>
      ` : ''}

      ${order.discount && order.discount > 0 ? `
      <div class="total-line">
        <span class="label">Discount:</span>
        <span class="amount">-$${(order.discount || 0).toFixed(2)}</span>
      </div>
      ` : ''}

      <div class="total-line grand-total">
        <span class="label">TOTAL:</span>
        <span class="amount">$${(order.total || 0).toFixed(2)}</span>
      </div>
    </div>

    <!-- Payment Info -->
    ${order.payment ? `
    <div class="payment-info">
      PAYMENT: ${order.payment.method || 'N/A'} - ${order.payment.status || 'N/A'}
    </div>
    ` : ''}

    <!-- Notes -->
    ${order.customerNotes ? `
    <div class="notes-section">
      <div class="notes-title">SPECIAL NOTES:</div>
      <div class="notes-content">${order.customerNotes}</div>
    </div>
    ` : ''}

    <!-- Footer -->
    <div class="footer">
      <div class="contact-info">
        <div>Phone: +1 (555) 123-4567</div>
        <div>Email: info@lacasa.com</div>
        <div>www.lacasa.com</div>
      </div>

      <div class="thank-you">*** THANK YOU! ***</div>
      <div class="visit-again">Please visit us again!</div>

      <div class="timestamp">${currentDate}</div>
    </div>
  </div>
</body>
</html>
    `;
};

export default PrintReceipt;
