import { format } from 'date-fns';

/**
 * Get table number from order data
 * Handles different data structures:
 * - order.diningTable.tableNumber (from Order entity)
 * - order.dineInInfo.tableNumber (from POSOrderResponse)
 * - order.tableIds (comma-separated table IDs for multi-table orders)
 */
const getTableNumber = (order) => {
  // Try diningTable (Order entity structure)
  if (order.diningTable?.tableNumber) {
    return order.diningTable.tableNumber;
  }
  // Try dineInInfo (POSOrderResponse structure)
  if (order.dineInInfo?.tableNumber) {
    return order.dineInInfo.tableNumber;
  }
  // Fallback: try tableIds field
  if (order.tableIds) {
    return order.tableIds;
  }
  return null;
};

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
    const currentDate = format(new Date(), 'dd/MM/yyyy HH:mm');

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
      size: 58mm auto;
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
      font-size: 13px;
      font-weight: 500;
      line-height: 1.3;
      width: 58mm;
      margin: 0 auto;
      padding: 0;
      background: white;
      color: black;
    }

    .receipt {
      width: 100%;
      padding: 2mm;
    }

    .header {
      text-align: center;
      margin-bottom: 4px;
      padding-bottom: 4px;
      border-bottom: 1px dashed black;
    }

    .brand-name {
      font-size: 20px;
      font-weight: bold;
      letter-spacing: 2px;
    }

    .info-section {
      margin: 4px 0;
      padding: 3px 0;
      border-bottom: 1px dashed black;
      font-size: 12px;
      font-weight: 600;
    }

    .info-line {
      display: flex;
      justify-content: space-between;
      margin: 2px 0;
    }

    .items-table {
      width: 100%;
      border-collapse: collapse;
      margin: 4px 0;
      font-size: 12px;
    }

    .items-table th {
      border-bottom: 1px solid black;
      padding: 3px 1px;
      font-size: 11px;
      font-weight: bold;
      text-align: left;
    }

    .items-table th:nth-child(2),
    .items-table th:nth-child(3),
    .items-table th:nth-child(4) {
      text-align: right;
    }

    .items-table td {
      padding: 3px 1px;
      font-size: 12px;
      font-weight: 500;
      vertical-align: top;
    }

    .items-table td.qty {
      text-align: right;
      width: 20px;
      font-weight: 600;
    }

    .items-table td.unit-price {
      text-align: right;
      width: 45px;
    }

    .items-table td.price {
      text-align: right;
      font-weight: bold;
      width: 50px;
    }

    .item-variant {
      font-size: 10px;
      font-style: italic;
    }

    .totals {
      margin: 4px 0;
      padding-top: 4px;
      border-top: 1px dashed black;
    }

    .total-line {
      display: flex;
      justify-content: space-between;
      margin: 2px 0;
      font-size: 12px;
      font-weight: 600;
    }

    .total-line.grand-total {
      border-top: 2px solid black;
      padding-top: 4px;
      margin-top: 4px;
      font-size: 16px;
      font-weight: bold;
    }

    .notes-section {
      margin: 4px 0;
      padding: 3px;
      border: 1px dashed black;
      font-size: 11px;
    }

    .notes-title {
      font-weight: bold;
      font-size: 11px;
    }

    .footer {
      margin-top: 5px;
      padding-top: 4px;
      border-top: 1px dashed black;
      text-align: center;
      font-size: 11px;
      font-weight: 500;
    }

    .contact-info {
      margin: 3px 0;
      line-height: 1.4;
      font-weight: 600;
    }

    .thank-you {
      font-size: 14px;
      font-weight: bold;
      margin: 4px 0;
    }

    .timestamp {
      margin-top: 4px;
      font-size: 10px;
      border-top: 1px dashed black;
      padding-top: 3px;
    }

    .print-button {
      position: fixed;
      top: 10px;
      right: 10px;
      background: black;
      color: white;
      border: none;
      padding: 8px 16px;
      font-weight: bold;
      font-size: 12px;
      cursor: pointer;
    }
  </style>
</head>
<body>
  <button class="print-button no-print" onclick="window.print()">CHOP ETISH</button>

  <div class="receipt">
    <div class="header">
      <div class="brand-name">Mayami Cafe</div>
    </div>

    <div class="info-section">
      <div class="info-line">
        <span>Sana:</span>
        <span>${currentDate}</span>
      </div>
      ${getTableNumber(order) ? `
      <div class="info-line">
        <span>Stol:</span>
        <span>${getTableNumber(order)}</span>
      </div>
      ` : ''}
      ${order.waiter?.name ? `
      <div class="info-line">
        <span>Ofitsiant:</span>
        <span>${order.waiter.name}</span>
      </div>
      ` : ''}
    </div>

    <table class="items-table">
      <thead>
        <tr>
          <th>Nomi</th>
          <th>Narx</th>
          <th>x</th>
          <th>Jami</th>
        </tr>
      </thead>
      <tbody>
        ${(order.items || []).map(item => `
        <tr>
          <td>${item.productName}${item.variantName ? `<br><span class="item-variant">${item.variantName}</span>` : ''}</td>
          <td class="unit-price">${Math.round(item.unitPrice || item.price || 0)}</td>
          <td class="qty">${item.quantity}</td>
          <td class="price">${Math.round(item.totalPrice || item.total || 0)}</td>
        </tr>
        `).join('')}
      </tbody>
    </table>

    <div class="totals">
      ${Number(order.deliveryFee) > 0 ? `
      <div class="total-line">
        <span>Yetkazish:</span>
        <span>${Math.round(order.deliveryFee)}</span>
      </div>
      ` : ''}
      ${Number(order.serviceFee) > 0 ? `
      <div class="total-line">
        <span>Xizmat haqi (${order.serviceFeePercent || 0}%):</span>
        <span>${Math.round(order.serviceFee)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 ? `
      <div class="total-line">
        <span>Chegirma:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      <div class="total-line grand-total">
        <span>JAMI:</span>
        <span>${Math.round(order.total || 0)}</span>
      </div>
    </div>

    ${order.customerNotes ? `
    <div class="notes-section">
      <div class="notes-title">Eslatma:</div>
      <div>${order.customerNotes}</div>
    </div>
    ` : ''}

    <div class="footer">
      <div class="contact-info">
        <div>+998 88 153 8888</div>
        <div>www.mayamicafe.uz</div>
      </div>
      <div class="thank-you">*** RAHMAT! ***</div>
      <div class="timestamp">${currentDate}</div>
    </div>
  </div>
</body>
</html>
    `;
};

export default PrintReceipt;
