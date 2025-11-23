import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { orderAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { format } from 'date-fns';

const statusColors = {
  NEW: 'bg-blue-100 text-blue-800',
  ACCEPTED: 'bg-green-100 text-green-800',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  READY: 'bg-purple-100 text-purple-800',
  COURIER_ASSIGNED: 'bg-indigo-100 text-indigo-800',
  ON_DELIVERY: 'bg-indigo-100 text-indigo-800',
  DELIVERED: 'bg-green-100 text-green-800',
  CANCELLED: 'bg-red-100 text-red-800',
};

export default function Orders() {
  const { t } = useTranslation();
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadOrders();
  }, []);

  const loadOrders = async () => {
    try {
      const response = await orderAPI.getAll({ page: 0, size: 50, sort: 'createdAt,desc' });
      setOrders(response.data.data.content || []);
    } catch (error) {
      console.error('Failed to load orders:', error);
    } finally {
      setLoading(false);
    }
  };

  const updateOrderStatus = async (orderId, newStatus) => {
    try {
      await orderAPI.updateStatus(orderId, newStatus, `Status updated to ${newStatus}`);
      loadOrders();
    } catch (error) {
      console.error('Failed to update order status:', error);
      alert(t('messages.error'));
    }
  };

  const nextStatusMap = {
    NEW: 'ACCEPTED',
    ACCEPTED: 'PREPARING',
    PREPARING: 'READY',
    READY: 'COURIER_ASSIGNED',
    COURIER_ASSIGNED: 'ON_DELIVERY',
    ON_DELIVERY: 'DELIVERED',
  };

  if (loading) {
    return <div>{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('orders.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('orders.allOrders')}
          </p>
        </div>
      </div>

      <div className="space-y-4">
        {orders.length === 0 ? (
          <Card>
            <CardContent className="pt-6">
              <p className="text-center text-muted-foreground">
                {t('common.noData')}
              </p>
            </CardContent>
          </Card>
        ) : (
          orders.map((order) => (
            <Card key={order.id}>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <div>
                    <CardTitle className="text-lg">
                      {t('orders.orderNumber')}: #{order.orderNumber}
                    </CardTitle>
                    <p className="text-sm text-muted-foreground mt-1">
                      {order.restaurant?.name || 'Restaurant'}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {order.createdAt && format(new Date(order.createdAt), 'PPpp')}
                    </p>
                  </div>
                  <Badge className={statusColors[order.status] || 'bg-gray-100'}>
                    {t(`orders.statuses.${order.status}`)}
                  </Badge>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid gap-4 md:grid-cols-3">
                  <div>
                    <p className="text-sm font-medium">{t('orders.subtotal')}</p>
                    <p className="text-lg font-bold">${order.subtotal?.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm font-medium">{t('orders.deliveryFee')}</p>
                    <p className="text-lg font-bold">${order.deliveryFee?.toFixed(2)}</p>
                  </div>
                  <div>
                    <p className="text-sm font-medium">{t('orders.total')}</p>
                    <p className="text-2xl font-bold">${order.total?.toFixed(2)}</p>
                  </div>
                </div>

                {order.items && order.items.length > 0 && (
                  <div className="mt-4">
                    <p className="text-sm font-medium mb-2">{t('orders.items')}:</p>
                    <div className="space-y-1">
                      {order.items.map((item, idx) => (
                        <div key={idx} className="text-sm text-muted-foreground flex justify-between">
                          <span>{item.quantity}x {item.productName} {item.variantName ? `(${item.variantName})` : ''}</span>
                          <span>${item.totalPrice?.toFixed(2)}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {order.deliveryInfo && (
                  <div className="mt-4">
                    <p className="text-sm font-medium">{t('orders.deliveryInfo.address')}:</p>
                    <p className="text-sm text-muted-foreground">
                      {order.deliveryInfo.address}, {order.deliveryInfo.city}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {t('orders.deliveryInfo.contactName')}: {order.deliveryInfo.contactName}
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {t('orders.deliveryInfo.contactPhone')}: {order.deliveryInfo.contactPhone}
                    </p>
                  </div>
                )}

                {order.customerNotes && (
                  <div className="mt-4">
                    <p className="text-sm font-medium">{t('orders.notes')}:</p>
                    <p className="text-sm text-muted-foreground">
                      {order.customerNotes}
                    </p>
                  </div>
                )}

                {order.payment && (
                  <div className="mt-4">
                    <p className="text-sm font-medium">{t('orders.payment')}:</p>
                    <p className="text-sm text-muted-foreground">
                      {t(`orders.paymentMethods.${order.payment.method}`)} - {t(`orders.paymentStatus.${order.payment.status}`)}
                    </p>
                  </div>
                )}

                {nextStatusMap[order.status] && (
                  <div className="mt-4 flex gap-2">
                    <Button
                      size="sm"
                      onClick={() => updateOrderStatus(order.id, nextStatusMap[order.status])}
                    >
                      {t('orders.updateStatus')}: {t(`orders.statuses.${nextStatusMap[order.status]}`)}
                    </Button>
                    {order.status !== 'CANCELLED' && order.status !== 'DELIVERED' && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => updateOrderStatus(order.id, 'CANCELLED')}
                      >
                        {t('orders.cancelOrder')}
                      </Button>
                    )}
                  </div>
                )}
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
