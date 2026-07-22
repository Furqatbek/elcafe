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

/**
 * Get customer name from order data
 * Handles different data structures from various sources
 */
const getCustomerName = (order) => {
  // Direct customerName field (POSOrderResponse)
  if (order.customerName) {
    return order.customerName;
  }
  // Customer object with firstName/lastName (Consumer OrderResponse)
  if (order.customer?.firstName || order.customer?.lastName) {
    return `${order.customer.firstName || ''} ${order.customer.lastName || ''}`.trim();
  }
  // Customer object with name
  if (order.customer?.name) {
    return order.customer.name;
  }
  return null;
};

/**
 * Get customer phone from order data
 */
const getCustomerPhone = (order) => {
  if (order.customerPhone) {
    return order.customerPhone;
  }
  if (order.customer?.phone) {
    return order.customer.phone;
  }
  return null;
};

/**
 * Get delivery address from order data
 * Handles different data structures
 */
const getDeliveryAddress = (order) => {
  // POSOrderResponse structure
  if (order.deliveryAddress) {
    const addr = order.deliveryAddress;
    const parts = [addr.street, addr.city, addr.state, addr.zipCode].filter(Boolean);
    return {
      address: parts.join(', '),
      instructions: addr.deliveryInstructions
    };
  }
  // Consumer OrderResponse structure
  if (order.deliveryInfo) {
    const addr = order.deliveryInfo;
    const parts = [addr.address || addr.deliveryAddress, addr.city, addr.state, addr.zipCode].filter(Boolean);
    return {
      address: parts.join(', '),
      instructions: addr.deliveryInstructions,
      courierName: addr.courierName,
      courierPhone: addr.courierPhone
    };
  }
  return null;
};

/**
 * Get waiter name from order data
 * Handles different data structures:
 * - order.waiter.name (legacy format)
 * - order.waiter.firstName/lastName (current backend format)
 */
const getWaiterName = (order) => {
  if (!order.waiter) return null;

  // Try firstName/lastName (current backend format)
  if (order.waiter.firstName || order.waiter.lastName) {
    return `${order.waiter.firstName || ''} ${order.waiter.lastName || ''}`.trim();
  }
  // Fallback to name field
  if (order.waiter.name) {
    return order.waiter.name;
  }
  return null;
};

/**
 * Get order type label in Uzbek
 */
const getOrderTypeLabel = (orderType) => {
  const labels = {
    'DELIVERY': 'YETKAZIB BERISH',
    'TAKEAWAY': 'OLIB KETISH',
    'DINE_IN': 'JOYIDA'
  };
  return labels[orderType] || orderType;
};

