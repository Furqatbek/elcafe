import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { menuAPI, restaurantAPI } from '../services/api';
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
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '../components/ui/accordion';
import {
  ChefHat,
  Package,
  DollarSign,
  UtensilsCrossed,
  Grid,
  List
} from 'lucide-react';

export default function Menu() {
  const { t } = useTranslation();
  const [menu, setMenu] = useState(null);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [viewMode, setViewMode] = useState('accordion'); // 'accordion' or 'grid'

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadMenu();
    }
  }, [selectedRestaurant]);

  const loadRestaurants = async () => {
    try {
      const response = await restaurantAPI.getAll({ page: 0, size: 100 });
      const restaurantList = response.data.data.content || [];
      setRestaurants(restaurantList);
      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadMenu = async () => {
    if (!selectedRestaurant) return;

    setLoading(true);
    try {
      const response = await menuAPI.getPublicMenu(selectedRestaurant);
      setMenu(response.data.data);
    } catch (error) {
      console.error('Failed to load menu:', error);
    } finally {
      setLoading(false);
    }
  };

  const calculateStats = () => {
    if (!menu?.categories) return { totalCategories: 0, totalProducts: 0, availableProducts: 0 };

    const totalCategories = menu.categories.length;
    const totalProducts = menu.categories.reduce((sum, cat) => sum + (cat.products?.length || 0), 0);
    const availableProducts = menu.categories.reduce(
      (sum, cat) => sum + (cat.products?.filter(p => p.available)?.length || 0),
      0
    );

    return { totalCategories, totalProducts, availableProducts };
  };

  const stats = calculateStats();

  if (loading && !menu) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('nav.menu')}</h1>
          <p className="text-muted-foreground mt-1">
            Complete menu with categories and products
          </p>
        </div>
        <div className="flex gap-3">
          <div className="flex gap-2">
            <Button
              variant={viewMode === 'accordion' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setViewMode('accordion')}
            >
              <List className="h-4 w-4" />
            </Button>
            <Button
              variant={viewMode === 'grid' ? 'default' : 'outline'}
              size="sm"
              onClick={() => setViewMode('grid')}
            >
              <Grid className="h-4 w-4" />
            </Button>
          </div>
          <Select
            value={selectedRestaurant?.toString()}
            onValueChange={(value) => setSelectedRestaurant(parseInt(value))}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('common.selectRestaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map((restaurant) => (
                <SelectItem key={restaurant.id} value={restaurant.id.toString()}>
                  {restaurant.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <ChefHat className="h-4 w-4" />
              {t('menu.categories')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalCategories}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <Package className="h-4 w-4" />
              {t('common.total')} {t('menu.products')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.totalProducts}</div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-sm font-medium flex items-center gap-2">
              <UtensilsCrossed className="h-4 w-4" />
              {t('menu.inStock')}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{stats.availableProducts}</div>
          </CardContent>
        </Card>
      </div>

      {/* Menu Content */}
      {!menu?.categories || menu.categories.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-center text-muted-foreground">
              {t('common.noData')}
            </p>
          </CardContent>
        </Card>
      ) : viewMode === 'accordion' ? (
        // Accordion View
        <Accordion type="multiple" className="space-y-4">
          {menu.categories.map((category, index) => (
            <AccordionItem key={category.id} value={`category-${index}`} className="border rounded-lg px-4">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center justify-between w-full pr-4">
                  <div className="flex items-center gap-3">
                    <ChefHat className="h-5 w-5 text-muted-foreground" />
                    <div className="text-left">
                      <h3 className="font-semibold text-lg">{category.name}</h3>
                      {category.description && (
                        <p className="text-sm text-muted-foreground">{category.description}</p>
                      )}
                    </div>
                  </div>
                  <Badge variant="secondary">
                    {category.products?.length || 0} items
                  </Badge>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 pt-4">
                  {category.products?.map((product) => (
                    <Card key={product.id} className="hover:shadow-md transition-shadow">
                      {product.imageUrl && (
                        <div className="h-32 overflow-hidden rounded-t-lg">
                          <img
                            src={product.imageUrl}
                            alt={product.name}
                            className="w-full h-full object-cover"
                          />
                        </div>
                      )}
                      <CardHeader className="pb-3">
                        <div className="flex justify-between items-start">
                          <CardTitle className="text-base line-clamp-1">{product.name}</CardTitle>
                          <Badge variant={product.available ? 'default' : 'secondary'} className="ml-2">
                            {product.available ? t('menu.inStock') : t('menu.outOfStock')}
                          </Badge>
                        </div>
                        {product.description && (
                          <CardDescription className="line-clamp-2 text-xs">
                            {product.description}
                          </CardDescription>
                        )}
                      </CardHeader>
                      <CardContent>
                        <div className="flex items-center justify-between">
                          <div className="flex items-center font-semibold text-lg">
                            <DollarSign className="h-5 w-5 mr-1" />
                            <span>{product.price?.toFixed(2)}</span>
                          </div>
                          {product.isFeatured && (
                            <Badge variant="outline" className="text-xs">
                              {t('menu.featured')}
                            </Badge>
                          )}
                        </div>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      ) : (
        // Grid View
        <div className="space-y-8">
          {menu.categories.map((category) => (
            <div key={category.id} className="space-y-4">
              <div className="flex items-center justify-between pb-2 border-b">
                <div className="flex items-center gap-3">
                  <ChefHat className="h-6 w-6 text-muted-foreground" />
                  <div>
                    <h2 className="text-2xl font-bold">{category.name}</h2>
                    {category.description && (
                      <p className="text-sm text-muted-foreground">{category.description}</p>
                    )}
                  </div>
                </div>
                <Badge variant="secondary">
                  {category.products?.length || 0} items
                </Badge>
              </div>

              <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
                {category.products?.map((product) => (
                  <Card key={product.id} className="hover:shadow-lg transition-shadow">
                    {product.imageUrl && (
                      <div className="h-40 overflow-hidden rounded-t-lg">
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          className="w-full h-full object-cover"
                        />
                      </div>
                    )}
                    <CardHeader className="pb-3">
                      <div className="flex justify-between items-start">
                        <CardTitle className="text-base line-clamp-1">{product.name}</CardTitle>
                        <Badge variant={product.available ? 'default' : 'secondary'} className="ml-2 flex-shrink-0">
                          {product.available ? t('menu.inStock') : t('menu.outOfStock')}
                        </Badge>
                      </div>
                      {product.description && (
                        <CardDescription className="line-clamp-2 text-xs">
                          {product.description}
                        </CardDescription>
                      )}
                    </CardHeader>
                    <CardContent>
                      <div className="flex items-center justify-between">
                        <div className="flex items-center font-semibold text-lg">
                          <DollarSign className="h-5 w-5 mr-1" />
                          <span>{product.price?.toFixed(2)}</span>
                        </div>
                        {product.isFeatured && (
                          <Badge variant="outline" className="text-xs">
                            {t('menu.featured')}
                          </Badge>
                        )}
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
