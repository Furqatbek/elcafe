import { useState, useEffect } from 'react';
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

export default function InstagramMarketing() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('subscribers');
  const [loading, setLoading] = useState(false);

  // Subscribers state
  const [subscribers, setSubscribers] = useState([]);
  const [subscriberPage, setSubscriberPage] = useState(0);
  const [subscriberTotalPages, setSubscriberTotalPages] = useState(0);
  const [searchQuery, setSearchQuery] = useState('');
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
      const response = await instagramAPI.getSubscribers({ page, size: 15 });
      setSubscribers(response.data.content || []);
      setSubscriberTotalPages(response.data.totalPages || 0);
      setSubscriberPage(page);
    } catch (error) {
      console.error('Failed to load subscribers:', error);
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
      const response = await instagramAPI.searchSubscribers(searchQuery.trim(), { page: 0, size: 15 });
      setSubscribers(response.data.content || []);
      setSubscriberTotalPages(response.data.totalPages || 0);
      setSubscriberPage(0);
    } catch (error) {
      console.error('Failed to search subscribers:', error);
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
      alert(t('instagram.errors.blockAction'));
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
      await instagramAPI.sendDm(dmTarget.id, dmText.trim());
      alert(t('instagram.dm.sent'));
      setDmTarget(null);
      setDmText('');
    } catch (error) {
      console.error('Failed to send DM:', error);
      alert(t('instagram.errors.sendDm'));
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
      const response = await instagramAPI.broadcast(broadcastText.trim(), broadcastTarget);
      setBroadcastResult(response.data.sent);
    } catch (error) {
      console.error('Broadcast failed:', error);
      alert(t('instagram.errors.broadcast'));
    } finally {
      setBroadcasting(false);
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
      console.error('Failed to load Instagram config:', error);
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
        alert(t('instagram.settings.configUpdated'));
      } else {
        await instagramAPI.createConfig(data);
        alert(t('instagram.settings.configCreated'));
      }
      loadConfig();
    } catch (error) {
      console.error('Failed to save Instagram config:', error);
      alert(t('instagram.errors.saveConfig'));
    } finally {
      setSavingConfig(false);
    }
  };

  const handleClearCredentials = async () => {
    if (!configId) return;
    if (!window.confirm(t('instagram.settings.confirmClearCredentials'))) return;
    try {
      await instagramAPI.clearCredentials(configId);
      alert(t('instagram.settings.credentialsCleared'));
      loadConfig();
    } catch (error) {
      console.error('Failed to clear credentials:', error);
      alert(t('instagram.errors.clearCredentials'));
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
            {t('instagram.tabs.broadcast')}
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
                        {loading ? t('common.loading') : t('instagram.subscribers.empty')}
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
        <TabsContent value="broadcast">
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
                  {t('instagram.broadcast.result', { count: broadcastResult })}
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
