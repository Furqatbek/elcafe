import { useState, useEffect } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
import { partnerAPI, restaurantAPI } from '../services/api';
import { copyToClipboard } from '../utils/passwordGenerator';
import { Plus, KeyRound, Copy, Check, Truck, Store, X, AlertTriangle } from 'lucide-react';
import { useTranslation } from 'react-i18next';

/**
 * Delivery-aggregator integrations (V187). SUPER_ADMIN only — a partner spans tenants, so managing
 * one is a platform decision rather than a restaurant's.
 *
 * <p>The screen is built around one awkward fact: an API key is shown exactly once. Only its hash is
 * stored, so there is no "view key" anywhere — the reveal panel below stays up until dismissed, and
 * the only recovery from a lost key is rotation, which kills the old one everywhere it is installed.
 */
const Partners = () => {
  const { t } = useTranslation();
  const [partners, setPartners] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showModal, setShowModal] = useState(false);
  const [copied, setCopied] = useState(false);

  // The one-time key reveal: { name, slug, apiKey }. Held in state, never refetched.
  const [revealedKey, setRevealedKey] = useState(null);

  const [formData, setFormData] = useState({ name: '', slug: '', contactEmail: '' });

  useEffect(() => {
    loadPartners();
    loadRestaurants();
  }, []);

  const loadPartners = async () => {
    setLoading(true);
    try {
      const response = await partnerAPI.getAll();
      setPartners(response.data.data || []);
    } catch (error) {
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll();
      const payload = response.data.data;
      setRestaurants(payload?.content || payload || []);
    } catch (error) {
      notifyError(error);
    }
  };

  const handleCreate = async () => {
    if (!formData.name.trim() || !formData.slug.trim()) {
      notifyWarning(t('partners.messages.fillRequiredFields', 'Name and slug are required'));
      return;
    }
    setLoading(true);
    try {
      const response = await partnerAPI.create(formData);
      setShowModal(false);
      setFormData({ name: '', slug: '', contactEmail: '' });
      setCopied(false);
      setRevealedKey(response.data.data);
      notifySuccess(t('partners.messages.createSuccess', 'Partner created'));
      loadPartners();
    } catch (error) {
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleRotate = async (partner) => {
    if (!confirm(t('partners.messages.confirmRotate',
      'Rotate this key? The current key stops working immediately, everywhere it is installed.'))) {
      return;
    }
    try {
      const response = await partnerAPI.rotateKey(partner.id);
      setCopied(false);
      setRevealedKey(response.data.data);
      notifySuccess(t('partners.messages.rotateSuccess', 'API key rotated'));
      loadPartners();
    } catch (error) {
      notifyError(error);
    }
  };

  const handleToggleActive = async (partner) => {
    try {
      await partnerAPI.setActive(partner.id, !partner.active);
      loadPartners();
    } catch (error) {
      notifyError(error);
    }
  };

  const handleSavePricing = async (partner, grant, pricing) => {
    try {
      // Re-granting is the update path: capabilities are sent unchanged so saving a markup can never
      // quietly widen or narrow what the partner is allowed to do.
      await partnerAPI.grantRestaurant(partner.id, grant.restaurantId, {
        canReadMenu: grant.canReadMenu,
        canPushOrders: grant.canPushOrders,
        ...pricing,
      });
      notifySuccess(t('partners.messages.pricingSaved', 'Channel pricing updated'));
      loadPartners();
    } catch (error) {
      notifyError(error);
    }
  };

  const handleGrant = async (partner, restaurantId, capabilities) => {
    if (!restaurantId) return;
    try {
      await partnerAPI.grantRestaurant(partner.id, restaurantId, capabilities);
      notifySuccess(t('partners.messages.grantSuccess', 'Venue access updated'));
      loadPartners();
    } catch (error) {
      notifyError(error);
    }
  };

  const handleRevoke = async (partner, grant) => {
    if (!confirm(t('partners.messages.confirmRevoke',
      'Revoke this venue? The partner immediately loses access to its menu and can no longer send it orders.'))) {
      return;
    }
    try {
      await partnerAPI.revokeRestaurant(partner.id, grant.restaurantId);
      notifySuccess(t('partners.messages.revokeSuccess', 'Venue revoked'));
      loadPartners();
    } catch (error) {
      notifyError(error);
    }
  };

  const handleRetryDeadLetters = async (partner) => {
    try {
      await partnerAPI.retryDeadLetters(partner.id);
      notifySuccess(t('partners.messages.retryQueued', 'Undelivered messages requeued'));
      loadPartners();
    } catch (error) {
      notifyError(error);
    }
  };

  const handleCopyKey = async () => {
    if (!revealedKey?.apiKey) return;
    await copyToClipboard(revealedKey.apiKey);
    setCopied(true);
  };

  const ungrantedRestaurants = (partner) => {
    const active = new Set(
      (partner.restaurants || []).filter((g) => g.active).map((g) => g.restaurantId)
    );
    return restaurants.filter((r) => !active.has(r.id));
  };

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Truck className="w-6 h-6" />
            {t('partners.title', 'Delivery Partners')}
          </h1>
          <p className="text-sm text-gray-600 mt-1">
            {t('partners.description',
              'Delivery aggregators that pull your menu and send you orders over the partner API')}
          </p>
        </div>
        <button
          onClick={() => setShowModal(true)}
          className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700"
        >
          <Plus className="w-4 h-4" />
          {t('partners.addPartner', 'Add Partner')}
        </button>
      </div>

      {revealedKey && (
        <div className="mb-6 border border-amber-300 bg-amber-50 rounded-lg p-4">
          <div className="flex items-start justify-between">
            <div className="flex-1">
              <h2 className="font-semibold text-amber-900 flex items-center gap-2">
                <KeyRound className="w-4 h-4" />
                {t('partners.keyTitle', 'API key for {{name}}', { name: revealedKey.name })}
              </h2>
              <p className="text-sm text-amber-800 mt-1">
                {t('partners.keyWarning',
                  'Copy this now. Only a hash is stored, so it can never be shown again — if it is lost, the only option is to rotate.')}
              </p>
              <div className="mt-3 flex items-center gap-2">
                <code
                  data-testid="revealed-api-key"
                  className="flex-1 px-3 py-2 bg-white border border-amber-300 rounded font-mono text-sm break-all"
                >
                  {revealedKey.apiKey}
                </code>
                <button
                  onClick={handleCopyKey}
                  className="flex items-center gap-1 px-3 py-2 bg-amber-600 text-white rounded hover:bg-amber-700"
                >
                  {copied ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  {copied ? t('partners.copied', 'Copied') : t('partners.copy', 'Copy')}
                </button>
              </div>
            </div>
            <button
              onClick={() => setRevealedKey(null)}
              aria-label={t('partners.dismissKey', 'Dismiss')}
              className="ml-4 text-amber-700 hover:text-amber-900"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-lg shadow overflow-hidden">
        <table className="min-w-full divide-y divide-gray-200">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('partners.name', 'Partner')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('partners.keyPrefix', 'Key')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('partners.venues', 'Venues')}
              </th>
              <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase">
                {t('partners.status', 'Status')}
              </th>
              <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 uppercase">
                {t('finance.common.actions', 'Actions')}
              </th>
            </tr>
          </thead>
          <tbody className="bg-white divide-y divide-gray-200">
            {loading && partners.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                  {t('finance.common.loading', 'Loading...')}
                </td>
              </tr>
            ) : partners.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-8 text-center text-gray-500">
                  {t('partners.empty', 'No delivery partners yet')}
                </td>
              </tr>
            ) : (
              partners.map((partner) => (
                <PartnerRow
                  key={partner.id}
                  partner={partner}
                  availableRestaurants={ungrantedRestaurants(partner)}
                  onToggleActive={handleToggleActive}
                  onRotate={handleRotate}
                  onGrant={handleGrant}
                  onRevoke={handleRevoke}
                  onSavePricing={handleSavePricing}
                  onRetryDeadLetters={handleRetryDeadLetters}
                  t={t}
                />
              ))
            )}
          </tbody>
        </table>
      </div>

      {showModal && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md">
            <div className="px-6 py-4 border-b">
              <h2 className="text-lg font-semibold">{t('partners.addPartner', 'Add Partner')}</h2>
            </div>
            <div className="px-6 py-4 space-y-4">
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('partners.name', 'Partner')} *
                </label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  placeholder={t('partners.namePlaceholder', 'e.g. Wolt, Yandex Eats')}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('partners.slug', 'Slug')} *
                </label>
                <input
                  type="text"
                  value={formData.slug}
                  onChange={(e) => setFormData({ ...formData, slug: e.target.value })}
                  placeholder={t('partners.slugPlaceholder', 'lowercase-handle')}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
                <p className="text-xs text-gray-500 mt-1">
                  {t('partners.slugHelp',
                    'Permanent. It tags their orders and payment records, so it cannot be changed later.')}
                </p>
              </div>
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  {t('partners.contactEmail', 'Contact email')}
                </label>
                <input
                  type="email"
                  value={formData.contactEmail}
                  onChange={(e) => setFormData({ ...formData, contactEmail: e.target.value })}
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg"
                />
              </div>
            </div>
            <div className="px-6 py-4 border-t flex justify-end gap-2">
              <button
                onClick={() => setShowModal(false)}
                className="px-4 py-2 border border-gray-300 rounded-lg hover:bg-gray-50"
              >
                {t('finance.common.cancel', 'Cancel')}
              </button>
              <button
                onClick={handleCreate}
                disabled={loading}
                className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {loading ? t('finance.common.saving', 'Saving...') : t('finance.common.save', 'Save')}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

/** One partner, with its venue grants expanded inline so access is visible without a second screen. */
const PartnerRow = ({ partner, availableRestaurants, onToggleActive, onRotate, onGrant, onRevoke,
  onSavePricing, onRetryDeadLetters, t }) => {
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [canPushOrders, setCanPushOrders] = useState(false);

  const activeGrants = (partner.restaurants || []).filter((g) => g.active);

  const submitGrant = () => {
    onGrant(partner, Number(selectedRestaurant), { canReadMenu: true, canPushOrders });
    setSelectedRestaurant('');
    setCanPushOrders(false);
  };

  return (
    <tr>
      <td className="px-6 py-4 align-top">
        <div className="font-medium text-gray-900">{partner.name}</div>
        <div className="text-sm text-gray-500 font-mono">{partner.slug}</div>
        {partner.contactEmail && (
          <div className="text-sm text-gray-500">{partner.contactEmail}</div>
        )}
        {partner.deadLetteredEvents > 0 && (
          // Surfaced on the row rather than behind a screen: an undelivered message means this
          // partner has stopped hearing something we promised to tell them, and nobody goes looking
          // for a problem they have not been shown.
          <button
            onClick={() => onRetryDeadLetters(partner)}
            className="mt-1 inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-xs bg-red-100 text-red-800 hover:bg-red-200"
          >
            <AlertTriangle className="w-3 h-3" />
            {t('partners.undelivered', '{{count}} undelivered — retry', {
              count: partner.deadLetteredEvents,
            })}
          </button>
        )}
        {partner.pendingEvents > 0 && (
          <div className="text-xs text-gray-500 mt-1">
            {t('partners.queued', '{{count}} queued', { count: partner.pendingEvents })}
          </div>
        )}
      </td>
      <td className="px-6 py-4 align-top">
        <code className="text-sm text-gray-600">{partner.apiKeyPrefix}…</code>
      </td>
      <td className="px-6 py-4 align-top">
        {activeGrants.length === 0 ? (
          <span className="text-sm text-gray-500">{t('partners.noVenues', 'No venues granted')}</span>
        ) : (
          <ul className="space-y-1">
            {activeGrants.map((grant) => (
              <li key={grant.restaurantId} className="flex items-center gap-2 text-sm">
                <Store className="w-3 h-3 text-gray-400" />
                <span>{grant.restaurantName || `#${grant.restaurantId}`}</span>
                <span
                  className={`px-1.5 py-0.5 rounded text-xs ${
                    grant.canPushOrders
                      ? 'bg-green-100 text-green-800'
                      : 'bg-gray-100 text-gray-700'
                  }`}
                >
                  {grant.canPushOrders
                    ? t('partners.capabilityOrders', 'menu + orders')
                    : t('partners.capabilityMenuOnly', 'menu only')}
                </span>
                <button
                  onClick={() => onRevoke(partner, grant)}
                  className="text-red-600 hover:text-red-800 text-xs"
                >
                  {t('partners.revoke', 'Revoke')}
                </button>
                <PricingEditor
                  grant={grant}
                  onSave={(pricing) => onSavePricing(partner, grant, pricing)}
                  t={t}
                />
              </li>
            ))}
          </ul>
        )}

        {availableRestaurants.length > 0 && (
          <div className="mt-2 flex flex-wrap items-center gap-2">
            <select
              value={selectedRestaurant}
              onChange={(e) => setSelectedRestaurant(e.target.value)}
              aria-label={t('partners.grantVenue', 'Grant a venue')}
              className="text-sm border border-gray-300 rounded px-2 py-1"
            >
              <option value="">{t('partners.grantVenue', 'Grant a venue')}</option>
              {availableRestaurants.map((restaurant) => (
                <option key={restaurant.id} value={restaurant.id}>
                  {restaurant.name}
                </option>
              ))}
            </select>
            <label className="flex items-center gap-1 text-xs text-gray-600">
              <input
                type="checkbox"
                checked={canPushOrders}
                onChange={(e) => setCanPushOrders(e.target.checked)}
              />
              {t('partners.allowOrders', 'Allow orders')}
            </label>
            <button
              onClick={submitGrant}
              disabled={!selectedRestaurant}
              className="text-xs px-2 py-1 bg-blue-600 text-white rounded disabled:opacity-40"
            >
              {t('partners.grant', 'Grant')}
            </button>
          </div>
        )}
      </td>
      <td className="px-6 py-4 align-top">
        <button
          onClick={() => onToggleActive(partner)}
          className={`px-2 py-1 rounded-full text-xs font-medium ${
            partner.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'
          }`}
        >
          {partner.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
        </button>
      </td>
      <td className="px-6 py-4 align-top text-right">
        <button
          onClick={() => onRotate(partner)}
          className="inline-flex items-center gap-1 text-sm text-blue-600 hover:text-blue-800"
        >
          <KeyRound className="w-4 h-4" />
          {t('partners.rotateKey', 'Rotate key')}
        </button>
      </td>
    </tr>
  );
};

/**
 * The channel markup for one venue: how much more (or less) this partner's customers pay than someone
 * standing at the counter. An aggregator takes a commission, so this is where the owner recovers it.
 *
 * Collapsed by default — most venues set it once and never look again, and an always-open form on
 * every row buries the access information the list is actually for.
 */
const PricingEditor = ({ grant, onSave, t }) => {
  const [open, setOpen] = useState(false);
  const [type, setType] = useState(grant.priceAdjustmentType || 'NONE');
  const [value, setValue] = useState(grant.priceAdjustmentValue ?? 0);
  const [rounding, setRounding] = useState(grant.priceRounding ?? 0);

  const summary = () => {
    if (!grant.priceAdjustmentType || grant.priceAdjustmentType === 'NONE') {
      return t('partners.pricingBase', 'base price');
    }
    const amount = Number(grant.priceAdjustmentValue ?? 0);
    const sign = amount < 0 ? '' : '+';
    return grant.priceAdjustmentType === 'PERCENT'
      ? `${sign}${amount}%`
      : `${sign}${amount}`;
  };

  const save = () => {
    onSave({
      priceAdjustmentType: type,
      priceAdjustmentValue: type === 'NONE' ? 0 : Number(value),
      priceRounding: Number(rounding) || 0,
    });
    setOpen(false);
  };

  return (
    <>
      <button
        onClick={() => setOpen((o) => !o)}
        className="text-xs px-1.5 py-0.5 rounded bg-indigo-100 text-indigo-800 hover:bg-indigo-200"
      >
        {summary()}
      </button>
      {open && (
        <div className="mt-1 ml-5 flex flex-wrap items-center gap-2 text-xs">
          <select
            value={type}
            onChange={(e) => setType(e.target.value)}
            aria-label={t('partners.markupType', 'Markup type')}
            className="border border-gray-300 rounded px-2 py-1"
          >
            <option value="NONE">{t('partners.markupNone', 'Base price')}</option>
            <option value="PERCENT">{t('partners.markupPercent', 'Percent (%)')}</option>
            <option value="AMOUNT">{t('partners.markupAmount', 'Fixed amount')}</option>
          </select>
          {type !== 'NONE' && (
            <input
              type="number"
              step="0.01"
              value={value}
              onChange={(e) => setValue(e.target.value)}
              aria-label={t('partners.markupValue', 'Markup')}
              className="w-24 border border-gray-300 rounded px-2 py-1"
            />
          )}
          <input
            type="number"
            step="1"
            min="0"
            value={rounding}
            onChange={(e) => setRounding(e.target.value)}
            aria-label={t('partners.markupRounding', 'Round to nearest')}
            placeholder={t('partners.markupRounding', 'Round to nearest')}
            className="w-28 border border-gray-300 rounded px-2 py-1"
          />
          <button
            onClick={save}
            className="px-2 py-1 bg-blue-600 text-white rounded"
          >
            {t('finance.common.save', 'Save')}
          </button>
          <span className="text-gray-500">
            {t('partners.markupHelp',
              'Applies only to this partner. Counter prices are unchanged.')}
          </span>
        </div>
      )}
    </>
  );
};

export default Partners;
