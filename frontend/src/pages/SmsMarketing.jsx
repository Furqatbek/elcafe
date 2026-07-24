import { useState, useEffect } from 'react';
import { notifySuccess, notifyWarning } from '../lib/errors';
import { useTranslation } from 'react-i18next';
import { smsAPI } from '../services/api';
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
  CheckCircle,
  Clock,
  AlertCircle,
  BarChart3,
} from 'lucide-react';

// Audiences with a real recipient query in the backend (SmsCampaignService.SUPPORTED_AUDIENCES).
// Labels come from i18n (sms.targetAudiences.*).
const targetAudienceValues = ['ALL', 'BIRTHDAY_TODAY', 'INACTIVE', 'NEW_CUSTOMERS', 'SEGMENT'];

// Canonical RFM buckets (mirrors CustomerActivityService.RFM_SEGMENTS); values are sent verbatim as
// filterCriteria.rfm_segment, so they must match the backend labels exactly.
const rfmSegmentOptions = [
  'Champions', 'Loyal Customers', 'Potential Loyalists', 'Recent Customers', 'Promising',
  'Need Attention', 'About to Sleep', 'At Risk', "Can't Lose Them", 'Hibernating', 'Lost',
  'Others', 'New/Inactive',
];

const statusColors = {
  DRAFT: 'bg-gray-500',
  SCHEDULED: 'bg-blue-500',
  SENDING: 'bg-yellow-500',
  PAUSED: 'bg-orange-500',
  COMPLETED: 'bg-green-500',
  CANCELLED: 'bg-red-500',
};

