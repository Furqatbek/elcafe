import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { linkedItemAPI } from '../services/api';
import { useApiCall } from '../hooks/useApiCall';
import QueryState from '../components/QueryState';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  ArrowLeft,
  Package,
  Tag,
  ShoppingBag,
  Gift,
  Star
} from 'lucide-react';

export default function LinkedItems() {
  const { productId } = useParams();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const [product, setProduct] = useState(null);
  const [filterType, setFilterType] = useState('all');

  // EH-3: the linked-items load owns loading/error/empty via useApiCall + <QueryState>, so a failed
  // fetch shows an error + retry instead of the "no linked items" empty state that hid the failure.
  const { data, loading, error, refetch: loadLinkedItems } = useApiCall(
    () => (productId
      ? linkedItemAPI.getLinkedItems(productId).then((res) => res.data.data || [])
      : Promise.resolve([])),
    [productId],
  );
  const linkedItems = data || [];

  useEffect(() => {
    if (productId) {
      loadProductDetails();
    }
  }, [productId]);

  const loadProductDetails = async () => {
    try {
      // Try to get product details from the menu
      // We'll need to search through all restaurants' menus
      // For simplicity, we'll use a placeholder
      // In a real scenario, you'd want to pass the restaurant ID or store it
      setProduct({
        id: productId,
        name: `Product #${productId}`,
        description: 'Product details'
      });
    } catch (error) {
      console.error('Failed to load product details:', error);
    }
  };

  const getLinkTypeIcon = (linkType) => {
    switch (linkType) {
      case 'RECOMMENDATION':
        return <Star className="h-4 w-4" />;
      case 'UPSELL':
        return <ShoppingBag className="h-4 w-4" />;
      case 'CROSS_SELL':
        return <Package className="h-4 w-4" />;
      case 'BUNDLE':
        return <Gift className="h-4 w-4" />;
      default:
        return <Tag className="h-4 w-4" />;
    }
  };

  const getLinkTypeBadgeVariant = (linkType) => {
    switch (linkType) {
      case 'RECOMMENDATION':
        return 'default';
      case 'UPSELL':
        return 'secondary';
      case 'CROSS_SELL':
        return 'outline';
      case 'BUNDLE':
        return 'destructive';
      default:
        return 'secondary';
    }
  };

  const filteredItems = filterType === 'all'
    ? linkedItems
    : linkedItems.filter(item => item.linkType === filterType);

  const linkTypes = [...new Set(linkedItems.map(item => item.linkType))];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Button
            variant="outline"
            size="sm"
            onClick={() => navigate('/products')}
          >
            <ArrowLeft className="h-4 w-4 mr-2" />
            {t('common.back')}
          </Button>
          <div>
            <h1 className="text-3xl font-bold">Linked Items</h1>
            <p className="text-muted-foreground mt-1">
              Related products and recommendations
            </p>
          </div>
        </div>
      </div>

      {/* Product Info */}
      {product && (
        <Card>
          <CardHeader>
            <CardTitle>Current Product</CardTitle>
            <CardDescription>{product.name}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="text-sm text-muted-foreground">
              Product ID: {productId}
            </div>
          </CardContent>
        </Card>
      )}

      <QueryState
        loading={loading}
        error={error}
        onRetry={loadLinkedItems}
        empty={linkedItems.length === 0}
        emptyMessage={t('linkedItems.noItems', 'No linked items found for this product')}
      >
      <div className="space-y-6">
      {/* Filter */}
      <div className="flex gap-4 items-center">
        <label className="text-sm font-medium">Filter by type:</label>
        <Select value={filterType} onValueChange={setFilterType}>
          <SelectTrigger className="w-[200px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('common.all')}</SelectItem>
            {linkTypes.map((type) => (
              <SelectItem key={type} value={type}>
                {type}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Total Linked Items</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{linkedItems.length}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Recommendations</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {linkedItems.filter(item => item.linkType === 'RECOMMENDATION').length}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Upsells</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {linkedItems.filter(item => item.linkType === 'UPSELL').length}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium">Cross-sells</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">
              {linkedItems.filter(item => item.linkType === 'CROSS_SELL').length}
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Linked Items Grid */}
      {filteredItems.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-center text-muted-foreground">
              {t('linkedItems.noneMatchFilter', 'No linked items match this filter')}
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
          {filteredItems.map((item) => (
            <Card key={item.id} className="hover:shadow-lg transition-shadow">
              {item.linkedProductImageUrl && (
                <div className="h-40 overflow-hidden rounded-t-lg">
                  <img
                    src={item.linkedProductImageUrl}
                    alt={item.linkedProductName}
                    className="w-full h-full object-cover"
                  />
                </div>
              )}
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="text-lg line-clamp-1">
                    {item.linkedProductName}
                  </CardTitle>
                  <Badge variant={getLinkTypeBadgeVariant(item.linkType)}>
                    <div className="flex items-center gap-1">
                      {getLinkTypeIcon(item.linkType)}
                      <span>{item.linkType}</span>
                    </div>
                  </Badge>
                </div>
                {item.description && (
                  <CardDescription className="line-clamp-2">
                    {item.description}
                  </CardDescription>
                )}
              </CardHeader>
              <CardContent className="space-y-3">
                {item.linkedProductPrice && (
                  <div className="font-semibold text-lg">
                    <span>{item.linkedProductPrice.toFixed(2)}</span>
                  </div>
                )}

                {item.discountPercent > 0 && (
                  <div className="flex items-center justify-between p-2 bg-green-50 rounded">
                    <span className="text-sm font-medium text-green-700">
                      Discount
                    </span>
                    <Badge variant="secondary" className="bg-green-100 text-green-800">
                      {item.discountPercent}% OFF
                    </Badge>
                  </div>
                )}

                <div className="text-sm text-muted-foreground">
                  <div className="flex justify-between">
                    <span>Sort Order:</span>
                    <span className="font-medium">{item.sortOrder}</span>
                  </div>
                  {item.priority && (
                    <div className="flex justify-between mt-1">
                      <span>Priority:</span>
                      <span className="font-medium">{item.priority}</span>
                    </div>
                  )}
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
      </div>
      </QueryState>
    </div>
  );
}
