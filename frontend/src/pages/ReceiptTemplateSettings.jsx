import { useState, useEffect } from 'react';
import { notifyError } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { receiptTemplateAPI } from '../services/api';
import { useAuthStore } from '../store/authStore';
import { generateReceiptHTML } from '../components/PrintReceipt';
import { Save, RefreshCw, Eye } from 'lucide-react';

// Demo order used only for the live preview
const PREVIEW_ORDER = {
  orderNumber: 'PRV-001',
  orderType: 'DINE_IN',
  diningTable: { tableNumber: '5' },
  waiter: { firstName: 'Ali', lastName: '' },
  items: [
    { productName: 'Osh', quantity: 2, unitPrice: 35000, totalPrice: 70000 },
    { productName: 'Limonad', quantity: 1, unitPrice: 15000, totalPrice: 15000 },
  ],
  total: 85000,
  deliveryFee: 0,
  serviceFee: 0,
  entryFee: 0,
  discount: 0,
  customerNotes: '',
};

const DEFAULT_TEMPLATE = {
  restaurantName: "Jangirov's",
  tagline: '',
  phone: '+998770049909',
  website: 'www.qahvoon.uz',
  footerMessage: '*** RAHMAT! ***',
  currency: 'UZS',
  showQrCode: true,
  qrUrl: 'https://qahvoon.uz/order/menu/1/TAKEAWAY',
  qrTitle: 'ONLINE BUYURTMA',
  qrSubtitle: 'Skanerlang va buyurtma bering',
  paperWidthMm: 58,
  kitchenHeaderText: '*** OSHXONA BUYURTMASI ***',
  kitchenFooterText: 'HOZIR TAYYORLANG!',
};

