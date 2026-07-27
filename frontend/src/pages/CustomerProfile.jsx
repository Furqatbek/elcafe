import { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { customerAPI } from '../services/api';
import { notifyError, notifySuccess } from '../lib/errors';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import {
  ArrowLeft, Instagram, Send, MessageSquare, Plus, Trash2, Cake, Phone, Mail, Star,
} from 'lucide-react';

/** Allergies are safety information, not taste — they get the loudest colour in the list. */
const PREFERENCE_STYLES = {
  LIKE:    'bg-green-100 text-green-800',
  DISLIKE: 'bg-orange-100 text-orange-800',
  ALLERGY: 'bg-red-100 text-red-800',
  DIETARY: 'bg-blue-100 text-blue-800',
};

const CHANNEL_ICONS = {
  INSTAGRAM: Instagram,
  TELEGRAM: Send,
  SMS: MessageSquare,
};

const TIMELINE_PAGE = 20;

export default function CustomerProfile() {
  const { customerId } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  // Conversation history is paged separately from the profile — see the backend service for why.
  const [entries, setEntries] = useState([]);
  const [cursor, setCursor] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [loadingTimeline, setLoadingTimeline] = useState(false);

  const [newPref, setNewPref] = useState({ preferenceType: 'LIKE', value: '' });
  const [savingPref, setSavingPref] = useState(false);

  const loadProfile = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const res = await customerAPI.getProfile(customerId);
      setProfile(res.data?.data ?? res.data);
    } catch (error) {
      console.error('Failed to load customer profile:', error);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [customerId]);

  /** `reset` starts a fresh window; otherwise this appends the next older page. */
  const loadTimeline = useCallback(async (reset = false) => {
    setLoadingTimeline(true);
    try {
      const params = { limit: TIMELINE_PAGE };
      if (!reset && cursor) params.before = cursor;
      const res = await customerAPI.getTimeline(customerId, params);
      const data = res.data?.data ?? res.data ?? {};
      setEntries((prev) => (reset ? (data.entries || []) : [...prev, ...(data.entries || [])]));
      setCursor(data.nextCursor || null);
      setHasMore(!!data.hasMore);
    } catch (error) {
      console.error('Failed to load conversation history:', error);
    } finally {
      setLoadingTimeline(false);
    }
  }, [customerId, cursor]);

  useEffect(() => { loadProfile(); }, [loadProfile]);
  // Only the first window loads with the page; older ones are pulled on demand.
  useEffect(() => { loadTimeline(true); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [customerId]);

  const handleAddPreference = async (e) => {
    e.preventDefault();
    if (!newPref.value.trim()) return;
    setSavingPref(true);
    try {
      await customerAPI.addPreference(customerId, {
        preferenceType: newPref.preferenceType,
        value: newPref.value.trim(),
      });
      notifySuccess(t('customerProfile.preferenceAdded', 'Preference added.'));
      setNewPref({ ...newPref, value: '' });
      loadProfile();
    } catch (error) {
      console.error('Failed to add preference:', error);
      notifyError(error?.response?.data?.message
        || t('customerProfile.preferenceError', 'Could not add the preference.'));
    } finally {
      setSavingPref(false);
    }
  };

  const handleDeletePreference = async (preferenceId) => {
    try {
      await customerAPI.deletePreference(customerId, preferenceId);
      loadProfile();
    } catch (error) {
      console.error('Failed to remove preference:', error);
      notifyError(t('customerProfile.preferenceError', 'Could not remove the preference.'));
    }
  };

  if (loading) {
    return <div className="p-8 text-center text-muted-foreground">{t('common.loading', 'Loading...')}</div>;
  }
  if (loadError || !profile) {
    return (
      <div className="p-8 text-center space-y-3">
        <p className="text-muted-foreground">
          {t('customerProfile.loadError', 'Could not load this customer.')}
        </p>
        <Button variant="outline" onClick={() => navigate('/customers')}>
          {t('customerProfile.backToCustomers', 'Back to customers')}
        </Button>
      </div>
    );
  }

  const fullName = `${profile.firstName || ''} ${profile.lastName || ''}`.trim() || `#${profile.id}`;
  const purchases = profile.purchases || {};
  const loyalty = profile.loyalty;

  return (
    <div className="space-y-6 p-4 sm:p-6">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="icon" onClick={() => navigate('/customers')}>
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div>
          <h1 className="text-2xl font-bold">{fullName}</h1>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            {profile.phone && (
              <span className="flex items-center gap-1"><Phone className="h-3 w-3" />{profile.phone}</span>
            )}
            {profile.email && (
              <span className="flex items-center gap-1"><Mail className="h-3 w-3" />{profile.email}</span>
            )}
            {profile.birthDate && (
              <span className="flex items-center gap-1">
                <Cake className="h-3 w-3" />{new Date(profile.birthDate).toLocaleDateString()}
              </span>
            )}
            {loyalty?.tierName && (
              <span className="flex items-center gap-1"><Star className="h-3 w-3" />{loyalty.tierName}</span>
            )}
          </div>
        </div>
      </div>

      {/* Headline numbers — the things worth knowing before reading anything else. */}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          ['orders', purchases.orderCount ?? 0],
          ['lifetimeSpend', purchases.lifetimeSpend ?? 0],
          ['averageOrder', purchases.averageOrderValue ?? 0],
          ['bonusBalance', loyalty?.currentBalance ?? '—'],
        ].map(([key, value]) => (
          <div key={key} className="rounded-lg border bg-card p-3">
            <p className="text-2xl font-bold tabular-nums">{value}</p>
            <p className="text-xs text-muted-foreground">{t(`customerProfile.stats.${key}`, key)}</p>
          </div>
        ))}
      </div>

      <Tabs defaultValue="preferences">
        <TabsList>
          <TabsTrigger value="preferences">
            {t('customerProfile.tabs.preferences', 'Preferences')}
          </TabsTrigger>
          <TabsTrigger value="conversations">
            {t('customerProfile.tabs.conversations', 'Conversations')}
          </TabsTrigger>
          <TabsTrigger value="purchases">
            {t('customerProfile.tabs.purchases', 'Purchases')}
          </TabsTrigger>
        </TabsList>

        {/* ---------------- Preferences ---------------- */}
        <TabsContent value="preferences">
          <Card>
            <CardHeader>
              <CardTitle>{t('customerProfile.preferences.title', 'What they like')}</CardTitle>
              <CardDescription>
                {t('customerProfile.preferences.description',
                  'Likes, dislikes, allergies and dietary needs — recorded so they can be acted on, not buried in a note.')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {(profile.preferences || []).length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  {t('customerProfile.preferences.empty', 'Nothing recorded yet.')}
                </p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {profile.preferences.map((p) => (
                    <span
                      key={p.id}
                      className={`inline-flex items-center gap-2 rounded-full px-3 py-1 text-sm ${
                        PREFERENCE_STYLES[p.preferenceType] || 'bg-gray-100 text-gray-800'
                      }`}
                    >
                      <span className="text-[10px] font-semibold uppercase tracking-wide opacity-70">
                        {t(`customerProfile.preferenceTypes.${p.preferenceType}`, p.preferenceType)}
                      </span>
                      {p.value}
                      <button
                        type="button"
                        onClick={() => handleDeletePreference(p.id)}
                        title={t('customerProfile.preferences.remove', 'Remove')}
                      >
                        <Trash2 className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}

              <form onSubmit={handleAddPreference} className="flex flex-wrap items-end gap-2 border-t pt-4">
                <div className="space-y-1">
                  <Label>{t('customerProfile.preferences.type', 'Type')}</Label>
                  <Select
                    value={newPref.preferenceType}
                    onValueChange={(v) => setNewPref({ ...newPref, preferenceType: v })}
                  >
                    <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
                    <SelectContent>
                      {['LIKE', 'DISLIKE', 'ALLERGY', 'DIETARY'].map((type) => (
                        <SelectItem key={type} value={type}>
                          {t(`customerProfile.preferenceTypes.${type}`, type)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="flex-1 space-y-1 min-w-[12rem]">
                  <Label htmlFor="pref-value">{t('customerProfile.preferences.value', 'Detail')}</Label>
                  <Input
                    id="pref-value"
                    value={newPref.value}
                    maxLength={120}
                    placeholder={t('customerProfile.preferences.placeholder', 'e.g. walnuts')}
                    onChange={(e) => setNewPref({ ...newPref, value: e.target.value })}
                  />
                </div>
                <Button type="submit" disabled={savingPref || !newPref.value.trim()}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t('customerProfile.preferences.add', 'Add')}
                </Button>
              </form>
            </CardContent>
          </Card>
        </TabsContent>

        {/* ---------------- Conversations ---------------- */}
        <TabsContent value="conversations">
          <Card>
            <CardHeader>
              <CardTitle>{t('customerProfile.conversations.title', 'Conversations')}</CardTitle>
              <CardDescription>
                {t('customerProfile.conversations.description',
                  'Recent messages across every channel. Only Instagram records what the guest sent back.')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              {entries.length === 0 && !loadingTimeline ? (
                <p className="text-sm text-muted-foreground">
                  {t('customerProfile.conversations.empty', 'No messages yet.')}
                </p>
              ) : (
                <div className="space-y-2">
                  {entries.map((e, i) => {
                    const Icon = CHANNEL_ICONS[e.channel] || MessageSquare;
                    const inbound = e.direction === 'IN';
                    return (
                      <div key={i} className={`flex ${inbound ? 'justify-start' : 'justify-end'}`}>
                        <div
                          className={`max-w-[75%] rounded-lg border px-3 py-2 text-sm ${
                            inbound ? 'bg-background' : 'bg-muted/50'
                          }`}
                        >
                          <div className="mb-1 flex items-center gap-2 text-[10px] uppercase tracking-wide text-muted-foreground">
                            <Icon className="h-3 w-3" />
                            {e.channel}
                            {e.messageType ? ` · ${e.messageType}` : ''}
                          </div>
                          <div className="whitespace-pre-wrap break-words">{e.text || '—'}</div>
                          <div className="mt-1 text-[10px] text-muted-foreground">
                            {e.timestamp ? new Date(e.timestamp).toLocaleString() : ''}
                            {!inbound && e.status ? ` · ${e.status}` : ''}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}

              {hasMore && (
                <div className="flex justify-center pt-2">
                  <Button variant="outline" size="sm" onClick={() => loadTimeline(false)}
                          disabled={loadingTimeline}>
                    {loadingTimeline
                      ? t('common.loading', 'Loading...')
                      : t('customerProfile.conversations.loadMore', 'Load older messages')}
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ---------------- Purchases ---------------- */}
        <TabsContent value="purchases">
          <Card>
            <CardHeader>
              <CardTitle>{t('customerProfile.purchases.title', 'Purchases')}</CardTitle>
              <CardDescription>
                {t('customerProfile.purchases.description',
                  'Completed orders only — cancelled ones are not purchases.')}
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="rounded-lg border p-3">
                  <p className="text-xs text-muted-foreground">
                    {t('customerProfile.purchases.firstOrder', 'First order')}
                  </p>
                  <p className="font-medium">
                    {purchases.firstOrderAt ? new Date(purchases.firstOrderAt).toLocaleDateString() : '—'}
                  </p>
                </div>
                <div className="rounded-lg border p-3">
                  <p className="text-xs text-muted-foreground">
                    {t('customerProfile.purchases.lastOrder', 'Last order')}
                  </p>
                  <p className="font-medium">
                    {purchases.lastOrderAt ? new Date(purchases.lastOrderAt).toLocaleDateString() : '—'}
                  </p>
                </div>
              </div>

              <div>
                <p className="mb-2 text-sm font-medium">
                  {t('customerProfile.purchases.topItems', 'Most ordered')}
                </p>
                {(purchases.topItems || []).length === 0 ? (
                  <p className="text-sm text-muted-foreground">
                    {t('customerProfile.purchases.noItems', 'Nothing ordered yet.')}
                  </p>
                ) : (
                  <div className="flex flex-wrap gap-2">
                    {purchases.topItems.map((item) => (
                      <Badge key={item.productName} variant="outline">
                        {item.productName} · {item.timesOrdered}
                      </Badge>
                    ))}
                  </div>
                )}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
