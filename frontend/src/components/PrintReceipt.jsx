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
      font-family: 'Courier New', monospace;
      font-size: 12px;
      line-height: 1.4;
      width: 80mm;
      margin: 0 auto;
      padding: 10px;
      background: white;
      color: black;
    }

    .receipt {
      width: 100%;
    }

    /* Header */
    .header {
      text-align: center;
      margin-bottom: 15px;
      border-bottom: 2px solid #000;
      padding-bottom: 10px;
    }

    .brand-name {
      font-size: 24px;
      font-weight: bold;
      letter-spacing: 2px;
      margin-bottom: 5px;
    }

    .subheader {
      font-size: 10px;
      margin-top: 8px;
    }

    .subheader-line {
      margin: 2px 0;
    }

    /* Divider */
    .divider {
      border-top: 1px dashed #000;
      margin: 10px 0;
    }

    .divider-solid {
      border-top: 2px solid #000;
      margin: 10px 0;
    }

    /* Order Info */
    .order-info {
      margin-bottom: 10px;
      font-size: 11px;
    }

    .order-info-line {
      display: flex;
      justify-content: space-between;
      margin: 3px 0;
    }

    .label {
      font-weight: bold;
    }

    /* Items Table */
    .items-table {
      width: 100%;
      margin: 10px 0;
      border-collapse: collapse;
    }

    .items-table th {
      border-top: 2px solid #000;
      border-bottom: 2px solid #000;
      padding: 5px 2px;
      text-align: left;
      font-weight: bold;
      font-size: 11px;
    }

    .items-table th.qty {
      width: 15%;
      text-align: center;
    }

    .items-table th.name {
      width: 55%;
    }

    .items-table th.price {
      width: 30%;
      text-align: right;
    }

    .items-table td {
      padding: 5px 2px;
      border-bottom: 1px solid #ddd;
      font-size: 11px;
    }

    .items-table td.qty {
      text-align: center;
    }

    .items-table td.name {
      word-wrap: break-word;
    }

    .items-table td.price {
      text-align: right;
    }

    .items-table tr:last-child td {
      border-bottom: 2px solid #000;
    }

    /* Totals */
    .totals {
      margin: 10px 0;
      font-size: 11px;
    }

    .total-line {
      display: flex;
      justify-content: space-between;
      margin: 5px 0;
      padding: 2px 0;
    }

    .total-line.subtotal {
      font-size: 11px;
    }

    .total-line.grand-total {
      border-top: 2px solid #000;
      border-bottom: 2px solid #000;
      padding: 8px 0;
      margin-top: 8px;
      font-size: 16px;
      font-weight: bold;
    }

    .total-line.grand-total .amount {
      font-size: 18px;
    }

    /* Payment Info */
    .payment-info {
      margin: 10px 0;
      font-size: 11px;
      text-align: center;
    }

    /* Footer */
    .footer {
      margin-top: 15px;
      padding-top: 10px;
      border-top: 2px solid #000;
      text-align: center;
      font-size: 10px;
    }

    .contact-info {
      margin: 5px 0;
    }

    .thank-you {
      margin-top: 10px;
      font-weight: bold;
      font-size: 12px;
    }

    .visit-again {
      margin-top: 5px;
      font-style: italic;
    }
  </style>
</head>
<body>
  <div class="receipt">
    <!-- Header -->
    <div class="header">
      <div class="brand-name">LaCasa</div>
      <div class="subheader">
        ${order.restaurant?.name || 'Restaurant'}
      </div>
    </div>

    <!-- Subheader -->
    <div class="subheader">
      <div class="subheader-line">Date: ${currentDate}</div>
      <div class="subheader-line">Cashier: ${cashier}</div>
      <div class="subheader-line">Order #: ${order.orderNumber}</div>
      ${order.orderType ? `<div class="subheader-line">Type: ${order.orderType.replace(/_/g, ' ')}</div>` : ''}
      ${order.diningTable ? `<div class="subheader-line">Table: ${order.diningTable.tableNumber}${order.diningTable.section ? ' - ' + order.diningTable.section : ''}</div>` : ''}
    </div>

    <div class="divider-solid"></div>

    <!-- Customer Info -->
    ${order.deliveryInfo ? `
    <div class="order-info">
      <div class="order-info-line">
        <span class="label">Customer:</span>
        <span>${order.deliveryInfo.contactName || 'N/A'}</span>
      </div>
      <div class="order-info-line">
        <span class="label">Phone:</span>
        <span>${order.deliveryInfo.contactPhone || 'N/A'}</span>
      </div>
      ${order.deliveryInfo.address ? `
      <div class="order-info-line">
        <span class="label">Address:</span>
      </div>
      <div style="margin-left: 10px; font-size: 10px;">
        ${order.deliveryInfo.address}, ${order.deliveryInfo.city || ''}
      </div>
      ` : ''}
    </div>
    <div class="divider"></div>
    ` : ''}

    <!-- Items Table -->
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
            ${item.variantName ? `<br><small style="font-size: 9px;">(${item.variantName})</small>` : ''}
          </td>
          <td class="price">$${(item.totalPrice || 0).toFixed(2)}</td>
        </tr>
        `).join('')}
      </tbody>
    </table>

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
      <div class="divider"></div>
      <div>Payment Method: ${order.payment.method || 'N/A'}</div>
      <div>Status: ${order.payment.status || 'N/A'}</div>
      <div class="divider"></div>
    </div>
    ` : ''}

    ${order.customerNotes ? `
    <div class="order-info">
      <div class="label">Notes:</div>
      <div style="margin-top: 5px; font-size: 10px; font-style: italic;">
        ${order.customerNotes}
      </div>
    </div>
    <div class="divider"></div>
    ` : ''}

    <!-- Footer -->
    <div class="footer">
      <div class="contact-info">
        <div>Phone: +1 (555) 123-4567</div>
        <div>Email: info@lacasa.com</div>
        <div>www.lacasa.com</div>
      </div>

      <div class="thank-you">
        ★ Thank You! ★
      </div>

      <div class="visit-again">
        Please visit us again!
      </div>

      <div style="margin-top: 10px; font-size: 9px;">
        ${currentDate}
      </div>
    </div>
  </div>
</body>
</html>
    `;
};

export default PrintReceipt;