const Field = ({ label, hint, children }) => (
  <div className="mb-4">
    <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
    {children}
    {hint && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
  </div>
);

const Input = ({ value, onChange, placeholder, maxLength }) => (
  <input
    type="text"
    value={value || ''}
    onChange={(e) => onChange(e.target.value)}
    placeholder={placeholder}
    maxLength={maxLength}
    className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
  />
);

const ReceiptTemplateSettings = () => {
  const { t } = useTranslation();
  const { selectedRestaurantId } = useAuthStore();
  const [form, setForm] = useState(DEFAULT_TEMPLATE);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [previewHtml, setPreviewHtml] = useState('');

  const restaurantId = selectedRestaurantId || Number(localStorage.getItem('selectedRestaurantId')) || 1;

  // Load template from server
  useEffect(() => {
    setLoading(true);
    receiptTemplateAPI.getTemplate(restaurantId)
      .then(res => {
        if (res.data?.data) {
          setForm({ ...DEFAULT_TEMPLATE, ...res.data.data });
        }
      })
      .catch(() => {})
      .finally(() => setLoading(false));
  }, [restaurantId]);

  // Rebuild preview whenever form changes
  useEffect(() => {
    try {
      const html = generateReceiptHTML(PREVIEW_ORDER, form);
      setPreviewHtml(html);
    } catch (_) {}
  }, [form]);

  const set = (key) => (val) => setForm(f => ({ ...f, [key]: val }));
  const setCheck = (key) => (e) => setForm(f => ({ ...f, [key]: e.target.checked }));

  const handleSave = async () => {
    setSaving(true);
    try {
      await receiptTemplateAPI.saveTemplate(restaurantId, form);
      // Update localStorage cache so PrintReceipt picks it up immediately
      localStorage.setItem('receiptTemplate', JSON.stringify(form));
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch (err) {
      notifyError(err);
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <RefreshCw className="animate-spin h-6 w-6 text-gray-500" />
      </div>
    );
  }

  return (
    <div className="p-6 max-w-7xl mx-auto">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">{t('receiptTemplate.title')}</h1>
          <p className="text-sm text-gray-500 mt-1">{t('receiptTemplate.subtitle')}</p>
        </div>
        <button
          onClick={handleSave}
          disabled={saving}
          className={`flex items-center gap-2 px-5 py-2.5 rounded font-medium text-sm text-white transition-colors ${
            saved ? 'bg-green-600' : 'bg-blue-600 hover:bg-blue-700'
          } disabled:opacity-50`}
        >
          {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          {saved ? t('receiptTemplate.saved') : t('common.save')}
        </button>
      </div>

      <div className="flex gap-6">
        {/* ── Form ── */}
        <div className="flex-1 space-y-6">

          {/* Header */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">{t('receiptTemplate.sections.header')}</h2>
            <Field label={t('receiptTemplate.fields.restaurantName')} hint={t('receiptTemplate.fields.restaurantNameHint')}>
              <Input value={form.restaurantName} onChange={set('restaurantName')} placeholder="Jangirov's" maxLength={100} />
            </Field>
            <Field label={t('receiptTemplate.fields.tagline')} hint={t('receiptTemplate.fields.taglineHint')}>
              <Input value={form.tagline} onChange={set('tagline')} placeholder="Maza qiling!" maxLength={200} />
            </Field>
          </section>

          {/* Footer */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">{t('receiptTemplate.sections.footer')}</h2>
            <Field label={t('receiptTemplate.fields.phone')}>
              <Input value={form.phone} onChange={set('phone')} placeholder="+998770049909" maxLength={50} />
            </Field>
            <Field label={t('receiptTemplate.fields.website')}>
              <Input value={form.website} onChange={set('website')} placeholder="www.qahvoon.uz" maxLength={100} />
            </Field>
            <Field label={t('receiptTemplate.fields.footerMessage')}>
              <Input value={form.footerMessage} onChange={set('footerMessage')} placeholder="*** RAHMAT! ***" maxLength={200} />
            </Field>
            <Field label={t('receiptTemplate.fields.currency')} hint={t('receiptTemplate.fields.currencyHint')}>
              <Input value={form.currency} onChange={set('currency')} placeholder="UZS" maxLength={10} />
            </Field>
          </section>

          {/* QR Code */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">{t('receiptTemplate.sections.qr')}</h2>
            <Field label="">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.showQrCode !== false}
                  onChange={setCheck('showQrCode')}
                  className="h-4 w-4 accent-blue-600"
                />
                <span className="text-sm text-gray-700">{t('receiptTemplate.fields.showQr')}</span>
              </label>
            </Field>
            {form.showQrCode !== false && (
              <>
                <Field label={t('receiptTemplate.fields.qrUrl')} hint={t('receiptTemplate.fields.qrUrlHint')}>
                  <Input value={form.qrUrl} onChange={set('qrUrl')} placeholder="https://qahvoon.uz/menu" maxLength={500} />
                </Field>
                <Field label={t('receiptTemplate.fields.qrTitle')}>
                  <Input value={form.qrTitle} onChange={set('qrTitle')} placeholder="ONLINE BUYURTMA" maxLength={100} />
                </Field>
                <Field label={t('receiptTemplate.fields.qrSubtitle')}>
                  <Input value={form.qrSubtitle} onChange={set('qrSubtitle')} placeholder="Skanerlang va buyurtma bering" maxLength={100} />
                </Field>
              </>
            )}
          </section>

          {/* Paper & Kitchen */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">{t('receiptTemplate.sections.printing')}</h2>
            <Field label={t('receiptTemplate.fields.paperWidth')}>
              <select
                value={form.paperWidthMm || 58}
                onChange={(e) => set('paperWidthMm')(Number(e.target.value))}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value={58}>{t('receiptTemplate.paper.small')}</option>
                <option value={80}>{t('receiptTemplate.paper.large')}</option>
              </select>
            </Field>
            <Field label={t('receiptTemplate.fields.kitchenHeader')} hint={t('receiptTemplate.fields.kitchenHeaderHint')}>
              <Input value={form.kitchenHeaderText} onChange={set('kitchenHeaderText')} placeholder="*** OSHXONA BUYURTMASI ***" maxLength={100} />
            </Field>
            <Field label={t('receiptTemplate.fields.kitchenFooter')} hint={t('receiptTemplate.fields.kitchenFooterHint')}>
              <Input value={form.kitchenFooterText} onChange={set('kitchenFooterText')} placeholder="HOZIR TAYYORLANG!" maxLength={100} />
            </Field>
          </section>
        </div>

        {/* ── Live Preview ── */}
        <div className="w-80 flex-shrink-0">
          <div className="sticky top-6">
            <div className="flex items-center gap-2 mb-3 text-sm font-medium text-gray-700">
              <Eye className="h-4 w-4" />
              {t('receiptTemplate.preview.title')}
            </div>
            <div className="bg-gray-100 rounded-lg p-3 border">
              <div
                className="bg-white rounded shadow-sm overflow-auto"
                style={{ maxHeight: '80vh' }}
              >
                <iframe
                  srcDoc={previewHtml}
                  title="Receipt Preview"
                  style={{
                    width: '100%',
                    minHeight: '500px',
                    border: 'none',
                    display: 'block',
                  }}
                />
              </div>
            </div>
            <p className="text-xs text-gray-400 mt-2 text-center">{t('receiptTemplate.preview.note')}</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ReceiptTemplateSettings;
