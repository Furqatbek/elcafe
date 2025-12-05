import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { customerAPI } from '../services/api';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '../components/ui/dialog';
import {
  Search,
  Download,
  UserPlus,
  Filter,
  Mail,
  Phone,
  MapPin,
  Tag,
  FileText,
  Calendar,
  DollarSign,
  ShoppingCart,
  Clock,
  TrendingUp,
  Users,
} from 'lucide-react';
import { format } from 'date-fns';

export default function CustomerSegments() {
  const { t } = useTranslation();
  const [customers, setCustomers] = useState([]);
  const [filteredCustomers, setFilteredCustomers] = useState([]);
  const [loading, setLoading] = useState(true);

  // Filter states
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [registrationSourceFilter, setRegistrationSourceFilter] = useState('all');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [minRecency, setMinRecency] = useState('');
  const [maxRecency, setMaxRecency] = useState('');
  const [minFrequency, setMinFrequency] = useState('');
  const [maxFrequency, setMaxFrequency] = useState('');
  const [minMonetary, setMinMonetary] = useState('');
  const [maxMonetary, setMaxMonetary] = useState('');

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    email: '',
    phone: '',
    defaultAddress: '',
    city: '',
    state: '',
    zipCode: '',
    notes: '',
    tags: '',
    active: true,
    registrationSource: 'ADMIN_PANEL',
  });
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadCustomers();
  }, []);

  useEffect(() => {
    applyFilters();
  }, [
    customers,
    searchTerm,
    statusFilter,
    registrationSourceFilter,
    startDate,
    endDate,
    minRecency,
    maxRecency,
    minFrequency,
    maxFrequency,
    minMonetary,
    maxMonetary,
  ]);

  const loadCustomers = async () => {
    setLoading(true);
    try {
      const response = await customerAPI.getAllActivity();
      setCustomers(response.data.data || response.data || []);
    } catch (error) {
      console.error('Failed to load customers:', error);
      setCustomers([]);
    } finally {
      setLoading(false);
    }
  };

  const applyFilters = () => {
    let filtered = [...customers];

    // Search filter
    if (searchTerm) {
      const search = searchTerm.toLowerCase();
      filtered = filtered.filter(
        (customer) =>
          customer.firstName?.toLowerCase().includes(search) ||
          customer.lastName?.toLowerCase().includes(search) ||
          customer.email?.toLowerCase().includes(search) ||
          customer.phone?.toLowerCase().includes(search) ||
          customer.city?.toLowerCase().includes(search)
      );
    }

    // Status filter
    if (statusFilter !== 'all') {
      filtered = filtered.filter((customer) =>
        statusFilter === 'active' ? customer.active : !customer.active
      );
    }

    // Registration source filter
    if (registrationSourceFilter !== 'all') {
      filtered = filtered.filter(
        (customer) => customer.registrationSource === registrationSourceFilter
      );
    }

    // Date range filter
    if (startDate) {
      filtered = filtered.filter(
        (customer) =>
          customer.registrationDate &&
          new Date(customer.registrationDate) >= new Date(startDate)
      );
    }
    if (endDate) {
      filtered = filtered.filter(
        (customer) =>
          customer.registrationDate &&
          new Date(customer.registrationDate) <= new Date(endDate)
      );
    }

    // Recency filter
    if (minRecency) {
      filtered = filtered.filter(
        (customer) => customer.recency !== null && customer.recency >= parseInt(minRecency)
      );
    }
    if (maxRecency) {
      filtered = filtered.filter(
        (customer) => customer.recency !== null && customer.recency <= parseInt(maxRecency)
      );
    }

    // Frequency filter
    if (minFrequency) {
      filtered = filtered.filter(
        (customer) =>
          customer.frequency !== null && customer.frequency >= parseInt(minFrequency)
      );
    }
    if (maxFrequency) {
      filtered = filtered.filter(
        (customer) =>
          customer.frequency !== null && customer.frequency <= parseInt(maxFrequency)
      );
    }

    // Monetary filter
    if (minMonetary) {
      filtered = filtered.filter(
        (customer) =>
          customer.monetary !== null && customer.monetary >= parseFloat(minMonetary)
      );
    }
    if (maxMonetary) {
      filtered = filtered.filter(
        (customer) =>
          customer.monetary !== null && customer.monetary <= parseFloat(maxMonetary)
      );
    }

    setFilteredCustomers(filtered);
  };

  const exportToCSV = () => {
    const headers = [
      'ID',
      'First Name',
      'Last Name',
      'Email',
      'Phone',
      'City',
      'Average Check',
      'Total Amount',
      'Days Since Last Order',
      'Total Orders',
      'Order Sources',
      'Registration Date',
      'Registration Source',
      'RFM Segment',
      'Status',
      'Tags',
    ];

    const csvData = filteredCustomers.map((customer) => [
      customer.customerId,
      customer.firstName,
      customer.lastName,
      customer.email,
      customer.phone,
      customer.city || '',
      customer.averageCheck || 0,
      customer.monetary || 0,
      customer.recency !== null ? customer.recency : 'N/A',
      customer.frequency || 0,
      customer.orderSources?.join(', ') || '',
      customer.registrationDate
        ? format(new Date(customer.registrationDate), 'yyyy-MM-dd HH:mm:ss')
        : '',
      customer.registrationSource || '',
      customer.rfmSegment || '',
      customer.active ? 'Active' : 'Inactive',
      customer.tags || '',
    ]);

    const csv = [headers, ...csvData]
      .map((row) => row.map((cell) => `"${cell}"`).join(','))
      .join('\n');

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `customers_activity_${format(new Date(), 'yyyy-MM-dd_HHmmss')}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  };

  const resetFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
    setRegistrationSourceFilter('all');
    setStartDate('');
    setEndDate('');
    setMinRecency('');
    setMaxRecency('');
    setMinFrequency('');
    setMaxFrequency('');
    setMinMonetary('');
    setMaxMonetary('');
  };

  const validateForm = () => {
    const errors = {};
    if (!formData.firstName.trim()) errors.firstName = t('validation.required');
    if (!formData.lastName.trim()) errors.lastName = t('validation.required');
    if (!formData.email.trim()) {
      errors.email = t('validation.required');
    } else if (!/\S+@\S+\.\S+/.test(formData.email)) {
      errors.email = t('validation.email');
    }
    if (!formData.phone.trim()) errors.phone = t('validation.required');
    setFormErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateForm()) return;

    setSubmitting(true);
    try {
      await customerAPI.create(formData);
      setIsDialogOpen(false);
      setFormData({
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        defaultAddress: '',
        city: '',
        state: '',
        zipCode: '',
        notes: '',
        tags: '',
        active: true,
        registrationSource: 'ADMIN_PANEL',
      });
      setFormErrors({});
      loadCustomers();
      alert(t('messages.createSuccess'));
    } catch (error) {
      console.error('Failed to create customer:', error);
      alert(t('messages.error'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleInputChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value,
    }));
    if (formErrors[name]) {
      setFormErrors((prev) => ({ ...prev, [name]: '' }));
    }
  };

  const getRecencyBadgeColor = (recency) => {
    if (recency === null) return 'bg-gray-100 text-gray-800';
    if (recency <= 7) return 'bg-green-100 text-green-800';
    if (recency <= 30) return 'bg-blue-100 text-blue-800';
    if (recency <= 90) return 'bg-yellow-100 text-yellow-800';
    return 'bg-red-100 text-red-800';
  };

  const getSegmentBadgeColor = (segment) => {
    const colors = {
      Champions: 'bg-purple-100 text-purple-800',
      'Loyal Customers': 'bg-blue-100 text-blue-800',
      'Potential Loyalists': 'bg-cyan-100 text-cyan-800',
      'Recent Customers': 'bg-green-100 text-green-800',
      Promising: 'bg-teal-100 text-teal-800',
      'Need Attention': 'bg-yellow-100 text-yellow-800',
      'About to Sleep': 'bg-orange-100 text-orange-800',
      'At Risk': 'bg-red-100 text-red-800',
      "Can't Lose Them": 'bg-pink-100 text-pink-800',
      Hibernating: 'bg-gray-100 text-gray-800',
      Lost: 'bg-slate-100 text-slate-800',
    };
    return colors[segment] || 'bg-gray-100 text-gray-800';
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="text-lg">{t('common.loading')}</div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('customerSegments.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {filteredCustomers.length} {t('customerSegments.totalSegments')}
          </p>
        </div>
        <div className="flex gap-2">
          <Button onClick={exportToCSV} variant="outline" className="gap-2">
            <Download className="h-4 w-4" />
            {t('common.export')} CSV
          </Button>
          <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
            <DialogTrigger asChild>
              <Button className="gap-2">
                <UserPlus className="h-4 w-4" />
                {t('customers.newCustomer')}
              </Button>
            </DialogTrigger>
            <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
              <DialogHeader>
                <DialogTitle>{t('customers.newCustomer')}</DialogTitle>
                <DialogDescription>{t('customers.fillCustomerDetails')}</DialogDescription>
              </DialogHeader>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="firstName">
                      {t('customers.firstName')} <span className="text-red-500">*</span>
                    </Label>
                    <Input
                      id="firstName"
                      name="firstName"
                      value={formData.firstName}
                      onChange={handleInputChange}
                      className={formErrors.firstName ? 'border-red-500' : ''}
                    />
                    {formErrors.firstName && (
                      <p className="text-sm text-red-500">{formErrors.firstName}</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="lastName">
                      {t('customers.lastName')} <span className="text-red-500">*</span>
                    </Label>
                    <Input
                      id="lastName"
                      name="lastName"
                      value={formData.lastName}
                      onChange={handleInputChange}
                      className={formErrors.lastName ? 'border-red-500' : ''}
                    />
                    {formErrors.lastName && (
                      <p className="text-sm text-red-500">{formErrors.lastName}</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="email">
                      {t('customers.email')} <span className="text-red-500">*</span>
                    </Label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                      <Input
                        id="email"
                        name="email"
                        type="email"
                        value={formData.email}
                        onChange={handleInputChange}
                        className={`pl-10 ${formErrors.email ? 'border-red-500' : ''}`}
                      />
                    </div>
                    {formErrors.email && (
                      <p className="text-sm text-red-500">{formErrors.email}</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="phone">
                      {t('customers.phone')} <span className="text-red-500">*</span>
                    </Label>
                    <div className="relative">
                      <Phone className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                      <Input
                        id="phone"
                        name="phone"
                        value={formData.phone}
                        onChange={handleInputChange}
                        className={`pl-10 ${formErrors.phone ? 'border-red-500' : ''}`}
                      />
                    </div>
                    {formErrors.phone && (
                      <p className="text-sm text-red-500">{formErrors.phone}</p>
                    )}
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="defaultAddress">{t('customers.address')}</Label>
                  <div className="relative">
                    <MapPin className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <Input
                      id="defaultAddress"
                      name="defaultAddress"
                      value={formData.defaultAddress}
                      onChange={handleInputChange}
                      className="pl-10"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="city">{t('customers.city')}</Label>
                    <Input
                      id="city"
                      name="city"
                      value={formData.city}
                      onChange={handleInputChange}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="state">{t('customers.state')}</Label>
                    <Input
                      id="state"
                      name="state"
                      value={formData.state}
                      onChange={handleInputChange}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="zipCode">{t('customers.zipCode')}</Label>
                    <Input
                      id="zipCode"
                      name="zipCode"
                      value={formData.zipCode}
                      onChange={handleInputChange}
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tags">{t('customers.tags')}</Label>
                  <div className="relative">
                    <Tag className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <Input
                      id="tags"
                      name="tags"
                      value={formData.tags}
                      onChange={handleInputChange}
                      placeholder="VIP, Regular, etc."
                      className="pl-10"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="registrationSource">{t('customers.registrationSource')}</Label>
                  <select
                    id="registrationSource"
                    name="registrationSource"
                    value={formData.registrationSource}
                    onChange={handleInputChange}
                    className="w-full px-3 py-2 border rounded-md"
                  >
                    <option value="ADMIN_PANEL">{t('customers.sources.adminPanel')}</option>
                    <option value="TELEGRAM_BOT">{t('customers.sources.telegramBot')}</option>
                    <option value="WEBSITE">{t('customers.sources.website')}</option>
                    <option value="MOBILE_APP">{t('customers.sources.mobileApp')}</option>
                    <option value="PHONE_CALL">{t('customers.sources.phoneCall')}</option>
                    <option value="WALK_IN">{t('customers.sources.walkIn')}</option>
                    <option value="OTHER">{t('customers.sources.other')}</option>
                  </select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="notes">{t('customers.notes')}</Label>
                  <div className="relative">
                    <FileText className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <textarea
                      id="notes"
                      name="notes"
                      value={formData.notes}
                      onChange={handleInputChange}
                      rows={3}
                      className="w-full pl-10 px-3 py-2 border rounded-md resize-none"
                    />
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="active"
                    name="active"
                    checked={formData.active}
                    onChange={handleInputChange}
                    className="rounded"
                  />
                  <Label htmlFor="active" className="cursor-pointer">
                    {t('customers.active')}
                  </Label>
                </div>

                <div className="flex justify-end gap-2 pt-4">
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => setIsDialogOpen(false)}
                    disabled={submitting}
                  >
                    {t('common.cancel')}
                  </Button>
                  <Button type="submit" disabled={submitting}>
                    {submitting ? t('common.saving') : t('common.save')}
                  </Button>
                </div>
              </form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {/* Advanced Filter Zone */}
      <div className="bg-white p-6 rounded-lg border shadow-sm space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Filter className="h-5 w-5 text-gray-500" />
            <h2 className="font-semibold text-lg">{t('customers.filters')}</h2>
          </div>
          <Button variant="ghost" size="sm" onClick={resetFilters}>
            {t('common.reset')}
          </Button>
        </div>

        {/* Search and Basic Filters */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="relative">
            <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
            <Input
              placeholder={`${t('common.search')}...`}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="pl-10"
            />
          </div>
          <select
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="px-3 py-2 border rounded-md text-sm bg-white"
          >
            <option value="all">{t('common.allStatuses')}</option>
            <option value="active">{t('customers.active')}</option>
            <option value="inactive">{t('restaurants.inactive')}</option>
          </select>
          <select
            value={registrationSourceFilter}
            onChange={(e) => setRegistrationSourceFilter(e.target.value)}
            className="px-3 py-2 border rounded-md text-sm bg-white"
          >
            <option value="all">{t('customers.allSources')}</option>
            <option value="ADMIN_PANEL">{t('customers.sources.adminPanel')}</option>
            <option value="TELEGRAM_BOT">{t('customers.sources.telegramBot')}</option>
            <option value="WEBSITE">{t('customers.sources.website')}</option>
            <option value="MOBILE_APP">{t('customers.sources.mobileApp')}</option>
            <option value="PHONE_CALL">{t('customers.sources.phoneCall')}</option>
            <option value="WALK_IN">{t('customers.sources.walkIn')}</option>
            <option value="OTHER">{t('customers.sources.other')}</option>
          </select>
        </div>

        {/* Date Range Filter */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label className="text-sm font-medium">{t('customers.startDate')}</Label>
            <Input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label className="text-sm font-medium">{t('customers.endDate')}</Label>
            <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
          </div>
        </div>

        {/* RFM Filters */}
        <div className="border-t pt-4">
          <h3 className="font-medium text-sm mb-3 text-gray-700">
            {t('customers.rfmFilters')}
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            {/* Recency Filter */}
            <div className="space-y-2">
              <Label className="text-sm font-medium flex items-center gap-2">
                <Clock className="h-4 w-4" />
                {t('customers.recency')} ({t('customers.days')})
              </Label>
              <div className="grid grid-cols-2 gap-2">
                <Input
                  type="number"
                  placeholder={t('common.min')}
                  value={minRecency}
                  onChange={(e) => setMinRecency(e.target.value)}
                />
                <Input
                  type="number"
                  placeholder={t('common.max')}
                  value={maxRecency}
                  onChange={(e) => setMaxRecency(e.target.value)}
                />
              </div>
            </div>

            {/* Frequency Filter */}
            <div className="space-y-2">
              <Label className="text-sm font-medium flex items-center gap-2">
                <ShoppingCart className="h-4 w-4" />
                {t('customers.frequency')} ({t('customers.orders')})
              </Label>
              <div className="grid grid-cols-2 gap-2">
                <Input
                  type="number"
                  placeholder={t('common.min')}
                  value={minFrequency}
                  onChange={(e) => setMinFrequency(e.target.value)}
                />
                <Input
                  type="number"
                  placeholder={t('common.max')}
                  value={maxFrequency}
                  onChange={(e) => setMaxFrequency(e.target.value)}
                />
              </div>
            </div>

            {/* Monetary Filter */}
            <div className="space-y-2">
              <Label className="text-sm font-medium flex items-center gap-2">
                <DollarSign className="h-4 w-4" />
                {t('customers.monetary')} ($)
              </Label>
              <div className="grid grid-cols-2 gap-2">
                <Input
                  type="number"
                  placeholder={t('common.min')}
                  value={minMonetary}
                  onChange={(e) => setMinMonetary(e.target.value)}
                  step="0.01"
                />
                <Input
                  type="number"
                  placeholder={t('common.max')}
                  value={maxMonetary}
                  onChange={(e) => setMaxMonetary(e.target.value)}
                  step="0.01"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Excel-Style Table */}
      <div className="bg-white rounded-lg border shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead className="bg-gray-50 border-b">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  ID
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.name')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.phone')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.averageCheck')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.totalAmount')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.recency')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.frequency')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.orderSources')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.registrationDate')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.segment')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.status')}
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredCustomers.length === 0 ? (
                <tr>
                  <td colSpan="11" className="px-4 py-8 text-center text-gray-500">
                    {t('common.noData')}
                  </td>
                </tr>
              ) : (
                filteredCustomers.map((customer) => (
                  <tr
                    key={customer.customerId}
                    className="hover:bg-gray-50 transition-colors cursor-pointer"
                  >
                    <td className="px-4 py-3 whitespace-nowrap text-sm font-medium text-gray-900">
                      #{customer.customerId}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="text-sm font-medium text-gray-900">
                        {customer.firstName} {customer.lastName}
                      </div>
                      <div className="text-sm text-gray-500">{customer.email}</div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                      {customer.phone}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                      ${customer.averageCheck?.toFixed(2) || '0.00'}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm font-medium text-gray-900">
                      ${customer.monetary?.toFixed(2) || '0.00'}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {customer.recency !== null ? (
                        <span
                          className={`px-2 py-1 text-xs font-medium rounded-full ${getRecencyBadgeColor(
                            customer.recency
                          )}`}
                        >
                          {customer.recency} {t('customers.daysAgo')}
                        </span>
                      ) : (
                        <span className="text-sm text-gray-500">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span className="px-2 py-1 text-xs font-medium rounded-full bg-blue-100 text-blue-800">
                        {customer.frequency || 0} {t('customers.orders')}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <div className="flex flex-wrap gap-1">
                        {customer.orderSources && customer.orderSources.length > 0 ? (
                          customer.orderSources.map((source, idx) => (
                            <span
                              key={idx}
                              className="px-2 py-1 text-xs font-medium rounded bg-indigo-100 text-indigo-800"
                            >
                              {t(`customers.sources.${source.toLowerCase().replace('_', '')}`)}
                            </span>
                          ))
                        ) : (
                          <span className="text-sm text-gray-500">-</span>
                        )}
                      </div>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                      {customer.registrationDate
                        ? format(new Date(customer.registrationDate), 'MMM dd, yyyy')
                        : '-'}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={`px-2 py-1 text-xs font-medium rounded-full ${getSegmentBadgeColor(
                          customer.rfmSegment
                        )}`}
                      >
                        {customer.rfmSegment}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      <span
                        className={`px-2 py-1 text-xs font-medium rounded-full ${
                          customer.active
                            ? 'bg-green-100 text-green-800'
                            : 'bg-gray-100 text-gray-800'
                        }`}
                      >
                        {customer.active ? t('customers.active') : t('restaurants.inactive')}
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
