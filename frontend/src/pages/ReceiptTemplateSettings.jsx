import { useState, useEffect } from 'react';
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
  website: 'www.jangirovs.uz',
  footerMessage: '*** RAHMAT! ***',
  currency: 'UZS',
  showQrCode: true,
  qrUrl: 'https://jangirovs.uz/order/menu/1/TAKEAWAY',
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
      alert('Saqlashda xato: ' + (err?.response?.data?.message || err.message));
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
          <h1 className="text-2xl font-bold text-gray-900">Chek Shabloni</h1>
          <p className="text-sm text-gray-500 mt-1">Chekda ko'rinadigan ma'lumotlarni tahrirlang</p>
        </div>
        <button
          onClick={handleSave}
          disabled={saving}
          className={`flex items-center gap-2 px-5 py-2.5 rounded font-medium text-sm text-white transition-colors ${
            saved ? 'bg-green-600' : 'bg-blue-600 hover:bg-blue-700'
          } disabled:opacity-50`}
        >
          {saving ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
          {saved ? 'Saqlandi ✓' : 'Saqlash'}
        </button>
      </div>

      <div className="flex gap-6">
        {/* ── Form ── */}
        <div className="flex-1 space-y-6">

          {/* Header */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">Sarlavha</h2>
            <Field label="Restoran nomi" hint="Chekda ko'rinadigan katta sarlavha">
              <Input value={form.restaurantName} onChange={set('restaurantName')} placeholder="Jangirov's" maxLength={100} />
            </Field>
            <Field label="Tagline / Shior" hint="Ixtiyoriy — nom ostida kichik matn">
              <Input value={form.tagline} onChange={set('tagline')} placeholder="Maza qiling!" maxLength={200} />
            </Field>
          </section>

          {/* Footer */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">Alt qism (Footer)</h2>
            <Field label="Telefon raqami">
              <Input value={form.phone} onChange={set('phone')} placeholder="+998770049909" maxLength={50} />
            </Field>
            <Field label="Veb-sayt">
              <Input value={form.website} onChange={set('website')} placeholder="www.jangirovs.uz" maxLength={100} />
            </Field>
            <Field label="Rahmat matni">
              <Input value={form.footerMessage} onChange={set('footerMessage')} placeholder="*** RAHMAT! ***" maxLength={200} />
            </Field>
            <Field label="Valyuta" hint="Chekdagi narxlar yonida ko'rsatiladi">
              <Input value={form.currency} onChange={set('currency')} placeholder="UZS" maxLength={10} />
            </Field>
          </section>

          {/* QR Code */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">QR Kod</h2>
            <Field label="">
              <label className="flex items-center gap-2 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.showQrCode !== false}
                  onChange={setCheck('showQrCode')}
                  className="h-4 w-4 accent-blue-600"
                />
                <span className="text-sm text-gray-700">Chekda QR kod ko'rsatilsin</span>
              </label>
            </Field>
            {form.showQrCode !== false && (
              <>
                <Field label="QR URL" hint="Mijozlar skaner qilganda ochadigan havola">
                  <Input value={form.qrUrl} onChange={set('qrUrl')} placeholder="https://jangirovs.uz/menu" maxLength={500} />
                </Field>
                <Field label="QR sarlavhasi">
                  <Input value={form.qrTitle} onChange={set('qrTitle')} placeholder="ONLINE BUYURTMA" maxLength={100} />
                </Field>
                <Field label="QR izoh matni">
                  <Input value={form.qrSubtitle} onChange={set('qrSubtitle')} placeholder="Skanerlang va buyurtma bering" maxLength={100} />
                </Field>
              </>
            )}
          </section>

          {/* Paper & Kitchen */}
          <section className="bg-white rounded-lg border p-5">
            <h2 className="text-base font-semibold text-gray-800 mb-4">Bosib chiqarish</h2>
            <Field label="Qog'oz kengligi (mm)">
              <select
                value={form.paperWidthMm || 58}
                onChange={(e) => set('paperWidthMm')(Number(e.target.value))}
                className="w-full border border-gray-300 rounded px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              >
                <option value={58}>58mm (kichik termal)</option>
                <option value={80}>80mm (katta termal)</option>
              </select>
            </Field>
            <Field label="Oshxona cheki sarlavhasi" hint="Oshxona printer chiqaradigan sarlavha">
              <Input value={form.kitchenHeaderText} onChange={set('kitchenHeaderText')} placeholder="*** OSHXONA BUYURTMASI ***" maxLength={100} />
            </Field>
            <Field label="Oshxona cheki alt matni" hint="Oshxona printer chiqaradigan quyi matn">
              <Input value={form.kitchenFooterText} onChange={set('kitchenFooterText')} placeholder="HOZIR TAYYORLANG!" maxLength={100} />
            </Field>
          </section>
        </div>

        {/* ── Live Preview ── */}
        <div className="w-80 flex-shrink-0">
          <div className="sticky top-6">
            <div className="flex items-center gap-2 mb-3 text-sm font-medium text-gray-700">
              <Eye className="h-4 w-4" />
              Jonli ko'rinish
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
            <p className="text-xs text-gray-400 mt-2 text-center">Namuna ma'lumotlar bilan ko'rsatilmoqda</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default ReceiptTemplateSettings;
