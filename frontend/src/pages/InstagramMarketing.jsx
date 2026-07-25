import { useState, useEffect } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { instagramAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../components/ui/dialog';
import {
  Instagram,
  Users,
  Settings,
  Save,
  Trash2,
  ChevronLeft,
  ChevronRight,
  Search,
  Eye,
  EyeOff,
  Send,
  Ban,
  CheckCircle,
  Megaphone,
  RefreshCw,
} from 'lucide-react';
import { Switch } from '../components/ui/switch';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';

const stateColors = {
  REGISTERED:              'bg-green-100 text-green-800',
  AWAITING_NAME:           'bg-yellow-100 text-yellow-800',
  AWAITING_PHONE:          'bg-yellow-100 text-yellow-800',
  AWAITING_BIRTHDAY:       'bg-yellow-100 text-yellow-800',
  AWAITING_ADDRESS:        'bg-yellow-100 text-yellow-800',
  AWAITING_MORE_ADDRESSES: 'bg-yellow-100 text-yellow-800',
};

const campaignStatusColors = {
  DRAFT:     'bg-gray-100 text-gray-700',
  SENDING:   'bg-blue-100 text-blue-800',
  COMPLETED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-orange-100 text-orange-800',
  SCHEDULED: 'bg-purple-100 text-purple-800',
  PAUSED:    'bg-yellow-100 text-yellow-800',
};

const recipientStatusColors = {
  PENDING:   'bg-gray-100 text-gray-700',
  SENT:      'bg-green-100 text-green-800',
  DELIVERED: 'bg-green-100 text-green-800',
  FAILED:    'bg-red-100 text-red-800',
};

export default function InstagramMarketing() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('subscribers');
  const [loading, setLoading] = useState(false);

  // Subscribers state
  const [subscribers, setSubscribers] = useState([]);
  const [subscriberPage, setSubscriberPage] = useState(0);
  const [subscriberTotalPages, setSubscriberTotalPages] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
  // The query the CURRENT result set reflects — pagination must reuse it.
  const [appliedQuery, setAppliedQuery] = useState('');
  const [loadError, setLoadError] = useState(false);
  const [searching, setSearching] = useState(false);

  // Send DM dialog
  const [dmTarget, setDmTarget] = useState(null); // { id, displayName, username }
  const [dmText, setDmText] = useState('');
  const [sendingDm, setSendingDm] = useState(false);

  // Broadcast tab state
  const [broadcastText, setBroadcastText] = useState('');
  const [broadcastTarget, setBroadcastTarget] = useState('ALL');
  const [broadcasting, setBroadcasting] = useState(false);
  const [broadcastResult, setBroadcastResult] = useState(null);

  // Campaigns state
  const [campaigns, setCampaigns] = useState([]);
  const [campaignsPage, setCampaignsPage] = useState(0);
  const [campaignsTotalPages, setCampaignsTotalPages] = useState(0);
  const [loadingCampaigns, setLoadingCampaigns] = useState(false);
  const [resendingId, setResendingId] = useState(null);

  // Recipients dialog
  const [recipientsCampaign, setRecipientsCampaign] = useState(null); // campaign being inspected
  const [recipients, setRecipients] = useState([]);
  const [recipientsPage, setRecipientsPage] = useState(0);
  const [recipientsTotalPages, setRecipientsTotalPages] = useState(0);
  const [loadingRecipients, setLoadingRecipients] = useState(false);

  // Config state
  const [configId, setConfigId] = useState(null);
  const [configForm, setConfigForm] = useState({
    appId: '',
    appSecret: '',
    accessToken: '',
    instagramAccountId: '',
    verifyToken: '',
    isActive: false,
    welcomeMessage: '',
    autoReplyEnabled: false,
    autoReplyTemplate: '',
  });
  const [hasAccessToken, setHasAccessToken] = useState(false);
  const [hasAppSecret, setHasAppSecret] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [showSecret, setShowSecret] = useState(false);
  const [showToken, setShowToken] = useState(false);
  const [showVerifyToken, setShowVerifyToken] = useState(false);

  useEffect(() => {
    if (activeTab === 'subscribers') {
      loadSubscribers(0);
    } else if (activeTab === 'broadcast') {
      loadCampaigns(0);
    } else if (activeTab === 'settings') {
      loadConfig();
    }
  }, [activeTab]);

  // -------------------------------------------------------------------------
  // Subscribers
  // -------------------------------------------------------------------------

  const loadSubscribers = async (page = 0) => {
    setLoading(true);
    try {
      const response = appliedQuery
        ? await instagramAPI.searchSubscribers(appliedQuery, { page, size: 15 })
        : await instagramAPI.getSubscribers({ page, size: 15 });
      setSubscribers(response.data.content || []);
      setSubscriberTotalPages(response.data.totalPages || 0);
      setSubscriberPage(page);
      setLoadError(false);
    } catch (error) {
      console.error('Failed to load subscribers:', error);
      notifyError(error);
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      loadSubscribers(0);
      return;
    }
    setSearching(true);
    try {
      // Commit the query so the pagination buttons keep filtering instead of silently
      // falling back to the unfiltered list.
      setAppliedQuery(searchQuery.trim());
      const response = await instagramAPI.searchSubscribers(searchQuery.trim(), { page: 0, size: 15 });
      setSubscribers(response.data.content || []);
      setSubscriberTotalPages(response.data.totalPages || 0);
      setSubscriberPage(0);
    } catch (error) {
      console.error('Failed to search subscribers:', error);
      setAppliedQuery('');
      notifyError(error);
    } finally {
      setSearching(false);
    }
  };

  const handleSearchKeyDown = (e) => {
    if (e.key === 'Enter') handleSearch();
    if (e.key === 'Escape') {
      setSearchQuery('');
      loadSubscribers(0);
    }
  };

  const handleBlock = async (subscriber) => {
    const action = subscriber.isBlocked ? 'unblock' : 'block';
    if (!window.confirm(t(`instagram.subscribers.confirm${action === 'block' ? 'Block' : 'Unblock'}`, { name: subscriber.displayName || subscriber.username || subscriber.igsid }))) return;
    try {
      const response = action === 'block'
        ? await instagramAPI.blockSubscriber(subscriber.id)
        : await instagramAPI.unblockSubscriber(subscriber.id);
      setSubscribers(prev => prev.map(s => s.id === subscriber.id ? response.data : s));
    } catch (error) {
      console.error(`Failed to ${action} subscriber:`, error);
      notifyWarning(t('instagram.errors.blockAction'));
    }
  };

  const openSendDm = (subscriber) => {
    setDmTarget(subscriber);
    setDmText('');
  };

  const handleSendDm = async () => {
    if (!dmText.trim() || !dmTarget) return;
    setSendingDm(true);
    try {
      const response = await instagramAPI.sendDm(dmTarget.id, dmText.trim());
      if (response?.data?.sent === false) {
        // The endpoint answers 200 {"sent": false} when there is no active config or the circuit
        // breaker is open. Keep the dialog (and the typed text) so nothing is silently lost.
        notifyWarning(t('instagram.errors.sendDm'));
        return;
      }
      notifySuccess(t('instagram.dm.sent'));
      setDmTarget(null);
      setDmText('');
    } catch (error) {
      console.error('Failed to send DM:', error);
      notifyWarning(t('instagram.errors.sendDm'));
    } finally {
      setSendingDm(false);
    }
  };

  // -------------------------------------------------------------------------
  // Broadcast
  // -------------------------------------------------------------------------

  const handleBroadcast = async () => {
    if (!broadcastText.trim()) return;
    if (!window.confirm(t('instagram.broadcast.confirm', { target: broadcastTarget }))) return;
    setBroadcasting(true);
    setBroadcastResult(null);
    try {
      // The broadcast now creates an async campaign (202): it returns immediately with the recipient
      // count and sends in the background, rather than blocking until every DM is delivered.
      const response = await instagramAPI.broadcast(broadcastText.trim(), broadcastTarget);
      setBroadcastResult(response.data?.recipientCount ?? 0);
      setBroadcastText('');
      loadCampaigns(0);   // surface the new campaign in the history below
    } catch (error) {
      console.error('Broadcast failed:', error);
      notifyWarning(t('instagram.errors.broadcast'));
    } finally {
      setBroadcasting(false);
    }
  };

  // -------------------------------------------------------------------------
  // Campaigns
  // -------------------------------------------------------------------------

  const loadCampaigns = async (page = 0) => {
    setLoadingCampaigns(true);
    try {
      const response = await instagramAPI.getCampaigns({ page, size: 10 });
      setCampaigns(response.data.content || []);
      setCampaignsTotalPages(response.data.totalPages || 0);
      setCampaignsPage(page);
    } catch (error) {
      console.error('Failed to load campaigns:', error);
      notifyError(error);
    } finally {
      setLoadingCampaigns(false);
    }
  };

  const handleResend = async (campaign) => {
    if (!window.confirm(t('instagram.campaigns.confirmResend'))) return;
    setResendingId(campaign.id);
    try {
      await instagramAPI.resendCampaign(campaign.id);
      notifySuccess(t('instagram.campaigns.resendStarted'));
      loadCampaigns(campaignsPage);
    } catch (error) {
      console.error('Failed to resend campaign:', error);
      notifyWarning(t('instagram.campaigns.resendError'));
    } finally {
      setResendingId(null);
    }
  };

  const openRecipients = (campaign) => {
    setRecipientsCampaign(campaign);
    loadRecipients(campaign.id, 0);
  };

  const loadRecipients = async (id, page = 0) => {
    setLoadingRecipients(true);
    try {
      const response = await instagramAPI.getCampaignRecipients(id, { page, size: 20 });
      setRecipients(response.data.content || []);
      setRecipientsTotalPages(response.data.totalPages || 0);
      setRecipientsPage(page);
    } catch (error) {
      console.error('Failed to load recipients:', error);
      notifyWarning(t('instagram.campaigns.recipientsError'));
    } finally {
      setLoadingRecipients(false);
    }
  };

  // -------------------------------------------------------------------------
  // Config
  // -------------------------------------------------------------------------

  const loadConfig = async () => {
    setLoading(true);
    try {
      const response = await instagramAPI.getConfigs();
      const configs = response.data || [];
      if (configs.length > 0) {
        const c = configs[0];
        setConfigId(c.id);
        setHasAccessToken(c.hasAccessToken);
        setHasAppSecret(c.hasAppSecret);
        setConfigForm({
          appId:               c.appId || '',
          appSecret:           '',
          accessToken:         '',
          instagramAccountId:  c.instagramAccountId || '',
          verifyToken:         '',
          isActive:            c.isActive ?? false,
          welcomeMessage:      c.welcomeMessage || '',
          autoReplyEnabled:    c.autoReplyEnabled ?? false,
          autoReplyTemplate:   c.autoReplyTemplate || '',
        });
      }
    } catch (error) {
      // A failed reload used to leave configId stale, so Save would fire createConfig a second
      // time and mint a duplicate config row.
      console.error('Failed to load Instagram config:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveConfig = async () => {
    setSavingConfig(true);
    try {
      const data = { ...configForm };
      // Don't send blank secrets — backend interprets null as "keep existing"
      if (!data.appSecret)   delete data.appSecret;
      if (!data.accessToken) delete data.accessToken;
      if (!data.verifyToken) delete data.verifyToken;

      if (configId) {
        await instagramAPI.updateConfig(configId, data);
        notifySuccess(t('instagram.settings.configUpdated'));
      } else {
        await instagramAPI.createConfig(data);
        notifySuccess(t('instagram.settings.configCreated'));
      }
      loadConfig();
    } catch (error) {
      console.error('Failed to save Instagram config:', error);
      notifyWarning(t('instagram.errors.saveConfig'));
    } finally {
      setSavingConfig(false);
    }
  };

  const handleClearCredentials = async () => {
    if (!configId) return;
    if (!window.confirm(t('instagram.settings.confirmClearCredentials'))) return;
    try {
      await instagramAPI.clearCredentials(configId);
      notifySuccess(t('instagram.settings.credentialsCleared'));
      loadConfig();
    } catch (error) {
      console.error('Failed to clear credentials:', error);
      notifyWarning(t('instagram.errors.clearCredentials'));
    }
  };

  // -------------------------------------------------------------------------
  // Render helpers
  // -------------------------------------------------------------------------

  const webhookUrl = `${window.location.protocol}//${window.location.host}/api/v1/instagram/webhook`;

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-3">
        <Instagram className="h-8 w-8 text-pink-500" />
        <div>
          <h1 className="text-2xl font-bold">{t('instagram.title')}</h1>
          <p className="text-muted-foreground">{t('instagram.description')}</p>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab}>
        <TabsList>
          <TabsTrigger value="subscribers" className="flex items-center gap-2">
            <Users className="h-4 w-4" />
            {t('instagram.tabs.subscribers')}
          </TabsTrigger>
          <TabsTrigger value="broadcast" className="flex items-center gap-2">
            <Megaphone className="h-4 w-4" />
            {t('instagram.tabs.campaigns')}
          </TabsTrigger>
          <TabsTrigger value="settings" className="flex items-center gap-2">
            <Settings className="h-4 w-4" />
            {t('instagram.tabs.settings')}
          </TabsTrigger>
        </TabsList>

        {/* ------------------------------------------------------------------ */}
        {/* Subscribers Tab                                                     */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="subscribers">
          <Card>
            <CardHeader>
              <CardTitle>{t('instagram.subscribers.title')}</CardTitle>
              <CardDescription>{t('instagram.subscribers.description')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Search bar */}
              <div className="flex gap-2">
                <Input
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  onKeyDown={handleSearchKeyDown}
                  placeholder={t('instagram.subscribers.searchPlaceholder')}
                  className="max-w-sm"
                />
                <Button variant="outline" onClick={handleSearch} disabled={searching}>
                  <Search className="h-4 w-4 mr-2" />
                  {t('common.search')}
                </Button>
                {searchQuery && (
                  <Button variant="ghost" onClick={() => { setSearchQuery(''); loadSubscribers(0); }}>
                    {t('common.clear')}
                  </Button>
                )}
              </div>

              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('instagram.subscribers.username')}</TableHead>
                    <TableHead>{t('instagram.subscribers.displayName')}</TableHead>
                    <TableHead>{t('instagram.subscribers.phone')}</TableHead>
                    <TableHead>{t('instagram.subscribers.state')}</TableHead>
                    <TableHead>{t('instagram.subscribers.status')}</TableHead>
                    <TableHead>{t('instagram.subscribers.subscribedAt')}</TableHead>
                    <TableHead className="text-right">{t('instagram.subscribers.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {subscribers.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={7} className="text-center text-muted-foreground py-8">
                        {loading
                          ? t('common.loading')
                          : loadError
                            ? (
                              <span className="flex items-center justify-center gap-3">
                                {t('instagram.errors.loadSubscribers')}
                                <Button variant="outline" size="sm" onClick={() => loadSubscribers(0)}>
                                  {t('common.retry', 'Retry')}
                                </Button>
                              </span>
                            )
                            : t('instagram.subscribers.empty')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    subscribers.map((s) => (
                      <TableRow key={s.id} className={s.isBlocked ? 'opacity-50' : ''}>
                        <TableCell className="font-mono text-sm">
                          {s.username ? `@${s.username}` : s.igsid}
                        </TableCell>
                        <TableCell>{s.displayName || '—'}</TableCell>
                        <TableCell>{s.phone || '—'}</TableCell>
                        <TableCell>
                          {s.conversationState ? (
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${stateColors[s.conversationState] || 'bg-gray-100 text-gray-700'}`}>
                              {s.conversationState === 'REGISTERED'
                                ? t('instagram.states.registered')
                                : t('instagram.states.inProgress')}
                            </span>
                          ) : '—'}
                        </TableCell>
                        <TableCell>
                          {s.isBlocked ? (
                            <Badge variant="destructive">{t('instagram.subscribers.blocked')}</Badge>
                          ) : (
                            <Badge variant={s.isActive ? 'default' : 'secondary'}>
                              {s.isActive ? t('instagram.subscribers.active') : t('instagram.subscribers.inactive')}
                            </Badge>
                          )}
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">
                          {s.subscribedAt ? new Date(s.subscribedAt).toLocaleDateString() : '—'}
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex items-center justify-end gap-1">
                            {/* Send DM */}
                            {!s.isBlocked && (
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 text-blue-500 hover:text-blue-700"
                                title={t('instagram.dm.sendButton')}
                                onClick={() => openSendDm(s)}
                              >
                                <Send className="h-4 w-4" />
                              </Button>
                            )}
                            {/* Block / Unblock */}
                            <Button
                              size="icon"
                              variant="ghost"
                              className={`h-8 w-8 ${s.isBlocked ? 'text-green-500 hover:text-green-700' : 'text-red-500 hover:text-red-700'}`}
                              title={s.isBlocked ? t('instagram.subscribers.unblock') : t('instagram.subscribers.block')}
                              onClick={() => handleBlock(s)}
                            >
                              {s.isBlocked ? <CheckCircle className="h-4 w-4" /> : <Ban className="h-4 w-4" />}
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>

              {/* Pagination */}
              {subscriberTotalPages > 1 && (
                <div className="flex items-center justify-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={subscriberPage === 0}
                    onClick={() => loadSubscribers(subscriberPage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    {subscriberPage + 1} / {subscriberTotalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={subscriberPage >= subscriberTotalPages - 1}
                    onClick={() => loadSubscribers(subscriberPage + 1)}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* ------------------------------------------------------------------ */}
        {/* Broadcast Tab                                                       */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="broadcast" className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Megaphone className="h-5 w-5 text-pink-500" />
                {t('instagram.broadcast.title')}
              </CardTitle>
              <CardDescription>{t('instagram.broadcast.description')}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4 max-w-xl">

              <div className="space-y-2">
                <Label>{t('instagram.broadcast.audience')}</Label>
                <Select value={broadcastTarget} onValueChange={setBroadcastTarget}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ALL">{t('instagram.broadcast.audienceAll')}</SelectItem>
                    <SelectItem value="REGISTERED">{t('instagram.broadcast.audienceRegistered')}</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">{t('instagram.broadcast.audienceHint')}</p>
              </div>

              <div className="space-y-2">
                <Label>{t('instagram.broadcast.message')}</Label>
                <Textarea
                  value={broadcastText}
                  onChange={(e) => setBroadcastText(e.target.value)}
                  placeholder={t('instagram.broadcast.messagePlaceholder')}
                  rows={5}
                />
              </div>

              {broadcastResult !== null && (
                <div className="rounded-md bg-green-50 border border-green-200 p-3 text-sm text-green-800">
                  {t('instagram.broadcast.queued', { count: broadcastResult })}
                </div>
              )}

              <Button
                onClick={handleBroadcast}
                disabled={broadcasting || !broadcastText.trim()}
                className="w-full"
              >
                <Megaphone className="mr-2 h-4 w-4" />
                {broadcasting ? t('instagram.broadcast.sending') : t('instagram.broadcast.send')}
              </Button>
            </CardContent>
          </Card>

          {/* Campaign history */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle>{t('instagram.campaigns.title')}</CardTitle>
                <CardDescription>{t('instagram.campaigns.description')}</CardDescription>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={() => loadCampaigns(campaignsPage)}
                disabled={loadingCampaigns}
              >
                <RefreshCw className={`h-4 w-4 mr-2 ${loadingCampaigns ? 'animate-spin' : ''}`} />
                {t('common.refresh', 'Refresh')}
              </Button>
            </CardHeader>
            <CardContent className="space-y-4">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('instagram.campaigns.name')}</TableHead>
                    <TableHead>{t('instagram.campaigns.audience')}</TableHead>
                    <TableHead>{t('instagram.campaigns.status')}</TableHead>
                    <TableHead>{t('instagram.campaigns.progress')}</TableHead>
                    <TableHead>{t('instagram.campaigns.created')}</TableHead>
                    <TableHead className="text-right">{t('instagram.campaigns.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {campaigns.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center text-muted-foreground py-8">
                        {loadingCampaigns ? t('common.loading') : t('instagram.campaigns.empty')}
                      </TableCell>
                    </TableRow>
                  ) : (
                    campaigns.map((c) => (
                      <TableRow key={c.id}>
                        <TableCell className="max-w-xs">
                          <div className="font-medium truncate">{c.name}</div>
                          <div className="text-xs text-muted-foreground truncate">{c.messageText}</div>
                        </TableCell>
                        <TableCell>
                          {c.targetAudience === 'REGISTERED'
                            ? t('instagram.broadcast.audienceRegistered')
                            : t('instagram.broadcast.audienceAll')}
                        </TableCell>
                        <TableCell>
                          <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${campaignStatusColors[c.status] || 'bg-gray-100 text-gray-700'}`}>
                            {t(`instagram.campaigns.statuses.${(c.status || '').toLowerCase()}`, c.status)}
                          </span>
                        </TableCell>
                        <TableCell className="text-sm whitespace-nowrap">
                          <span className="text-green-700">{c.sentCount}</span>
                          {' / '}
                          {c.recipientCount}
                          {c.failedCount > 0 && (
                            <span className="text-red-600 ml-1">
                              {t('instagram.campaigns.failedInline', { count: c.failedCount })}
                            </span>
                          )}
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                          {c.createdAt ? new Date(c.createdAt).toLocaleString() : '—'}
                        </TableCell>
                        <TableCell className="text-right space-x-1 whitespace-nowrap">
                          <Button variant="ghost" size="sm" onClick={() => openRecipients(c)}>
                            <Users className="h-4 w-4 mr-1" />
                            {t('instagram.campaigns.viewRecipients')}
                          </Button>
                          {(c.status === 'CANCELLED' || c.status === 'DRAFT') && (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleResend(c)}
                              disabled={resendingId === c.id}
                            >
                              <RefreshCw className={`h-4 w-4 mr-1 ${resendingId === c.id ? 'animate-spin' : ''}`} />
                              {t('instagram.campaigns.resend')}
                            </Button>
                          )}
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>

              {campaignsTotalPages > 1 && (
                <div className="flex items-center justify-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={campaignsPage === 0}
                    onClick={() => loadCampaigns(campaignsPage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    {campaignsPage + 1} / {campaignsTotalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={campaignsPage >= campaignsTotalPages - 1}
                    onClick={() => loadCampaigns(campaignsPage + 1)}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>

          {/* Per-recipient delivery records */}
          <Dialog
            open={!!recipientsCampaign}
            onOpenChange={(open) => { if (!open) setRecipientsCampaign(null); }}
          >
            <DialogContent className="max-w-2xl">
              <DialogHeader>
                <DialogTitle>{t('instagram.campaigns.recipientsTitle')}</DialogTitle>
                <DialogDescription>{recipientsCampaign?.name}</DialogDescription>
              </DialogHeader>
              <div className="max-h-96 overflow-y-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>{t('instagram.campaigns.recipient')}</TableHead>
                      <TableHead>{t('instagram.campaigns.status')}</TableHead>
                      <TableHead>{t('instagram.campaigns.sentAt')}</TableHead>
                      <TableHead>{t('instagram.campaigns.error')}</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {recipients.length === 0 ? (
                      <TableRow>
                        <TableCell colSpan={4} className="text-center text-muted-foreground py-6">
                          {loadingRecipients ? t('common.loading') : t('instagram.campaigns.recipientsEmpty')}
                        </TableCell>
                      </TableRow>
                    ) : (
                      recipients.map((r) => (
                        <TableRow key={r.id}>
                          <TableCell className="font-mono text-xs">{r.igsid}</TableCell>
                          <TableCell>
                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${recipientStatusColors[r.status] || 'bg-gray-100 text-gray-700'}`}>
                              {t(`instagram.campaigns.recipientStatuses.${(r.status || '').toLowerCase()}`, r.status)}
                            </span>
                          </TableCell>
                          <TableCell className="text-xs text-muted-foreground whitespace-nowrap">
                            {r.sentAt ? new Date(r.sentAt).toLocaleString() : '—'}
                          </TableCell>
                          <TableCell className="text-xs text-red-600 max-w-xs truncate">
                            {r.errorMessage || '—'}
                          </TableCell>
                        </TableRow>
                      ))
                    )}
                  </TableBody>
                </Table>
              </div>
              {recipientsTotalPages > 1 && recipientsCampaign && (
                <div className="flex items-center justify-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={recipientsPage === 0}
                    onClick={() => loadRecipients(recipientsCampaign.id, recipientsPage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm text-muted-foreground">
                    {recipientsPage + 1} / {recipientsTotalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={recipientsPage >= recipientsTotalPages - 1}
                    onClick={() => loadRecipients(recipientsCampaign.id, recipientsPage + 1)}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </DialogContent>
          </Dialog>
        </TabsContent>

        {/* ------------------------------------------------------------------ */}
        {/* Settings Tab                                                        */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="settings">
          <div className="grid gap-6 md:grid-cols-2">

            {/* App Credentials */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Instagram className="h-5 w-5 text-pink-500" />
                  {t('instagram.settings.credentials')}
                </CardTitle>
                <CardDescription>{t('instagram.settings.credentialsDescription')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">

                <div className="space-y-2">
                  <Label>{t('instagram.settings.appId')}</Label>
                  <Input
                    value={configForm.appId}
                    onChange={(e) => setConfigForm({ ...configForm, appId: e.target.value })}
                    placeholder="123456789"
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.settings.appSecret')}</Label>
                  <div className="relative">
                    <Input
                      type={showSecret ? 'text' : 'password'}
                      value={configForm.appSecret}
                      onChange={(e) => setConfigForm({ ...configForm, appSecret: e.target.value })}
                      placeholder={hasAppSecret ? '••••••••' : t('instagram.settings.enterSecret')}
                    />
                    <button
                      type="button"
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowSecret(!showSecret)}
                    >
                      {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.secretHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.settings.accessToken')}</Label>
                  <div className="relative">
                    <Input
                      type={showToken ? 'text' : 'password'}
                      value={configForm.accessToken}
                      onChange={(e) => setConfigForm({ ...configForm, accessToken: e.target.value })}
                      placeholder={hasAccessToken ? '••••••••' : t('instagram.settings.enterToken')}
                    />
                    <button
                      type="button"
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowToken(!showToken)}
                    >
                      {showToken ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.tokenHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.settings.accountId')}</Label>
                  <Input
                    value={configForm.instagramAccountId}
                    onChange={(e) => setConfigForm({ ...configForm, instagramAccountId: e.target.value })}
                    placeholder="17841400000000000"
                  />
                </div>

                {/* Webhook URL (read-only, for copy-paste into Meta dashboard) */}
                <div className="space-y-2">
                  <Label>{t('instagram.settings.webhookUrl')}</Label>
                  <Input
                    value={webhookUrl}
                    readOnly
                    className="bg-muted font-mono text-xs"
                    onClick={(e) => e.target.select()}
                  />
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.webhookHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.settings.verifyToken')}</Label>
                  <div className="relative">
                    <Input
                      type={showVerifyToken ? 'text' : 'password'}
                      value={configForm.verifyToken}
                      onChange={(e) => setConfigForm({ ...configForm, verifyToken: e.target.value })}
                      placeholder={t('instagram.settings.enterVerifyToken')}
                    />
                    <button
                      type="button"
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowVerifyToken(!showVerifyToken)}
                    >
                      {showVerifyToken ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.verifyTokenHint')}</p>
                </div>

                <div className="flex items-center justify-between">
                  <Label>{t('instagram.settings.active')}</Label>
                  <Switch
                    checked={configForm.isActive}
                    onCheckedChange={(checked) => setConfigForm({ ...configForm, isActive: checked })}
                  />
                </div>

                {configId && (
                  <Button
                    variant="destructive"
                    size="sm"
                    className="w-full"
                    onClick={handleClearCredentials}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    {t('instagram.settings.clearCredentials')}
                  </Button>
                )}
              </CardContent>
            </Card>

            {/* Bot Behaviour */}
            <Card>
              <CardHeader>
                <CardTitle>{t('instagram.settings.botBehaviour')}</CardTitle>
                <CardDescription>{t('instagram.settings.botBehaviourDescription')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">

                <div className="space-y-2">
                  <Label>{t('instagram.settings.welcomeMessage')}</Label>
                  <Textarea
                    value={configForm.welcomeMessage}
                    onChange={(e) => setConfigForm({ ...configForm, welcomeMessage: e.target.value })}
                    placeholder={t('instagram.settings.welcomeMessagePlaceholder')}
                    rows={3}
                  />
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <Label>{t('instagram.settings.autoReply')}</Label>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {t('instagram.settings.autoReplyDescription')}
                    </p>
                  </div>
                  <Switch
                    checked={configForm.autoReplyEnabled}
                    onCheckedChange={(checked) => setConfigForm({ ...configForm, autoReplyEnabled: checked })}
                  />
                </div>

                {configForm.autoReplyEnabled && (
                  <div className="space-y-2">
                    <Label>{t('instagram.settings.autoReplyTemplate')}</Label>
                    <Textarea
                      value={configForm.autoReplyTemplate}
                      onChange={(e) => setConfigForm({ ...configForm, autoReplyTemplate: e.target.value })}
                      placeholder={t('instagram.settings.autoReplyTemplatePlaceholder')}
                      rows={4}
                    />
                    <p className="text-xs text-muted-foreground">
                      {t('instagram.settings.autoReplyTemplateHint')}
                    </p>
                  </div>
                )}

                <Button
                  onClick={handleSaveConfig}
                  disabled={savingConfig}
                  className="w-full"
                >
                  <Save className="mr-2 h-4 w-4" />
                  {savingConfig ? t('common.saving') : t('common.save')}
                </Button>
              </CardContent>
            </Card>

          </div>
        </TabsContent>
      </Tabs>

      {/* -------------------------------------------------------------------- */}
      {/* Send DM Dialog                                                        */}
      {/* -------------------------------------------------------------------- */}
      <Dialog open={!!dmTarget} onOpenChange={(open) => { if (!open) setDmTarget(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('instagram.dm.title')}</DialogTitle>
            <DialogDescription>
              {t('instagram.dm.description', {
                name: dmTarget?.displayName || dmTarget?.username || dmTarget?.igsid || '',
              })}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-2">
            <Label>{t('instagram.dm.message')}</Label>
            <Textarea
              value={dmText}
              onChange={(e) => setDmText(e.target.value)}
              placeholder={t('instagram.dm.messagePlaceholder')}
              rows={4}
              autoFocus
            />
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setDmTarget(null)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSendDm} disabled={sendingDm || !dmText.trim()}>
              <Send className="mr-2 h-4 w-4" />
              {sendingDm ? t('common.sending') : t('instagram.dm.sendButton')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
