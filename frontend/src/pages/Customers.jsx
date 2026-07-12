import { useEffect, useState } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
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
  Gift,
  Coins,
  ShoppingCart,
  Users,
  Copy,
  QrCode,
  RefreshCw,
} from 'lucide-react';
import { format } from 'date-fns';

export default function Customers() {
  const { t } = useTranslation();
  const [customers, setCustomers] = useState([]);
  const [filteredCustomers, setFilteredCustomers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
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
    referralCode: '',
  });
  const [formErrors, setFormErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  const formatPrice = (price) => {
    if (price === null || price === undefined) return '0.00';
    return new Intl.NumberFormat('en-US', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(price);
  };

  const copyToClipboard = (text, e) => {
    e.stopPropagation();
    navigator.clipboard.writeText(text);
  };

  const handleRegenerateQr = async (customer, e) => {
    e.stopPropagation();
    const confirmMsg = t(
      'customers.qrRegenerateConfirm',
      'Rotate {{name}}\'s loyalty QR code? The old code will stop working.',
      { name: `${customer.firstName} ${customer.lastName}`.trim() }
    );
    if (!window.confirm(confirmMsg)) return;
    try {
      await customerAPI.regenerateQrCode(customer.id);
      loadCustomers();
    } catch (err) {
      console.error('Failed to regenerate QR code:', err);
      notifyError(err);
    }
  };

  useEffect(() => {
    loadCustomers();
  }, []);

  useEffect(() => {
    filterCustomers();
  }, [customers, searchTerm, statusFilter]);

  const loadCustomers = async () => {
    try {
      const response = await customerAPI.getAll({ page: 0, size: 1000, sort: 'createdAt,desc' });
      const data = response.data.data.content || [];
      setCustomers(data);
      setFilteredCustomers(data);
    } catch (error) {
      console.error('Failed to load customers:', error);
    } finally {
      setLoading(false);
    }
  };

  const filterCustomers = () => {
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

    setFilteredCustomers(filtered);
  };

  const exportToCSV = () => {
    const headers = [
      t('pages.customers.id', 'ID'),
      t('pages.customers.firstName', 'First Name'),
      t('pages.customers.lastName', 'Last Name'),
      t('pages.customers.email', 'Email'),
      t('pages.customers.phone', 'Phone'),
      t('customers.qrCode', 'Loyalty QR'),
      t('pages.customers.address', 'Address'),
      t('pages.customers.city', 'City'),
      t('pages.customers.state', 'State'),
      t('pages.customers.zipCode', 'ZIP Code'),
      t('pages.customers.tags', 'Tags'),
      t('customers.bonusBalance', 'Bonus Balance'),
      t('customers.totalSpent', 'Total Spent'),
      t('customers.orders', 'Orders'),
      t('customers.referralCode', 'Referral Code'),
      t('customers.referrals', 'Referrals'),
      t('pages.customers.status', 'Status'),
      t('pages.customers.createdAt', 'Created At'),
    ];

    const csvData = filteredCustomers.map((customer) => [
      customer.id,
      customer.firstName,
      customer.lastName,
      customer.email,
      customer.phone,
      customer.qrCode || '',
      customer.defaultAddress,
      customer.city,
      customer.state,
      customer.zipCode,
      customer.tags,
      customer.bonusBalance || 0,
      customer.totalSpent || 0,
      customer.orderCount || 0,
      customer.referralCode || '',
      customer.referralSuccessCount || 0,
      customer.active ? 'Active' : 'Inactive',
      customer.createdAt ? format(new Date(customer.createdAt), 'yyyy-MM-dd HH:mm:ss') : '',
    ]);

    const csv = [headers, ...csvData].map((row) => row.join(',')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `customers_${format(new Date(), 'yyyy-MM-dd_HHmmss')}.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  };

  const validateForm = () => {
    const errors = {};
    if (!formData.firstName.trim()) errors.firstName = t('validation.required');
    if (!formData.lastName.trim()) errors.lastName = t('validation.required');
    if (formData.email && !/\S+@\S+\.\S+/.test(formData.email)) {
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
        referralCode: '',
      });
      setFormErrors({});
      loadCustomers();
      notifySuccess(t('messages.createSuccess'));
    } catch (error) {
      console.error('Failed to create customer:', error);
      notifyWarning(t('messages.error'));
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return <div className="flex items-center justify-center h-96">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('customers.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {filteredCustomers.length} {t('customers.allCustomers')}
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
                <DialogTitle>{t('customers.createCustomer')}</DialogTitle>
                <DialogDescription>
                  {t('customers.fillCustomerDetails')}
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={handleSubmit} className="space-y-4">
                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="firstName">{t('customers.firstName')} *</Label>
                    <Input
                      id="firstName"
                      value={formData.firstName}
                      onChange={(e) => setFormData({ ...formData, firstName: e.target.value })}
                      className={formErrors.firstName ? 'border-red-500' : ''}
                    />
                    {formErrors.firstName && (
                      <p className="text-sm text-red-600">{formErrors.firstName}</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="lastName">{t('customers.lastName')} *</Label>
                    <Input
                      id="lastName"
                      value={formData.lastName}
                      onChange={(e) => setFormData({ ...formData, lastName: e.target.value })}
                      className={formErrors.lastName ? 'border-red-500' : ''}
                    />
                    {formErrors.lastName && (
                      <p className="text-sm text-red-600">{formErrors.lastName}</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="email">{t('customers.email')}</Label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                      <Input
                        id="email"
                        type="email"
                        value={formData.email}
                        onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                        className={`pl-10 ${formErrors.email ? 'border-red-500' : ''}`}
                      />
                    </div>
                    {formErrors.email && (
                      <p className="text-sm text-red-600">{formErrors.email}</p>
                    )}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="phone">{t('customers.phone')} *</Label>
                    <div className="relative">
                      <Phone className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                      <Input
                        id="phone"
                        value={formData.phone}
                        onChange={(e) => setFormData({ ...formData, phone: e.target.value })}
                        className={`pl-10 ${formErrors.phone ? 'border-red-500' : ''}`}
                      />
                    </div>
                    {formErrors.phone && (
                      <p className="text-sm text-red-600">{formErrors.phone}</p>
                    )}
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="defaultAddress">{t('customers.defaultAddress')}</Label>
                  <div className="relative">
                    <MapPin className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <Input
                      id="defaultAddress"
                      value={formData.defaultAddress}
                      onChange={(e) => setFormData({ ...formData, defaultAddress: e.target.value })}
                      className="pl-10"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-3 gap-4">
                  <div className="space-y-2">
                    <Label htmlFor="city">{t('customers.city')}</Label>
                    <Input
                      id="city"
                      value={formData.city}
                      onChange={(e) => setFormData({ ...formData, city: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="state">{t('customers.state')}</Label>
                    <Input
                      id="state"
                      value={formData.state}
                      onChange={(e) => setFormData({ ...formData, state: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="zipCode">{t('customers.zipCode')}</Label>
                    <Input
                      id="zipCode"
                      value={formData.zipCode}
                      onChange={(e) => setFormData({ ...formData, zipCode: e.target.value })}
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tags">{t('customers.tags')}</Label>
                  <div className="relative">
                    <Tag className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <Input
                      id="tags"
                      value={formData.tags}
                      onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                      placeholder={t("common.placeholders.tags")}
                      className="pl-10"
                    />
                  </div>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="registrationSource">{t('customers.registrationSource')}</Label>
                  <select
                    id="registrationSource"
                    value={formData.registrationSource}
                    onChange={(e) => setFormData({ ...formData, registrationSource: e.target.value })}
                    className="w-full px-3 py-2 border rounded-md"
                  >
                    <option value="ADMIN_PANEL">{t('customers.sources.adminpanel')}</option>
                    <option value="TELEGRAM_BOT">{t('customers.sources.telegrambot')}</option>
                    <option value="WEBSITE">{t('customers.sources.website')}</option>
                    <option value="MOBILE_APP">{t('customers.sources.mobileapp')}</option>
                    <option value="PHONE_CALL">{t('customers.sources.phonecall')}</option>
                    <option value="WALK_IN">{t('customers.sources.walkin')}</option>
                    <option value="OTHER">{t('customers.sources.other')}</option>
                  </select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="referralCode">{t('customers.referralCode')}</Label>
                  <div className="relative">
                    <Gift className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <Input
                      id="referralCode"
                      value={formData.referralCode}
                      onChange={(e) => setFormData({ ...formData, referralCode: e.target.value.toUpperCase() })}
                      placeholder={t('customers.referralCodePlaceholder')}
                      className="pl-10 uppercase"
                      maxLength={10}
                    />
                  </div>
                  <p className="text-xs text-muted-foreground">{t('customers.referralCodeHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="notes">{t('customers.notes')}</Label>
                  <div className="relative">
                    <FileText className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
                    <textarea
                      id="notes"
                      value={formData.notes}
                      onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                      className="flex min-h-[80px] w-full rounded-md border border-input bg-background px-3 py-2 pl-10 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50"
                      rows={3}
                    />
                  </div>
                </div>

                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="active"
                    checked={formData.active}
                    onChange={(e) => setFormData({ ...formData, active: e.target.checked })}
                    className="h-4 w-4 rounded border-gray-300"
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
                  >
                    {t('common.cancel')}
                  </Button>
                  <Button type="submit" disabled={submitting}>
                    {submitting ? t('common.loading') : t('common.save')}
                  </Button>
                </div>
              </form>
            </DialogContent>
          </Dialog>
        </div>
      </div>

      {/* Filter Zone */}
      <div className="bg-white p-4 rounded-lg border shadow-sm">
        <div className="flex items-center gap-4">
          <div className="flex-1">
            <div className="relative">
              <Search className="absolute left-3 top-3 h-4 w-4 text-gray-400" />
              <Input
                placeholder={`${t('common.search')}...`}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-10"
              />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Filter className="h-4 w-4 text-gray-500" />
            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="px-3 py-2 border rounded-md text-sm bg-white"
            >
              <option value="all">{t('common.all')}</option>
              <option value="active">{t('customers.active')}</option>
              <option value="inactive">{t('restaurants.inactive')}</option>
            </select>
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
                  {t('customers.firstName')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.lastName')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.phone')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  <div className="flex items-center gap-1">
                    <QrCode className="h-3 w-3" />
                    {t('customers.qrCode', 'Loyalty QR')}
                  </div>
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  <div className="flex items-center gap-1">
                    <Coins className="h-3 w-3" />
                    {t('customers.bonusBalance', 'Bonus')}
                  </div>
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  <div className="flex items-center gap-1">
                    <ShoppingCart className="h-3 w-3" />
                    {t('customers.totalSpent', 'Total Spent')}
                  </div>
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('customers.orders', 'Orders')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  <div className="flex items-center gap-1">
                    <Gift className="h-3 w-3" />
                    {t('customers.referralCode', 'Promo Code')}
                  </div>
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  <div className="flex items-center gap-1">
                    <Users className="h-3 w-3" />
                    {t('customers.referrals', 'Referrals')}
                  </div>
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('common.status')}
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  {t('orders.createdAt')}
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredCustomers.length === 0 ? (
                <tr>
                  <td colSpan={12} className="px-4 py-8 text-center text-gray-500">
                    {t('common.noData')}
                  </td>
                </tr>
              ) : (
                filteredCustomers.map((customer) => (
                  <tr
                    key={customer.id}
                    className="hover:bg-gray-50 transition-colors cursor-pointer"
                  >
                    <td className="px-4 py-3 whitespace-nowrap text-sm font-medium text-gray-900">
                      #{customer.id}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                      {customer.firstName}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-900">
                      {customer.lastName}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-600">
                      {customer.phone}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm">
                      {customer.qrCode ? (
                        <div className="flex items-center gap-1">
                          <code className="px-2 py-1 bg-gray-100 rounded text-xs font-mono">
                            {customer.qrCode}
                          </code>
                          <button
                            onClick={(e) => copyToClipboard(customer.qrCode, e)}
                            className="p-1 hover:bg-gray-100 rounded"
                            title={t('common.copy', 'Copy')}
                          >
                            <Copy className="h-3 w-3 text-gray-400" />
                          </button>
                          <button
                            onClick={(e) => handleRegenerateQr(customer, e)}
                            className="p-1 hover:bg-gray-100 rounded"
                            title={t('customers.qrRegenerate', 'Regenerate QR code')}
                          >
                            <RefreshCw className="h-3 w-3 text-gray-400" />
                          </button>
                        </div>
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm">
                      {customer.bonusBalance > 0 ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                          <Coins className="h-3 w-3 mr-1" />
                          {formatPrice(customer.bonusBalance)}
                        </span>
                      ) : (
                        <span className="text-gray-400">0.00</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-600">
                      {formatPrice(customer.totalSpent)}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-center">
                      <span className="inline-flex items-center justify-center w-8 h-8 rounded-full bg-blue-50 text-blue-700 font-medium">
                        {customer.orderCount || 0}
                      </span>
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm">
                      {customer.referralCode ? (
                        <div className="flex items-center gap-1">
                          <code className="px-2 py-1 bg-gray-100 rounded text-xs font-mono">
                            {customer.referralCode}
                          </code>
                          <button
                            onClick={(e) => copyToClipboard(customer.referralCode, e)}
                            className="p-1 hover:bg-gray-100 rounded"
                            title={t('common.copy', 'Copy')}
                          >
                            <Copy className="h-3 w-3 text-gray-400" />
                          </button>
                        </div>
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-center">
                      {(customer.referralSuccessCount || 0) > 0 ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-purple-100 text-purple-800">
                          <Users className="h-3 w-3 mr-1" />
                          {customer.referralSuccessCount}
                        </span>
                      ) : (
                        <span className="text-gray-400">0</span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap">
                      {customer.active ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
                          {t('customers.active')}
                        </span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                          {t('restaurants.inactive')}
                        </span>
                      )}
                    </td>
                    <td className="px-4 py-3 whitespace-nowrap text-sm text-gray-600">
                      {customer.createdAt
                        ? format(new Date(customer.createdAt), 'MMM dd, yyyy')
                        : '-'}
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
