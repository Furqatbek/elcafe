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
      display: none;
    }

    .items-table {
      width: 100%;
      border-collapse: collapse;
      margin: 10px 0;
    }

    .items-table th {
      border: 2px solid black;
      padding: 8px 4px;
      font-size: 14px;
      font-weight: bold;
      text-align: center;
      background: #f0f0f0;
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

    .items-table td.unit-price {
      text-align: right;
      font-size: 14px;
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
  <button class="print-button no-print" onclick="window.print()">CHEK CHOP ETISH</button>

  <div class="receipt">
    <!-- Header -->
    <div class="header">
      <div class="brand-name">LaCasa</div>
    </div>

    <!-- Order Number -->
    <div class="order-number">
      BUYURTMA #${order.orderNumber}
    </div>

    <!-- Customer Info -->
    ${(order.deliveryInfo || order.deliveryAddress || order.customerName) ? `
    <div class="customer-info">
      <div class="customer-info-title">MIJOZ MA'LUMOTLARI</div>
      <div class="customer-info-line">
        <strong>Ism:</strong> ${order.deliveryInfo?.contactName || order.customerName || order.customer?.firstName + ' ' + order.customer?.lastName || 'N/A'}
      </div>
      <div class="customer-info-line">
        <strong>Telefon:</strong> ${order.deliveryInfo?.contactPhone || order.customerPhone || order.customer?.phone || 'N/A'}
      </div>
      ${(order.deliveryInfo?.address || order.deliveryAddress?.street) ? `
      <div class="customer-info-line">
        <strong>Manzil:</strong> ${order.deliveryInfo?.address || order.deliveryAddress?.street || ''}, ${order.deliveryInfo?.city || order.deliveryAddress?.city || ''}
      </div>
      ` : ''}
    </div>
    ` : ''}

    <!-- Items Section -->
    <div class="items-section">
      <table class="items-table">
        <thead>
          <tr>
            <th>Nomi</th>
            <th>Narxi</th>
            <th>Soni</th>
            <th>Jami</th>
          </tr>
        </thead>
        <tbody>
          ${(order.items || []).map(item => `
          <tr>
            <td class="name">
              ${item.productName}
              ${item.variantName ? ` (${item.variantName})` : ''}
            </td>
            <td class="unit-price">${(item.unitPrice || item.price || 0).toFixed(2)}</td>
            <td class="qty">${item.quantity}</td>
            <td class="price">${(item.totalPrice || item.total || 0).toFixed(2)}</td>
          </tr>
          `).join('')}
        </tbody>
      </table>
    </div>

    <!-- Totals -->
    <div class="totals">
      ${order.deliveryFee && order.deliveryFee > 0 ? `
      <div class="total-line">
        <span class="label">Yetkazib berish:</span>
        <span class="amount">${(order.deliveryFee || 0).toFixed(2)}</span>
      </div>
      ` : ''}

      ${order.discount && order.discount > 0 ? `
      <div class="total-line">
        <span class="label">Chegirma:</span>
        <span class="amount">-${(order.discount || 0).toFixed(2)}</span>
      </div>
      ` : ''}

      <div class="total-line grand-total">
        <span class="label">JAMI:</span>
        <span class="amount">${(order.total || 0).toFixed(2)}</span>
      </div>
    </div>

    <!-- Payment Info -->
    ${(order.payment || order.paymentMethod || order.paymentInfo) ? `
    <div class="payment-info">
      TO'LOV: ${order.payment?.method || order.paymentMethod || order.paymentInfo?.paymentMethod || 'N/A'}
    </div>
    ` : ''}

    <!-- Notes -->
    ${(order.customerNotes || order.orderNotes) ? `
    <div class="notes-section">
      <div class="notes-title">MAXSUS ESLATMALAR:</div>
      <div class="notes-content">${order.customerNotes || order.orderNotes}</div>
    </div>
    ` : ''}

    <!-- Footer -->
    <div class="footer">
      <div class="contact-info">
        <div>Telefon: +99 (897) 421 8989</div>
        <div>www.lacasa.uz</div>
      </div>

      <div class="thank-you">*** RAHMAT! ***</div>
      <div class="visit-again">Yana tashrif buyuring!</div>

      <div class="timestamp">${currentDate}</div>
    </div>
  </div>
</body>
</html>
    `;
};

export default PrintReceipt;