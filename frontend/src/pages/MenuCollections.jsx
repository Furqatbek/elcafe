import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { menuCollectionAPI, restaurantAPI, menuAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter
} from '../components/ui/dialog';
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
  Plus,
  Calendar,
  Package,
  Trash2,
  ImageIcon,
  Edit,
  Eye,
  ShoppingBag
} from 'lucide-react';

export default function MenuCollections() {
  const { t } = useTranslation();
  const [collections, setCollections] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [viewModalOpen, setViewModalOpen] = useState(false);
  const [addProductsModalOpen, setAddProductsModalOpen] = useState(false);
  const [selectedCollection, setSelectedCollection] = useState(null);
  const [products, setProducts] = useState([]);
  const [selectedProducts, setSelectedProducts] = useState([]);

  // Form state for creating collections
  const [formData, setFormData] = useState({
    restaurantId: '',
    name: '',
    description: '',
    imageUrl: '',
    startDate: '',
    endDate: '',
    sortOrder: 0,
    productIds: []
  });

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadCollections();
    }
  }, [selectedRestaurant, page]);

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

  const loadCollections = async () => {
    if (!selectedRestaurant) return;

    setLoading(true);
    try {
      const response = await menuCollectionAPI.getAll(selectedRestaurant, { page, size: 12 });
      setCollections(response.data.data.content || []);
      setTotalPages(response.data.data.totalPages || 0);
    } catch (error) {
      console.error('Failed to load collections:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadProducts = async (restaurantId) => {
    try {
      const response = await menuAPI.getPublicMenu(restaurantId);
      // Extract all products from all categories
      const allProducts = response.data.data?.categories?.flatMap(cat => cat.products || []) || [];
      setProducts(allProducts);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const handleCreateCollection = async (e) => {
    e.preventDefault();
    try {
      await menuCollectionAPI.create({
        ...formData,
        restaurantId: selectedRestaurant
      });
      setCreateModalOpen(false);
      resetForm();
      loadCollections();
    } catch (error) {
      console.error('Failed to create collection:', error);
      alert(t('menuCollections.createFailed'));
    }
  };

  const handleDeleteCollection = async (id) => {
    if (!confirm(t('menuCollections.confirmDelete'))) return;

    try {
      await menuCollectionAPI.delete(id);
      loadCollections();
    } catch (error) {
      console.error('Failed to delete collection:', error);
      alert(t('menuCollections.deleteFailed'));
    }
  };

  const handleViewCollection = async (collection) => {
    setSelectedCollection(collection);
    setViewModalOpen(true);
  };

  const handleOpenAddProducts = async (collection) => {
    setSelectedCollection(collection);
    setSelectedProducts([]);
    await loadProducts(collection.restaurantId);
    setAddProductsModalOpen(true);
  };

  const handleAddProducts = async () => {
    if (selectedProducts.length === 0) return;

    try {
      await menuCollectionAPI.addProducts(selectedCollection.id, selectedProducts);
      setAddProductsModalOpen(false);
      setSelectedProducts([]);
      loadCollections();
    } catch (error) {
      console.error('Failed to add products:', error);
      alert(t('menuCollections.addProductsFailed'));
    }
  };

  const toggleProductSelection = (productId) => {
    setSelectedProducts(prev =>
      prev.includes(productId)
        ? prev.filter(id => id !== productId)
        : [...prev, productId]
    );
  };

  const resetForm = () => {
    setFormData({
      restaurantId: '',
      name: '',
      description: '',
      imageUrl: '',
      startDate: '',
      endDate: '',
      sortOrder: 0,
      productIds: []
    });
  };

  const formatDate = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleDateString();
  };

  if (loading && collections.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('menuCollections.title')}</h1>
          <p className="text-muted-foreground mt-1">
            {t('menuCollections.subtitle')}
          </p>
        </div>
        <div className="flex gap-3">
          <Select
            value={selectedRestaurant?.toString()}
            onValueChange={(value) => {
              setSelectedRestaurant(parseInt(value));
              setPage(0);
            }}
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
          <Button onClick={() => setCreateModalOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            {t('menuCollections.createNew')}
          </Button>
        </div>
      </div>

      {collections.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-center text-muted-foreground">
              {t('menuCollections.noCollections')}
            </p>
          </CardContent>
        </Card>
      ) : (
        <>
          <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
            {collections.map((collection) => (
              <Card key={collection.id} className="hover:shadow-lg transition-shadow">
                {collection.imageUrl && (
                  <div className="h-40 overflow-hidden rounded-t-lg">
                    <img
                      src={collection.imageUrl}
                      alt={collection.name}
                      className="w-full h-full object-cover"
                    />
                  </div>
                )}
                <CardHeader>
                  <div className="flex justify-between items-start">
                    <CardTitle className="text-xl">{collection.name}</CardTitle>
                    <Badge variant={collection.isActive ? 'default' : 'secondary'}>
                      {collection.isActive ? t('common.active') : t('common.inactive')}
                    </Badge>
                  </div>
                  {collection.description && (
                    <CardDescription className="line-clamp-2">
                      {collection.description}
                    </CardDescription>
                  )}
                </CardHeader>
                <CardContent className="space-y-4">
                  <div className="space-y-2 text-sm">
                    {collection.startDate && (
                      <div className="flex items-center text-muted-foreground">
                        <Calendar className="h-4 w-4 mr-2" />
                        <span>
                          {t('menuCollections.start')}: {formatDate(collection.startDate)}
                        </span>
                      </div>
                    )}
                    {collection.endDate && (
                      <div className="flex items-center text-muted-foreground">
                        <Calendar className="h-4 w-4 mr-2" />
                        <span>
                          {t('menuCollections.end')}: {formatDate(collection.endDate)}
                        </span>
                      </div>
                    )}
                    <div className="flex items-center text-muted-foreground">
                      <Package className="h-4 w-4 mr-2" />
                      <span>
                        {collection.items?.length || 0} {t('menuCollections.products')}
                      </span>
                    </div>
                  </div>

                  <div className="pt-2 flex gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      className="flex-1"
                      onClick={() => handleViewCollection(collection)}
                    >
                      <Eye className="h-4 w-4 mr-1" />
                      {t('common.view')}
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="flex-1"
                      onClick={() => handleOpenAddProducts(collection)}
                    >
                      <ShoppingBag className="h-4 w-4 mr-1" />
                      {t('menuCollections.addProducts')}
                    </Button>
                    <Button
                      size="sm"
                      variant="destructive"
                      onClick={() => handleDeleteCollection(collection.id)}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>

          {totalPages > 1 && (
            <div className="flex justify-center gap-2">
              <Button
                variant="outline"
                onClick={() => setPage(p => Math.max(0, p - 1))}
                disabled={page === 0}
              >
                {t('common.previous')}
              </Button>
              <span className="flex items-center px-4">
                {t('common.page')} {page + 1} {t('common.of')} {totalPages}
              </span>
              <Button
                variant="outline"
                onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
              >
                {t('common.next')}
              </Button>
            </div>
          )}
        </>
      )}

      {/* Create Collection Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('menuCollections.createNew')}</DialogTitle>
            <DialogDescription>
              {t('menuCollections.createDescription')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateCollection}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="name">{t('menuCollections.name')} *</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  required
                  maxLength={200}
                  placeholder={t('menuCollections.namePlaceholder')}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">{t('menuCollections.description')}</Label>
                <Textarea
                  id="description"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  maxLength={1000}
                  rows={3}
                  placeholder={t('menuCollections.descriptionPlaceholder')}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="imageUrl">{t('menuCollections.imageUrl')}</Label>
                <div className="flex gap-2">
                  <ImageIcon className="h-5 w-5 text-muted-foreground mt-2" />
                  <Input
                    id="imageUrl"
                    type="url"
                    value={formData.imageUrl}
                    onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
                    maxLength={500}
                    placeholder="https://example.com/image.jpg"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="startDate">{t('menuCollections.startDate')}</Label>
                  <Input
                    id="startDate"
                    type="date"
                    value={formData.startDate}
                    onChange={(e) => setFormData({ ...formData, startDate: e.target.value })}
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="endDate">{t('menuCollections.endDate')}</Label>
                  <Input
                    id="endDate"
                    type="date"
                    value={formData.endDate}
                    onChange={(e) => setFormData({ ...formData, endDate: e.target.value })}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="sortOrder">{t('menuCollections.sortOrder')}</Label>
                <Input
                  id="sortOrder"
                  type="number"
                  value={formData.sortOrder}
                  onChange={(e) => setFormData({ ...formData, sortOrder: parseInt(e.target.value) || 0 })}
                  min={0}
                />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setCreateModalOpen(false)}>
                {t('common.cancel')}
              </Button>
              <Button type="submit">
                {t('common.create')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* View Collection Modal */}
      <Dialog open={viewModalOpen} onOpenChange={setViewModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{selectedCollection?.name}</DialogTitle>
            <DialogDescription>
              {selectedCollection?.description}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            {selectedCollection?.imageUrl && (
              <div className="w-full h-48 overflow-hidden rounded-lg">
                <img
                  src={selectedCollection.imageUrl}
                  alt={selectedCollection.name}
                  className="w-full h-full object-cover"
                />
              </div>
            )}

            <div className="grid grid-cols-2 gap-4 text-sm">
              <div>
                <span className="font-medium">{t('common.status')}:</span>
                <Badge className="ml-2" variant={selectedCollection?.isActive ? 'default' : 'secondary'}>
                  {selectedCollection?.isActive ? t('common.active') : t('common.inactive')}
                </Badge>
              </div>
              {selectedCollection?.startDate && (
                <div>
                  <span className="font-medium">{t('menuCollections.startDate')}:</span>
                  <span className="ml-2">{formatDate(selectedCollection.startDate)}</span>
                </div>
              )}
              {selectedCollection?.endDate && (
                <div>
                  <span className="font-medium">{t('menuCollections.endDate')}:</span>
                  <span className="ml-2">{formatDate(selectedCollection.endDate)}</span>
                </div>
              )}
              <div>
                <span className="font-medium">{t('menuCollections.sortOrder')}:</span>
                <span className="ml-2">{selectedCollection?.sortOrder}</span>
              </div>
            </div>

            <div>
              <h3 className="font-semibold mb-3">{t('menuCollections.productsInCollection')}</h3>
              {selectedCollection?.items?.length === 0 ? (
                <p className="text-muted-foreground text-sm">{t('menuCollections.noProducts')}</p>
              ) : (
                <div className="grid gap-3">
                  {selectedCollection?.items?.map((item) => (
                    <div key={item.id} className="flex items-center gap-3 p-3 border rounded-lg">
                      {item.productImageUrl && (
                        <img
                          src={item.productImageUrl}
                          alt={item.productName}
                          className="w-16 h-16 object-cover rounded"
                        />
                      )}
                      <div className="flex-1">
                        <p className="font-medium">{item.productName}</p>
                        <p className="text-sm text-muted-foreground">
                          {t('menuCollections.sortOrder')}: {item.sortOrder}
                        </p>
                      </div>
                      {item.isFeatured && (
                        <Badge variant="secondary">{t('menuCollections.featured')}</Badge>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button onClick={() => setViewModalOpen(false)}>
              {t('common.close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Add Products Modal */}
      <Dialog open={addProductsModalOpen} onOpenChange={setAddProductsModalOpen}>
        <DialogContent className="max-w-3xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('menuCollections.addProductsTo')} "{selectedCollection?.name}"</DialogTitle>
            <DialogDescription>
              {t('menuCollections.addProductsDescription')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="text-sm text-muted-foreground">
              {selectedProducts.length} {t('menuCollections.productsSelected')}
            </div>
            <div className="grid gap-3 max-h-96 overflow-y-auto">
              {products.map((product) => (
                <div
                  key={product.id}
                  className={`flex items-center gap-3 p-3 border rounded-lg cursor-pointer transition-colors ${
                    selectedProducts.includes(product.id) ? 'bg-primary/10 border-primary' : 'hover:bg-gray-50'
                  }`}
                  onClick={() => toggleProductSelection(product.id)}
                >
                  <input
                    type="checkbox"
                    checked={selectedProducts.includes(product.id)}
                    onChange={() => {}}
                    className="h-4 w-4"
                  />
                  {product.imageUrl && (
                    <img
                      src={product.imageUrl}
                      alt={product.name}
                      className="w-16 h-16 object-cover rounded"
                    />
                  )}
                  <div className="flex-1">
                    <p className="font-medium">{product.name}</p>
                    <p className="text-sm text-muted-foreground">{product.description}</p>
                    <p className="text-sm font-medium mt-1">${product.price?.toFixed(2)}</p>
                  </div>
                  {!product.available && (
                    <Badge variant="secondary">{t('common.unavailable')}</Badge>
                  )}
                </div>
              ))}
              {products.length === 0 && (
                <p className="text-center text-muted-foreground py-8">
                  {t('menuCollections.noAvailableProducts')}
                </p>
              )}
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setAddProductsModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleAddProducts} disabled={selectedProducts.length === 0}>
              {t('menuCollections.addSelected')} ({selectedProducts.length})
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