export default function SmsMarketing() {
  const { t } = useTranslation();
  const [activeTab, setActiveTab] = useState('campaigns');
  const [_loading, setLoading] = useState(false);

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
    filterCriteria: {},
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

  // Statistics state
  const [stats, setStats] = useState(null);

  useEffect(() => {
    if (activeTab === 'campaigns') {
      loadCampaigns();
    } else if (activeTab === 'templates') {
      loadTemplates();
    } else if (activeTab === 'statistics') {
      loadStatistics();
    }
  }, [activeTab]);

  const loadCampaigns = async (page = 0) => {
    setLoading(true);
    try {
      const response = await smsAPI.getCampaigns({ page, size: 10 });
      setCampaigns(response.data.content || []);
      setCampaignTotalPages(response.data.totalPages || 0);
      setCampaignPage(page);
    } catch (error) {
      console.error('Failed to load campaigns:', error);
      notifyWarning(t('sms.errors.loadCampaigns'));
    } finally {
      setLoading(false);
    }
  };

  const loadTemplates = async (page = 0) => {
    setLoading(true);
    try {
      const response = await smsAPI.getTemplates({ page, size: 10 });
      setTemplates(response.data.content || []);
      setTemplateTotalPages(response.data.totalPages || 0);
      setTemplatePage(page);
    } catch (error) {
      console.error('Failed to load templates:', error);
      notifyWarning(t('sms.errors.loadTemplates'));
    } finally {
      setLoading(false);
    }
  };

  const loadStatistics = async () => {
    setLoading(true);
    try {
      const response = await smsAPI.getStatistics(30);
      setStats(response.data);
    } catch (error) {
      console.error('Failed to load statistics:', error);
      notifyWarning(t('sms.errors.loadStats'));
    } finally {
      setLoading(false);
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
      filterCriteria: {},
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
      targetAudience: campaign.targetAudience,
      filterCriteria: campaign.filterCriteria || {},
    });
    setCampaignModalOpen(true);
  };

  const handleSaveCampaign = async () => {
    // SEGMENT needs exactly one discriminator; guard here so we don't post a campaign the backend
    // would reject (and that would otherwise target nobody).
    if (campaignForm.targetAudience === 'SEGMENT') {
      const tag = campaignForm.filterCriteria?.tag?.trim();
      const rfm = campaignForm.filterCriteria?.rfm_segment?.trim();
      if (!tag && !rfm) {
        notifyWarning(t('sms.campaigns.segmentRequired'));
        return;
      }
    }
    try {
      const data = {
        ...campaignForm,
        templateId: campaignForm.templateId ? parseInt(campaignForm.templateId) : null,
      };

      if (editingCampaign) {
        await smsAPI.updateCampaign(editingCampaign.id, data);
        notifySuccess(t('sms.campaigns.updated'));
      } else {
        await smsAPI.createCampaign(data);
        notifySuccess(t('sms.campaigns.created'));
      }

      setCampaignModalOpen(false);
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to save campaign:', error);
      notifyWarning(t('sms.errors.saveCampaign'));
    }
  };

  const handleSendCampaign = async (id) => {
    try {
      await smsAPI.sendCampaign(id);
      notifySuccess(t('sms.campaigns.sending'));
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to send campaign:', error);
      notifyWarning(t('sms.errors.sendCampaign'));
    }
  };

  const handleCancelCampaign = async (id) => {
    try {
      await smsAPI.cancelCampaign(id);
      notifySuccess(t('sms.campaigns.cancelled'));
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to cancel campaign:', error);
      notifyWarning(t('sms.errors.cancelCampaign'));
    }
  };

  const handleDeleteCampaign = async (id) => {
    if (!window.confirm(t('sms.campaigns.confirmDelete'))) return;
    try {
      await smsAPI.deleteCampaign(id);
      notifySuccess(t('sms.campaigns.deleted'));
      loadCampaigns(campaignPage);
    } catch (error) {
      console.error('Failed to delete campaign:', error);
      notifyWarning(t('sms.errors.deleteCampaign'));
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
        await smsAPI.updateTemplate(editingTemplate.id, templateForm);
        notifySuccess(t('sms.templates.updated'));
      } else {
        await smsAPI.createTemplate(templateForm);
        notifySuccess(t('sms.templates.created'));
      }

      setTemplateModalOpen(false);
      loadTemplates(templatePage);
    } catch (error) {
      console.error('Failed to save template:', error);
      notifyWarning(t('sms.errors.saveTemplate'));
    }
  };

  const handleToggleTemplate = async (id) => {
    try {
      await smsAPI.toggleTemplate(id);
      notifySuccess(t('sms.templates.toggled'));
      loadTemplates(templatePage);
    } catch (error) {
      console.error('Failed to toggle template:', error);
      notifyWarning(t('sms.errors.toggleTemplate'));
    }
  };

  const handleDeleteTemplate = async (id) => {
    if (!window.confirm(t('sms.templates.confirmDelete'))) return;
    try {
      await smsAPI.deleteTemplate(id);
      notifySuccess(t('sms.templates.deleted'));
      loadTemplates(templatePage);
    } catch (error) {
      console.error('Failed to delete template:', error);
      notifyWarning(t('sms.errors.deleteTemplate'));
    }
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('sms.title')}</h1>
          <p className="text-muted-foreground">{t('sms.description')}</p>
        </div>
      </div>

      <Tabs value={activeTab} onValueChange={setActiveTab} className="space-y-4">
        <TabsList>
          <TabsTrigger value="campaigns" className="flex items-center gap-2">
            <Send className="h-4 w-4" />
            {t('sms.tabs.campaigns')}
          </TabsTrigger>
          <TabsTrigger value="templates" className="flex items-center gap-2">
            <FileText className="h-4 w-4" />
            {t('sms.tabs.templates')}
          </TabsTrigger>
          <TabsTrigger value="statistics" className="flex items-center gap-2">
            <BarChart3 className="h-4 w-4" />
            {t('sms.tabs.statistics')}
          </TabsTrigger>
        </TabsList>

        {/* Campaigns Tab */}
        <TabsContent value="campaigns">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between">
              <div>
                <CardTitle>{t('sms.campaigns.title')}</CardTitle>
                <CardDescription>{t('sms.campaigns.description')}</CardDescription>
              </div>
              <Button onClick={handleCreateCampaign}>
                <Plus className="mr-2 h-4 w-4" />
                {t('sms.campaigns.create')}
              </Button>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('sms.campaigns.name')}</TableHead>
                    <TableHead>{t('sms.campaigns.audience')}</TableHead>
                    <TableHead>{t('sms.campaigns.recipients')}</TableHead>
                    <TableHead>{t('sms.campaigns.status')}</TableHead>
                    <TableHead>{t('sms.campaigns.sent')}</TableHead>
                    <TableHead>{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {campaigns.map((campaign) => (
                    <TableRow key={campaign.id}>
                      <TableCell className="font-medium">{campaign.name}</TableCell>
                      <TableCell>{campaign.targetAudience}</TableCell>
                      <TableCell>{campaign.recipientCount}</TableCell>
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
                        {t('sms.campaigns.empty')}
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
                <CardTitle>{t('sms.templates.title')}</CardTitle>
                <CardDescription>{t('sms.templates.description')}</CardDescription>
              </div>
              <Button onClick={handleCreateTemplate}>
                <Plus className="mr-2 h-4 w-4" />
                {t('sms.templates.create')}
              </Button>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('sms.templates.name')}</TableHead>
                    <TableHead>{t('sms.templates.type')}</TableHead>
                    <TableHead>{t('sms.templates.content')}</TableHead>
                    <TableHead>{t('sms.templates.usage')}</TableHead>
                    <TableHead>{t('sms.templates.status')}</TableHead>
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
                        {t('sms.templates.empty')}
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

        {/* Statistics Tab */}
        <TabsContent value="statistics">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('sms.stats.totalSent')}</CardTitle>
                <Send className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats?.totalSent || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('sms.stats.delivered')}</CardTitle>
                <CheckCircle className="h-4 w-4 text-green-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats?.delivered || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('sms.stats.pending')}</CardTitle>
                <Clock className="h-4 w-4 text-yellow-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats?.pending || 0}</div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('sms.stats.failed')}</CardTitle>
                <AlertCircle className="h-4 w-4 text-red-500" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{stats?.failed || 0}</div>
              </CardContent>
            </Card>
          </div>

          <Card className="mt-4">
            <CardHeader>
              <CardTitle>{t('sms.stats.deliveryRate')}</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-4xl font-bold">
                {stats?.deliveryRate?.toFixed(1) || 0}%
              </div>
              <p className="text-muted-foreground mt-2">
                {t('sms.stats.deliveryRateDescription')}
              </p>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Campaign Modal */}
      <Dialog open={campaignModalOpen} onOpenChange={setCampaignModalOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>
              {editingCampaign ? t('sms.campaigns.edit') : t('sms.campaigns.create')}
            </DialogTitle>
            <DialogDescription>
              {t('sms.campaigns.modalDescription')}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('sms.campaigns.name')}</Label>
              <Input
                value={campaignForm.name}
                onChange={(e) => setCampaignForm({ ...campaignForm, name: e.target.value })}
                placeholder={t('sms.campaigns.namePlaceholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('sms.campaigns.description')}</Label>
              <Input
                value={campaignForm.description}
                onChange={(e) => setCampaignForm({ ...campaignForm, description: e.target.value })}
                placeholder={t('sms.campaigns.descriptionPlaceholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('sms.campaigns.audience')}</Label>
              <Select
                value={campaignForm.targetAudience}
                onValueChange={(value) =>
                  setCampaignForm({
                    ...campaignForm,
                    targetAudience: value,
                    // filterCriteria only carries SEGMENT discriminators today; drop it when leaving.
                    filterCriteria: value === 'SEGMENT' ? campaignForm.filterCriteria : {},
                  })
                }
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {targetAudienceValues.map((value) => (
                    <SelectItem key={value} value={value}>
                      {t(`sms.targetAudiences.${value}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {campaignForm.targetAudience === 'SEGMENT' && (() => {
              const segmentType =
                campaignForm.filterCriteria?.rfm_segment !== undefined ? 'rfm' : 'tag';
              return (
                <div className="space-y-2">
                  <Label>{t('sms.campaigns.segmentType')}</Label>
                  <Select
                    value={segmentType}
                    onValueChange={(value) =>
                      setCampaignForm({
                        ...campaignForm,
                        filterCriteria: value === 'rfm' ? { rfm_segment: '' } : { tag: '' },
                      })
                    }
                  >
                    <SelectTrigger>
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="tag">{t('sms.campaigns.byTag')}</SelectItem>
                      <SelectItem value="rfm">{t('sms.campaigns.byRfmSegment')}</SelectItem>
                    </SelectContent>
                  </Select>

                  {segmentType === 'tag' ? (
                    <Input
                      value={campaignForm.filterCriteria?.tag || ''}
                      onChange={(e) =>
                        setCampaignForm({ ...campaignForm, filterCriteria: { tag: e.target.value } })
                      }
                      placeholder={t('sms.campaigns.tagPlaceholder')}
                    />
                  ) : (
                    <Select
                      value={campaignForm.filterCriteria?.rfm_segment || ''}
                      onValueChange={(value) =>
                        setCampaignForm({ ...campaignForm, filterCriteria: { rfm_segment: value } })
                      }
                    >
                      <SelectTrigger>
                        <SelectValue placeholder={t('sms.campaigns.rfmSegmentPlaceholder')} />
                      </SelectTrigger>
                      <SelectContent>
                        {rfmSegmentOptions.map((seg) => (
                          <SelectItem key={seg} value={seg}>
                            {seg}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                  <p className="text-xs text-muted-foreground">{t('sms.campaigns.segmentHint')}</p>
                </div>
              );
            })()}

            <div className="space-y-2">
              <Label>{t('sms.campaigns.message')}</Label>
              <Textarea
                value={campaignForm.customMessage}
                onChange={(e) => setCampaignForm({ ...campaignForm, customMessage: e.target.value })}
                placeholder={t('sms.campaigns.messagePlaceholder')}
                rows={4}
              />
              <p className="text-xs text-muted-foreground">
                {t('sms.campaigns.placeholderHint')}
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
              {editingTemplate ? t('sms.templates.edit') : t('sms.templates.create')}
            </DialogTitle>
            <DialogDescription>
              {t('sms.templates.modalDescription')}
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4">
            <div className="space-y-2">
              <Label>{t('sms.templates.name')}</Label>
              <Input
                value={templateForm.name}
                onChange={(e) => setTemplateForm({ ...templateForm, name: e.target.value })}
                placeholder={t('sms.templates.namePlaceholder')}
              />
            </div>

            <div className="space-y-2">
              <Label>{t('sms.templates.type')}</Label>
              <Select
                value={templateForm.type}
                onValueChange={(value) => setTemplateForm({ ...templateForm, type: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="WELCOME">Welcome</SelectItem>
                  <SelectItem value="PROMOTION">Promotion</SelectItem>
                  <SelectItem value="BIRTHDAY">Birthday</SelectItem>
                  <SelectItem value="REMINDER">Reminder</SelectItem>
                  <SelectItem value="ORDER_STATUS">Order Status</SelectItem>
                  <SelectItem value="REFERRAL">Referral</SelectItem>
                  <SelectItem value="CUSTOM">Custom</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label>{t('sms.templates.content')}</Label>
              <Textarea
                value={templateForm.content}
                onChange={(e) => setTemplateForm({ ...templateForm, content: e.target.value })}
                placeholder={t('sms.templates.contentPlaceholder')}
                rows={4}
              />
              <p className="text-xs text-muted-foreground">
                {t('sms.templates.placeholderHint')}
              </p>
            </div>

            <div className="space-y-2">
              <Label>{t('sms.templates.description')}</Label>
              <Input
                value={templateForm.description}
                onChange={(e) => setTemplateForm({ ...templateForm, description: e.target.value })}
                placeholder={t('sms.templates.descriptionPlaceholder')}
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
