import { useEffect, useState } from 'react';
import { notifyError, notifySuccess, notifyWarning } from '../../lib/errors';
import { useTranslation } from 'react-i18next';
import { stockAlertAPI } from '../../services/api';
import { formatDateTime } from '../../utils/dateUtils';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Label } from '../../components/ui/label';
import { Input } from '../../components/ui/input';
import { Checkbox } from '../../components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../../components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../components/ui/table';
import {
  Plus,
  Edit,
  Trash2,
  Bell,
  Send,
  Power,
  AlertTriangle,
  TrendingDown,
} from 'lucide-react';

export default function InventoryAlerts() {
  const { t } = useTranslation();
  const { selectedRestaurant } = useInventory();

  const [subscriptions, setSubscriptions] = useState([]);
  const [alertSummary, setAlertSummary] = useState(null);
  const [subscriptionModalOpen, setSubscriptionModalOpen] = useState(false);
  const [editingSubscription, setEditingSubscription] = useState(null);
  const [subscriptionFormData, setSubscriptionFormData] = useState({
    telegramChatId: '',
    subscriberName: '',
    alertOnLowStock: true,
    alertOnReorder: true,
  });

  useEffect(() => {
    if (selectedRestaurant) {
      loadSubscriptions();
      loadAlertSummary();
    }
  }, [selectedRestaurant]);

  const loadSubscriptions = async () => {
    try {
      const response = await stockAlertAPI.getSubscriptions(selectedRestaurant);
      setSubscriptions(response.data.data || []);
    } catch (error) {
      console.error('Failed to load subscriptions:', error);
    }
  };

  const loadAlertSummary = async () => {
    try {
      const response = await stockAlertAPI.getSummary(selectedRestaurant);
      setAlertSummary(response.data.data || null);
    } catch (error) {
      console.error('Failed to load alert summary:', error);
    }
  };

  const handleAddSubscription = () => {
    setEditingSubscription(null);
    setSubscriptionFormData({
      telegramChatId: '',
      subscriberName: '',
      alertOnLowStock: true,
      alertOnReorder: true,
    });
    setSubscriptionModalOpen(true);
  };

  const handleEditSubscription = (subscription) => {
    setEditingSubscription(subscription);
    setSubscriptionFormData({
      telegramChatId: subscription.telegramChatId.toString(),
      subscriberName: subscription.subscriberName || '',
      alertOnLowStock: subscription.alertOnLowStock,
      alertOnReorder: subscription.alertOnReorder,
    });
    setSubscriptionModalOpen(true);
  };

  const handleSaveSubscription = async () => {
    try {
      const data = {
        restaurantId: selectedRestaurant,
        telegramChatId: parseInt(subscriptionFormData.telegramChatId),
        subscriberName: subscriptionFormData.subscriberName,
        alertOnLowStock: subscriptionFormData.alertOnLowStock,
        alertOnReorder: subscriptionFormData.alertOnReorder,
      };

      if (editingSubscription) {
        await stockAlertAPI.updateSubscription(editingSubscription.id, data);
      } else {
        await stockAlertAPI.createSubscription(data);
      }

      setSubscriptionModalOpen(false);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to save subscription:', error);
      notifyWarning(t('inventory.stockAlerts.errors.saveFailed', 'Failed to save subscription'));
    }
  };

  const handleToggleSubscription = async (id) => {
    try {
      await stockAlertAPI.toggleSubscription(id);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to toggle subscription:', error);
      notifyError(error);
    }
  };

  const handleDeleteSubscription = async (id) => {
    if (!confirm(t('inventory.stockAlerts.confirmDelete', 'Are you sure you want to delete this subscription?'))) return;

    try {
      await stockAlertAPI.deleteSubscription(id);
      loadSubscriptions();
    } catch (error) {
      console.error('Failed to delete subscription:', error);
      notifyError(error);
    }
  };

  const handleTriggerAlerts = async () => {
    try {
      await stockAlertAPI.trigger(selectedRestaurant);
      notifySuccess(t('inventory.stockAlerts.alertTriggered', 'Stock alert sent successfully!'));
      loadAlertSummary();
    } catch (error) {
      console.error('Failed to trigger alerts:', error);
      notifyWarning('Failed to trigger alerts');
    }
  };

  return (
    <InventoryLayout>
      <div className="space-y-6">
        {/* Summary Stats */}
        {alertSummary && (alertSummary.lowStockCount > 0 || alertSummary.reorderCount > 0) && (
          <div className="grid gap-4 md:grid-cols-3">
            <Card className="border-red-200">
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stockAlerts.lowStockItems', 'Low Stock Items')}</CardTitle>
                <AlertTriangle className="h-4 w-4 text-red-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-red-600">{alertSummary.lowStockCount}</div>
                <p className="text-xs text-muted-foreground">{t('inventory.stats.needsAttention', 'Needs attention')}</p>
              </CardContent>
            </Card>
            <Card className="border-yellow-200">
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stockAlerts.reorderItems', 'Reorder Items')}</CardTitle>
                <TrendingDown className="h-4 w-4 text-yellow-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold text-yellow-600">{alertSummary.reorderCount}</div>
                <p className="text-xs text-muted-foreground">{t('inventory.stats.reorderSoon', 'Reorder soon')}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">{t('inventory.stockAlerts.activeSubscriptions', 'Active Subscriptions')}</CardTitle>
                <Bell className="h-4 w-4 text-blue-600" />
              </CardHeader>
              <CardContent>
                <div className="text-2xl font-bold">{subscriptions.filter(s => s.active).length}</div>
                <Button size="sm" className="mt-2" onClick={handleTriggerAlerts}>
                  <Send className="h-3 w-3 mr-1" />
                  {t('inventory.stockAlerts.trigger', 'Trigger Now')}
                </Button>
              </CardContent>
            </Card>
          </div>
        )}

        {/* Subscriptions Table */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Bell className="h-5 w-5" />
                  {t('inventory.stockAlerts.subscriptions', 'Telegram Subscriptions')}
                </CardTitle>
                <CardDescription>
                  {t('inventory.stockAlerts.subscriptionsDesc', 'Manage Telegram chat subscriptions for stock alerts')}
                </CardDescription>
              </div>
              <Button onClick={handleAddSubscription}>
                <Plus className="h-4 w-4 mr-2" />
                {t('inventory.stockAlerts.addSubscription', 'Add Subscription')}
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('inventory.stockAlerts.subscriberName', 'Subscriber')}</TableHead>
                  <TableHead>{t('inventory.stockAlerts.chatId', 'Telegram Chat ID')}</TableHead>
                  <TableHead>{t('inventory.stockAlerts.lowStockAlerts', 'Low Stock')}</TableHead>
                  <TableHead>{t('inventory.stockAlerts.reorderAlerts', 'Reorder')}</TableHead>
                  <TableHead>{t('inventory.stockAlerts.status', 'Status')}</TableHead>
                  <TableHead>{t('inventory.stockAlerts.lastAlert', 'Last Alert')}</TableHead>
                  <TableHead className="text-right">{t('common.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {subscriptions.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                      {t('inventory.stockAlerts.noSubscriptions', 'No subscriptions found. Add a Telegram chat to receive stock alerts.')}
                    </TableCell>
                  </TableRow>
                ) : (
                  subscriptions.map((subscription) => (
                    <TableRow key={subscription.id}>
                      <TableCell className="font-medium">{subscription.subscriberName || '-'}</TableCell>
                      <TableCell>{subscription.telegramChatId}</TableCell>
                      <TableCell>
                        <Badge className={subscription.alertOnLowStock ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}>
                          {subscription.alertOnLowStock ? t('common.yes', 'Yes') : t('common.no', 'No')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge className={subscription.alertOnReorder ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}>
                          {subscription.alertOnReorder ? t('common.yes', 'Yes') : t('common.no', 'No')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge className={subscription.active ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800'}>
                          {subscription.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        {subscription.lastAlertSentAt
                          ? formatDateTime(subscription.lastAlertSentAt)
                          : t('inventory.stockAlerts.never', 'Never')}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleToggleSubscription(subscription.id)}
                            title={subscription.active ? t('common.deactivate', 'Deactivate') : t('common.activate', 'Activate')}
                          >
                            <Power className={`h-4 w-4 ${subscription.active ? 'text-green-600' : 'text-gray-400'}`} />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleEditSubscription(subscription)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleDeleteSubscription(subscription.id)}
                          >
                            <Trash2 className="h-4 w-4 text-red-600" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </CardContent>
        </Card>

        {/* Setup Instructions */}
        <Card>
          <CardHeader>
            <CardTitle>{t('inventory.stockAlerts.howToSetup', 'How to Get Telegram Chat ID')}</CardTitle>
          </CardHeader>
          <CardContent>
            <ol className="list-decimal list-inside space-y-2 text-sm text-muted-foreground">
              <li>{t('inventory.stockAlerts.step1', 'Start a chat with your Telegram bot')}</li>
              <li>{t('inventory.stockAlerts.step2', 'Send the /start command to the bot')}</li>
              <li>{t('inventory.stockAlerts.step3', 'The bot will reply with your Chat ID')}</li>
              <li>{t('inventory.stockAlerts.step4', 'Copy the Chat ID and add it as a subscription here')}</li>
            </ol>
          </CardContent>
        </Card>
      </div>

      {/* Add/Edit Modal */}
      <Dialog open={subscriptionModalOpen} onOpenChange={setSubscriptionModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingSubscription
                ? t('inventory.stockAlerts.editSubscription', 'Edit Subscription')
                : t('inventory.stockAlerts.addSubscription', 'Add Subscription')}
            </DialogTitle>
            <DialogDescription>
              {t('inventory.stockAlerts.subscriptionDesc', 'Configure Telegram alerts for stock notifications')}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.stockAlerts.subscriberName', 'Subscriber Name')}</Label>
              <Input
                value={subscriptionFormData.subscriberName}
                onChange={(e) => setSubscriptionFormData({ ...subscriptionFormData, subscriberName: e.target.value })}
                placeholder={t('inventory.stockAlerts.namePlaceholder', 'e.g., Manager John')}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('inventory.stockAlerts.chatId', 'Telegram Chat ID')} *</Label>
              <Input
                type="number"
                value={subscriptionFormData.telegramChatId}
                onChange={(e) => setSubscriptionFormData({ ...subscriptionFormData, telegramChatId: e.target.value })}
              />
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                id="alertOnLowStock"
                checked={subscriptionFormData.alertOnLowStock}
                onCheckedChange={(checked) => setSubscriptionFormData({ ...subscriptionFormData, alertOnLowStock: checked })}
              />
              <Label htmlFor="alertOnLowStock">{t('inventory.stockAlerts.alertOnLowStock', 'Alert on Low Stock')}</Label>
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                id="alertOnReorder"
                checked={subscriptionFormData.alertOnReorder}
                onCheckedChange={(checked) => setSubscriptionFormData({ ...subscriptionFormData, alertOnReorder: checked })}
              />
              <Label htmlFor="alertOnReorder">{t('inventory.stockAlerts.alertOnReorder', 'Alert on Reorder Level')}</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSubscriptionModalOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleSaveSubscription} disabled={!subscriptionFormData.telegramChatId}>{t('common.save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