const PrintReceipt = (order, onPrint) => {
  const printWindow = window.open('', '_blank');

  // Read saved template from localStorage (loaded on admin startup)
  let tmpl = {};
  try {
    const raw = localStorage.getItem('receiptTemplate');
    if (raw) tmpl = JSON.parse(raw);
  } catch (_) {}

  const receiptHTML = generateReceiptHTML(order, tmpl);

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

export const generateReceiptHTML = (order, tmpl = {}) => {
    const currentDate = format(new Date(), 'dd/MM/yyyy HH:mm');

    // Template values with fallback defaults
    const brandName    = tmpl.restaurantName || 'Jangirov\'s';
    const tagline      = tmpl.tagline || '';
    const phone        = tmpl.phone || '+998770049909';
    const website      = tmpl.website || 'www.jangirovs.uz';
    const footerMsg    = tmpl.footerMessage || '*** RAHMAT! ***';
    const currency     = tmpl.currency || 'UZS';
    const showQr       = tmpl.showQrCode !== false;
    const qrUrl        = tmpl.qrUrl || 'https://jangirovs.uz/order/menu/1/TAKEAWAY';
    const qrTitle      = tmpl.qrTitle || 'ONLINE BUYURTMA';
    const qrSubtitle   = tmpl.qrSubtitle || 'Skanerlang va buyurtma bering';
    const paperWidth   = tmpl.paperWidthMm || 58;

    // Helper to truncate text for thermal printer width
    const truncate = (text, maxLen) => {
      if (!text) return '';
      return text.length > maxLen ? text.substring(0, maxLen - 2) + '..' : text;
    };

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
      size: ${paperWidth}mm auto;
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
      font-size: 15px;
      font-weight: 500;
      line-height: 1.1;
      width: ${paperWidth}mm;
      margin: 0 auto;
      padding: 0;
      background: white;
      color: black;
    }

    .receipt {
      width: 100%;
      padding: 0.5mm;
    }

    .header {
      text-align: center;
      margin-bottom: 1px;
      padding-bottom: 1px;
      border-bottom: 1px dashed black;
    }

    .brand-name {
      font-size: 20px;
      font-weight: bold;
      letter-spacing: 1px;
    }

    .separator {
      text-align: center;
      font-size: 14px;
      letter-spacing: -1px;
    }

    .info-section {
      margin: 0;
      padding: 1px 0;
      border-bottom: 1px dashed black;
      font-size: 14px;
    }

    .info-row {
      display: flex;
      justify-content: space-between;
      margin: 0;
    }

    .items-section {
      margin: 1px 0;
      font-size: 14px;
    }

    .item-row {
      margin: 1px 0;
      padding: 0;
      border-bottom: 1px dotted #ccc;
    }

    .item-name {
      font-weight: bold;
      word-wrap: break-word;
    }

    .item-details {
      display: flex;
      justify-content: space-between;
      font-size: 14px;
    }

    .item-free-tag {
      font-weight: bold;
      font-size: 13px;
    }

    .item-promo-info {
      font-size: 12px;
      font-style: italic;
    }

    .totals-section {
      margin: 1px 0;
      padding-top: 1px;
      border-top: 1px dashed black;
      font-size: 14px;
    }

    .total-row {
      display: flex;
      justify-content: space-between;
      margin: 0;
    }

    .grand-total {
      border-top: 1px solid black;
      padding-top: 1px;
      margin-top: 1px;
      font-size: 18px;
      font-weight: bold;
    }

    .promo-section {
      margin: 1px 0;
      padding: 1px;
      border: 1px dashed black;
      font-size: 13px;
    }

    .promo-header {
      font-weight: bold;
      text-align: center;
      border-bottom: 1px dotted black;
      padding-bottom: 0;
      margin-bottom: 1px;
      font-size: 14px;
    }

    .promo-row {
      margin: 1px 0;
      padding: 0;
    }

    .promo-label {
      font-weight: bold;
    }

    .promo-value {
      font-size: 13px;
    }

    .promo-discount {
      text-align: right;
      font-weight: bold;
    }

    .notes-section {
      margin: 1px 0;
      padding: 1px;
      border: 1px dashed black;
      font-size: 13px;
    }

    .qr-section {
      margin: 1px 0;
      padding: 1px;
      text-align: center;
      border: 1px dashed black;
    }

    .qr-title {
      font-weight: bold;
      font-size: 14px;
      margin-bottom: 1px;
    }

    .qr-code {
      width: 80px;
      height: 80px;
      margin: 1px auto;
    }

    .qr-subtitle {
      font-size: 12px;
      margin-top: 1px;
    }

    .footer {
      margin-top: 1px;
      padding-top: 1px;
      border-top: 1px dashed black;
      text-align: center;
      font-size: 14px;
    }

    .thank-you {
      font-size: 16px;
      font-weight: bold;
      margin: 1px 0;
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
      <div class="brand-name">${brandName}</div>
      ${tagline ? `<div style="font-size:13px;margin-top:1px;">${tagline}</div>` : ''}
    </div>

    <div class="info-section">
      <div class="info-row">
        <span>Sana:</span>
        <span>${currentDate}</span>
      </div>
      ${order.orderType && order.orderType !== 'DINE_IN' ? `
      <div class="info-row">
        <span>Turi:</span>
        <span style="font-weight:bold;">${getOrderTypeLabel(order.orderType)}</span>
      </div>
      ` : ''}
      ${getTableNumber(order) ? `
      <div class="info-row">
        <span>Stol:</span>
        <span>${getTableNumber(order)}</span>
      </div>
      ` : ''}
      ${getWaiterName(order) ? `
      <div class="info-row">
        <span>Ofitsiant:</span>
        <span>${truncate(getWaiterName(order), 15)}</span>
      </div>
      ` : ''}
    </div>

    ${(() => {
      const customerName = getCustomerName(order);
      const customerPhone = getCustomerPhone(order);
      const deliveryAddr = getDeliveryAddress(order);
      const hasCustomerInfo = customerName || customerPhone || deliveryAddr;

      if (!hasCustomerInfo) return '';

      return `
    <div class="info-section">
      ${customerName ? `
      <div class="info-row">
        <span>Mijoz:</span>
        <span>${truncate(customerName, 18)}</span>
      </div>
      ` : ''}
      ${customerPhone ? `
      <div class="info-row">
        <span>Tel:</span>
        <span>${customerPhone}</span>
      </div>
      ` : ''}
      ${deliveryAddr?.address ? `
      <div style="margin-top:2px;">
        <div style="font-weight:bold;">Manzil:</div>
        <div style="font-size:13px;">${truncate(deliveryAddr.address, 40)}</div>
      </div>
      ` : ''}
      ${deliveryAddr?.instructions ? `
      <div style="margin-top:2px;">
        <div style="font-weight:bold;font-size:12px;">Izoh:</div>
        <div style="font-size:12px;">${truncate(deliveryAddr.instructions, 35)}</div>
      </div>
      ` : ''}
      ${deliveryAddr?.courierName ? `
      <div class="info-row" style="margin-top:2px;">
        <span>Kuryer:</span>
        <span>${truncate(deliveryAddr.courierName, 15)}</span>
      </div>
      ` : ''}
      ${deliveryAddr?.courierPhone ? `
      <div class="info-row">
        <span>Kuryer tel:</span>
        <span>${deliveryAddr.courierPhone}</span>
      </div>
      ` : ''}
    </div>
      `;
    })()}

    <div class="separator">--------------------------------</div>

    <div class="items-section">
      ${(order.items || []).map(item => {
        const isFree = item.isFreeItem || (item.unitPrice === 0 && item.price === 0) || item.totalPrice === 0;
        const unitPrice = Math.round(item.unitPrice || item.price || 0);
        const totalPrice = Math.round(item.totalPrice || item.total || 0);
        return `
      <div class="item-row">
        <div class="item-name">${truncate(item.productName, 22)}</div>
        ${item.variantName ? `<div class="item-promo-info">${truncate(item.variantName, 20)}</div>` : ''}
        ${isFree ? `
        <div class="item-free-tag">** BEPUL **</div>
        ${item.couponCode ? `<div class="item-promo-info">Kupon: ${truncate(item.couponCode, 15)}</div>` : ''}
        ${item.promotionName ? `<div class="item-promo-info">${truncate(item.promotionName, 20)}</div>` : ''}
        ` : ''}
        <div class="item-details">
          <span>${item.quantity} x ${isFree ? '0' : unitPrice}</span>
          <span>${isFree ? 'BEPUL' : totalPrice}</span>
        </div>
      </div>
      `;
      }).join('')}
    </div>

    <div class="totals-section">
      ${Number(order.deliveryFee) > 0 ? `
      <div class="total-row">
        <span>Yetkazish:</span>
        <span>${Math.round(order.deliveryFee)}</span>
      </div>
      ` : ''}
      ${Number(order.serviceFee) > 0 ? `
      <div class="total-row">
        <span>Xizmat (${order.serviceFeePercent || 0}%):</span>
        <span>${Math.round(order.serviceFee)}</span>
      </div>
      ` : ''}
      ${Number(order.entryFee) > 0 ? `
      <div class="total-row">
        <span>Kirish:</span>
        <span>${Math.round(order.entryFee)}</span>
      </div>
      ` : ''}
      ${Number(order.discount) > 0 ? `
      <div class="total-row">
        <span>Chegirma:</span>
        <span>-${Math.round(order.discount)}</span>
      </div>
      ` : ''}
      <div class="total-row grand-total">
        <span>JAMI:</span>
        <span>${Math.round(order.total || 0)}</span>
      </div>
    </div>

    ${(() => {
      const freeItems = (order.items || []).filter(item =>
        item.isFreeItem || (item.unitPrice === 0 && item.price === 0) || item.totalPrice === 0
      );
      const hasPromotion = (Number(order.discount) > 0 && order.discountType) || freeItems.length > 0;

      if (!hasPromotion) return '';

      return `
    <div class="promo-section">
      <div class="promo-header">AKSIYALAR</div>
      ${order.discountType === 'HAPPY_HOUR' ? `
      <div class="promo-row">
        <div class="promo-label">HAPPY HOUR</div>
        ${order.promotionName ? `<div class="promo-value">${truncate(order.promotionName, 22)}</div>` : ''}
        <div class="promo-discount">-${Math.round(order.discount)} ${currency}</div>
      </div>
      ` : ''}
      ${order.discountType === 'COUPON' ? `
      <div class="promo-row">
        <div class="promo-label">KUPON: ${truncate(order.couponCode, 12) || 'N/A'}</div>
        ${order.promotionName ? `<div class="promo-value">${truncate(order.promotionName, 22)}</div>` : ''}
        <div class="promo-discount">-${Math.round(order.discount)} ${currency}</div>
      </div>
      ` : ''}
      ${order.discountType === 'PROMOTION' ? `
      <div class="promo-row">
        <div class="promo-label">AKSIYA</div>
        ${order.promotionName ? `<div class="promo-value">${truncate(order.promotionName, 22)}</div>` : ''}
        <div class="promo-discount">-${Math.round(order.discount)} ${currency}</div>
      </div>
      ` : ''}
      ${order.discountType === 'FREE_ITEM' ? `
      <div class="promo-row">
        <div class="promo-label">BEPUL MAHSULOT</div>
        ${order.promotionName ? `<div class="promo-value">${truncate(order.promotionName, 22)}</div>` : ''}
      </div>
      ` : ''}
      ${order.discountType === 'MANUAL' ? `
      <div class="promo-row">
        <div class="promo-label">CHEGIRMA</div>
        ${order.discountReason ? `<div class="promo-value">${truncate(order.discountReason, 22)}</div>` : ''}
        <div class="promo-discount">-${Math.round(order.discount)} ${currency}</div>
      </div>
      ` : ''}
      ${freeItems.length > 0 ? `
      <div class="separator">- - - - - - - - - - - -</div>
      <div class="promo-label">BEPUL MAHSULOTLAR:</div>
      ${freeItems.map(item => `
      <div class="promo-row">
        <div class="promo-value">* ${truncate(item.productName, 18)}</div>
        ${item.couponCode ? `<div class="promo-value">  Kupon: ${truncate(item.couponCode, 12)}</div>` : ''}
      </div>
      `).join('')}
      ` : ''}
    </div>
      `;
    })()}

    ${order.customerNotes ? `
    <div class="notes-section">
      <div style="font-weight:bold;">Eslatma:</div>
      <div>${truncate(order.customerNotes, 50)}</div>
    </div>
    ` : ''}

    ${showQr ? `
    <div class="qr-section">
      <div class="qr-title">${qrTitle}</div>
      <img class="qr-code" src="https://api.qrserver.com/v1/create-qr-code/?size=100x100&data=${encodeURIComponent(qrUrl)}" alt="QR Code" />
      <div class="qr-subtitle">${qrSubtitle}</div>
    </div>
    ` : ''}

    <div class="footer">
      ${phone ? `<div>${phone}</div>` : ''}
      ${website ? `<div>${website}</div>` : ''}
      <div class="thank-you">${footerMsg}</div>
      <div style="font-size:13px;">${currentDate}</div>
    </div>
  </div>
</body>
</html>
    `;
};

export default PrintReceipt;
