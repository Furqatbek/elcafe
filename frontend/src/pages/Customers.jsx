import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { customerAPI } from '../services/api';
import { Card, CardContent, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { User, Mail, Phone, MapPin, Tag } from 'lucide-react';
import { format } from 'date-fns';

export default function Customers() {
  const { t } = useTranslation();
  const [customers, setCustomers] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadCustomers();
  }, []);

  const loadCustomers = async () => {
    try {
      const response = await customerAPI.getAll({ page: 0, size: 50, sort: 'createdAt,desc' });
      setCustomers(response.data.data.content || []);
    } catch (error) {
      console.error('Failed to load customers:', error);
    } finally {
      setLoading(false);
    }
  };

  if (loading) {
    return <div>{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('customers.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('customers.allCustomers')}
          </p>
        </div>
        <Button>{t('customers.newCustomer')}</Button>
      </div>

      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {customers.length === 0 ? (
          <Card className="col-span-full">
            <CardContent className="pt-6">
              <p className="text-center text-muted-foreground">
                {t('common.noData')}
              </p>
            </CardContent>
          </Card>
        ) : (
          customers.map((customer) => (
            <Card key={customer.id} className="hover:shadow-lg transition-shadow">
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="flex items-center">
                    <User className="h-5 w-5 mr-2" />
                    {customer.firstName} {customer.lastName}
                  </CardTitle>
                  {customer.active ? (
                    <Badge variant="secondary" className="bg-green-100 text-green-800">
                      {t('customers.active')}
                    </Badge>
                  ) : (
                    <Badge variant="secondary" className="bg-gray-100 text-gray-800">
                      {t('restaurants.inactive')}
                    </Badge>
                  )}
                </div>
              </CardHeader>
              <CardContent className="space-y-3">
                {customer.email && (
                  <div className="flex items-center text-sm text-muted-foreground">
                    <Mail className="h-4 w-4 mr-2 flex-shrink-0" />
                    <span className="truncate">{customer.email}</span>
                  </div>
                )}
                {customer.phone && (
                  <div className="flex items-center text-sm text-muted-foreground">
                    <Phone className="h-4 w-4 mr-2 flex-shrink-0" />
                    {customer.phone}
                  </div>
                )}
                {customer.defaultAddress && (
                  <div className="flex items-start text-sm text-muted-foreground">
                    <MapPin className="h-4 w-4 mr-2 flex-shrink-0 mt-0.5" />
                    <span>
                      {customer.defaultAddress}
                      {customer.city && `, ${customer.city}`}
                      {customer.state && `, ${customer.state}`}
                      {customer.zipCode && ` ${customer.zipCode}`}
                    </span>
                  </div>
                )}
                {customer.tags && (
                  <div className="flex items-start text-sm text-muted-foreground">
                    <Tag className="h-4 w-4 mr-2 flex-shrink-0 mt-0.5" />
                    <span>{customer.tags}</span>
                  </div>
                )}
                {customer.notes && (
                  <div className="pt-2 border-t">
                    <p className="text-sm font-medium">{t('customers.notes')}:</p>
                    <p className="text-sm text-muted-foreground">{customer.notes}</p>
                  </div>
                )}
                {customer.createdAt && (
                  <div className="pt-2 border-t">
                    <p className="text-xs text-muted-foreground">
                      {t('orders.createdAt')}: {format(new Date(customer.createdAt), 'PP')}
                    </p>
                  </div>
                )}
                <div className="pt-2 flex gap-2">
                  <Button size="sm" variant="outline" className="flex-1">
                    {t('customers.viewOrders')}
                  </Button>
                  <Button size="sm" variant="outline" className="flex-1">
                    {t('common.edit')}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>
    </div>
  );
}
