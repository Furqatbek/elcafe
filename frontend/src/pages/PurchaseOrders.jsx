import React, { useState, useEffect } from 'react';
import { financialAPI, inventoryAPI } from '../services/api';
import { useAuthStore } from '../stores/authStore';
import { Plus, Edit, Trash2, Check, X, DollarSign, Package } from 'lucide-react';

const PurchaseOrders = () => {
  const { user } = useAuthStore();
  const [purchaseOrders, setPurchaseOrders] = useState([]);
  const [ingredients, setIngredients] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [showReceiveModal, setShowReceiveModal] = useState(false);
  const [showPaymentModal, setShowPaymentModal] = useState(false);
  const [selectedPO, setSelectedPO] = useState(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState('all');

  const [formData, setFormData] = useState({
    restaurantId: '',
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
    if (user?.restaurantId) {
      setSelectedRestaurant(user.restaurantId);
      setFormData(prev => ({ ...prev, restaurantId: user.restaurantId }));
      loadPurchaseOrders(user.restaurantId);
      loadIngredients(user.restaurantId);
    }
  }, [user]);

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

  const handleAddItem = () => {
    if (!itemForm.itemName || !itemForm.quantity || !itemForm.unitPrice) {
      alert('Please fill in all required fields');
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
        alert('Please fill in supplier name and add at least one item');
        return;
      }

      setLoading(true);
      await financialAPI.createPurchaseOrder(formData);
      alert('Purchase order created successfully!');
      setShowModal(false);
      resetForm();
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to create purchase order:', error);
      alert('Failed to create purchase order: ' + (error.response?.data?.message || error.message));
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id) => {
    try {
      await financialAPI.approvePurchaseOrder(id, user?.username);
      alert('Purchase order approved successfully!');
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to approve purchase order:', error);
      alert('Failed to approve purchase order');
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
      alert('Purchase order received successfully!');
      setShowReceiveModal(false);
      setSelectedPO(null);
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to receive purchase order:', error);
      alert('Failed to receive purchase order');
    } finally {
      setLoading(false);
    }
  };

  const handleRecordPayment = async () => {
    try {
      setLoading(true);
      await financialAPI.recordPOPayment(selectedPO.id, paymentForm);
      alert('Payment recorded successfully!');
      setShowPaymentModal(false);
      setSelectedPO(null);
      loadPurchaseOrders(selectedRestaurant);
    } catch (error) {
      console.error('Failed to record payment:', error);
      alert('Failed to record payment');
    } finally {
      setLoading(false);
    }
  };

  const openReceiveModal = (po) => {
    setSelectedPO(po);
    setReceiveForm({
      actualDeliveryDate: new Date().toISOString().split('T')[0],
      receivedBy: user?.username || '',
      items: po.items.map(item => ({
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
      {status?.replace(/_/g, ' ')}
    </span>;
  };

  const getPaymentStatusBadge = (status) => {
    const statusColors = {
      UNPAID: 'bg-red-100 text-red-800',
      PARTIALLY_PAID: 'bg-yellow-100 text-yellow-800',
      PAID: 'bg-green-100 text-green-800'
    };
    return <span className={`px-2 py-1 rounded-full text-xs font-medium ${statusColors[status] || 'bg-gray-100 text-gray-800'}`}>
      {status?.replace(/_/g, ' ')}
    </span>;
  };

  const filteredPOs = purchaseOrders.filter(po => {
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
        <h1 className="text-2xl font-bold">Purchase Orders</h1>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus size={20} />
          Create PO
        </button>
      </div>

      {/* Filters */}
      <div className="mb-4 flex gap-4">
        <input
          type="text"
          placeholder="Search by PO number or supplier..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        />
        <select
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
          className="px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
        >
          <option value="all">All Status</option>
          <option value="DRAFT">Draft</option>
          <option value="PENDING_APPROVAL">Pending Approval</option>
          <option value="APPROVED">Approved</option>
          <option value="ORDERED">Ordered</option>
          <option value="PARTIALLY_RECEIVED">Partially Received</option>
          <option value="RECEIVED">Received</option>
        </select>
      </div>

      {/* Purchase Orders List */}
      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">PO Number</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Supplier</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Order Date</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Total Amount</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Status</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Payment</th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">Actions</th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading ? (
              <tr>
                <td colSpan="7" className="px-6 py-4 text-center text-gray-500">Loading...</td>
              </tr>
            ) : filteredPOs.length === 0 ? (
              <tr>
                <td colSpan="7" className="px-6 py-4 text-center text-gray-500">No purchase orders found</td>
              </tr>
            ) : (
              filteredPOs.map((po) => (
                <tr key={po.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">{po.poNumber}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{po.supplierName}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">{po.orderDate}</td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">${po.totalAmount?.toFixed(2)}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getStatusBadge(po.status)}</td>
                  <td className="px-6 py-4 whitespace-nowrap">{getPaymentStatusBadge(po.paymentStatus)}</td>
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
                          <DollarSign size={18} />
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
            <h2 className="text-xl font-bold mb-4">Create Purchase Order</h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Supplier Name *</label>
                <input
                  type="text"
                  value={formData.supplierName}
                  onChange={(e) => setFormData({ ...formData, supplierName: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Supplier Contact</label>
                <input
                  type="text"
                  value={formData.supplierContact}
                  onChange={(e) => setFormData({ ...formData, supplierContact: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Order Date *</label>
                <input
                  type="date"
                  value={formData.orderDate}
                  onChange={(e) => setFormData({ ...formData, orderDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Expected Delivery</label>
                <input
                  type="date"
                  value={formData.expectedDeliveryDate}
                  onChange={(e) => setFormData({ ...formData, expectedDeliveryDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
            </div>

            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">Supplier Address</label>
              <textarea
                value={formData.supplierAddress}
                onChange={(e) => setFormData({ ...formData, supplierAddress: e.target.value })}
                className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                rows="2"
              />
            </div>

            {/* Items Section */}
            <div className="mb-4 border-t pt-4">
              <h3 className="text-lg font-semibold mb-3">Items</h3>

              {/* Add Item Form */}
              <div className="bg-gray-50 p-4 rounded-lg mb-4">
                <div className="grid grid-cols-4 gap-2 mb-2">
                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-1">Item Name *</label>
                    <input
                      type="text"
                      value={itemForm.itemName}
                      onChange={(e) => setItemForm({ ...itemForm, itemName: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                      placeholder="Item name"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Quantity *</label>
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
                    <label className="block text-sm font-medium text-gray-700 mb-1">Unit</label>
                    <input
                      type="text"
                      value={itemForm.unit}
                      onChange={(e) => setItemForm({ ...itemForm, unit: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                      placeholder="kg, pcs, etc"
                    />
                  </div>
                </div>
                <div className="grid grid-cols-4 gap-2">
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">Unit Price *</label>
                    <input
                      type="number"
                      value={itemForm.unitPrice}
                      onChange={(e) => setItemForm({ ...itemForm, unitPrice: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                      min="0"
                      step="0.01"
                    />
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">SKU</label>
                    <input
                      type="text"
                      value={itemForm.sku}
                      onChange={(e) => setItemForm({ ...itemForm, sku: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    />
                  </div>
                  <div className="col-span-2">
                    <label className="block text-sm font-medium text-gray-700 mb-1">Link to Ingredient (Optional)</label>
                    <select
                      value={itemForm.ingredientId}
                      onChange={(e) => setItemForm({ ...itemForm, ingredientId: e.target.value })}
                      className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    >
                      <option value="">None</option>
                      {ingredients.map(ing => (
                        <option key={ing.id} value={ing.id}>{ing.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
                <button
                  onClick={handleAddItem}
                  className="mt-3 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
                >
                  Add Item
                </button>
              </div>

              {/* Items List */}
              {formData.items.length > 0 && (
                <div className="border rounded-lg overflow-hidden">
                  <table className="min-w-full">
                    <thead className="bg-gray-50">
                      <tr>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Item</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Quantity</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Unit Price</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Total</th>
                        <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-200">
                      {formData.items.map((item, index) => (
                        <tr key={index}>
                          <td className="px-4 py-2 text-sm">{item.itemName}</td>
                          <td className="px-4 py-2 text-sm">{item.quantity} {item.unit}</td>
                          <td className="px-4 py-2 text-sm">${item.unitPrice}</td>
                          <td className="px-4 py-2 text-sm">${(item.quantity * item.unitPrice).toFixed(2)}</td>
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
                  <label className="block text-sm font-medium text-gray-700 mb-1">Tax Amount</label>
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
                  <label className="block text-sm font-medium text-gray-700 mb-1">Shipping Cost</label>
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
                  <span className="font-medium">Subtotal:</span> ${calculateSubtotal().toFixed(2)}
                </div>
                <div className="text-xl font-bold">
                  <span>Total:</span> ${calculateTotal().toFixed(2)}
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button
                onClick={() => { setShowModal(false); resetForm(); }}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                Cancel
              </button>
              <button
                onClick={handleSave}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? 'Saving...' : 'Create Purchase Order'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Receive Modal */}
      {showReceiveModal && selectedPO && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-3xl">
            <h2 className="text-xl font-bold mb-4">Receive Purchase Order: {selectedPO.poNumber}</h2>

            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Actual Delivery Date</label>
                <input
                  type="date"
                  value={receiveForm.actualDeliveryDate}
                  onChange={(e) => setReceiveForm({ ...receiveForm, actualDeliveryDate: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">Received By</label>
                <input
                  type="text"
                  value={receiveForm.receivedBy}
                  onChange={(e) => setReceiveForm({ ...receiveForm, receivedBy: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
            </div>

            <div className="mb-4">
              <h3 className="font-semibold mb-2">Items to Receive</h3>
              <div className="border rounded-lg overflow-hidden">
                <table className="min-w-full">
                  <thead className="bg-gray-50">
                    <tr>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Item</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Ordered</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Already Received</th>
                      <th className="px-4 py-2 text-left text-xs font-medium text-gray-500">Receive Now</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-gray-200">
                    {selectedPO.items.map((item, index) => (
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
                Cancel
              </button>
              <button
                onClick={handleReceive}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? 'Recording...' : 'Record Receipt'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Payment Modal */}
      {showPaymentModal && selectedPO && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-bold mb-4">Record Payment: {selectedPO.poNumber}</h2>

            <div className="mb-4">
              <div className="bg-gray-50 p-3 rounded-lg mb-4">
                <div className="flex justify-between mb-2">
                  <span className="text-sm text-gray-600">Total Amount:</span>
                  <span className="font-medium">${selectedPO.totalAmount?.toFixed(2)}</span>
                </div>
                <div className="flex justify-between mb-2">
                  <span className="text-sm text-gray-600">Already Paid:</span>
                  <span className="font-medium">${(selectedPO.paidAmount || 0).toFixed(2)}</span>
                </div>
                <div className="flex justify-between border-t pt-2">
                  <span className="text-sm font-semibold">Remaining:</span>
                  <span className="font-bold text-red-600">
                    ${(selectedPO.totalAmount - (selectedPO.paidAmount || 0)).toFixed(2)}
                  </span>
                </div>
              </div>

              <div className="space-y-4">
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Payment Date</label>
                  <input
                    type="date"
                    value={paymentForm.paymentDate}
                    onChange={(e) => setPaymentForm({ ...paymentForm, paymentDate: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Amount</label>
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
                  <label className="block text-sm font-medium text-gray-700 mb-1">Payment Method</label>
                  <select
                    value={paymentForm.paymentMethod}
                    onChange={(e) => setPaymentForm({ ...paymentForm, paymentMethod: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                  >
                    <option value="CASH">Cash</option>
                    <option value="CARD">Card</option>
                    <option value="BANK_TRANSFER">Bank Transfer</option>
                    <option value="CHECK">Check</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Reference Number</label>
                  <input
                    type="text"
                    value={paymentForm.referenceNumber}
                    onChange={(e) => setPaymentForm({ ...paymentForm, referenceNumber: e.target.value })}
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                    placeholder="Transaction ID, Check number, etc."
                  />
                </div>
                <div>
                  <label className="block text-sm font-medium text-gray-700 mb-1">Notes</label>
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
                Cancel
              </button>
              <button
                onClick={handleRecordPayment}
                disabled={loading}
                className="px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
              >
                {loading ? 'Recording...' : 'Record Payment'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PurchaseOrders;
