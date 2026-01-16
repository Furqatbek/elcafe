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

    .item-free {
      font-size: 10px;
      font-weight: bold;
      color: #006400;
    }

    .item-promo {
      font-size: 9px;
      font-style: italic;
    }

    .promotions-section {
      margin: 4px 0;
      padding: 4px;
      border: 1px dashed black;
      background: #f9f9f9;
    }

    .promo-title {
      font-weight: bold;
      font-size: 11px;
      text-align: center;
      margin-bottom: 3px;
      text-transform: uppercase;
    }

    .promo-item {
      font-size: 10px;
      margin: 2px 0;
      padding-left: 4px;
    }

    .promo-name {
      font-weight: bold;
    }

    .discount-line {
      display: flex;
      justify-content: space-between;
      margin: 2px 0;
      font-size: 11px;
      padding: 2px 0;
    }

    .discount-line.happy-hour {
      background: #fffde7;
      padding: 2px 4px;
    }

    .discount-line.coupon {
      background: #e8f5e9;
      padding: 2px 4px;
    }

    .discount-line.free-item {
      background: #e3f2fd;
      padding: 2px 4px;
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
        ${(order.items || []).map(item => {
          const isFree = item.isFreeItem || (item.unitPrice === 0 && item.price === 0) || item.totalPrice === 0;
          const promoSource = item.couponCode ? `Kupon: ${item.couponCode}` : (item.promotionName || item.notes?.includes('Free item') ? 'Aksiya' : '');
          return `
        <tr>
          <td>
            ${item.productName}
            ${item.variantName ? `<br><span class="item-variant">${item.variantName}</span>` : ''}
            ${isFree ? `<br><span class="item-free">*** BEPUL ***</span>` : ''}
            ${isFree && promoSource ? `<br><span class="item-promo">${promoSource}</span>` : ''}
          </td>
          <td class="unit-price">${isFree ? '0' : Math.round(item.unitPrice || item.price || 0)}</td>
          <td class="qty">${item.quantity}</td>
          <td class="price">${isFree ? 'BEPUL' : Math.round(item.totalPrice || item.total || 0)}</td>
        </tr>
        `;
        }).join('')}
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
      ${Number(order.discount) > 0 && order.discountType === 'HAPPY_HOUR' ? `
      <div class="discount-line happy-hour">
        <span>🎉 HAPPY HOUR${order.promotionName ? `<br><small>${order.promotionName}</small>` : ''}:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 && order.discountType === 'COUPON' ? `
      <div class="discount-line coupon">
        <span>🎫 KUPON${order.couponCode ? `<br><small>${order.couponCode}</small>` : ''}${order.promotionName ? ` - ${order.promotionName}` : ''}:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 && order.discountType === 'PROMOTION' ? `
      <div class="discount-line coupon">
        <span>🏷️ AKSIYA${order.promotionName ? `<br><small>${order.promotionName}</small>` : ''}:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 && order.discountType === 'FREE_ITEM' ? `
      <div class="discount-line free-item">
        <span>🎁 BEPUL MAHSULOT${order.promotionName ? `<br><small>${order.promotionName}</small>` : ''}:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 && order.discountType === 'MANUAL' ? `
      <div class="discount-line">
        <span>Chegirma${order.discountReason ? `<br><small>${order.discountReason}</small>` : ''}:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 && !order.discountType ? `
      <div class="discount-line">
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

    ${(() => {
      const freeItems = (order.items || []).filter(item =>
        item.isFreeItem || (item.unitPrice === 0 && item.price === 0) || item.totalPrice === 0
      );
      const hasPromotion = order.discountType || freeItems.length > 0;

      if (!hasPromotion) return '';

      return `
    <div class="promotions-section">
      <div class="promo-title">🎁 Qo'llanilgan aksiyalar</div>
      ${order.discountType === 'HAPPY_HOUR' ? `
      <div class="promo-item">
        ✓ <span class="promo-name">Happy Hour</span>${order.promotionName ? `: ${order.promotionName}` : ''}
        <br>&nbsp;&nbsp;Chegirma: ${Math.round(order.discount)} so'm
      </div>
      ` : ''}
      ${order.discountType === 'COUPON' ? `
      <div class="promo-item">
        ✓ <span class="promo-name">Kupon</span>: ${order.couponCode || 'N/A'}
        ${order.promotionName ? `<br>&nbsp;&nbsp;${order.promotionName}` : ''}
        <br>&nbsp;&nbsp;Chegirma: ${Math.round(order.discount)} so'm
      </div>
      ` : ''}
      ${order.discountType === 'PROMOTION' ? `
      <div class="promo-item">
        ✓ <span class="promo-name">Aksiya</span>${order.promotionName ? `: ${order.promotionName}` : ''}
        <br>&nbsp;&nbsp;Chegirma: ${Math.round(order.discount)} so'm
      </div>
      ` : ''}
      ${order.discountType === 'FREE_ITEM' ? `
      <div class="promo-item">
        ✓ <span class="promo-name">Bepul mahsulot</span>${order.promotionName ? `: ${order.promotionName}` : ''}
      </div>
      ` : ''}
      ${freeItems.map(item => `
      <div class="promo-item">
        🎁 <span class="promo-name">${item.productName}</span> - BEPUL
        ${item.couponCode ? `<br>&nbsp;&nbsp;Kupon: ${item.couponCode}` : ''}
      </div>
      `).join('')}
    </div>
      `;
    })()}

    <div class="footer">
      <div class="contact-info">
        <div>+998 88 153 88 88</div>
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
