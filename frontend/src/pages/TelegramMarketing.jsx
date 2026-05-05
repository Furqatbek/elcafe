import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { telegramAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Textarea } from '../components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from '../components/ui/dialog';
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
  Send,
  FileText,
  Plus,
  Edit,
  Trash2,
  Play,
  Pause,
  XCircle,
  ChevronLeft,
  ChevronRight,
  Users,
  CheckCircle,
  Clock,
  UserX,
  UserCheck,
  Settings,
  Bot,
  Save
} from 'lucide-react';
import { Switch } from '../components/ui/switch';

const statusColors = {
  DRAFT: 'bg-gray-500',
  SCHEDULED: 'bg-blue-500',
  SENDING: 'bg-yellow-500',
  PAUSED: 'bg-orange-500',
  COMPLETED: 'bg-green-500',
  CANCELLED: 'bg-red-500',
};

export default function TelegramMarketing() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('subscribers');
  const [_loading, setLoading] = useState(false);

  // Subscribers state
  const [subscribers, setSubscribers] = useState([]);
  const [subscriberPage, setSubscriberPage] = useState(0);
  const [subscriberTotalPages, setSubscriberTotalPages] = useState(0);
  const [subscriberStats, setSubscriberStats] = useState(null);

  // Campaigns state
  const [campaigns, setCampaigns] = useState([]);
  const [campaignPage, setCampaignPage] = useState(0);
  const [campaignTotalPages, setCampaignTotalPages] = useState(0);
  const [campaignModalOpen, setCampaignModalOpen] = useState(false);
  const [editingCampaign, setEditingCampaign] = useState(null);
  const [campaignForm, setCampaignForm] = useState({
    name: '',
    description: '',
    templateId: '',
    customMessage: '',
    targetAudience: 'ALL',
  });

  // Templates state
  const [templates, setTemplates] = useState([]);
  const [templatePage, setTemplatePage] = useState(0);
  const [templateTotalPages, setTemplateTotalPages] = useState(0);
  const [templateModalOpen, setTemplateModalOpen] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null);
  const [templateForm, setTemplateForm] = useState({
    name: '',
    content: '',
    type: 'CUSTOM',
    description: '',
    isActive: true,
  });

  // Restaurants
  const [restaurants, setRestaurants] = useState([]);

  // Bot configs state
  const [customerBotConfigs, setCustomerBotConfigs] = useState([]);
  const [ownerBotConfigs, setOwnerBotConfigs] = useState([]);
  const [customerBotForm, setCustomerBotForm] = useState({
    botToken: '',
    botUsername: '',
    webhookUrl: '',
    isActive: true,
    welcomeMessage: '',
  });
  const [ownerBotForm, setOwnerBotForm] = useState({
    botToken: '',
    botUsername: '',
    isActive: true,
    welcomeMessage: '',
    autoVerifyOwners: true,
  });
  const [editingCustomerBotId, setEditingCustomerBotId] = useState(null);
  const [editingOwnerBotId, setEditingOwnerBotId] = useState(null);
  const [savingConfig, setSavingConfig] = useState(false);

  useEffect(() => {
    if (activeTab === 'subscribers') {
      loadSubscribers();
      loadSubscriberStats();
    } else if (activeTab === 'campaigns') {
      loadCampaigns();
    } else if (activeTab === 'templates') {
      loadTemplates();
    } else if (activeTab === 'settings') {
      loadBotConfigs();
      restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
        const list = res.data.data?.content || res.data.data || [];
        setRestaurants(Array.isArray(list) ? list : []);
      }).catch(console.error);
    }
  }, [activeTab]);

  const loadSubscribers = async (page = 0) => {
    setLoading(true);
    try {
      const response = await telegramAPI.getSubscribers({ page, size: 10 });
      setSubscribers(response.data.content || []);
      setSubscriberTotalPages(response.data.totalPages || 0);
      setSubscriberPage(page);
    } catch (error) {
      console.error('Failed to load subscribers:', error);
      alert(t('telegram.errors.loadSubscribers'));
    } finally {
      setLoading(false);
    }
  };

  const loadSubscriberStats = async () => {
    try {
      const response = await telegramAPI.getSubscriberStats();
      setSubscriberStats(response.data);
    } catch (error) {
      console.error('Failed to load subscriber stats:', error);
    }
  };

  const loadCampaigns = async (page = 0) => {
    setLoading(true);
    try {
      const response = await telegramAPI.getCampaigns({ page, size: 10 });
      setCampaigns(response.data.content || []);
      setCampaignTotalPages(response.data.totalPages || 0);
      setCampaignPage(page);
    } catch (error) {
      console.error('Failed to load campaigns:', error);
      alert(t('telegram.errors.loadCampaigns'));
    } finally {
      setLoading(false);
    }
  };

  const loadTemplates = async (page = 0) => {
    setLoading(true);
    try {
      const response = await telegramAPI.getTemplates({ page, size: 10 });
      setTemplates(response.data.content || []);
      setTemplateTotalPages(response.data.totalPages || 0);
      setTemplatePage(page);
    } catch (error) {
      console.error('Failed to load templates:', error);
      alert(t('telegram.errors.loadTemplates'));
    } finally {
      setLoading(false);
    }
  };

  // Bot config functions
  const loadBotConfigs = async () => {
    setLoading(true);
    try {
      const [customerRes, ownerRes] = await Promise.all([
        telegramAPI.getCustomerBotConfigs().catch(() => ({ data: [] })),
        telegramAPI.getOwnerBotConfigs().catch(() => ({ data: [] })),
      ]);
      setCustomerBotConfigs(customerRes.data || []);
      setOwnerBotConfigs(ownerRes.data || []);

      // Pre-fill forms if configs exist
      if (customerRes.data && customerRes.data.length > 0) {
        const config = customerRes.data[0];
        setEditingCustomerBotId(config.id);
        setCustomerBotForm({
          botToken: '',
          botUsername: config.botUsername || '',
          webhookUrl: config.webhookUrl || '',
          isActive: config.isActive ?? true,
          welcomeMessage: config.welcomeMessage || '',
        });
      }
      if (ownerRes.data && ownerRes.data.length > 0) {
        const config = ownerRes.data[0];
        setEditingOwnerBotId(config.id);
        setOwnerBotForm({
          botToken: '',
          botUsername: config.botUsername || '',
          isActive: config.isActive ?? true,
          welcomeMessage: config.welcomeMessage || '',
          autoVerifyOwners: config.autoVerifyOwners ?? true,
        });
      }
    } catch (error) {
      console.error('Failed to load bot configs:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSaveCustomerBotConfig = async () => {
    setSavingConfig(true);
    try {
      const data = { ...customerBotForm };
      if (!data.botToken) delete data.botToken; // Don't send empty token

      if (editingCustomerBotId) {
        await telegramAPI.updateCustomerBotConfig(editingCustomerBotId, data);
        alert(t('telegram.settings.customerBotUpdated'));
      } else {
        await telegramAPI.createCustomerBotConfig(data);
        alert(t('telegram.settings.customerBotCreated'));
      }
      loadBotConfigs();
    } catch (error) {
      console.error('Failed to save customer bot config:', error);
      alert(t('telegram.errors.saveBotConfig'));
    } finally {
      setSavingConfig(false);
    }
  };

  const handleSaveOwnerBotConfig = async () => {
    setSavingConfig(true);
    try {
      const data = { ...ownerBotForm };
      if (!data.botToken) delete data.botToken; // Don't send empty token

      if (editingOwnerBotId) {
        await telegramAPI.updateOwnerBotConfig(editingOwnerBotId, data);
        alert(t('telegram.settings.ownerBotUpdated'));
      } else {
        await telegramAPI.createOwnerBotConfig(data);
        alert(t('telegram.settings.ownerBotCreated'));
      }
      loadBotConfigs();
    } catch (error) {
      console.error('Failed to save owner bot config:', error);
      alert(t('telegram.errors.saveBotConfig'));
    } finally {
      setSavingConfig(false);
    }
  };

  // Subscriber handlers
  const handleBlockSubscriber = async (id) => {
    try {
      await telegramAPI.blockSubscriber(id);
      alert(t('telegram.subscribers.blocked'));
      loadSubscribers(subscriberPage);
    } catch (error) {
      console.error('Failed to block subscriber:', error);
      alert(t('telegram.errors.blockSubscriber'));
    }
  };

  const handleUnblockSubscriber = async (id) => {
    try {
      await telegramAPI.unblockSubscriber(id);
      alert(t('telegram.subscribers.unblocked'));
      loadSubscribers(subscriberPage);
    } catch (error) {
      console.error('Failed to unblock subscriber:', error);
      alert(t('telegram.errors.unblockSubscriber'));
    }
  };

  // Campaign handlers
  const handleCreateCampaign = () => {
    setEditingCampaign(null);
    setCampaignForm({
      name: '',
      description: '',
      templateId: '',
      customMessage: '',
      targetAudience: 'ALL',
    });
    setCampaignModalOpen(true);
  };

  const handleEditCampaign = (campaign) => {
    setEditingCampaign(campaign);
    setCampaignForm({
      name: campaign.name,
      description: campaign.description || '',
      templateId: campaign.templateId?.toString() || '',
      customMessage: campaign.customMessage || '',
      targetAudience: campaign.targetAudience || 'ALL',
    });
    setCampaignModalOpen(true);
  };

  const handleSaveCampaign = async () => {
    try {
      const data = {
        ...campaignForm,
        templateId: campaignForm.templateId ? parseInt(campaignForm.templateId) : null,
      };

      if (editingCampaign) {
        await telegramAPI.updateCampaign(editingCampaign.id, data);
        alert(t('telegram.campaigns.updated'));
      } else {
        await telegramAPI.createCampaign(data);
        alert(t('telegram.campaigns.created'));
      }

      setCampaignModalOpen(false);
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to save campaign:', error);
      alert(t('telegram.errors.saveCampaign'));
    }
  };

  const handleSendCampaign = async (id) => {
    try {
      await telegramAPI.sendCampaign(id);
      alert(t('telegram.campaigns.sending'));
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to send campaign:', error);
      alert(t('telegram.errors.sendCampaign'));
    }
  };

  const handleCancelCampaign = async (id) => {
    try {
      await telegramAPI.cancelCampaign(id);
      alert(t('telegram.campaigns.cancelled'));
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to cancel campaign:', error);
      alert(t('telegram.errors.cancelCampaign'));
    }
  };

  const handleDeleteCampaign = async (id) => {
    if (!window.confirm(t('telegram.campaigns.confirmDelete'))) return;
    try {
      await telegramAPI.deleteCampaign(id);
      alert(t('telegram.campaigns.deleted'));
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to delete campaign:', error);
      alert(t('telegram.errors.deleteCampaign'));
    }
  };

  // Template handlers
  const handleCreateTemplate = () => {
    setEditingTemplate(null);
    setTemplateForm({
      name: '',
      content: '',
      type: 'CUSTOM',
      description: '',
      isActive: true,
    });
    setTemplateModalOpen(true);
  };

  const handleEditTemplate = (template) => {
    setEditingTemplate(template);
    setTemplateForm({
      name: template.name,
      content: template.content,
      type: template.type,
      description: template.description || '',
      isActive: template.isActive,
    });
    setTemplateModalOpen(true);
  };

  const handleSaveTemplate = async () => {
    try {
      if (editingTemplate) {
        await telegramAPI.updateTemplate(editingTemplate.id, templateForm);
        alert(t('telegram.templates.updated'));
      } else {
        await telegramAPI.createTemplate(templateForm);
        alert(t('telegram.templates.created'));
      }

      setTemplateModalOpen(false);
      loadTemplates(templatePage);
    } catch (error) {
      console.error('Failed to save template:', error);
      alert(t('telegram.errors.saveTemplate'));
    }
  };

  const handleToggleTemplate = async (id) => {
    try {
      await telegramAPI.toggleTemplate(id);
      alert(t('telegram.templates.toggled'));
      loadTemplates(templatePage);
    } catch (error) {
      console.error('Failed to toggle template:', error);
      alert(t('telegram.errors.toggleTemplate'));
    }
  };

  const handleDeleteTemplate = async (id) => {
    if (!window.confirm(t('telegram.templates.confirmDelete'))) return;
    try {
      await telegramAPI.deleteTemplate(id);
      alert(t('telegram.templates.deleted'));
      loadTemplates(templatePage);
    } catch (error) {
      console.error('Failed to delete template:', error);
      alert(t('telegram.errors.deleteTemplate'));
    }
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('telegram.title')}</h1>
          <p className="text-muted-foreground">{t('telegram.description')}</p>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="subscribers" className="flex items-center gap-2">
            <Users className="h-4 w-4" />
            {t('telegram.tabs.subscribers')}
          </TabsTrigger>
          <TabsTrigger value="campaigns" className="flex items-center gap-2">
            <Send className="h-4 w-4" />
            {t('telegram.tabs.campaigns')}
          </TabsTrigger>
          <TabsTrigger value="templates" className="flex items-center gap-2">
            <FileText className="h-4 w-4" />
            {t('telegram.tabs.templates')}
          </TabsTrigger>
          <TabsTrigger value="settings" className="flex items-center gap-2">
            <Settings className="h-4 w-4" />
            {t('telegram.tabs.settings')}
          </TabsTrigger>
        </TabsList>

        {/* Subscribers Tab */}
        <TabsContent value="subscribers">
          {/* Stats Cards */}
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 mb-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('telegram.stats.totalSubscribers')}</CardTitle>
                <Users className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{subscriberStats?.totalSubscribers || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('telegram.stats.activeSubscribers')}</CardTitle>
                <CheckCircle className="h-4 w-4 text-green-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{subscriberStats?.activeSubscribers || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('telegram.stats.blockedSubscribers')}</CardTitle>
                <UserX className="h-4 w-4 text-red-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{subscriberStats?.blockedSubscribers || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('telegram.stats.newThisMonth')}</CardTitle>
                <Clock className="h-4 w-4 text-blue-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{subscriberStats?.newThisMonth || 0}</div>
              </CardContent>
            </Card>
          </div>

          <Card>
            <CardHeader>
              <CardTitle>{t('telegram.subscribers.title')}</CardTitle>
              <CardDescription>{t('telegram.subscribers.description')}</CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('telegram.subscribers.username')}</TableHead>
                    <TableHead>{t('telegram.subscribers.firstName')}</TableHead>
                    <TableHead>{t('telegram.subscribers.customer')}</TableHead>
                    <TableHead>{t('telegram.subscribers.status')}</TableHead>
                    <TableHead>{t('telegram.subscribers.subscribed')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {subscribers.map((subscriber) => (
                    <TableRow key={subscriber.id}>
                      <TableCell className="font-medium">@{subscriber.username || '-'}</TableCell>
                      <TableCell>{subscriber.firstName} {subscriber.lastName}</TableCell>
                      <TableCell>{subscriber.customerName || '-'}</TableCell>
                      <TableCell>
                        <Badge variant={subscriber.isBlocked ? 'destructive' : 'default'}>
                          {subscriber.isBlocked ? t('telegram.subscribers.blockedStatus') : t('telegram.subscribers.activeStatus')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {new Date(subscriber.subscribedAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell>
                        {subscriber.isBlocked ? (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleUnblockSubscriber(subscriber.id)}
                          >
                            <UserCheck className="h-4 w-4" />
                          </Button>
                        ) : (
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleBlockSubscriber(subscriber.id)}
                          >
                            <UserX className="h-4 w-4" />
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                  {subscribers.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                        {t('telegram.subscribers.empty')}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>

              {/* Pagination */}
              {subscriberTotalPages > 1 && (
                <div className="flex items-center justify-end gap-2 mt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={subscriberPage === 0}
                    onClick={() => loadSubscribers(subscriberPage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm">
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

        {/* Campaigns Tab */}
        <TabsContent value="campaigns">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('telegram.campaigns.title')}</CardTitle>
                <CardDescription>{t('telegram.campaigns.description')}</CardDescription>
              </div>
              <Button onClick={handleCreateCampaign}>
                <Plus className="mr-2 h-4 w-4" />
                {t('telegram.campaigns.create')}
              </Button>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('telegram.campaigns.name')}</TableHead>
                    <TableHead>{t('telegram.campaigns.audience')}</TableHead>
                    <TableHead>{t('telegram.campaigns.recipients')}</TableHead>
                    <TableHead>{t('telegram.campaigns.status')}</TableHead>
                    <TableHead>{t('telegram.campaigns.sent')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {campaigns.map((campaign) => (
                    <TableRow key={campaign.id}>
                      <TableCell className="font-medium">{campaign.name}</TableCell>
                      <TableCell>{campaign.targetAudience}</TableCell>
                      <TableCell>{campaign.recipientCount || 0}</TableCell>
                      <TableCell>
                        <Badge className={statusColors[campaign.status]}>
                          {campaign.status}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {campaign.sentCount || 0} / {campaign.recipientCount || 0}
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          {campaign.status === 'DRAFT' && (
                            <>
                              <Button
                                size="sm"
                                variant="outline"
                                onClick={() => handleEditCampaign(campaign)}
                              >
                                <Edit className="h-4 w-4" />
                              </Button>
                              <Button
                                size="sm"
                                onClick={() => handleSendCampaign(campaign.id)}
                              >
                                <Play className="h-4 w-4" />
                              </Button>
                              <Button
                                size="sm"
                                variant="destructive"
                                onClick={() => handleDeleteCampaign(campaign.id)}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </>
                          )}
                          {campaign.status === 'SENDING' && (
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleCancelCampaign(campaign.id)}
                            >
                              <XCircle className="h-4 w-4" />
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                  {campaigns.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                        {t('telegram.campaigns.empty')}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>

              {/* Pagination */}
              {campaignTotalPages > 1 && (
                <div className="flex items-center justify-end gap-2 mt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={campaignPage === 0}
                    onClick={() => loadCampaigns(campaignPage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm">
                    {campaignPage + 1} / {campaignTotalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={campaignPage >= campaignTotalPages - 1}
                    onClick={() => loadCampaigns(campaignPage + 1)}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Templates Tab */}
        <TabsContent value="templates">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('telegram.templates.title')}</CardTitle>
                <CardDescription>{t('telegram.templates.description')}</CardDescription>
              </div>
              <Button onClick={handleCreateTemplate}>
                <Plus className="mr-2 h-4 w-4" />
                {t('telegram.templates.create')}
              </Button>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('telegram.templates.name')}</TableHead>
                    <TableHead>{t('telegram.templates.type')}</TableHead>
                    <TableHead>{t('telegram.templates.content')}</TableHead>
                    <TableHead>{t('telegram.templates.usage')}</TableHead>
                    <TableHead>{t('telegram.templates.status')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {templates.map((template) => (
                    <TableRow key={template.id}>
                      <TableCell className="font-medium">{template.name}</TableCell>
                      <TableCell>
                        <Badge variant="outline">{template.type}</Badge>
                      </TableCell>
                      <TableCell className="max-w-xs truncate">
                        {template.content}
                      </TableCell>
                      <TableCell>{template.usageCount || 0}</TableCell>
                      <TableCell>
                        <Badge variant={template.isActive ? 'default' : 'secondary'}>
                          {template.isActive ? t('common.active') : t('common.inactive')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleEditTemplate(template)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleToggleTemplate(template.id)}
                          >
                            {template.isActive ? (
                              <Pause className="h-4 w-4" />
                            ) : (
                              <Play className="h-4 w-4" />
                            )}
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            onClick={() => handleDeleteTemplate(template.id)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                  {templates.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                        {t('telegram.templates.empty')}
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>

              {/* Pagination */}
              {templateTotalPages > 1 && (
                <div className="flex items-center justify-end gap-2 mt-4">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={templatePage === 0}
                    onClick={() => loadTemplates(templatePage - 1)}
                  >
                    <ChevronLeft className="h-4 w-4" />
                  </Button>
                  <span className="text-sm">
                    {templatePage + 1} / {templateTotalPages}
                  </span>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={templatePage >= templateTotalPages - 1}
                    onClick={() => loadTemplates(templatePage + 1)}
                  >
                    <ChevronRight className="h-4 w-4" />
                  </Button>
                </div>
              )}
            </CardContent>
          </Card>
        </TabsContent>

        {/* Settings Tab */}
        <TabsContent value="settings">
          <div className="grid gap-6 md:grid-cols-2">
            {/* Customer Bot Configuration */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Bot className="h-5 w-5" />
                  {t('telegram.settings.customerBot')}
                </CardTitle>
                <CardDescription>{t('telegram.settings.customerBotDescription')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>{t('telegram.settings.botUsername')}</Label>
                  <Input
                    value={customerBotForm.botUsername}
                    onChange={(e) => setCustomerBotForm({ ...customerBotForm, botUsername: e.target.value })}
                    placeholder="@YourBotUsername"
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('telegram.settings.botToken')}</Label>
                  <Input
                    type="password"
                    value={customerBotForm.botToken}
                    onChange={(e) => setCustomerBotForm({ ...customerBotForm, botToken: e.target.value })}
                    placeholder={customerBotConfigs.length > 0 && customerBotConfigs[0].hasToken ? '********' : t('telegram.settings.enterToken')}
                  />
                  <p className="text-xs text-muted-foreground">{t('telegram.settings.tokenHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label>{t('telegram.settings.webhookUrl')}</Label>
                  <Input
                    value={customerBotForm.webhookUrl}
                    onChange={(e) => setCustomerBotForm({ ...customerBotForm, webhookUrl: e.target.value })}
                    placeholder="https://your-domain.com/webhook"
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('telegram.settings.welcomeMessage')}</Label>
                  <Textarea
                    value={customerBotForm.welcomeMessage}
                    onChange={(e) => setCustomerBotForm({ ...customerBotForm, welcomeMessage: e.target.value })}
                    placeholder={t('telegram.settings.welcomeMessagePlaceholder')}
                    rows={3}
                  />
                </div>

                <div className="flex items-center justify-between">
                  <Label>{t('telegram.settings.botActive')}</Label>
                  <Switch
                    checked={customerBotForm.isActive}
                    onCheckedChange={(checked) => setCustomerBotForm({ ...customerBotForm, isActive: checked })}
                  />
                </div>

                <Button
                  onClick={handleSaveCustomerBotConfig}
                  disabled={savingConfig}
                  className="w-full"
                >
                  <Save className="mr-2 h-4 w-4" />
                  {savingConfig ? t('common.saving') : t('common.save')}
                </Button>
                {editingCustomerBotId && (
                  <Button
                    variant="destructive"
                    onClick={async () => {
                      if (!window.confirm(t('telegram.settings.confirmStopBot', 'Stop this bot and clear its configuration?'))) return;
                      try {
                        await telegramAPI.deleteCustomerBotConfig(editingCustomerBotId);
                        setEditingCustomerBotId(null);
                        setCustomerBotForm({ botToken: '', botUsername: '', webhookUrl: '', isActive: true, welcomeMessage: '' });
                        loadBotConfigs();
                        alert(t('telegram.settings.botStopped', 'Bot stopped and configuration cleared'));
                      } catch (e) { console.error(e); alert('Failed to stop bot'); }
                    }}
                    className="w-full"
                  >
                    {t('telegram.settings.stopBot', 'Stop Bot & Clear Config')}
                  </Button>
                )}
              </CardContent>
            </Card>

            {/* Owner Bot Configuration */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Bot className="h-5 w-5" />
                  {t('telegram.settings.ownerBot')}
                </CardTitle>
                <CardDescription>{t('telegram.settings.ownerBotDescription')}</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label>{t('telegram.settings.botUsername')}</Label>
                  <Input
                    value={ownerBotForm.botUsername}
                    onChange={(e) => setOwnerBotForm({ ...ownerBotForm, botUsername: e.target.value })}
                    placeholder="@YourOwnerBotUsername"
                  />
                </div>

                <div className="space-y-2">
                  <Label>{t('telegram.settings.botToken')}</Label>
                  <Input
                    type="password"
                    value={ownerBotForm.botToken}
                    onChange={(e) => setOwnerBotForm({ ...ownerBotForm, botToken: e.target.value })}
                    placeholder={ownerBotConfigs.length > 0 && ownerBotConfigs[0].hasToken ? '********' : t('telegram.settings.enterToken')}
                  />
                  <p className="text-xs text-muted-foreground">{t('telegram.settings.tokenHint')}</p>
                </div>

                <div className="space-y-2">
                  <Label>{t('telegram.settings.welcomeMessage')}</Label>
                  <Textarea
                    value={ownerBotForm.welcomeMessage}
                    onChange={(e) => setOwnerBotForm({ ...ownerBotForm, welcomeMessage: e.target.value })}
                    placeholder={t('telegram.settings.welcomeMessagePlaceholder')}
                    rows={3}
                  />
                </div>

                <div className="flex items-center justify-between">
                  <Label>{t('telegram.settings.botActive')}</Label>
                  <Switch
                    checked={ownerBotForm.isActive}
                    onCheckedChange={(checked) => setOwnerBotForm({ ...ownerBotForm, isActive: checked })}
                  />
                </div>

                <div className="flex items-center justify-between">
                  <Label>{t('telegram.settings.autoVerifyOwners')}</Label>
                  <Switch
                    checked={ownerBotForm.autoVerifyOwners}
                    onCheckedChange={(checked) => setOwnerBotForm({ ...ownerBotForm, autoVerifyOwners: checked })}
                  />
                </div>

                <Button
                  onClick={handleSaveOwnerBotConfig}
                  disabled={savingConfig}
                  className="w-full"
                >
                  <Save className="mr-2 h-4 w-4" />
                  {savingConfig ? t('common.saving') : t('common.save')}
                </Button>
                {editingOwnerBotId && (
                  <Button
                    variant="destructive"
                    onClick={async () => {
                      if (!window.confirm(t('telegram.settings.confirmStopBot', 'Stop this bot and clear its configuration?'))) return;
                      try {
                        await telegramAPI.deleteOwnerBotConfig(editingOwnerBotId);
                        setEditingOwnerBotId(null);
                        setOwnerBotForm({ botToken: '', botUsername: '', welcomeMessage: '', isActive: true, autoVerifyOwners: false });
                        loadBotConfigs();
                        alert(t('telegram.settings.botStopped', 'Bot stopped and configuration cleared'));
                      } catch (e) { console.error(e); alert('Failed to stop bot'); }
                    }}
                    className="w-full"
                  >
                    {t('telegram.settings.stopBot', 'Stop Bot & Clear Config')}
                  </Button>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Connect to Owner Bot */}
          {editingOwnerBotId && (
            <Card className="mt-4">
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  🔗 {t('telegram.settings.connectToBot', 'Connect to Owner Bot')}
                </CardTitle>
                <CardDescription>
                  {t('telegram.settings.connectDescription', 'Generate a code, then send it to the owner bot in Telegram to receive notifications')}
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex gap-2 items-center">
                  <select
                    id="connectRestaurant"
                    className="flex-1 border rounded-md px-3 py-2 text-sm bg-background"
                    defaultValue=""
                  >
                    <option value="" disabled>{t('telegram.settings.selectRestaurant', 'Select restaurant')}</option>
                    {restaurants.map(r => <option key={r.id} value={r.id}>{r.name}</option>)}
                  </select>
                  <Button onClick={async () => {
                    const restaurantSelect = document.getElementById('connectRestaurant');
                    const restaurantId = restaurantSelect?.value;
                    if (!restaurantId) { alert(t('telegram.settings.selectRestaurant', 'Select a restaurant first')); return; }
                    const user = JSON.parse(localStorage.getItem('user'));
                    if (!user?.id) { alert('User not found'); return; }
                    try {
                      const res = await telegramAPI.generateOwnerBotCode(user.id, restaurantId);
                      const code = res.data?.data || res.data;
                      alert(t('telegram.settings.codeGenerated', 'Your verification code:') + '\n\n' + code + '\n\n' + t('telegram.settings.sendCodeToBot', 'Send this code to the owner bot in Telegram'));
                    } catch (e) { console.error(e); alert(e.response?.data?.message || 'Failed to generate code'); }
                  }}>
                    {t('telegram.settings.generateCode', 'Generate Code')}
                  </Button>
                </div>
                <p className="text-sm text-muted-foreground">
                  {t('telegram.settings.connectSteps', '1. Click "Generate Code" → 2. Open owner bot in Telegram → 3. Send /start → 4. Send the 6-digit code')}
                </p>
              </CardContent>
            </Card>
          )}
        </TabsContent>
      </Tabs>

      {/* Campaign Modal */}
      <Dialog open={campaignModalOpen} onOpenChange={setCampaignModalOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingCampaign ? t('telegram.campaigns.edit') : t('telegram.campaigns.create')}
            </DialogTitle>
            <DialogDescription>
              {t('telegram.campaigns.modalDescription')}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('telegram.campaigns.name')}</Label>
              <Input
                value={campaignForm.name}
                onChange={(e) => setCampaignForm({ ...campaignForm, name: e.target.value })}
                placeholder={t('telegram.campaigns.namePlaceholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('telegram.campaigns.description')}</Label>
              <Input
                value={campaignForm.description}
                onChange={(e) => setCampaignForm({ ...campaignForm, description: e.target.value })}
                placeholder={t('telegram.campaigns.descriptionPlaceholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('telegram.campaigns.audience')}</Label>
              <Select
                value={campaignForm.targetAudience}
                onValueChange={(value) => setCampaignForm({ ...campaignForm, targetAudience: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ALL">{t('telegram.audience.all')}</SelectItem>
                  <SelectItem value="ACTIVE">{t('telegram.audience.active')}</SelectItem>
                  <SelectItem value="NEW">{t('telegram.audience.new')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>{t('telegram.campaigns.message')}</Label>
              <Textarea
                value={campaignForm.customMessage}
                onChange={(e) => setCampaignForm({ ...campaignForm, customMessage: e.target.value })}
                placeholder={t('telegram.campaigns.messagePlaceholder')}
                rows={4}
              />
              <p className="text-xs text-muted-foreground">
                {t('telegram.campaigns.placeholderHint')}
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setCampaignModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveCampaign}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Template Modal */}
      <Dialog open={templateModalOpen} onOpenChange={setTemplateModalOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingTemplate ? t('telegram.templates.edit') : t('telegram.templates.create')}
            </DialogTitle>
            <DialogDescription>
              {t('telegram.templates.modalDescription')}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('telegram.templates.name')}</Label>
              <Input
                value={templateForm.name}
                onChange={(e) => setTemplateForm({ ...templateForm, name: e.target.value })}
                placeholder={t('telegram.templates.namePlaceholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('telegram.templates.type')}</Label>
              <Select
                value={templateForm.type}
                onValueChange={(value) => setTemplateForm({ ...templateForm, type: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="WELCOME">{t('telegram.templateTypes.welcome')}</SelectItem>
                  <SelectItem value="PROMOTION">{t('telegram.templateTypes.promotion')}</SelectItem>
                  <SelectItem value="BIRTHDAY">{t('telegram.templateTypes.birthday')}</SelectItem>
                  <SelectItem value="REMINDER">{t('telegram.templateTypes.reminder')}</SelectItem>
                  <SelectItem value="ORDER_STATUS">{t('telegram.templateTypes.orderStatus')}</SelectItem>
                  <SelectItem value="REFERRAL">{t('telegram.templateTypes.referral')}</SelectItem>
                  <SelectItem value="CUSTOM">{t('telegram.templateTypes.custom')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>{t('telegram.templates.content')}</Label>
              <Textarea
                value={templateForm.content}
                onChange={(e) => setTemplateForm({ ...templateForm, content: e.target.value })}
                placeholder={t('telegram.templates.contentPlaceholder')}
                rows={4}
              />
              <p className="text-xs text-muted-foreground">
                {t('telegram.templates.placeholderHint')}
              </p>
            </div>

            <div className="space-y-2">
              <Label>{t('telegram.templates.description')}</Label>
              <Input
                value={templateForm.description}
                onChange={(e) => setTemplateForm({ ...templateForm, description: e.target.value })}
                placeholder={t('telegram.templates.descriptionPlaceholder')}
              />
            </div>
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setTemplateModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveTemplate}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
