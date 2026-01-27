import { useState, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { cn } from '../../lib/utils';
import {
  ArrowLeft,
  Users,
  ShoppingBag,
  DollarSign,
  Check,
  AlertCircle,
  CreditCard,
  Plus,
  Minus,
  X
} from 'lucide-react';
import TouchButton from '../components/TouchButton';
import usePOSStore from '../store/posStore';
import { posAPI } from '../../services/api';

/**
 * SplitBillScreen - Split an order's bill
 * Modes: ITEMS (assign items to people), EVEN (split evenly), AMOUNT (custom amounts)
 */
const SplitBillScreen = () => {
  const { t } = useTranslation();
  const { activeOrder, setCurrentScreen, ui } = usePOSStore();

  const [mode, setMode] = useState(null); // 'ITEMS' | 'EVEN' | 'AMOUNT'
  const [numPeople, setNumPeople] = useState(2);
  const [itemAssignments, setItemAssignments] = useState({});
  const [customAmounts, setCustomAmounts] = useState([]);
  const [_selectedPerson, _setSelectedPerson] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [splitResult, setSplitResult] = useState(null);
  const [_editingAmountIndex, _setEditingAmountIndex] = useState(null);

  const order = activeOrder;

  // Calculate even split amounts
  const evenSplitAmount = useMemo(() => {
    if (!order?.total) return 0;
    return order.total / numPeople;
  }, [order?.total, numPeople]);

  // Calculate item-based splits
  const itemSplits = useMemo(() => {
    if (mode !== 'ITEMS' || !order?.items) return {};

    const splits = {};
    for (let i = 1; i <= numPeople; i++) {
      splits[i] = {
        items: [],
        total: 0,
      };
    }

    order.items.forEach(item => {
      const person = itemAssignments[item.id] || 1;
      if (splits[person]) {
        splits[person].items.push(item);
        splits[person].total += item.subtotal || item.itemTotal || (item.price * item.quantity);
      }
    });

    return splits;
  }, [order?.items, itemAssignments, numPeople, mode]);

  // Calculate remaining for custom amounts
  const remainingAmount = useMemo(() => {
    if (!order?.total) return 0;
    const assigned = customAmounts.reduce((sum, a) => sum + (a.amount || 0), 0);
    return order.total - assigned;
  }, [order?.total, customAmounts]);

  const handleAssignItem = (itemId, personNum) => {
    setItemAssignments(prev => ({
      ...prev,
      [itemId]: personNum,
    }));
  };

  const handleAddCustomAmount = () => {
    setCustomAmounts(prev => [
      ...prev,
      { personNumber: prev.length + 1, amount: remainingAmount > 0 ? remainingAmount : 0 },
    ]);
  };

  const handleUpdateCustomAmount = (index, amount) => {
    setCustomAmounts(prev => prev.map((a, i) =>
      i === index ? { ...a, amount: parseFloat(amount) || 0 } : a
    ));
  };

  const handleRemoveCustomAmount = (index) => {
    setCustomAmounts(prev => prev.filter((_, i) => i !== index));
  };

  const handleSplitBill = async () => {
    setLoading(true);
    setError(null);

    try {
      let splitData;

      if (mode === 'ITEMS') {
        // Convert to API format
        const itemSplitsData = [];
        for (let i = 1; i <= numPeople; i++) {
          const itemIds = order.items
            .filter(item => (itemAssignments[item.id] || 1) === i)
            .map(item => item.id);
          if (itemIds.length > 0) {
            itemSplitsData.push({ personNumber: i, itemIds });
          }
        }
        splitData = {
          mode: 'ITEMS',
          itemSplits: itemSplitsData,
        };
      } else if (mode === 'EVEN') {
        splitData = {
          mode: 'EVEN',
          numPeople,
        };
      } else if (mode === 'AMOUNT') {
        splitData = {
          mode: 'AMOUNT',
          amountSplits: customAmounts,
        };
      }

      const response = await posAPI.splitBill(order.id, splitData);
      setSplitResult(response.data.data);
    } catch (err) {
      console.error('Failed to split bill:', err);
      setError(err.response?.data?.message || t('pos.split.error', 'Failed to split bill'));
    } finally {
      setLoading(false);
    }
  };

  const handlePaySplit = (split) => {
    // Navigate to payment with the split amount
    usePOSStore.setState({
      currentOrder: {
        ...usePOSStore.getState().currentOrder,
        id: order.id,
        orderNumber: order.orderNumber,
        total: split.amount,
        subtotal: split.amount,
      },
      payment: {
        method: null,
        amountTendered: 0,
        changeDue: 0,
        status: 'PENDING',
      },
      ui: { ...ui, currentScreen: 'payment' },
    });
  };

  if (!order) {
    return (
      <div className="h-screen flex items-center justify-center bg-gray-50">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-xl text-gray-600">{t('pos.split.noOrder', 'No order selected')}</p>
          <TouchButton
            variant="primary"
            className="mt-4"
            onClick={() => setCurrentScreen('active-orders')}
          >
            {t('pos.orders.backToOrders', 'Back to Orders')}
          </TouchButton>
        </div>
      </div>
    );
  }

  // Show split result
  if (splitResult) {
    return (
      <div className="h-screen flex flex-col bg-gray-50">
        <div className="bg-white border-b-2 border-gray-200 px-6 py-4">
          <div className="flex items-center gap-4">
            <TouchButton
              variant="ghost"
              size="medium"
              onClick={() => setSplitResult(null)}
            >
              <ArrowLeft className="w-5 h-5" />
            </TouchButton>
            <h1 className="text-2xl font-bold text-gray-900">
              {t('pos.split.billSplit', 'Bill Split')} - #{order.orderNumber}
            </h1>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-2xl mx-auto">
            <div className="bg-green-100 border-l-4 border-green-500 text-green-700 p-4 mb-6 rounded-lg flex items-center gap-2">
              <Check className="w-5 h-5" />
              {t('pos.split.success', 'Bill split successfully!')}
            </div>

            <div className="space-y-4">
              {splitResult.splits?.map((split, idx) => (
                <div
                  key={idx}
                  className="bg-white rounded-xl border-2 border-gray-200 p-5"
                >
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-2">
                      <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center">
                        <Users className="w-5 h-5 text-blue-600" />
                      </div>
                      <span className="text-lg font-bold text-gray-900">
                        {t('pos.split.person', 'Person')} {split.personNumber}
                      </span>
                    </div>
                    <span className={cn(
                      'px-3 py-1 rounded-full text-sm font-medium',
                      split.paid ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'
                    )}>
                      {split.paid ? t('pos.split.paid', 'Paid') : t('pos.split.unpaid', 'Unpaid')}
                    </span>
                  </div>

                  {split.items?.length > 0 && (
                    <div className="text-sm text-gray-600 mb-3">
                      {split.items.map((item, i) => (
                        <span key={i}>
                          {item.quantity}x {item.productName}
                          {i < split.items.length - 1 && ', '}
                        </span>
                      ))}
                    </div>
                  )}

                  <div className="flex items-center justify-between">
                    <span className="text-2xl font-bold text-gray-900">
                      {split.amount?.toFixed(2)}
                    </span>
                    {!split.paid && (
                      <TouchButton
                        variant="success"
                        size="medium"
                        onClick={() => handlePaySplit(split)}
                      >
                        <CreditCard className="w-5 h-5 mr-2" />
                        {t('pos.split.payNow', 'Pay Now')}
                      </TouchButton>
                    )}
                  </div>
                </div>
              ))}
            </div>

            <div className="mt-6 bg-white rounded-xl border-2 border-gray-200 p-4">
              <div className="flex justify-between text-gray-600">
                <span>{t('pos.split.originalTotal', 'Original Total')}</span>
                <span className="font-medium">{splitResult.originalTotal?.toFixed(2)}</span>
              </div>
            </div>

            <TouchButton
              variant="secondary"
              size="large"
              className="w-full mt-6"
              onClick={() => setCurrentScreen('active-orders')}
            >
              {t('pos.orders.backToOrders', 'Back to Orders')}
            </TouchButton>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b-2 border-gray-200 px-6 py-4 flex-shrink-0">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <TouchButton
              variant="ghost"
              size="medium"
              onClick={() => setCurrentScreen('active-orders')}
            >
              <ArrowLeft className="w-5 h-5" />
            </TouchButton>
            <div>
              <h1 className="text-2xl font-bold text-gray-900">
                {t('pos.split.title', 'Split Bill')} #{order.orderNumber}
              </h1>
              <p className="text-sm text-gray-500">
                {t('pos.cart.total', 'Total')}: {order.total?.toFixed(2)}
              </p>
            </div>
          </div>

          {mode && (
            <TouchButton
              variant="success"
              size="medium"
              onClick={handleSplitBill}
              disabled={loading || (mode === 'AMOUNT' && Math.abs(remainingAmount) > 0.01)}
            >
              {loading ? (
                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin mr-2" />
              ) : (
                <Check className="w-5 h-5 mr-2" />
              )}
              {t('pos.split.splitBill', 'Split Bill')}
            </TouchButton>
          )}
        </div>
      </div>

      {/* Error Message */}
      {error && (
        <div className="bg-red-100 border-l-4 border-red-500 text-red-700 p-4 flex items-center gap-2">
          <AlertCircle className="w-5 h-5" />
          {error}
          <button onClick={() => setError(null)} className="ml-auto">
            <X className="w-5 h-5" />
          </button>
        </div>
      )}

      {/* Mode Selection */}
      {!mode ? (
        <div className="flex-1 flex items-center justify-center p-6">
          <div className="max-w-lg w-full space-y-4">
            <h2 className="text-xl font-bold text-gray-900 text-center mb-6">
              {t('pos.split.chooseMode', 'How would you like to split?')}
            </h2>

            <button
              onClick={() => setMode('EVEN')}
              className="w-full bg-white rounded-xl border-2 border-gray-200 p-6 hover:border-blue-400 hover:shadow-lg transition-all text-left"
            >
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 rounded-full bg-blue-100 flex items-center justify-center">
                  <Users className="w-7 h-7 text-blue-600" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-gray-900">
                    {t('pos.split.splitEvenly', 'Split Evenly')}
                  </h3>
                  <p className="text-gray-600">
                    {t('pos.split.evenlyDesc', 'Divide the bill equally between guests')}
                  </p>
                </div>
              </div>
            </button>

            <button
              onClick={() => setMode('ITEMS')}
              className="w-full bg-white rounded-xl border-2 border-gray-200 p-6 hover:border-blue-400 hover:shadow-lg transition-all text-left"
            >
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 rounded-full bg-green-100 flex items-center justify-center">
                  <ShoppingBag className="w-7 h-7 text-green-600" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-gray-900">
                    {t('pos.split.splitByItems', 'Split by Items')}
                  </h3>
                  <p className="text-gray-600">
                    {t('pos.split.itemsDesc', 'Assign items to different guests')}
                  </p>
                </div>
              </div>
            </button>

            <button
              onClick={() => { setMode('AMOUNT'); setCustomAmounts([{ personNumber: 1, amount: 0 }]); }}
              className="w-full bg-white rounded-xl border-2 border-gray-200 p-6 hover:border-blue-400 hover:shadow-lg transition-all text-left"
            >
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 rounded-full bg-purple-100 flex items-center justify-center">
                  <DollarSign className="w-7 h-7 text-purple-600" />
                </div>
                <div>
                  <h3 className="text-xl font-bold text-gray-900">
                    {t('pos.split.customAmounts', 'Custom Amounts')}
                  </h3>
                  <p className="text-gray-600">
                    {t('pos.split.amountsDesc', 'Enter specific amounts for each person')}
                  </p>
                </div>
              </div>
            </button>
          </div>
        </div>
      ) : mode === 'EVEN' ? (
        /* Even Split Mode */
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-lg mx-auto">
            <div className="bg-white rounded-xl border-2 border-gray-200 p-6 mb-6">
              <h3 className="text-lg font-bold text-gray-900 mb-4">
                {t('pos.split.numberOfPeople', 'Number of People')}
              </h3>
              <div className="flex items-center justify-center gap-4">
                <TouchButton
                  variant="secondary"
                  size="large"
                  onClick={() => setNumPeople(Math.max(2, numPeople - 1))}
                  disabled={numPeople <= 2}
                >
                  <Minus className="w-6 h-6" />
                </TouchButton>
                <span className="text-4xl font-bold text-gray-900 w-16 text-center">
                  {numPeople}
                </span>
                <TouchButton
                  variant="secondary"
                  size="large"
                  onClick={() => setNumPeople(numPeople + 1)}
                >
                  <Plus className="w-6 h-6" />
                </TouchButton>
              </div>
            </div>

            <div className="space-y-3">
              {Array.from({ length: numPeople }, (_, i) => (
                <div
                  key={i}
                  className="bg-white rounded-xl border-2 border-gray-200 p-4 flex items-center justify-between"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-full bg-blue-100 flex items-center justify-center">
                      <span className="font-bold text-blue-600">{i + 1}</span>
                    </div>
                    <span className="font-medium text-gray-900">
                      {t('pos.split.person', 'Person')} {i + 1}
                    </span>
                  </div>
                  <span className="text-2xl font-bold text-gray-900">
                    {evenSplitAmount.toFixed(2)}
                  </span>
                </div>
              ))}
            </div>

            <TouchButton
              variant="ghost"
              size="medium"
              className="mt-6"
              onClick={() => setMode(null)}
            >
              {t('pos.split.changeMode', 'Change Split Method')}
            </TouchButton>
          </div>
        </div>
      ) : mode === 'ITEMS' ? (
        /* Items Split Mode */
        <div className="flex-1 flex overflow-hidden">
          {/* Items List */}
          <div className="flex-1 overflow-y-auto p-6">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-bold text-gray-900">
                {t('pos.split.assignItems', 'Assign Items')}
              </h3>
              <div className="flex items-center gap-2">
                <span className="text-gray-600">{t('pos.split.people', 'People')}:</span>
                <TouchButton
                  variant="ghost"
                  size="small"
                  onClick={() => setNumPeople(Math.max(2, numPeople - 1))}
                >
                  <Minus className="w-4 h-4" />
                </TouchButton>
                <span className="font-bold w-8 text-center">{numPeople}</span>
                <TouchButton
                  variant="ghost"
                  size="small"
                  onClick={() => setNumPeople(numPeople + 1)}
                >
                  <Plus className="w-4 h-4" />
                </TouchButton>
              </div>
            </div>

            <div className="space-y-3">
              {order.items?.map(item => (
                <div
                  key={item.id}
                  className="bg-white rounded-xl border-2 border-gray-200 p-4"
                >
                  <div className="flex items-center justify-between mb-2">
                    <div>
                      <h4 className="font-semibold text-gray-900">
                        {item.quantity}x {item.productName || item.name}
                      </h4>
                      <p className="text-gray-600">
                        {(item.subtotal || item.itemTotal || 0).toFixed(2)}
                      </p>
                    </div>
                  </div>
                  <div className="flex gap-2 flex-wrap">
                    {Array.from({ length: numPeople }, (_, i) => (
                      <button
                        key={i}
                        onClick={() => handleAssignItem(item.id, i + 1)}
                        className={cn(
                          'px-4 py-2 rounded-lg font-medium transition-all',
                          (itemAssignments[item.id] || 1) === i + 1
                            ? 'bg-blue-600 text-white'
                            : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                        )}
                      >
                        {t('pos.split.person', 'Person')} {i + 1}
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>

            <TouchButton
              variant="ghost"
              size="medium"
              className="mt-6"
              onClick={() => setMode(null)}
            >
              {t('pos.split.changeMode', 'Change Split Method')}
            </TouchButton>
          </div>

          {/* Split Summary */}
          <div className="w-80 bg-white border-l-2 border-gray-200 p-4 overflow-y-auto">
            <h3 className="text-lg font-bold text-gray-900 mb-4">
              {t('pos.split.summary', 'Split Summary')}
            </h3>
            <div className="space-y-3">
              {Array.from({ length: numPeople }, (_, i) => {
                const split = itemSplits[i + 1] || { items: [], total: 0 };
                return (
                  <div
                    key={i}
                    className="bg-gray-50 rounded-lg p-3"
                  >
                    <div className="flex items-center justify-between mb-2">
                      <span className="font-medium text-gray-900">
                        {t('pos.split.person', 'Person')} {i + 1}
                      </span>
                      <span className="font-bold text-gray-900">
                        {split.total.toFixed(2)}
                      </span>
                    </div>
                    <div className="text-xs text-gray-500">
                      {split.items.length} {t('pos.split.items', 'items')}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      ) : (
        /* Custom Amounts Mode */
        <div className="flex-1 overflow-y-auto p-6">
          <div className="max-w-lg mx-auto">
            <div className="space-y-3">
              {customAmounts.map((entry, idx) => (
                <div
                  key={idx}
                  className="bg-white rounded-xl border-2 border-gray-200 p-4"
                >
                  <div className="flex items-center justify-between mb-3">
                    <div className="flex items-center gap-3">
                      <div className="w-10 h-10 rounded-full bg-purple-100 flex items-center justify-center">
                        <span className="font-bold text-purple-600">{idx + 1}</span>
                      </div>
                      <span className="font-medium text-gray-900">
                        {t('pos.split.person', 'Person')} {idx + 1}
                      </span>
                    </div>
                    {customAmounts.length > 1 && (
                      <button onClick={() => handleRemoveCustomAmount(idx)}>
                        <X className="w-5 h-5 text-gray-400 hover:text-red-500" />
                      </button>
                    )}
                  </div>
                  <div className="relative">
                    <span className="absolute left-4 top-1/2 -translate-y-1/2 text-gray-500 text-xl">$</span>
                    <input
                      type="number"
                      value={entry.amount}
                      onChange={(e) => handleUpdateCustomAmount(idx, e.target.value)}
                      className="w-full pl-8 pr-4 py-3 text-2xl font-bold text-right border-2 border-gray-200 rounded-lg focus:border-blue-400 focus:outline-none"
                      step="0.01"
                      min="0"
                    />
                  </div>
                </div>
              ))}
            </div>

            <TouchButton
              variant="secondary"
              size="large"
              className="w-full mt-4"
              onClick={handleAddCustomAmount}
            >
              <Plus className="w-5 h-5 mr-2" />
              {t('pos.split.addPerson', 'Add Person')}
            </TouchButton>

            <div className={cn(
              'mt-6 p-4 rounded-xl border-2',
              Math.abs(remainingAmount) < 0.01
                ? 'bg-green-50 border-green-200'
                : 'bg-yellow-50 border-yellow-200'
            )}>
              <div className="flex justify-between items-center">
                <span className="font-medium text-gray-700">
                  {remainingAmount >= 0
                    ? t('pos.split.remaining', 'Remaining')
                    : t('pos.split.over', 'Over')}
                </span>
                <span className={cn(
                  'text-2xl font-bold',
                  Math.abs(remainingAmount) < 0.01 ? 'text-green-600' : 'text-yellow-600'
                )}>
                  {Math.abs(remainingAmount).toFixed(2)}
                </span>
              </div>
              {Math.abs(remainingAmount) >= 0.01 && (
                <p className="text-sm text-yellow-700 mt-2">
                  {remainingAmount > 0
                    ? t('pos.split.assignRemaining', 'Please assign the remaining amount')
                    : t('pos.split.reduceAmounts', 'Total exceeds the bill')}
                </p>
              )}
            </div>

            <TouchButton
              variant="ghost"
              size="medium"
              className="mt-6"
              onClick={() => setMode(null)}
            >
              {t('pos.split.changeMode', 'Change Split Method')}
            </TouchButton>
          </div>
        </div>
      )}
    </div>
  );
};

export default SplitBillScreen;
