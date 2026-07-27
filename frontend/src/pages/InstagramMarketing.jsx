import { useState, useEffect } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { instagramAPI, customerAPI } from '../services/api';
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
  Inbox as InboxIcon,
  MessageSquare,
  UserCheck,
  Undo2,
  Zap,
  Plus,
  Pencil,
  AlertTriangle,
  FileText,
  Link2,
  Link2Off,
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

  // Statistics (Subscribers-tab stat cards)
  const [stats, setStats] = useState(null);

  // Send DM dialog
  const [dmTarget, setDmTarget] = useState(null); // { id, displayName, username }
  const [dmText, setDmText] = useState('');
  const [sendingDm, setSendingDm] = useState(false);

  // Broadcast tab state
  const [broadcastText, setBroadcastText] = useState('');
  const [broadcastImageUrl, setBroadcastImageUrl] = useState('');
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

  // Templates (message library, V172)
  const [templates, setTemplates] = useState([]);
  const [templatesPage, setTemplatesPage] = useState(0);
  const [templatesTotalPages, setTemplatesTotalPages] = useState(0);
  const [loadingTemplates, setLoadingTemplates] = useState(false);
  const [templatesError, setTemplatesError] = useState(false);
  const [templateDialogOpen, setTemplateDialogOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null); // null = creating
  const [savingTemplate, setSavingTemplate] = useState(false);
  const [deletingTemplateId, setDeletingTemplateId] = useState(null);
  // Meta's quick-reply ceiling — mirrored from InstagramTemplateService so the UI stops you before
  // the backend has to.
  const MAX_BUTTONS = 13;
  const MAX_BUTTON_TITLE = 20;
  const emptyTemplateForm = {
    name: '', description: '', messageText: '', imageUrl: '', isActive: true,
    hasButtons: false, buttons: [],
  };
  const [templateForm, setTemplateForm] = useState(emptyTemplateForm);
  const [previewText, setPreviewText] = useState(null);
  const [previewing, setPreviewing] = useState(false);

  // Subscriber ↔ customer link dialog
  const [linkTarget, setLinkTarget] = useState(null);     // subscriber being linked
  const [linkPhone, setLinkPhone] = useState('');
  const [linkFound, setLinkFound] = useState(null);       // customer found by phone
  const [linkSearched, setLinkSearched] = useState(false);
  const [linkSearching, setLinkSearching] = useState(false);
  const [linking, setLinking] = useState(false);

  // Automation rules (birthday / win-back, V178)
  const [rules, setRules] = useState([]);
  const [rulesPage, setRulesPage] = useState(0);
  const [rulesTotalPages, setRulesTotalPages] = useState(0);
  const [loadingRules, setLoadingRules] = useState(false);
  const [rulesError, setRulesError] = useState(false);
  const [ruleTemplates, setRuleTemplates] = useState([]);
  const [ruleDialogOpen, setRuleDialogOpen] = useState(false);
  const [editingRule, setEditingRule] = useState(null); // null = creating
  const [savingRule, setSavingRule] = useState(false);
  const [deletingRuleId, setDeletingRuleId] = useState(null);
  const emptyRuleForm = { name: '', description: '', triggerType: 'BIRTHDAY', templateId: '', isActive: true, daysInactive: '14' };
  const [ruleForm, setRuleForm] = useState(emptyRuleForm);

  // Inbox (agent-takeover conversations, V179)
  const [conversations, setConversations] = useState([]);
  const [conversationsPage, setConversationsPage] = useState(0);
  const [conversationsTotalPages, setConversationsTotalPages] = useState(0);
  const [loadingConversations, setLoadingConversations] = useState(false);
  const [conversationsError, setConversationsError] = useState(false);
  const [openConversation, setOpenConversation] = useState(null); // { subscriber, messages } detail
  const [loadingConversation, setLoadingConversation] = useState(false);
  const [replyText, setReplyText] = useState('');
  const [replying, setReplying] = useState(false);
  const [handoffBusy, setHandoffBusy] = useState(false);

  // Config state
  const [configId, setConfigId] = useState(null);
  // True when the last settings load failed. A failed load leaves configId null even when a config
  // exists, so Save must NOT fall through to createConfig (which would mint a duplicate row).
  const [configLoadError, setConfigLoadError] = useState(false);
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
    privateReplyEnabled: false,
    privateReplyKeyword: '',
    privateReplyTemplate: '',
    privateReplyPromotionId: '',
  });
  const [hasAccessToken, setHasAccessToken] = useState(false);
  const [tokenHealthy, setTokenHealthy] = useState(true);
  const [tokenExpiresAt, setTokenExpiresAt] = useState(null);
  const [lastWebhookAt, setLastWebhookAt] = useState(null);
  const [testingConnection, setTestingConnection] = useState(false);
  const [hasAppSecret, setHasAppSecret] = useState(false);
  const [savingConfig, setSavingConfig] = useState(false);
  const [showSecret, setShowSecret] = useState(false);
  const [showToken, setShowToken] = useState(false);
  const [showVerifyToken, setShowVerifyToken] = useState(false);

  useEffect(() => {
    if (activeTab === 'subscribers') {
      loadSubscribers(0);
      loadStatistics();
    } else if (activeTab === 'broadcast') {
      loadCampaigns(0);
    } else if (activeTab === 'inbox') {
      loadConversations(0);
    } else if (activeTab === 'templates') {
      loadTemplates(0);
    } else if (activeTab === 'automation') {
      loadRules(0);
      loadRuleTemplates();
    } else if (activeTab === 'settings') {
      loadConfig();
    }
  }, [activeTab]);

  // Auto-refresh the campaign list while any campaign is still sending, so the sent/failed counts and
  // the status badge advance on their own — no manual Refresh. Silent (no spinner, no error toast), and
  // it tears down the moment nothing is SENDING or the tab changes.
  const hasSendingCampaign = campaigns.some((c) => c.status === 'SENDING');
  useEffect(() => {
    if (activeTab !== 'broadcast' || !hasSendingCampaign) return undefined;
    const timer = setInterval(() => loadCampaigns(campaignsPage, true), 4000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeTab, hasSendingCampaign, campaignsPage]);

  // -------------------------------------------------------------------------
  // Subscribers
  // -------------------------------------------------------------------------

  // The query defaults to the committed one so pagination keeps filtering, but callers that are
  // CLEARING the search pass '' explicitly: setAppliedQuery is async, so a bare loadSubscribers(0)
  // would still read the old appliedQuery from this render's closure and keep the results filtered.
  const loadSubscribers = async (page = 0, query = appliedQuery) => {
    setLoading(true);
    try {
      const response = query
        ? await instagramAPI.searchSubscribers(query, { page, size: 15 })
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

  // Reset both the input and the committed query, and reload the unfiltered list explicitly — so
  // "clear" actually returns to all subscribers instead of leaving them stuck on the last search.
  const clearSearch = () => {
    setSearchQuery('');
    setAppliedQuery('');
    loadSubscribers(0, '');
  };

  const handleSearch = async () => {
    if (!searchQuery.trim()) {
      clearSearch();
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
    if (e.key === 'Escape') clearSearch();
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
    // Show the same translated audience label the Select above uses, not the raw enum — a Russian
    // operator should not see "…всем подписчикам (REGISTERED)?" in an irreversible mass-DM confirm.
    const audienceLabel = t(broadcastTarget === 'ALL'
      ? 'instagram.broadcast.audienceAll'
      : 'instagram.broadcast.audienceRegistered');
    if (!window.confirm(t('instagram.broadcast.confirm', { target: audienceLabel }))) return;
    setBroadcasting(true);
    setBroadcastResult(null);
    try {
      // The broadcast now creates an async campaign (202): it returns immediately with the recipient
      // count and sends in the background, rather than blocking until every DM is delivered.
      const response = await instagramAPI.broadcast(
        broadcastText.trim(), broadcastTarget, broadcastImageUrl.trim() || null);
      setBroadcastResult(response.data?.recipientCount ?? 0);
      setBroadcastText('');
      setBroadcastImageUrl('');
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

  const loadCampaigns = async (page = 0, silent = false) => {
    if (!silent) setLoadingCampaigns(true);
    try {
      const response = await instagramAPI.getCampaigns({ page, size: 10 });
      setCampaigns(response.data.content || []);
      setCampaignsTotalPages(response.data.totalPages || 0);
      setCampaignsPage(page);
    } catch (error) {
      console.error('Failed to load campaigns:', error);
      if (!silent) notifyError(error);   // a background poll stays quiet on a transient failure
    } finally {
      if (!silent) setLoadingCampaigns(false);
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
  // Inbox (agent-takeover conversations, V179)
  // -------------------------------------------------------------------------

  const loadConversations = async (page = 0) => {
    setLoadingConversations(true);
    setConversationsError(false);
    try {
      const response = await instagramAPI.getConversations({ page, size: 20 });
      setConversations(response.data.content || []);
      setConversationsTotalPages(response.data.totalPages || 0);
      setConversationsPage(page);
    } catch (error) {
      console.error('Failed to load conversations:', error);
      setConversationsError(true);
    } finally {
      setLoadingConversations(false);
    }
  };

  const openConversationDetail = async (subscriberId) => {
    setLoadingConversation(true);
    setReplyText('');
    try {
      const response = await instagramAPI.getConversation(subscriberId);
      setOpenConversation(response.data);
    } catch (error) {
      console.error('Failed to load conversation:', error);
      notifyWarning(t('instagram.inbox.loadError'));
    } finally {
      setLoadingConversation(false);
    }
  };

  const handleReply = async () => {
    if (!replyText.trim() || !openConversation?.subscriber) return;
    const subscriberId = openConversation.subscriber.id;
    setReplying(true);
    try {
      const response = await instagramAPI.replyConversation(subscriberId, replyText.trim());
      if (response?.data?.sent === false) {
        // 200 {"sent": false} — no active config, circuit breaker open, or the 24h window closed.
        // Keep the typed text so nothing is silently lost.
        notifyWarning(t('instagram.inbox.replyFailed'));
        return;
      }
      notifySuccess(t('instagram.inbox.replySent'));
      setReplyText('');
      await openConversationDetail(subscriberId); // refresh transcript with the just-sent line
    } catch (error) {
      console.error('Failed to send reply:', error);
      notifyWarning(t('instagram.inbox.replyFailed'));
    } finally {
      setReplying(false);
    }
  };

  const handleTakeover = async () => {
    if (!openConversation?.subscriber) return;
    const subscriberId = openConversation.subscriber.id;
    setHandoffBusy(true);
    try {
      const response = await instagramAPI.takeoverConversation(subscriberId);
      // Reflect the new humanHandoffUntil without a full reload of the transcript.
      setOpenConversation((prev) => (prev ? { ...prev, subscriber: response.data } : prev));
      notifySuccess(t('instagram.inbox.takenOver'));
      loadConversations(conversationsPage);
    } catch (error) {
      console.error('Failed to take over conversation:', error);
      notifyWarning(t('instagram.inbox.handoffError'));
    } finally {
      setHandoffBusy(false);
    }
  };

  const handleRelease = async () => {
    if (!openConversation?.subscriber) return;
    const subscriberId = openConversation.subscriber.id;
    setHandoffBusy(true);
    try {
      const response = await instagramAPI.releaseConversation(subscriberId);
      setOpenConversation((prev) => (prev ? { ...prev, subscriber: response.data } : prev));
      notifySuccess(t('instagram.inbox.released'));
      loadConversations(conversationsPage);
    } catch (error) {
      console.error('Failed to release conversation:', error);
      notifyWarning(t('instagram.inbox.handoffError'));
    } finally {
      setHandoffBusy(false);
    }
  };

  // -------------------------------------------------------------------------
  // Templates (message library, V172)
  // -------------------------------------------------------------------------

  const loadTemplates = async (page = 0) => {
    setLoadingTemplates(true);
    setTemplatesError(false);
    try {
      const response = await instagramAPI.getTemplates({ page, size: 20 });
      setTemplates(response.data.content || []);
      setTemplatesTotalPages(response.data.totalPages || 0);
      setTemplatesPage(page);
    } catch (error) {
      console.error('Failed to load templates:', error);
      setTemplatesError(true);
    } finally {
      setLoadingTemplates(false);
    }
  };

  const openNewTemplate = () => {
    setEditingTemplate(null);
    setTemplateForm(emptyTemplateForm);
    setPreviewText(null);
    setTemplateDialogOpen(true);
  };

  const openEditTemplate = (tpl) => {
    setEditingTemplate(tpl);
    setTemplateForm({
      name: tpl.name || '',
      description: tpl.description || '',
      messageText: tpl.messageText || '',
      imageUrl: tpl.imageUrl || '',
      isActive: tpl.isActive !== false,
      hasButtons: tpl.hasButtons === true,
      buttons: (tpl.buttonsConfig || []).map((b) => ({
        title: b.title || '', payload: b.payload || '',
      })),
    });
    setPreviewText(null);
    setTemplateDialogOpen(true);
  };

  const handleSaveTemplate = async () => {
    if (!templateForm.name.trim() || !templateForm.messageText.trim()) return;
    setSavingTemplate(true);
    try {
      const imageUrl = templateForm.imageUrl.trim();
      // Drop rows the operator started and abandoned — a half-typed button would be rejected by the
      // backend's validation and block the whole save.
      const cleanButtons = templateForm.buttons
        .map((b) => ({ title: b.title.trim(), payload: b.payload.trim() }))
        .filter((b) => b.title || b.payload);
      const payload = {
        name: templateForm.name.trim(),
        description: templateForm.description.trim() || null,
        messageText: templateForm.messageText,
        imageUrl: imageUrl || null,
        hasImage: !!imageUrl,
        isActive: templateForm.isActive,
        hasButtons: templateForm.hasButtons && cleanButtons.length > 0,
        buttonsConfig: cleanButtons.length > 0 ? cleanButtons : null,
      };
      if (editingTemplate) {
        await instagramAPI.updateTemplate(editingTemplate.id, payload);
        notifySuccess(t('instagram.templates.updated'));
      } else {
        await instagramAPI.createTemplate(payload);
        notifySuccess(t('instagram.templates.created'));
      }
      setTemplateDialogOpen(false);
      loadTemplates(editingTemplate ? templatesPage : 0);
    } catch (error) {
      console.error('Failed to save template:', error);
      notifyError(error?.response?.data?.message || t('instagram.templates.saveError'));
    } finally {
      setSavingTemplate(false);
    }
  };

  const handlePreviewTemplate = async () => {
    if (!editingTemplate) return;
    setPreviewing(true);
    try {
      // Sample values for the placeholders the automation scheduler actually substitutes, so the
      // preview shows what a real send looks like rather than raw {name} braces.
      const response = await instagramAPI.previewTemplate(editingTemplate.id, {
        name: 'Dilnoza', first_name: 'Dilnoza',
      });
      setPreviewText(response.data?.preview ?? '');
    } catch (error) {
      console.error('Failed to preview template:', error);
      notifyWarning(t('instagram.templates.previewError'));
    } finally {
      setPreviewing(false);
    }
  };

  const handleDeleteTemplate = async (tpl) => {
    if (!window.confirm(t('instagram.templates.confirmDelete', { name: tpl.name }))) return;
    setDeletingTemplateId(tpl.id);
    try {
      await instagramAPI.deleteTemplate(tpl.id);
      notifySuccess(t('instagram.templates.deleted'));
      loadTemplates(templatesPage);
    } catch (error) {
      console.error('Failed to delete template:', error);
      // A template an automation rule still points at is refused by the backend (ON DELETE RESTRICT).
      notifyError(error?.response?.data?.message || t('instagram.templates.deleteError'));
    } finally {
      setDeletingTemplateId(null);
    }
  };

  // -------------------------------------------------------------------------
  // Subscriber ↔ customer link / unlink / erase
  // -------------------------------------------------------------------------

  const openLinkDialog = (subscriber) => {
    setLinkTarget(subscriber);
    setLinkPhone(subscriber.phone || '');   // the wizard-collected phone is the best first guess
    setLinkFound(null);
    setLinkSearched(false);
  };

  const handleLinkSearch = async () => {
    if (!linkPhone.trim()) return;
    setLinkSearching(true);
    setLinkFound(null);
    try {
      const response = await customerAPI.getByPhone(linkPhone.trim());
      // The endpoint answers 200 with a null payload when nothing matches — not a 404.
      setLinkFound(response.data?.data ?? null);
    } catch (error) {
      console.error('Customer lookup failed:', error);
      setLinkFound(null);
    } finally {
      setLinkSearched(true);
      setLinkSearching(false);
    }
  };

  const handleLinkConfirm = async () => {
    if (!linkTarget || !linkFound?.id) return;
    setLinking(true);
    try {
      await instagramAPI.linkSubscriber(linkTarget.id, linkFound.id);
      notifySuccess(t('instagram.subscribers.linked'));
      setLinkTarget(null);
      loadSubscribers(subscriberPage);
    } catch (error) {
      console.error('Failed to link subscriber:', error);
      notifyError(t('instagram.subscribers.linkError'));
    } finally {
      setLinking(false);
    }
  };

  const handleUnlink = async (subscriber) => {
    try {
      await instagramAPI.unlinkSubscriber(subscriber.id);
      notifySuccess(t('instagram.subscribers.unlinked'));
      loadSubscribers(subscriberPage);
    } catch (error) {
      console.error('Failed to unlink subscriber:', error);
      notifyError(t('instagram.subscribers.linkError'));
    }
  };

  const handleDeleteSubscriber = async (subscriber) => {
    const label = subscriber.displayName || subscriber.username || subscriber.igsid;
    if (!window.confirm(t('instagram.subscribers.confirmDelete', { name: label }))) return;
    try {
      await instagramAPI.deleteSubscriber(subscriber.id);
      notifySuccess(t('instagram.subscribers.deleted'));
      loadSubscribers(subscriberPage);
      loadStatistics();
    } catch (error) {
      console.error('Failed to delete subscriber:', error);
      notifyError(t('instagram.subscribers.deleteError'));
    }
  };

  // -------------------------------------------------------------------------
  // Automation rules (birthday / win-back, V178)
  // -------------------------------------------------------------------------

  const loadRules = async (page = 0) => {
    setLoadingRules(true);
    setRulesError(false);
    try {
      const response = await instagramAPI.getAutomationRules({ page, size: 20 });
      setRules(response.data.content || []);
      setRulesTotalPages(response.data.totalPages || 0);
      setRulesPage(page);
    } catch (error) {
      console.error('Failed to load automation rules:', error);
      setRulesError(true);
    } finally {
      setLoadingRules(false);
    }
  };

  const loadRuleTemplates = async () => {
    try {
      const response = await instagramAPI.getTemplates({ page: 0, size: 100 });
      setRuleTemplates(response.data.content || []);
    } catch (error) {
      // Non-fatal: the form still opens; the template picker is just empty and the operator is told.
      console.error('Failed to load templates for automation form:', error);
      setRuleTemplates([]);
    }
  };

  const openNewRule = () => {
    setEditingRule(null);
    setRuleForm(emptyRuleForm);
    setRuleDialogOpen(true);
  };

  const openEditRule = (rule) => {
    setEditingRule(rule);
    setRuleForm({
      name: rule.name || '',
      description: rule.description || '',
      triggerType: rule.triggerType || 'BIRTHDAY',
      templateId: rule.templateId != null ? String(rule.templateId) : '',
      isActive: rule.isActive !== false,
      daysInactive: rule.conditions?.days_inactive != null ? String(rule.conditions.days_inactive) : '14',
    });
    setRuleDialogOpen(true);
  };

  const buildRulePayload = (form) => ({
    name: form.name.trim(),
    description: form.description.trim() || null,
    triggerType: form.triggerType,
    templateId: Number(form.templateId),
    isActive: form.isActive,
    // days_inactive only applies to WIN_BACK; BIRTHDAY carries no conditions.
    conditions: form.triggerType === 'WIN_BACK' && form.daysInactive
      ? { days_inactive: Number(form.daysInactive) }
      : null,
  });

  const handleSaveRule = async () => {
    if (!ruleForm.name.trim() || !ruleForm.templateId) return;
    setSavingRule(true);
    try {
      const payload = buildRulePayload(ruleForm);
      if (editingRule) {
        await instagramAPI.updateAutomationRule(editingRule.id, payload);
        notifySuccess(t('instagram.automation.updated'));
      } else {
        await instagramAPI.createAutomationRule(payload);
        notifySuccess(t('instagram.automation.created'));
      }
      setRuleDialogOpen(false);
      loadRules(editingRule ? rulesPage : 0);
    } catch (error) {
      console.error('Failed to save automation rule:', error);
      // Surface the backend's own message (duplicate name, non-zero delay, missing template) when present.
      notifyError(error?.response?.data?.message || t('instagram.automation.saveError'));
    } finally {
      setSavingRule(false);
    }
  };

  const handleToggleRuleActive = async (rule) => {
    try {
      await instagramAPI.updateAutomationRule(rule.id, {
        name: rule.name,
        description: rule.description,
        triggerType: rule.triggerType,
        templateId: rule.templateId,
        isActive: !rule.isActive,
        conditions: rule.conditions,
      });
      loadRules(rulesPage);
    } catch (error) {
      console.error('Failed to toggle automation rule:', error);
      notifyError(error?.response?.data?.message || t('instagram.automation.saveError'));
    }
  };

  const handleDeleteRule = async (rule) => {
    if (!window.confirm(t('instagram.automation.confirmDelete', { name: rule.name }))) return;
    setDeletingRuleId(rule.id);
    try {
      await instagramAPI.deleteAutomationRule(rule.id);
      notifySuccess(t('instagram.automation.deleted'));
      loadRules(rulesPage);
    } catch (error) {
      console.error('Failed to delete automation rule:', error);
      notifyError(t('instagram.automation.deleteError'));
    } finally {
      setDeletingRuleId(null);
    }
  };

  const loadStatistics = async () => {
    try {
      const response = await instagramAPI.getStatistics();
      setStats(response.data);
    } catch (error) {
      // Non-fatal: the subscriber list still renders without the stat cards.
      console.error('Failed to load Instagram statistics:', error);
    }
  };

  // -------------------------------------------------------------------------
  // Config
  // -------------------------------------------------------------------------

  const loadConfig = async () => {
    setLoading(true);
    try {
      const response = await instagramAPI.getConfigs();
      setConfigLoadError(false);
      const configs = response.data || [];
      if (configs.length > 0) {
        const c = configs[0];
        setConfigId(c.id);
        setHasAccessToken(c.hasAccessToken);
        setHasAppSecret(c.hasAppSecret);
        setTokenHealthy(c.tokenHealthy !== false);   // undefined (older payload) reads as healthy
        setTokenExpiresAt(c.tokenExpiresAt || null);
        setLastWebhookAt(c.lastWebhookReceivedAt || null);
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
          privateReplyEnabled:     c.privateReplyEnabled ?? false,
          privateReplyKeyword:     c.privateReplyKeyword || '',
          privateReplyTemplate:    c.privateReplyTemplate || '',
          privateReplyPromotionId: c.privateReplyPromotionId ?? '',
        });
      }
    } catch (error) {
      // A failed reload leaves configId null even when a config exists, so Save must not fire
      // createConfig (a duplicate row). Flag it: the Save button is disabled until a load succeeds.
      setConfigLoadError(true);
      console.error('Failed to load Instagram config:', error);
      notifyError(error);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveConfig = async () => {
    // If the settings never loaded, we can't tell whether a config already exists — saving would risk
    // a duplicate. The Save button is disabled in this state; this guards the path defensively.
    if (configLoadError) return;
    setSavingConfig(true);
    try {
      const data = { ...configForm };
      // Don't send blank secrets — backend interprets null as "keep existing"
      if (!data.appSecret)   delete data.appSecret;
      if (!data.accessToken) delete data.accessToken;
      if (!data.verifyToken) delete data.verifyToken;
      // Promotion id is a Long on the backend: send '' (unset) as null, not an unparseable empty string.
      data.privateReplyPromotionId =
        data.privateReplyPromotionId === '' || data.privateReplyPromotionId == null
          ? null
          : Number(data.privateReplyPromotionId);

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

  const handleTestConnection = async () => {
    if (!configId) return;
    setTestingConnection(true);
    try {
      const { data } = await instagramAPI.testConnection(configId);
      if (data?.ok) {
        notifySuccess(t('instagram.settings.testConnectionOk', { name: data.username || data.accountId || '' }));
      } else {
        notifyWarning(t('instagram.settings.testConnectionFailed', { reason: data?.failure || 'UNKNOWN' }));
      }
      loadConfig();   // a failed test may have flipped tokenHealthy — refresh the banner
    } catch (error) {
      console.error('Instagram connection test failed:', error);
      notifyError(error);
    } finally {
      setTestingConnection(false);
    }
  };

  // -------------------------------------------------------------------------
  // Render helpers
  // -------------------------------------------------------------------------

  // The callback URL Meta calls must point at the API origin, not the browser's. In a split-origin
  // deployment (SPA and API on different hosts) window.location is the SPA host — a URL Meta could
  // never reach. VITE_API_URL already carries the "/api/v1" base, and the webhook lives one segment
  // beyond it, so derive it from there; fall back to the current origin for same-origin dev.
  const apiBase = (import.meta.env.VITE_API_URL || `${window.location.origin}/api/v1`).replace(/\/+$/, '');
  const webhookUrl = `${apiBase}/instagram/webhook`;

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
          <TabsTrigger value="inbox" className="flex items-center gap-2">
            <InboxIcon className="h-4 w-4" />
            {t('instagram.tabs.inbox')}
          </TabsTrigger>
          <TabsTrigger value="templates" className="flex items-center gap-2">
            <FileText className="h-4 w-4" />
            {t('instagram.tabs.templates')}
          </TabsTrigger>
          <TabsTrigger value="automation" className="flex items-center gap-2">
            <Zap className="h-4 w-4" />
            {t('instagram.tabs.automation')}
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
              {/* Statistics — subscriber counts + instagram_logs message breakdown (V171/V173) */}
              {stats && (
                <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
                  {[
                    ['totalSubscribers', stats.totalSubscribers],
                    ['activeSubscribers', stats.activeSubscribers],
                    ['registeredSubscribers', stats.registeredSubscribers],
                    ['newThisWeek', stats.newThisWeek],
                    ['messagesSent', stats.sentMessages],
                    ['messagesFailed', stats.failedMessages],
                  ].map(([key, value]) => (
                    <div key={key} className="rounded-lg border bg-card p-3">
                      <p className="text-2xl font-bold">{value ?? 0}</p>
                      <p className="text-xs text-muted-foreground">{t(`instagram.stats.${key}`)}</p>
                    </div>
                  ))}
                </div>
              )}

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
                  <Button variant="ghost" onClick={clearSearch}>
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
                          {s.marketingOptIn === false && (
                            <Badge variant="outline" className="ml-1">{t('instagram.subscribers.optedOut')}</Badge>
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
                            {/* Link / unlink to a loyalty customer — the escape hatch when the
                                wizard's phone-based auto-link didn't fire. */}
                            {s.customerId ? (
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 text-amber-600 hover:text-amber-700"
                                title={t('instagram.subscribers.unlink')}
                                onClick={() => handleUnlink(s)}
                              >
                                <Link2Off className="h-4 w-4" />
                              </Button>
                            ) : (
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 text-indigo-500 hover:text-indigo-700"
                                title={t('instagram.subscribers.link')}
                                onClick={() => openLinkDialog(s)}
                              >
                                <Link2 className="h-4 w-4" />
                              </Button>
                            )}
                            {/* Erase subscriber + PII. Irreversible; ADMIN/OWNER only server-side. */}
                            <Button
                              size="icon"
                              variant="ghost"
                              className="h-8 w-8 text-red-600 hover:text-red-800"
                              title={t('instagram.subscribers.delete')}
                              onClick={() => handleDeleteSubscriber(s)}
                            >
                              <Trash2 className="h-4 w-4" />
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
                <Label htmlFor="ig-broadcast-audience">{t('instagram.broadcast.audience')}</Label>
                <Select value={broadcastTarget} onValueChange={setBroadcastTarget}>
                  <SelectTrigger id="ig-broadcast-audience">
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
                <Label htmlFor="ig-broadcast-message">{t('instagram.broadcast.message')}</Label>
                <Textarea
                  id="ig-broadcast-message"
                  value={broadcastText}
                  onChange={(e) => setBroadcastText(e.target.value)}
                  placeholder={t('instagram.broadcast.messagePlaceholder')}
                  rows={5}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="ig-broadcast-image">{t('instagram.broadcast.imageUrl')}</Label>
                <Input
                  id="ig-broadcast-image"
                  value={broadcastImageUrl}
                  onChange={(e) => setBroadcastImageUrl(e.target.value)}
                  placeholder={t('instagram.broadcast.imageUrlPlaceholder')}
                />
                <p className="text-xs text-muted-foreground">{t('instagram.broadcast.imageUrlHint')}</p>
              </div>

              <p className="text-xs text-muted-foreground">{t('instagram.broadcast.windowNote')}</p>

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
              <div className="flex items-center gap-3">
                {hasSendingCampaign && (
                  <span className="flex items-center gap-1.5 text-xs text-blue-600">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-blue-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-blue-500"></span>
                    </span>
                    {t('instagram.campaigns.autoUpdating')}
                  </span>
                )}
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => loadCampaigns(campaignsPage)}
                  disabled={loadingCampaigns}
                >
                  <RefreshCw className={`h-4 w-4 mr-2 ${loadingCampaigns ? 'animate-spin' : ''}`} />
                  {t('common.refresh', 'Refresh')}
                </Button>
              </div>
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
        {/* Inbox Tab (agent-takeover conversations, V179)                      */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="inbox">
          <Card>
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <CardTitle>{t('instagram.inbox.title')}</CardTitle>
                  <CardDescription>{t('instagram.inbox.description')}</CardDescription>
                </div>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => loadConversations(conversationsPage)}
                  disabled={loadingConversations}
                >
                  <RefreshCw className={`h-4 w-4 ${loadingConversations ? 'animate-spin' : ''}`} />
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {conversationsError ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  {t('instagram.inbox.loadError')}
                </div>
              ) : conversations.length === 0 && !loadingConversations ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  {t('instagram.inbox.empty')}
                </div>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('instagram.inbox.colSubscriber')}</TableHead>
                        <TableHead>{t('instagram.inbox.colPreview')}</TableHead>
                        <TableHead>{t('instagram.inbox.colLastMessage')}</TableHead>
                        <TableHead>{t('instagram.inbox.colStatus')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {conversations.map((c) => {
                        const handoffActive =
                          c.humanHandoffUntil && new Date(c.humanHandoffUntil).getTime() > Date.now();
                        return (
                          <TableRow
                            key={c.subscriberId}
                            className="cursor-pointer"
                            onClick={() => openConversationDetail(c.subscriberId)}
                          >
                            <TableCell>
                              <div className="font-medium">
                                {c.displayName || c.username || `#${c.subscriberId}`}
                              </div>
                              {c.username && (
                                <div className="text-xs text-muted-foreground">@{c.username}</div>
                              )}
                            </TableCell>
                            <TableCell className="max-w-xs truncate text-sm text-muted-foreground">
                              {c.preview || '—'}
                            </TableCell>
                            <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                              {c.lastMessageAt ? new Date(c.lastMessageAt).toLocaleString() : '—'}
                            </TableCell>
                            <TableCell>
                              {c.isBlocked ? (
                                <Badge className="bg-red-100 text-red-800">
                                  {t('instagram.subscribers.blocked')}
                                </Badge>
                              ) : handoffActive ? (
                                <Badge className="bg-blue-100 text-blue-800">
                                  {t('instagram.inbox.agentHandling')}
                                </Badge>
                              ) : (
                                <Badge className="bg-gray-100 text-gray-700">
                                  {t('instagram.inbox.botHandling')}
                                </Badge>
                              )}
                            </TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>

                  {conversationsTotalPages > 1 && (
                    <div className="mt-4 flex items-center justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => loadConversations(conversationsPage - 1)}
                        disabled={conversationsPage === 0 || loadingConversations}
                      >
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-sm text-muted-foreground">
                        {conversationsPage + 1} / {conversationsTotalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => loadConversations(conversationsPage + 1)}
                        disabled={conversationsPage >= conversationsTotalPages - 1 || loadingConversations}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          {/* Conversation detail — transcript + reply + human handoff */}
          <Dialog
            open={!!openConversation}
            onOpenChange={(open) => {
              if (!open) {
                setOpenConversation(null);
                setReplyText('');
              }
            }}
          >
            <DialogContent className="max-w-2xl">
              {openConversation && (() => {
                const sub = openConversation.subscriber;
                const handoffActive =
                  sub?.humanHandoffUntil && new Date(sub.humanHandoffUntil).getTime() > Date.now();
                return (
                  <>
                    <DialogHeader>
                      <DialogTitle className="flex items-center gap-2">
                        <MessageSquare className="h-4 w-4" />
                        {sub?.displayName || sub?.username || `#${sub?.id}`}
                      </DialogTitle>
                      <DialogDescription>
                        {handoffActive
                          ? t('instagram.inbox.handoffUntil', {
                              date: new Date(sub.humanHandoffUntil).toLocaleString(),
                            })
                          : t('instagram.inbox.botHandlingDescription')}
                      </DialogDescription>
                    </DialogHeader>

                    {/* Take over / release */}
                    <div className="flex justify-end">
                      {handoffActive ? (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={handleRelease}
                          disabled={handoffBusy}
                        >
                          <Undo2 className="mr-2 h-4 w-4" />
                          {t('instagram.inbox.release')}
                        </Button>
                      ) : (
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={handleTakeover}
                          disabled={handoffBusy}
                        >
                          <UserCheck className="mr-2 h-4 w-4" />
                          {t('instagram.inbox.takeover')}
                        </Button>
                      )}
                    </div>

                    {/* Transcript */}
                    <div className="max-h-80 space-y-2 overflow-y-auto rounded-md border bg-muted/30 p-3">
                      {loadingConversation ? (
                        <div className="py-6 text-center text-sm text-muted-foreground">
                          {t('common.loading')}
                        </div>
                      ) : openConversation.messages.length === 0 ? (
                        <div className="py-6 text-center text-sm text-muted-foreground">
                          {t('instagram.inbox.noMessages')}
                        </div>
                      ) : (
                        openConversation.messages.map((m, i) => (
                          <div
                            key={i}
                            className={`flex ${m.direction === 'OUT' ? 'justify-end' : 'justify-start'}`}
                          >
                            <div
                              className={`max-w-[75%] rounded-lg px-3 py-2 text-sm ${
                                m.direction === 'OUT'
                                  ? 'bg-primary text-primary-foreground'
                                  : 'bg-background border'
                              }`}
                            >
                              <div className="whitespace-pre-wrap break-words">{m.text || '—'}</div>
                              <div
                                className={`mt-1 text-[10px] ${
                                  m.direction === 'OUT'
                                    ? 'text-primary-foreground/70'
                                    : 'text-muted-foreground'
                                }`}
                              >
                                {m.timestamp ? new Date(m.timestamp).toLocaleString() : ''}
                                {m.direction === 'OUT' && m.status ? ` · ${m.status}` : ''}
                              </div>
                            </div>
                          </div>
                        ))
                      )}
                    </div>

                    {/* Reply box */}
                    <div className="space-y-2">
                      <Textarea
                        value={replyText}
                        onChange={(e) => setReplyText(e.target.value)}
                        placeholder={t('instagram.inbox.replyPlaceholder')}
                        rows={3}
                        disabled={sub?.isBlocked}
                      />
                      {sub?.isBlocked && (
                        <p className="text-xs text-muted-foreground">
                          {t('instagram.inbox.blockedNotice')}
                        </p>
                      )}
                    </div>
                    <DialogFooter>
                      <Button
                        onClick={handleReply}
                        disabled={replying || !replyText.trim() || sub?.isBlocked}
                      >
                        <Send className="mr-2 h-4 w-4" />
                        {t('instagram.inbox.sendReply')}
                      </Button>
                    </DialogFooter>
                  </>
                );
              })()}
            </DialogContent>
          </Dialog>
        </TabsContent>

        {/* ------------------------------------------------------------------ */}
        {/* Templates Tab (message library, V172)                               */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="templates">
          <Card>
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <CardTitle>{t('instagram.templates.title')}</CardTitle>
                  <CardDescription>{t('instagram.templates.description')}</CardDescription>
                </div>
                <Button size="sm" onClick={openNewTemplate}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t('instagram.templates.newTemplate')}
                </Button>
              </div>
            </CardHeader>
            <CardContent>
              {templatesError ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  {t('instagram.templates.loadError')}
                </div>
              ) : templates.length === 0 && !loadingTemplates ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  {t('instagram.templates.empty')}
                </div>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('instagram.templates.colName')}</TableHead>
                        <TableHead>{t('instagram.templates.colMessage')}</TableHead>
                        <TableHead>{t('instagram.templates.colStatus')}</TableHead>
                        <TableHead className="text-right">{t('instagram.templates.colUsage')}</TableHead>
                        <TableHead className="text-right">{t('instagram.automation.colActions')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {templates.map((tpl) => (
                        <TableRow key={tpl.id}>
                          <TableCell>
                            <div className="font-medium">{tpl.name}</div>
                            {tpl.description && (
                              <div className="max-w-xs truncate text-xs text-muted-foreground">
                                {tpl.description}
                              </div>
                            )}
                          </TableCell>
                          <TableCell className="max-w-md">
                            <div className="truncate text-sm text-muted-foreground">
                              {tpl.messageText}
                            </div>
                          </TableCell>
                          <TableCell>
                            {tpl.isActive !== false ? (
                              <Badge className="bg-green-100 text-green-800">
                                {t('instagram.automation.active')}
                              </Badge>
                            ) : (
                              <Badge className="bg-gray-100 text-gray-700">
                                {t('instagram.automation.inactive')}
                              </Badge>
                            )}
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{tpl.usageCount ?? 0}</TableCell>
                          <TableCell>
                            <div className="flex justify-end gap-1">
                              <Button variant="ghost" size="sm" onClick={() => openEditTemplate(tpl)}>
                                <Pencil className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleDeleteTemplate(tpl)}
                                disabled={deletingTemplateId === tpl.id}
                              >
                                <Trash2 className="h-4 w-4 text-red-600" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>

                  {templatesTotalPages > 1 && (
                    <div className="mt-4 flex items-center justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => loadTemplates(templatesPage - 1)}
                        disabled={templatesPage === 0 || loadingTemplates}
                      >
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-sm text-muted-foreground">
                        {templatesPage + 1} / {templatesTotalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => loadTemplates(templatesPage + 1)}
                        disabled={templatesPage >= templatesTotalPages - 1 || loadingTemplates}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          {/* Create / edit template dialog */}
          <Dialog open={templateDialogOpen} onOpenChange={setTemplateDialogOpen}>
            <DialogContent className="max-w-xl">
              <DialogHeader>
                <DialogTitle>
                  {editingTemplate
                    ? t('instagram.templates.editTitle')
                    : t('instagram.templates.createTitle')}
                </DialogTitle>
                <DialogDescription>{t('instagram.templates.formHint')}</DialogDescription>
              </DialogHeader>

              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>{t('instagram.templates.fieldName')}</Label>
                  <Input
                    value={templateForm.name}
                    onChange={(e) => setTemplateForm({ ...templateForm, name: e.target.value })}
                    placeholder={t('instagram.templates.namePlaceholder')}
                    maxLength={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.templates.fieldDescription')}</Label>
                  <Input
                    value={templateForm.description}
                    onChange={(e) => setTemplateForm({ ...templateForm, description: e.target.value })}
                    placeholder={t('instagram.templates.descriptionPlaceholder')}
                    maxLength={500}
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.templates.fieldMessage')}</Label>
                  <Textarea
                    value={templateForm.messageText}
                    onChange={(e) => setTemplateForm({ ...templateForm, messageText: e.target.value })}
                    placeholder={t('instagram.templates.messagePlaceholder')}
                    rows={5}
                  />
                  <p className="text-xs text-muted-foreground">
                    {t('instagram.templates.placeholderHint')}
                  </p>
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.templates.fieldImageUrl')}</Label>
                  <Input
                    value={templateForm.imageUrl}
                    onChange={(e) => setTemplateForm({ ...templateForm, imageUrl: e.target.value })}
                    placeholder="https://…"
                    maxLength={500}
                  />
                </div>

                {/* Quick-reply buttons. Tapping one sends its payload back to the bot, so a payload of
                    ORDER drops the customer straight into the in-DM ordering flow. */}
                <div className="space-y-3 rounded-md border p-3">
                  <div className="flex items-center justify-between">
                    <Label>{t('instagram.templates.fieldButtons')}</Label>
                    <Switch
                      checked={templateForm.hasButtons}
                      onCheckedChange={(v) => setTemplateForm({ ...templateForm, hasButtons: v })}
                    />
                  </div>

                  {templateForm.hasButtons && (
                    <>
                      <p className="text-xs text-muted-foreground">
                        {t('instagram.templates.buttonsHint', { max: MAX_BUTTONS })}
                      </p>

                      {templateForm.buttons.map((btn, i) => (
                        <div key={i} className="flex items-start gap-2">
                          <div className="flex-1 space-y-1">
                            <Input
                              value={btn.title}
                              maxLength={MAX_BUTTON_TITLE}
                              placeholder={t('instagram.templates.buttonTitlePlaceholder')}
                              onChange={(e) => {
                                const buttons = [...templateForm.buttons];
                                buttons[i] = { ...buttons[i], title: e.target.value };
                                setTemplateForm({ ...templateForm, buttons });
                              }}
                            />
                            <p className="text-[10px] text-muted-foreground">
                              {btn.title.length}/{MAX_BUTTON_TITLE}
                            </p>
                          </div>
                          <Input
                            className="flex-1"
                            value={btn.payload}
                            placeholder={t('instagram.templates.buttonPayloadPlaceholder')}
                            onChange={(e) => {
                              const buttons = [...templateForm.buttons];
                              buttons[i] = { ...buttons[i], payload: e.target.value };
                              setTemplateForm({ ...templateForm, buttons });
                            }}
                          />
                          <Button
                            variant="ghost"
                            size="icon"
                            className="mt-0.5 h-9 w-9 shrink-0"
                            title={t('instagram.templates.removeButton')}
                            onClick={() => setTemplateForm({
                              ...templateForm,
                              buttons: templateForm.buttons.filter((_, idx) => idx !== i),
                            })}
                          >
                            <Trash2 className="h-4 w-4 text-red-600" />
                          </Button>
                        </div>
                      ))}

                      <Button
                        variant="outline"
                        size="sm"
                        disabled={templateForm.buttons.length >= MAX_BUTTONS}
                        onClick={() => setTemplateForm({
                          ...templateForm,
                          buttons: [...templateForm.buttons, { title: '', payload: 'ORDER' }],
                        })}
                      >
                        <Plus className="mr-2 h-4 w-4" />
                        {t('instagram.templates.addButton')}
                      </Button>
                    </>
                  )}
                </div>

                <div className="flex items-center justify-between">
                  <Label>{t('instagram.templates.fieldActive')}</Label>
                  <Switch
                    checked={templateForm.isActive}
                    onCheckedChange={(v) => setTemplateForm({ ...templateForm, isActive: v })}
                  />
                </div>

                {/* Preview — only meaningful for a saved template (the endpoint renders by id) */}
                {editingTemplate && (
                  <div className="space-y-2 rounded-md border bg-muted/30 p-3">
                    <div className="flex items-center justify-between">
                      <Label>{t('instagram.templates.preview')}</Label>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={handlePreviewTemplate}
                        disabled={previewing}
                      >
                        <Eye className="mr-2 h-4 w-4" />
                        {t('instagram.templates.previewButton')}
                      </Button>
                    </div>
                    {previewText !== null && (
                      <div className="whitespace-pre-wrap rounded bg-background p-2 text-sm">
                        {previewText || '—'}
                      </div>
                    )}
                    <p className="text-xs text-muted-foreground">
                      {t('instagram.templates.previewHint')}
                    </p>
                  </div>
                )}
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setTemplateDialogOpen(false)}>
                  {t('common.cancel')}
                </Button>
                <Button
                  onClick={handleSaveTemplate}
                  disabled={savingTemplate || !templateForm.name.trim() || !templateForm.messageText.trim()}
                >
                  <Save className="mr-2 h-4 w-4" />
                  {t('common.save')}
                </Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>
        </TabsContent>

        {/* ------------------------------------------------------------------ */}
        {/* Automation Tab (birthday / win-back rules, V178)                    */}
        {/* ------------------------------------------------------------------ */}
        <TabsContent value="automation">
          <Card>
            <CardHeader>
              <div className="flex items-start justify-between gap-4">
                <div>
                  <CardTitle>{t('instagram.automation.title')}</CardTitle>
                  <CardDescription>{t('instagram.automation.description')}</CardDescription>
                </div>
                <Button size="sm" onClick={openNewRule}>
                  <Plus className="mr-2 h-4 w-4" />
                  {t('instagram.automation.newRule')}
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* The 24-hour messaging window makes scheduled outbound largely undeliverable — the
                  caveat the operator must understand before relying on this. */}
              <div className="flex gap-3 rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <AlertTriangle className="mt-0.5 h-5 w-5 shrink-0" />
                <p>{t('instagram.automation.windowWarning')}</p>
              </div>

              {rulesError ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  {t('instagram.automation.loadError')}
                </div>
              ) : rules.length === 0 && !loadingRules ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  {t('instagram.automation.empty')}
                </div>
              ) : (
                <>
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('instagram.automation.colName')}</TableHead>
                        <TableHead>{t('instagram.automation.colTrigger')}</TableHead>
                        <TableHead>{t('instagram.automation.colTemplate')}</TableHead>
                        <TableHead>{t('instagram.automation.colStatus')}</TableHead>
                        <TableHead className="text-right">{t('instagram.automation.colSent')}</TableHead>
                        <TableHead>{t('instagram.automation.colLastTriggered')}</TableHead>
                        <TableHead className="text-right">{t('instagram.automation.colActions')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {rules.map((rule) => (
                        <TableRow key={rule.id}>
                          <TableCell>
                            <div className="font-medium">{rule.name}</div>
                            {rule.description && (
                              <div className="max-w-xs truncate text-xs text-muted-foreground">
                                {rule.description}
                              </div>
                            )}
                          </TableCell>
                          <TableCell>
                            <Badge
                              className={
                                rule.triggerType === 'BIRTHDAY'
                                  ? 'bg-pink-100 text-pink-800'
                                  : 'bg-indigo-100 text-indigo-800'
                              }
                            >
                              {t(`instagram.automation.trigger.${rule.triggerType}`)}
                            </Badge>
                            {rule.triggerType === 'WIN_BACK' && rule.conditions?.days_inactive != null && (
                              <div className="mt-1 text-xs text-muted-foreground">
                                {t('instagram.automation.daysInactiveShort', {
                                  days: rule.conditions.days_inactive,
                                })}
                              </div>
                            )}
                          </TableCell>
                          <TableCell className="text-sm">{rule.templateName || '—'}</TableCell>
                          <TableCell>
                            <button
                              type="button"
                              onClick={() => handleToggleRuleActive(rule)}
                              title={t('instagram.automation.toggleHint')}
                            >
                              {rule.isActive ? (
                                <Badge className="bg-green-100 text-green-800">
                                  {t('instagram.automation.active')}
                                </Badge>
                              ) : (
                                <Badge className="bg-gray-100 text-gray-700">
                                  {t('instagram.automation.inactive')}
                                </Badge>
                              )}
                            </button>
                          </TableCell>
                          <TableCell className="text-right tabular-nums">{rule.sentCount ?? 0}</TableCell>
                          <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                            {rule.lastTriggeredAt ? new Date(rule.lastTriggeredAt).toLocaleString() : '—'}
                          </TableCell>
                          <TableCell>
                            <div className="flex justify-end gap-1">
                              <Button variant="ghost" size="sm" onClick={() => openEditRule(rule)}>
                                <Pencil className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => handleDeleteRule(rule)}
                                disabled={deletingRuleId === rule.id}
                              >
                                <Trash2 className="h-4 w-4 text-red-600" />
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>

                  {rulesTotalPages > 1 && (
                    <div className="flex items-center justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => loadRules(rulesPage - 1)}
                        disabled={rulesPage === 0 || loadingRules}
                      >
                        <ChevronLeft className="h-4 w-4" />
                      </Button>
                      <span className="text-sm text-muted-foreground">
                        {rulesPage + 1} / {rulesTotalPages}
                      </span>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => loadRules(rulesPage + 1)}
                        disabled={rulesPage >= rulesTotalPages - 1 || loadingRules}
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </div>
                  )}
                </>
              )}
            </CardContent>
          </Card>

          {/* Create / edit rule dialog */}
          <Dialog open={ruleDialogOpen} onOpenChange={setRuleDialogOpen}>
            <DialogContent>
              <DialogHeader>
                <DialogTitle>
                  {editingRule
                    ? t('instagram.automation.editTitle')
                    : t('instagram.automation.createTitle')}
                </DialogTitle>
                <DialogDescription>{t('instagram.automation.formHint')}</DialogDescription>
              </DialogHeader>

              <div className="space-y-4">
                <div className="space-y-2">
                  <Label>{t('instagram.automation.fieldName')}</Label>
                  <Input
                    value={ruleForm.name}
                    onChange={(e) => setRuleForm({ ...ruleForm, name: e.target.value })}
                    placeholder={t('instagram.automation.namePlaceholder')}
                    maxLength={100}
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.automation.fieldDescription')}</Label>
                  <Input
                    value={ruleForm.description}
                    onChange={(e) => setRuleForm({ ...ruleForm, description: e.target.value })}
                    placeholder={t('instagram.automation.descriptionPlaceholder')}
                  />
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <div className="space-y-2">
                    <Label>{t('instagram.automation.fieldTrigger')}</Label>
                    <Select
                      value={ruleForm.triggerType}
                      onValueChange={(v) => setRuleForm({ ...ruleForm, triggerType: v })}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="BIRTHDAY">{t('instagram.automation.trigger.BIRTHDAY')}</SelectItem>
                        <SelectItem value="WIN_BACK">{t('instagram.automation.trigger.WIN_BACK')}</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  {ruleForm.triggerType === 'WIN_BACK' && (
                    <div className="space-y-2">
                      <Label>{t('instagram.automation.fieldDaysInactive')}</Label>
                      <Input
                        type="number"
                        min="1"
                        value={ruleForm.daysInactive}
                        onChange={(e) => setRuleForm({ ...ruleForm, daysInactive: e.target.value })}
                      />
                    </div>
                  )}
                </div>

                <div className="space-y-2">
                  <Label>{t('instagram.automation.fieldTemplate')}</Label>
                  {ruleTemplates.length === 0 ? (
                    <p className="text-sm text-muted-foreground">
                      {t('instagram.automation.noTemplates')}
                    </p>
                  ) : (
                    <Select
                      value={ruleForm.templateId}
                      onValueChange={(v) => setRuleForm({ ...ruleForm, templateId: v })}
                    >
                      <SelectTrigger>
                        <SelectValue placeholder={t('instagram.automation.templatePlaceholder')} />
                      </SelectTrigger>
                      <SelectContent>
                        {ruleTemplates.map((tpl) => (
                          <SelectItem key={tpl.id} value={String(tpl.id)}>
                            {tpl.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                </div>

                <div className="flex items-center justify-between">
                  <Label>{t('instagram.automation.fieldActive')}</Label>
                  <Switch
                    checked={ruleForm.isActive}
                    onCheckedChange={(v) => setRuleForm({ ...ruleForm, isActive: v })}
                  />
                </div>
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={() => setRuleDialogOpen(false)}>
                  {t('common.cancel')}
                </Button>
                <Button
                  onClick={handleSaveRule}
                  disabled={savingRule || !ruleForm.name.trim() || !ruleForm.templateId}
                >
                  <Save className="mr-2 h-4 w-4" />
                  {t('common.save')}
                </Button>
              </DialogFooter>
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
                  <Label htmlFor="ig-app-id">{t('instagram.settings.appId')}</Label>
                  <Input
                    id="ig-app-id"
                    value={configForm.appId}
                    onChange={(e) => setConfigForm({ ...configForm, appId: e.target.value })}
                    placeholder="123456789"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="ig-app-secret">{t('instagram.settings.appSecret')}</Label>
                  <div className="relative">
                    <Input
                      id="ig-app-secret"
                      type={showSecret ? 'text' : 'password'}
                      className="pr-10"
                      value={configForm.appSecret}
                      onChange={(e) => setConfigForm({ ...configForm, appSecret: e.target.value })}
                      placeholder={hasAppSecret ? '••••••••' : t('instagram.settings.enterSecret')}
                    />
                    <button
                      type="button"
                      aria-label={t('instagram.settings.appSecret')}
                      aria-pressed={showSecret}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowSecret(!showSecret)}
                    >
                      {showSecret ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.secretHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="ig-access-token">{t('instagram.settings.accessToken')}</Label>
                  <div className="relative">
                    <Input
                      id="ig-access-token"
                      type={showToken ? 'text' : 'password'}
                      className="pr-10"
                      value={configForm.accessToken}
                      onChange={(e) => setConfigForm({ ...configForm, accessToken: e.target.value })}
                      placeholder={hasAccessToken ? '••••••••' : t('instagram.settings.enterToken')}
                    />
                    <button
                      type="button"
                      aria-label={t('instagram.settings.accessToken')}
                      aria-pressed={showToken}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowToken(!showToken)}
                    >
                      {showToken ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.tokenHint')}</p>
                  {hasAccessToken && !tokenHealthy && (
                    <div className="rounded-md bg-red-50 border border-red-200 p-2 text-xs text-red-800">
                      {t('instagram.settings.tokenUnhealthy')}
                    </div>
                  )}
                  {hasAccessToken && tokenHealthy && tokenExpiresAt &&
                    (new Date(tokenExpiresAt).getTime() - Date.now()) < 7 * 24 * 3600 * 1000 && (
                    <div className="rounded-md bg-amber-50 border border-amber-200 p-2 text-xs text-amber-800">
                      {t('instagram.settings.tokenExpiring', { date: new Date(tokenExpiresAt).toLocaleDateString() })}
                    </div>
                  )}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="ig-account-id">{t('instagram.settings.accountId')}</Label>
                  <Input
                    id="ig-account-id"
                    value={configForm.instagramAccountId}
                    onChange={(e) => setConfigForm({ ...configForm, instagramAccountId: e.target.value })}
                    placeholder="17841400000000000"
                  />
                </div>

                {/* Webhook URL (read-only, for copy-paste into Meta dashboard) */}
                <div className="space-y-2">
                  <Label htmlFor="ig-webhook-url">{t('instagram.settings.webhookUrl')}</Label>
                  <Input
                    id="ig-webhook-url"
                    value={webhookUrl}
                    readOnly
                    className="bg-muted font-mono text-xs"
                    onClick={(e) => e.target.select()}
                  />
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.webhookHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="ig-verify-token">{t('instagram.settings.verifyToken')}</Label>
                  <div className="relative">
                    <Input
                      id="ig-verify-token"
                      type={showVerifyToken ? 'text' : 'password'}
                      className="pr-10"
                      value={configForm.verifyToken}
                      onChange={(e) => setConfigForm({ ...configForm, verifyToken: e.target.value })}
                      placeholder={t('instagram.settings.enterVerifyToken')}
                    />
                    <button
                      type="button"
                      aria-label={t('instagram.settings.verifyToken')}
                      aria-pressed={showVerifyToken}
                      className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                      onClick={() => setShowVerifyToken(!showVerifyToken)}
                    >
                      {showVerifyToken ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                  <p className="text-xs text-muted-foreground">{t('instagram.settings.verifyTokenHint')}</p>
                </div>

                <div className="flex items-center justify-between">
                  <Label htmlFor="ig-active">{t('instagram.settings.active')}</Label>
                  <Switch
                    id="ig-active"
                    checked={configForm.isActive}
                    onCheckedChange={(checked) => setConfigForm({ ...configForm, isActive: checked })}
                  />
                </div>

                {configId && (
                  <div className="border-t pt-4 flex items-center justify-between gap-3">
                    <span className="text-xs text-muted-foreground">
                      {t('instagram.settings.lastWebhook')}:{' '}
                      {lastWebhookAt
                        ? new Date(lastWebhookAt).toLocaleString()
                        : t('instagram.settings.lastWebhookNever')}
                    </span>
                    <Button variant="outline" size="sm" onClick={handleTestConnection} disabled={testingConnection}>
                      {testingConnection ? t('instagram.settings.testing') : t('instagram.settings.testConnection')}
                    </Button>
                  </div>
                )}

                {configId && (
                  // Separated, right-aligned, outline — NOT a full-width primary button. On mobile the
                  // cards stack, so a full-width destructive button here sat directly under the token
                  // fields where Save is expected; this makes the wipe clearly a secondary action.
                  <div className="border-t pt-4 flex justify-end">
                    <Button
                      variant="outline"
                      size="sm"
                      className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                      onClick={handleClearCredentials}
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      {t('instagram.settings.clearCredentials')}
                    </Button>
                  </div>
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
                  <Label htmlFor="ig-welcome-message">{t('instagram.settings.welcomeMessage')}</Label>
                  <Textarea
                    id="ig-welcome-message"
                    value={configForm.welcomeMessage}
                    onChange={(e) => setConfigForm({ ...configForm, welcomeMessage: e.target.value })}
                    placeholder={t('instagram.settings.welcomeMessagePlaceholder')}
                    rows={3}
                  />
                </div>

                <div className="flex items-center justify-between">
                  <div>
                    <Label htmlFor="ig-auto-reply">{t('instagram.settings.autoReply')}</Label>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {t('instagram.settings.autoReplyDescription')}
                    </p>
                  </div>
                  <Switch
                    id="ig-auto-reply"
                    checked={configForm.autoReplyEnabled}
                    onCheckedChange={(checked) => setConfigForm({ ...configForm, autoReplyEnabled: checked })}
                  />
                </div>

                {configForm.autoReplyEnabled && (
                  <div className="space-y-2">
                    <Label htmlFor="ig-auto-reply-template">{t('instagram.settings.autoReplyTemplate')}</Label>
                    <Textarea
                      id="ig-auto-reply-template"
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

                {/* Private replies — DM a commenter who types a keyword (V173) */}
                <div className="border-t pt-4 flex items-center justify-between">
                  <div>
                    <Label htmlFor="ig-private-reply">{t('instagram.settings.privateReply')}</Label>
                    <p className="text-xs text-muted-foreground mt-0.5">
                      {t('instagram.settings.privateReplyDescription')}
                    </p>
                  </div>
                  <Switch
                    id="ig-private-reply"
                    checked={configForm.privateReplyEnabled}
                    onCheckedChange={(checked) => setConfigForm({ ...configForm, privateReplyEnabled: checked })}
                  />
                </div>

                {configForm.privateReplyEnabled && (
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="ig-private-reply-keyword">{t('instagram.settings.privateReplyKeyword')}</Label>
                      <Input
                        id="ig-private-reply-keyword"
                        value={configForm.privateReplyKeyword}
                        onChange={(e) => setConfigForm({ ...configForm, privateReplyKeyword: e.target.value })}
                        placeholder={t('instagram.settings.privateReplyKeywordPlaceholder')}
                      />
                      <p className="text-xs text-muted-foreground">{t('instagram.settings.privateReplyKeywordHint')}</p>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="ig-private-reply-template">{t('instagram.settings.privateReplyTemplate')}</Label>
                      <Textarea
                        id="ig-private-reply-template"
                        value={configForm.privateReplyTemplate}
                        onChange={(e) => setConfigForm({ ...configForm, privateReplyTemplate: e.target.value })}
                        placeholder={t('instagram.settings.privateReplyTemplatePlaceholder')}
                        rows={4}
                      />
                      <p className="text-xs text-muted-foreground">{t('instagram.settings.privateReplyTemplateHint')}</p>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="ig-private-reply-promotion">{t('instagram.settings.privateReplyPromotion')}</Label>
                      <Input
                        id="ig-private-reply-promotion"
                        type="number"
                        min="1"
                        value={configForm.privateReplyPromotionId}
                        onChange={(e) => setConfigForm({ ...configForm, privateReplyPromotionId: e.target.value })}
                        placeholder={t('instagram.settings.privateReplyPromotionPlaceholder')}
                      />
                      <p className="text-xs text-muted-foreground">{t('instagram.settings.privateReplyPromotionHint')}</p>
                    </div>
                  </div>
                )}

                <Button
                  onClick={handleSaveConfig}
                  disabled={savingConfig || configLoadError}
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
            <Label htmlFor="ig-dm-message">{t('instagram.dm.message')}</Label>
            <Textarea
              id="ig-dm-message"
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

      {/* Link a subscriber to a loyalty customer, looked up by phone */}
      <Dialog open={!!linkTarget} onOpenChange={(open) => { if (!open) setLinkTarget(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('instagram.subscribers.linkTitle')}</DialogTitle>
            <DialogDescription>
              {t('instagram.subscribers.linkHint', {
                name: linkTarget?.displayName || linkTarget?.username || linkTarget?.igsid || '',
              })}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-3">
            <Label htmlFor="ig-link-phone">{t('instagram.subscribers.linkPhone')}</Label>
            <div className="flex gap-2">
              <Input
                id="ig-link-phone"
                value={linkPhone}
                onChange={(e) => { setLinkPhone(e.target.value); setLinkSearched(false); setLinkFound(null); }}
                placeholder="+998901234567"
              />
              <Button variant="outline" onClick={handleLinkSearch} disabled={linkSearching || !linkPhone.trim()}>
                <Search className="h-4 w-4" />
              </Button>
            </div>

            {linkFound && (
              <div className="rounded-md border bg-muted/30 p-3 text-sm">
                <div className="font-medium">{linkFound.name || linkFound.fullName || `#${linkFound.id}`}</div>
                <div className="text-xs text-muted-foreground">{linkFound.phone}</div>
              </div>
            )}
            {linkSearched && !linkFound && (
              <p className="text-sm text-muted-foreground">{t('instagram.subscribers.linkNotFound')}</p>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setLinkTarget(null)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleLinkConfirm} disabled={linking || !linkFound?.id}>
              <Link2 className="mr-2 h-4 w-4" />
              {t('instagram.subscribers.link')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
