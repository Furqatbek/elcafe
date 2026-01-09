import React, { useState, useEffect } from 'react';
import { financialAPI, inventoryAPI, restaurantAPI, supplierAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { Plus, Edit, Trash2, Check, X, Package, Link as LinkIcon } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';

const PurchaseOrders = () => {
  const { t } = useTranslation();
  const { user } = useAuthStore();
  const location = useLocation();
  const [purchaseOrders, setPurchaseOrders] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [ingredients, setIngredients] = useState([]);
  const [suppliers, setSuppliers] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [prefillProcessed, setPrefillProcessed] = useState(false);
  const [showReceiveModal, setShowReceiveModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedPO, setSelectedPO] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');

  const [formData, setFormData] = useState({
    restaurantId: '',
    supplierId: '',
    supplierName: '',
    supplierContact: '',
    supplierAddress: '',
    orderDate: new Date().toISOString().split('T')[0],
    expectedDeliveryDate: '',
    taxAmount: 0,
    shippingCost: 0,
    notes: '',
    items: []
  });

  const [itemForm, setItemForm] = useState({
    ingredientId: '',
    itemName: '',
    description: '',
    sku: '',
    quantity: 1,
    unit: '',
    unitPrice: 0,
    notes: ''
  });

  const [receiveForm, setReceiveForm] = useState({
    actualDeliveryDate: new Date().toISOString().split('T')[0],
    receivedBy: user?.username || '',
    items: []
  });

  const [paymentForm, setPaymentForm] = useState({
    paymentDate: new Date().toISOString().split('T')[0],
    amount: 0,
    paymentMethod: 'BANK_TRANSFER',
    referenceNumber: '',
    notes: '',
    recordedBy: user?.username || ''
  });

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (user?.restaurantId && !selectedRestaurant) {
      setSelectedRestaurant(user.restaurantId);
      setFormData(prev => ({ ...prev, restaurantId: user.restaurantId }));
    }
  }, [user]);

  useEffect(() => {
    if (selectedRestaurant) {
      loadPurchaseOrders(selectedRestaurant);
      loadIngredients(selectedRestaurant);
      loadSuppliers(selectedRestaurant);
    }
  }, [selectedRestaurant]);

  // Handle prefill data from inventory navigation
  useEffect(() => {
    if (location.state?.prefillData && suppliers.length > 0 && ingredients.length > 0 && !prefillProcessed) {
      const prefill = location.state.prefillData;

      // Set restaurant if provided
      if (prefill.restaurantId && !selectedRestaurant) {
        setSelectedRestaurant(prefill.restaurantId);
        setFormData(prev => ({ ...prev, restaurantId: prefill.restaurantId }));
      }

      // Find and set supplier
      if (prefill.supplierId) {
        const supplier = suppliers.find(s => s.id === parseInt(prefill.supplierId));
        if (supplier) {
          setFormData(prev => ({
            ...prev,
            supplierId: supplier.id.toString(),
            supplierName: supplier.name,
            supplierContact: supplier.contactPerson ?
              `${supplier.contactPerson}${supplier.phone ? ' - ' + supplier.phone : ''}` :
              supplier.phone || '',
            supplierAddress: supplier.address || ''
          }));
        }
      }

      // Pre-fill the item form with ingredient data
      if (prefill.ingredientId) {
        setItemForm({
          ingredientId: prefill.ingredientId.toString(),
          itemName: prefill.ingredientName || '',
          description: '',
          sku: prefill.sku || '',
          quantity: prefill.reorderQuantity || 1,
          unit: prefill.unit || '',
          unitPrice: prefill.unitPrice || 0,
          notes: ''
        });
      }

      // Open the modal
      setShowModal(true);
      setPrefillProcessed(true);

      // Clear the state to prevent re-processing
      window.history.replaceState({}, document.title);
    }
  }, [location.state, suppliers, ingredients, prefillProcessed, selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      // Handle both paginated and non-paginated responses
      const restaurantsData = response.data.data?.content || response.data.data || [];
      setRestaurants(Array.isArray(restaurantsData) ? restaurantsData : []);

      // Set first restaurant as default if none selected
      if (!selectedRestaurant && restaurantsData.length > 0 && !user?.restaurantId) {
        const firstRestaurantId = restaurantsData[0].id;
        setSelectedRestaurant(firstRestaurantId);
        setFormData(prev => ({ ...prev, restaurantId: firstRestaurantId }));
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
      setRestaurants([]);
    }
  };

  const loadPurchaseOrders = async (restaurantId) => {
    try {
      setLoading(true);
      const response = await financialAPI.getPurchaseOrders(restaurantId);
      setPurchaseOrders(response.data.data || []);
    } catch (error) {
      console.error('Failed to load purchase orders:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadIngredients = async (restaurantId) => {
    try {
      const response = await inventoryAPI.getIngredients(restaurantId);
      setIngredients(response.data.data || []);
    } catch (error) {
      console.error('Failed to load ingredients:', error);
    }
  };

  const loadSuppliers = async (restaurantId) => {
    try {
      const response = await supplierAPI.getAll(restaurantId, true); // activeOnly = true
      setSuppliers(response.data.data || []);
    } catch (error) {
      console.error('Failed to load suppliers:', error);
    }
  };

  const handleSupplierChange = (supplierId) => {
    if (!supplierId) {
      setFormData(prev => ({
        ...prev,
        supplierId: '',
        supplierName: '',
        supplierContact: '',
        supplierAddress: ''
      }));
      // Clear ingredient selection when supplier is cleared
      setItemForm(prev => ({
        ...prev,
        ingredientId: '',
        itemName: '',
        sku: '',
        unit: '',
        unitPrice: 0
      }));
      return;
    }

    const supplier = suppliers.find(s => s.id === parseInt(supplierId));
    if (supplier) {
      setFormData(prev => ({
        ...prev,
        supplierId: supplier.id.toString(),
        supplierName: supplier.name,
        supplierContact: supplier.contactPerson ?
          `${supplier.contactPerson}${supplier.phone ? ' - ' + supplier.phone : ''}` :
          supplier.phone || '',
        supplierAddress: supplier.address || ''
      }));
      // Clear ingredient selection when supplier changes
      setItemForm(prev => ({
        ...prev,
        ingredientId: '',
        itemName: '',
        sku: '',
        unit: '',
        unitPrice: 0
      }));
    }
  };

  // Filter ingredients by selected supplier
  const filteredIngredients = formData.supplierId
    ? ingredients.filter(ing => ing.supplierId === parseInt(formData.supplierId))
    : ingredients;

  const handleIngredientChange = (ingredientId) => {
    if (!ingredientId) {
      setItemForm(prev => ({
        ...prev,
        ingredientId: '',
        itemName: '',
        sku: '',
        unit: '',
        unitPrice: 0
      }));
      return;
    }

    const ingredient = ingredients.find(i => i.id === parseInt(ingredientId));
    if (ingredient) {
      setItemForm(prev => ({
        ...prev,
        ingredientId: ingredient.id.toString(),
        itemName: ingredient.name,
        sku: ingredient.sku || '',
        unit: ingredient.unit || '',
        unitPrice: ingredient.costPerUnit || 0,
        quantity: ingredient.reorderQuantity || prev.quantity
      }));
    }
  };

  const handleAddItem = () => {
    if (!itemForm.itemName || !itemForm.quantity || !itemForm.unitPrice) {
      alert(t('finance.common.fillRequiredFields'));
      return;
    }

    const newItem = {
      ...itemForm,
      quantity: parseFloat(itemForm.quantity),
      unitPrice: parseFloat(itemForm.unitPrice),
      totalPrice: parseFloat(itemForm.quantity) * parseFloat(itemForm.unitPrice)
    };

    setFormData(prev => ({
      ...prev,
      items: [...prev.items, newItem]
    }));

    // Reset item form
    setItemForm({
      ingredientId: '',
      itemName: '',
      description: '',
      sku: '',
      quantity: 1,
      unit: '',
      unitPrice: 0,
      notes: ''
    });
  };

  const handleRemoveItem = (index) => {
    setFormData(prev => ({
      ...prev,
      items: prev.items.filter((_, i) => i !== index)
    }));
  };

  const handleSave = async () => {
    try {
      if (!formData.supplierName || formData.items.length === 0) {
        alert(t('finance.purchaseOrders.messages.fillSupplierAndItems'));
        return;
      }

      setLoading(true);
      const submitData = {
        ...formData,
        supplierId: formData.supplierId ? parseInt(formData.supplierId) : null
      };
      await financialAPI.createPurchaseOrder(submitData);
      alert(t('finance.purchaseOrders.messages.createSuccess'));
      setShowModal(false);
      resetForm();
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to create purchase order:', error);
      alert(t('finance.purchaseOrders.messages.createError') + ': ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      await financialAPI.approvePurchaseOrder(id, user?.username || 'ADMIN');
      alert(t('finance.purchaseOrders.messages.approveSuccess'));
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to approve purchase order:', error);
      alert(t('finance.purchaseOrders.messages.approveError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleReceive = async () => {
    try {
      setLoading(true);
      const receiveData = {
        ...receiveForm,
        items: receiveForm.items.filter(item => item.receivedQuantity > 0)
      };

      await financialAPI.receivePurchaseOrder(selectedPO.id, receiveData);
      alert(t('finance.purchaseOrders.messages.receiveSuccess'));
      setShowReceiveModal(false);
      setSelectedPO(null);
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to receive purchase order:', error);
      alert(t('finance.purchaseOrders.messages.receiveError'));
    } finally {
      setLoading(false);
    }
  };

  const handleRecordPayment = async () => {
    try {
      setLoading(true);
      await financialAPI.recordPOPayment(selectedPO.id, paymentForm);
      alert(t('finance.purchaseOrders.messages.paymentSuccess'));
      setShowPaymentModal(false);
      setSelectedPO(null);
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to record payment:', error);
      alert(t('finance.purchaseOrders.messages.paymentError'));
    } finally {
      setLoading(false);
    }
  };

  const openReceiveModal = (po) => {
    setSelectedPO(po);
    setReceiveForm({
      actualDeliveryDate: new Date().toISOString().split('T')[0],
      receivedBy: user?.username || '',
      items: (Array.isArray(po.items) ? po.items : []).map(item => ({
        itemId: item.id,
        receivedQuantity: item.quantity - (item.receivedQuantity || 0),
        notes: ''
      }))
    });
    setShowReceiveModal(true);
  };

  const openPaymentModal = (po) => {
    setSelectedPO(po);
    const remainingAmount = po.totalAmount - (po.paidAmount || 0);
    setPaymentForm({
      paymentDate: new Date().toISOString().split('T')[0],
      amount: remainingAmount,
      paymentMethod: 'BANK_TRANSFER',
      referenceNumber: '',
      notes: '',
      recordedBy: user?.username || ''
    });
    setShowPaymentModal(true);
  };

  const resetForm = () => {
    setFormData({
      restaurantId: selectedRestaurant,
      supplierId: '',
      supplierName: '',
      supplierContact: '',
      supplierAddress: '',
      orderDate: new Date().toISOString().split('T')[0],
      expectedDeliveryDate: '',
      taxAmount: 0,
      shippingCost: 0,
      notes: '',
      items: []
    });
    setItemForm({
      ingredientId: '',
      itemName: '',
      description: '',
      sku: '',
      quantity: 1,
      unit: '',
      unitPrice: 0,
      notes: ''
    });
  };

  const getStatusBadge = (status) => {
    const statusColors = {
      DRAFT: 'bg-gray-100 text-gray-800',
      PENDING_APPROVAL: 'bg-yellow-100 text-yellow-800',
      APPROVED: 'bg-blue-100 text-blue-800',
      ORDERED: 'bg-purple-100 text-purple-800',
      PARTIALLY_RECEIVED: 'bg-orange-100 text-orange-800',
      RECEIVED: 'bg-green-100 text-green-800',
      CANCELLED: 'bg-red-100 text-red-800'
    };
    return <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[status] || 'bg-gray-100 text-gray-800'}`}>
      {t(`finance.purchaseOrders.statuses.${status}`)}
    </span>;
  };

  const getPaymentStatusBadge = (status) => {
    const statusColors = {
      UNPAID: 'bg-red-100 text-red-800',
      PARTIALLY_PAID: 'bg-yellow-100 text-yellow-800',
      PAID: 'bg-green-100 text-green-800'
    };
    return <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[status] || 'bg-gray-100 text-gray-800'}`}>
      {t(`finance.purchaseOrders.paymentStatuses.${status}`)}
    </span>;
  };

  const filteredPOs = (Array.isArray(purchaseOrders) ? purchaseOrders : []).filter(po => {
    const matchesSearch = po.poNumber?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         po.supplierName?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesFilter = filterStatus === 'all' || po.status === filterStatus;
    return matchesSearch && matchesFilter;
  });

  const calculateSubtotal = () => {
    return formData.items.reduce((sum, item) => sum + (item.quantity * item.unitPrice), 0);
  };

  const calculateTotal = () => {
    const subtotal = calculateSubtotal();
    const tax = parseFloat(formData.taxAmount) || 0;
    const shipping = parseFloat(formData.shippingCost) || 0;
    return subtotal + tax + shipping;
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">{t('finance.purchaseOrders.title')}</h1>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          {t('finance.purchaseOrders.createPO')}
        </button>
      </div>

      {/* Filters */}
      <div className="mb-4 flex gap-4">
        <select
          value={selectedRestaurant || ''}
          onChange={(e) => {
            const restaurantId = e.target.value ? parseInt(e.target.value) : null;
            setSelectedRestaurant(restaurantId);
            setFormData(prev => ({ ...prev, restaurantId }));
          }}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="">{t('finance.common.selectRestaurant')}</option>
          {(Array.isArray(restaurants) ? restaurants : []).map(restaurant => (
            <option key={restaurant.id} value={restaurant.id}>
              {restaurant.name}
            </option>
          ))}
        </select>
        <input
          type="text"
          placeholder={t('finance.purchaseOrders.searchPlaceholder')}
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">{t('finance.common.allStatus')}</option>
          <option value="DRAFT">{t('finance.purchaseOrders.statuses.DRAFT')}</option>
          <option value="PENDING_APPROVAL">{t('finance.purchaseOrders.statuses.PENDING_APPROVAL')}</option>
          <option value="APPROVED">{t('finance.purchaseOrders.statuses.APPROVED')}</option>
          <option value="ORDERED">{t('finance.purchaseOrders.statuses.ORDERED')}</option>
          <option value="PARTIALLY_RECEIVED">{t('finance.purchaseOrders.statuses.PARTIALLY_RECEIVED')}</option>
          <option value="RECEIVED">{t('finance.purchaseOrders.statuses.RECEIVED')}</option>
        </select>
      </div>

      {/* Purchase Orders List */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.purchaseOrders.poNumber')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.purchaseOrders.supplier')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.purchaseOrders.orderDate')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.totalAmount')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.status')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.payment')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.purchaseOrders.linkedExpense', 'Linked Expense')}</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">{t('finance.common.actions')}</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan="8" className="px-6 py-4 text-center text-gray-500">{t('finance.common.loading')}</td>
              </tr>
            ) : filteredPOs.length === 0 ? (
              <tr>
                <td colSpan="8" className="px-6 py-4 text-center text-gray-500">{t('finance.purchaseOrders.noPurchaseOrders')}</td>
              </tr>
            ) : (
              filteredPOs.map((po) => (
                <tr key={po.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{po.poNumber}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{po.supplierName}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{po.orderDate}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">{po.totalAmount?.toFixed(2)}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getStatusBadge(po.status)}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getPaymentStatusBadge(po.paymentStatus)}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    {po.expenseId ? (
                      <Link
                        to={`/expenses?id=${po.expenseId}`}
                        className="flex items-center gap-1 text-blue-600 hover:text-blue-800"
                      >
                        <LinkIcon size={14} />
                        <span>{po.expenseNumber}</span>
                      </Link>
                    ) : (
                      <span className="text-gray-400">-</span>
                    )}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                    <div className="flex gap-2">
                      {po.status === 'DRAFT' && (
                        <button
                          onClick={() => handleApprove(po.id)}
                          className="text-green-600 hover:text-green-900"
                          title="Approve"
                        >
                          <Check size={18} />
                        </button>
                      )}
                      {(po.status === 'APPROVED' || po.status === 'ORDERED' || po.status === 'PARTIALLY_RECEIVED') && (
                        <button
                          onClick={() => openReceiveModal(po)}
                          className="text-blue-600 hover:text-blue-900"
                          title="Receive"
                        >
                          <Package size={18} />
                        </button>
                      )}
                      {po.paymentStatus !== 'PAID' && po.status === 'RECEIVED' && (
                        <button
                          onClick={() => openPaymentModal(po)}
                          className="text-green-600 hover:text-green-900"
                          title="Record Payment"
                        >
                          <Check size={18} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {/* Create/Edit Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 overflow-y-auto">
          <div className="bg-white rounded-lg p-6 w-full max-w-4xl my-8">
            <h2 className="text-xl font-bold mb-4">{t('finance.purchaseOrders.createPO')}</h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.supplier')}</label>
                <select
                  value={formData.supplierId}
                  onChange={(e) => handleSupplierChange(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500"
                >
                  <option value="">{t('finance.purchaseOrders.manualEntry', 'Manual Entry')}</option>
                  {suppliers.map(supplier => (
                    <option key={supplier.id} value={supplier.id}>
                      {supplier.name} {supplier.code ? `(${supplier.code})` : ''}
                    </option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.supplierName', 'Supplier Name')} *</label>
                <input
                  type="text"
                  value={formData.supplierName}
                  onChange={(e) => setFormData({ ...formData, supplierName: e.target.value })}
                  className={`w-full px-3 py-2 border border-gray-300 rounded-lg ${formData.supplierId ? 'bg-gray-50' : ''}`}
                  placeholder={t('finance.purchaseOrders.enterSupplierName', 'Enter supplier name')}
                  readOnly={!!formData.supplierId}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.supplierContact')}</label>
                <input
                  type="text"
                  value={formData.supplierContact}
                  onChange={(e) => setFormData({ ...formData, supplierContact: e.target.value })}
                  className={`w-full px-3 py-2 border border-gray-300 rounded-lg ${formData.supplierId ? 'bg-gray-50' : ''}`}
                  placeholder={formData.supplierId ? t('finance.purchaseOrders.autoPopulated') : t('finance.purchaseOrders.enterSupplierContact', 'Enter contact info')}
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.orderDate')} *</label>
                <input
                  type="date"
                  value={formData.orderDate}
                  onChange={(e) => setFormData({ ...formData, orderDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.expectedDelivery')}</label>
                <input
                  type="date"
                  value={formData.expectedDeliveryDate}
                  onChange={(e) => setFormData({ ...formData, expectedDeliveryDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.supplierAddress')}</label>
              <textarea
                value={formData.supplierAddress}
                onChange={(e) => setFormData({ ...formData, supplierAddress: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg bg-gray-50"
                rows="2"
                placeholder={t('finance.purchaseOrders.autoPopulated')}
              />
            </div>

            {/* Items Section */}
            <div className="mb-4 border-t pt-4">
              <h3 className="text-lg font-semibold mb-3">{t('finance.purchaseOrders.items')}</h3>

              {/* Add Item Form */}
              <div className="bg-gray-50 p-4 rounded-lg mb-4">
                <div className="grid grid-cols-4 gap-2 mb-2">
                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.linkToIngredient')}</label>
                    <select
                      value={itemForm.ingredientId}
                      onChange={(e) => handleIngredientChange(e.target.value)}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    >
                      <option value="">{t('finance.common.none')} ({t('finance.purchaseOrders.optional', 'Optional')})</option>
                      {(Array.isArray(filteredIngredients) ? filteredIngredients : []).map(ing => (
                        <option key={ing.id} value={ing.id}>
                          {ing.name} {ing.sku ? `(${ing.sku})` : ''}
                        </option>
                      ))}
                    </select>
                    {itemForm.ingredientId && (
                      <p className="text-xs text-blue-600 mt-1">{t('finance.purchaseOrders.autoPopulated')}</p>
                    )}
                    {formData.supplierId && filteredIngredients.length === 0 && (
                      <p className="text-xs text-orange-600 mt-1">{t('finance.purchaseOrders.noIngredientsForSupplier')}</p>
                    )}
                  </div>
                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.itemName')} *</label>
                    <input
                      type="text"
                      value={itemForm.itemName}
                      onChange={(e) => setItemForm({ ...itemForm, itemName: e.target.value })}
                      className={`w-full px-3 py-2 border border-gray-300 rounded-lg ${itemForm.ingredientId ? 'bg-gray-50' : ''}`}
                      placeholder={t('finance.purchaseOrders.itemNamePlaceholder')}
                    />
                  </div>
                </div>
                <div className="grid grid-cols-4 gap-2 mb-2">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.quantity')} *</label>
                    <input
                      type="number"
                      value={itemForm.quantity}
                      onChange={(e) => setItemForm({ ...itemForm, quantity: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                      min="0"
                      step="0.01"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.unit')}</label>
                    <input
                      type="text"
                      value={itemForm.unit}
                      onChange={(e) => setItemForm({ ...itemForm, unit: e.target.value })}
                      className={`w-full px-3 py-2 border border-gray-300 rounded-lg ${itemForm.ingredientId ? 'bg-gray-50' : ''}`}
                      placeholder={t('finance.purchaseOrders.unitPlaceholder')}
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.unitPrice')} *</label>
                    <input
                      type="number"
                      value={itemForm.unitPrice}
                      onChange={(e) => setItemForm({ ...itemForm, unitPrice: e.target.value })}
                      className={`w-full px-3 py-2 border border-gray-300 rounded-lg ${itemForm.ingredientId ? 'bg-gray-50' : ''}`}
                      min="0"
                      step="0.01"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.sku')}</label>
                    <input
                      type="text"
                      value={itemForm.sku}
                      onChange={(e) => setItemForm({ ...itemForm, sku: e.target.value })}
                      className={`w-full px-3 py-2 border border-gray-300 rounded-lg ${itemForm.ingredientId ? 'bg-gray-50' : ''}`}
                    />
                  </div>
                </div>
                <button
                  onClick={handleAddItem}
                  className="mt-3 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  {t('finance.purchaseOrders.addItem')}
                </button>
              </div>

              {/* Items List */}
              {formData.items.length > 0 && (
                <div className="border rounded-lg overflow-hidden">
                  <table className="min-w-full">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.common.item')}</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.common.quantity')}</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.common.unitPrice')}</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.common.total')}</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.common.actions')}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {formData.items.map((item, index) => (
                        <tr key={index}>
                          <td className="px-4 py-2 text-sm">{item.itemName}</td>
                          <td className="px-4 py-2 text-sm">{item.quantity} {item.unit}</td>
                          <td className="px-4 py-2 text-sm">{item.unitPrice}</td>
                          <td className="px-4 py-2 text-sm">{(item.quantity * item.unitPrice).toFixed(2)}</td>
                          <td className="px-4 py-2 text-sm">
                            <button
                              onClick={() => handleRemoveItem(index)}
                              className="text-red-600 hover:text-red-900"
                            >
                              <Trash2 size={16} />
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>

            {/* Totals */}
            <div className="border-t pt-4 mb-4">
              <div className="grid grid-cols-3 gap-4 mb-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.taxAmount')}</label>
                  <input
                    type="number"
                    value={formData.taxAmount}
                    onChange={(e) => setFormData({ ...formData, taxAmount: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    min="0"
                    step="0.01"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.shippingCost')}</label>
                  <input
                    type="number"
                    value={formData.shippingCost}
                    onChange={(e) => setFormData({ ...formData, shippingCost: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    min="0"
                    step="0.01"
                  />
                </div>
              </div>

              <div className="text-right space-y-2">
                <div className="text-lg">
                  <span className="font-medium">{t('finance.common.subtotal')}:</span> {calculateSubtotal().toFixed(2)}
                </div>
                <div className="text-xl font-bold">
                  <span>{t('finance.common.total')}:</span> {calculateTotal().toFixed(2)}
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowModal(false); resetForm(); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel')}
              </button>
              <button
                onClick={handleSave}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('finance.common.saving') : t('finance.purchaseOrders.createPO')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Receive Modal */}
      {showReceiveModal && selectedPO && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-3xl">
            <h2 className="text-xl font-bold mb-4">{t('finance.purchaseOrders.receiveModalTitle')}: {selectedPO.poNumber}</h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.actualDeliveryDate')}</label>
                <input
                  type="date"
                  value={receiveForm.actualDeliveryDate}
                  onChange={(e) => setReceiveForm({ ...receiveForm, actualDeliveryDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.purchaseOrders.receivedBy')}</label>
                <input
                  type="text"
                  value={receiveForm.receivedBy}
                  onChange={(e) => setReceiveForm({ ...receiveForm, receivedBy: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
            </div>

            <div className="mb-4">
              <h3 className="font-semibold mb-2">{t('finance.purchaseOrders.itemsToReceive')}</h3>
              <div className="border rounded-lg overflow-hidden">
                <table className="min-w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.common.item')}</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.purchaseOrders.ordered')}</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.purchaseOrders.alreadyReceived')}</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">{t('finance.purchaseOrders.receiveNow')}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {(Array.isArray(selectedPO.items) ? selectedPO.items : []).map((item, index) => (
                      <tr key={item.id}>
                        <td className="px-4 py-2 text-sm">{item.itemName}</td>
                        <td className="px-4 py-2 text-sm">{item.quantity} {item.unit}</td>
                        <td className="px-4 py-2 text-sm">{item.receivedQuantity || 0} {item.unit}</td>
                        <td className="px-4 py-2 text-sm">
                          <input
                            type="number"
                            value={receiveForm.items[index]?.receivedQuantity || 0}
                            onChange={(e) => {
                              const newItems = [...receiveForm.items];
                              newItems[index].receivedQuantity = parseFloat(e.target.value);
                              setReceiveForm({ ...receiveForm, items: newItems });
                            }}
                            className="w-24 px-3 py-1 border border-gray-300 rounded"
                            min="0"
                            step="0.01"
                            max={item.quantity - (item.receivedQuantity || 0)}
                          />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowReceiveModal(false); setSelectedPO(null); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel')}
              </button>
              <button
                onClick={handleReceive}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('finance.common.recording') : t('finance.purchaseOrders.recordReceipt')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Payment Modal */}
      {showPaymentModal && selectedPO && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">{t('finance.purchaseOrders.recordPayment')}: {selectedPO.poNumber}</h2>

            <div className="mb-4">
              <div className="bg-gray-50 p-3 rounded-lg mb-4">
                <div className="flex justify-between mb-2">
                  <span className="text-sm text-gray-600">{t('finance.common.totalAmount')}:</span>
                  <span className="font-medium">{selectedPO.totalAmount?.toFixed(2)}</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-sm text-gray-600">{t('finance.purchaseOrders.alreadyPaid')}:</span>
                  <span className="font-medium">{(selectedPO.paidAmount || 0).toFixed(2)}</span>
                </div>
                <div className="flex justify-between border-t pt-2">
                  <span className="text-sm font-semibold">{t('finance.common.remaining')}:</span>
                  <span className="font-bold text-red-600">
                    {(selectedPO.totalAmount - (selectedPO.paidAmount || 0)).toFixed(2)}
                  </span>
                </div>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.paymentDate')}</label>
                  <input
                    type="date"
                    value={paymentForm.paymentDate}
                    onChange={(e) => setPaymentForm({ ...paymentForm, paymentDate: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.amount')}</label>
                  <input
                    type="number"
                    value={paymentForm.amount}
                    onChange={(e) => setPaymentForm({ ...paymentForm, amount: parseFloat(e.target.value) })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    min="0"
                    step="0.01"
                    max={selectedPO.totalAmount - (selectedPO.paidAmount || 0)}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.paymentMethod')}</label>
                  <select
                    value={paymentForm.paymentMethod}
                    onChange={(e) => setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  >
                    <option value="CASH">{t('finance.expenses.paymentMethods.CASH')}</option>
                    <option value="CARD">{t('finance.expenses.paymentMethods.CARD')}</option>
                    <option value="BANK_TRANSFER">{t('finance.expenses.paymentMethods.BANK_TRANSFER')}</option>
                    <option value="CHECK">{t('finance.expenses.paymentMethods.CHECK')}</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.referenceNumber')}</label>
                  <input
                    type="text"
                    value={paymentForm.referenceNumber}
                    onChange={(e) => setPaymentForm({ ...paymentForm, referenceNumber: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    placeholder={t('finance.purchaseOrders.referenceNumberPlaceholder')}
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">{t('finance.common.notes')}</label>
                  <textarea
                    value={paymentForm.notes}
                    onChange={(e) => setPaymentForm({ ...paymentForm, notes: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    rows="2"
                  />
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowPaymentModal(false); setSelectedPO(null); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel')}
              </button>
              <button
                onClick={handleRecordPayment}
                disabled={loading}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                {loading ? t('finance.common.recording') : t('finance.purchaseOrders.recordPayment')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseOrders;
