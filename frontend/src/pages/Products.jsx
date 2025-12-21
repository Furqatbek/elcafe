import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { menuAPI, restaurantAPI, uploadAPI, productVariantAPI } from '../services/api';
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
  Package,
  Search,
  DollarSign,
  Plus,
  Star,
  Edit,
  Trash2,
  List
} from 'lucide-react';

export default function Products() {
  const { t } = useTranslation();
  const [products, setProducts] = useState([]);
  const [filteredProducts, setFilteredProducts] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(null);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [categories, setCategories] = useState([]);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    imageUrl: '',
    price: '',
    categoryId: '',
    sortOrder: 0,
    inStock: true,
    featured: false
  });
  const [imageFile, setImageFile] = useState(null);
  const [imagePreview, setImagePreview] = useState('');

  // Variant management state
  const [variantsModalOpen, setVariantsModalOpen] = useState(false);
  const [variants, setVariants] = useState([]);
  const [selectedProductForVariants, setSelectedProductForVariants] = useState(null);
  const [variantFormData, setVariantFormData] = useState({
    name: '',
    description: '',
    price: '',
    inStock: true,
    sortOrder: 0
  });
  const [createVariantModalOpen, setCreateVariantModalOpen] = useState(false);
  const [editVariantModalOpen, setEditVariantModalOpen] = useState(false);
  const [deleteVariantDialogOpen, setDeleteVariantDialogOpen] = useState(false);
  const [selectedVariant, setSelectedVariant] = useState(null);

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadProducts();
      loadCategories();
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    filterProducts();
  }, [searchTerm, selectedCategory, products]);

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

  const loadCategories = async () => {
    if (!selectedRestaurant) return;

    try {
      const response = await menuAPI.getCategories(selectedRestaurant);
      const cats = response.data.data || [];
      setCategories(cats);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  };

  const loadProducts = async () => {
    if (!selectedRestaurant) return;

    setLoading(true);
    try {
      const response = await menuAPI.getProductsByRestaurant(selectedRestaurant);
      const productsData = response.data.data || [];

      setProducts(productsData);
    } catch (error) {
      console.error('Failed to load products:', error);
    } finally {
      setLoading(false);
    }
  };

  const filterProducts = () => {
    let filtered = [...products];

    // Filter by search term
    if (searchTerm) {
      filtered = filtered.filter(product =>
        product.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        product.description?.toLowerCase().includes(searchTerm.toLowerCase())
      );
    }

    // Filter by category
    if (selectedCategory !== 'all') {
      filtered = filtered.filter(product => product.categoryId === parseInt(selectedCategory));
    }

    setFilteredProducts(filtered);
  };

  const handleImageChange = (e) => {
    const file = e.target.files?.[0];
    if (file) {
      setImageFile(file);
      // Create preview URL
      const reader = new FileReader();
      reader.onloadend = () => {
        setImagePreview(reader.result);
      };
      reader.readAsDataURL(file);
    }
  };

  const handleCreateProduct = async (e) => {
    e.preventDefault();

    try {
      let imageUrl = formData.imageUrl;

      // Upload image file if provided
      if (imageFile) {
        const uploadResponse = await uploadAPI.uploadImage(imageFile);
        imageUrl = uploadResponse.data.data;
      }

      await menuAPI.createProduct({
        ...formData,
        imageUrl,
        price: parseFloat(formData.price),
        categoryId: parseInt(formData.categoryId)
      });
      setCreateModalOpen(false);
      resetForm();
      loadProducts();
    } catch (error) {
      console.error('Failed to create product:', error);
      alert(t('menu.messages.createProductError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const resetForm = () => {
    setFormData({
      name: '',
      description: '',
      imageUrl: '',
      price: '',
      categoryId: '',
      sortOrder: 0,
      inStock: true,
      featured: false
    });
    setImageFile(null);
    setImagePreview('');
  };

  const handleEditClick = (product) => {
    setSelectedProduct(product);
    setFormData({
      name: product.name,
      description: product.description || '',
      imageUrl: product.imageUrl || '',
      price: product.price?.toString() || '',
      categoryId: product.categoryId?.toString() || '',
      sortOrder: product.sortOrder || 0,
      inStock: product.available ?? true,
      featured: product.isFeatured ?? false
    });
    setImagePreview(product.imageUrl || '');
    setEditModalOpen(true);
  };

  const handleUpdateProduct = async (e) => {
    e.preventDefault();

    try {
      let imageUrl = formData.imageUrl;

      // Upload new image file if provided
      if (imageFile) {
        const uploadResponse = await uploadAPI.uploadImage(imageFile);
        imageUrl = uploadResponse.data.data;
      }

      await menuAPI.updateProduct(selectedProduct.id, {
        ...formData,
        imageUrl,
        price: parseFloat(formData.price),
        categoryId: parseInt(formData.categoryId)
      });
      setEditModalOpen(false);
      resetForm();
      setSelectedProduct(null);
      loadProducts();
    } catch (error) {
      console.error('Failed to update product:', error);
      alert(t('menu.messages.updateProductError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleDeleteClick = (product) => {
    setSelectedProduct(product);
    setDeleteDialogOpen(true);
  };

  const handleConfirmDelete = async () => {
    if (!selectedProduct) return;

    try {
      await menuAPI.deleteProduct(selectedProduct.id);
      setDeleteDialogOpen(false);
      setSelectedProduct(null);
      loadProducts();
    } catch (error) {
      console.error('Failed to delete product:', error);
      alert(t('menu.messages.deleteProductError') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  // Variant management handlers
  const handleViewVariants = async (product) => {
    setSelectedProductForVariants(product);
    setVariantsModalOpen(true);
    await loadVariants(product.id);
  };

  const loadVariants = async (productId) => {
    try {
      const response = await productVariantAPI.getAllNoPaging(productId);
      setVariants(response.data.data || []);
    } catch (error) {
      console.error('Failed to load variants:', error);
      setVariants([]);
    }
  };

  const resetVariantForm = () => {
    setVariantFormData({
      name: '',
      description: '',
      price: '',
      inStock: true,
      sortOrder: 0
    });
  };

  const handleCreateVariant = async (e) => {
    e.preventDefault();
    if (!selectedProductForVariants) return;

    try {
      await productVariantAPI.create(selectedProductForVariants.id, {
        ...variantFormData,
        price: parseFloat(variantFormData.price)
      });
      setCreateVariantModalOpen(false);
      resetVariantForm();
      await loadVariants(selectedProductForVariants.id);
    } catch (error) {
      console.error('Failed to create variant:', error);
      alert(t('pages.products.errors.createVariantFailed', 'Failed to create variant') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleEditVariantClick = (variant) => {
    setSelectedVariant(variant);
    setVariantFormData({
      name: variant.name,
      description: variant.description || '',
      price: variant.price?.toString() || '',
      inStock: variant.inStock ?? true,
      sortOrder: variant.sortOrder || 0
    });
    setEditVariantModalOpen(true);
  };

  const handleUpdateVariant = async (e) => {
    e.preventDefault();
    if (!selectedProductForVariants || !selectedVariant) return;

    try {
      await productVariantAPI.update(selectedProductForVariants.id, selectedVariant.id, {
        ...variantFormData,
        price: parseFloat(variantFormData.price)
      });
      setEditVariantModalOpen(false);
      resetVariantForm();
      setSelectedVariant(null);
      await loadVariants(selectedProductForVariants.id);
    } catch (error) {
      console.error('Failed to update variant:', error);
      alert(t('pages.products.errors.updateVariantFailed', 'Failed to update variant') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  const handleDeleteVariantClick = (variant) => {
    setSelectedVariant(variant);
    setDeleteVariantDialogOpen(true);
  };

  const handleConfirmDeleteVariant = async () => {
    if (!selectedProductForVariants || !selectedVariant) return;

    try {
      await productVariantAPI.delete(selectedProductForVariants.id, selectedVariant.id);
      setDeleteVariantDialogOpen(false);
      setSelectedVariant(null);
      await loadVariants(selectedProductForVariants.id);
    } catch (error) {
      console.error('Failed to delete variant:', error);
      alert(t('pages.products.errors.deleteVariantFailed', 'Failed to delete variant') + ': ' + (error.response?.data?.message || error.message));
    }
  };

  if (loading && products.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('pages.products.title', 'Products')}</h1>
          <p className="text-muted-foreground mt-1">{t('pages.products.subtitle', 'Browse all food items')}</p>
        </div>
        <div className="flex gap-3">
          <Button onClick={() => setCreateModalOpen(true)}>
            <Plus className="h-4 w-4 mr-2" />
            {t('pages.products.newProduct', 'New Product')}
          </Button>
          <Select
            value={selectedRestaurant?.toString()}
            onValueChange={(value) => setSelectedRestaurant(parseInt(value))}
          >
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('pages.products.selectRestaurant', 'Select Restaurant')} />
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

      {/* Search Bar */}
      <div className="flex gap-4">
        <div className="flex-1 relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t("common.placeholders.searchProducts")}
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-10"
          />
        </div>
        <Select value={selectedCategory} onValueChange={setSelectedCategory}>
          <SelectTrigger className="w-[200px]">
            <SelectValue placeholder={t("common.placeholders.allCategories")} />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">{t('pages.products.allCategories', 'All Categories')}</SelectItem>
            {categories.map((category) => (
              <SelectItem key={category.id} value={category.id.toString()}>
                {category.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Products Grid */}
      {filteredProducts.length === 0 ? (
        <Card>
          <CardContent className="pt-6">
            <p className="text-center text-muted-foreground">
              {t("common.messages.noProductsFound")}
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {filteredProducts.map((product) => (
            <Card key={product.id} className="hover:shadow-lg transition-shadow overflow-hidden">
              <div className="h-48 overflow-hidden relative">
                {product.imageUrl ? (
                  <img
                    src={product.imageUrl}
                    alt={product.name}
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <div className="w-full h-full bg-gray-100 flex items-center justify-center">
                    <Package className="h-16 w-16 text-gray-400" />
                  </div>
                )}
                <div className="absolute top-2 right-2 flex gap-2">
                  {product.status && (
                    <Badge variant={product.status === 'LIVE' ? 'default' : 'secondary'}>
                      {product.status}
                    </Badge>
                  )}
                  {product.isFeatured && (
                    <Badge className="bg-yellow-500">
                      <Star className="h-3 w-3 mr-1" />
                      {t('pages.products.featured', 'Featured')}
                    </Badge>
                  )}
                </div>
              </div>
              <CardHeader>
                <div className="flex justify-between items-start">
                  <CardTitle className="text-lg line-clamp-1">{product.name}</CardTitle>
                  <Badge variant={product.available ? 'default' : 'secondary'}>
                    {product.available ? t('pages.products.inStock', 'In Stock') : t('pages.products.outOfStock', 'Out of Stock')}
                  </Badge>
                </div>
                {product.description && (
                  <CardDescription className="line-clamp-2">
                    {product.description}
                  </CardDescription>
                )}
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center text-muted-foreground text-sm">
                    <Package className="h-4 w-4 mr-2" />
                    <span>{product.categoryName}</span>
                  </div>
                  <div className="flex items-center font-semibold text-lg text-green-600">
                    <DollarSign className="h-5 w-5" />
                    <span>{product.price?.toFixed(2)}</span>
                  </div>
                </div>
                <div className="flex gap-2 pt-2 flex-wrap">
                  <Button
                    size="sm"
                    variant="outline"
                    className="flex-1 min-w-[80px]"
                    onClick={() => handleEditClick(product)}
                  >
                    <Edit className="h-4 w-4 mr-1" />
                    {t('pages.products.edit', 'Edit')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    className="flex-1 min-w-[80px]"
                    onClick={() => handleViewVariants(product)}
                  >
                    <List className="h-4 w-4 mr-1" />
                    {t('pages.products.variants', 'Variants')}
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    className="flex-1 min-w-[80px] text-red-600 hover:text-red-700 hover:bg-red-50"
                    onClick={() => handleDeleteClick(product)}
                  >
                    <Trash2 className="h-4 w-4 mr-1" />
                    {t('pages.products.delete', 'Delete')}
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Create Product Modal */}
      <Dialog open={createModalOpen} onOpenChange={setCreateModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('menu.createProduct')}</DialogTitle>
            <DialogDescription>
              {t('pages.products.createDescription', 'Fill in the details to create a new product')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateProduct}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="name">{t('menu.productName')} *</Label>
                <Input
                  id="name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  required
                  maxLength={200}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="category">{t('pages.products.category', 'Category')} *</Label>
                <Select
                  value={formData.categoryId}
                  onValueChange={(value) => setFormData({ ...formData, categoryId: value })}
                  required
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.placeholders.selectCategory")} />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((category) => (
                      <SelectItem key={category.id} value={category.id.toString()}>
                        {category.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="description">{t('menu.description')}</Label>
                <Textarea
                  id="description"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  maxLength={1000}
                  rows={3}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="imageFile">{t('pages.products.productImage', 'Product Image')}</Label>
                <Input
                  id="imageFile"
                  type="file"
                  accept="image/*"
                  onChange={handleImageChange}
                />
                {imagePreview && (
                  <div className="mt-2">
                    <img
                      src={imagePreview}
                      alt={t('pages.products.preview', 'Preview')}
                      className="w-32 h-32 object-cover rounded-md border"
                    />
                  </div>
                )}
                <p className="text-sm text-muted-foreground">
                  {t('pages.products.orEnterImageUrl', 'Or enter image URL instead:')}
                </p>
                <Input
                  id="imageUrl"
                  type="url"
                  placeholder={t("common.placeholders.imageUrl")}
                  value={formData.imageUrl}
                  onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
                  maxLength={500}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="price">{t('menu.price')} *</Label>
                  <Input
                    id="price"
                    type="number"
                    step="0.01"
                    value={formData.price}
                    onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                    required
                    min="0"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="sortOrder">{t('menu.sortOrder')}</Label>
                  <Input
                    id="sortOrder"
                    type="number"
                    value={formData.sortOrder}
                    onChange={(e) => setFormData({ ...formData, sortOrder: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
              </div>

              <div className="flex gap-4">
                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="inStock"
                    checked={formData.inStock}
                    onChange={(e) => setFormData({ ...formData, inStock: e.target.checked })}
                    className="h-4 w-4"
                  />
                  <Label htmlFor="inStock">{t('menu.inStock')}</Label>
                </div>

                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="featured"
                    checked={formData.featured}
                    onChange={(e) => setFormData({ ...formData, featured: e.target.checked })}
                    className="h-4 w-4"
                  />
                  <Label htmlFor="featured">{t('menu.featured')}</Label>
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateModalOpen(false); resetForm(); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit">
                {t('common.create')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Edit Product Modal */}
      <Dialog open={editModalOpen} onOpenChange={setEditModalOpen}>
        <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('pages.products.editProduct', 'Edit Product')}</DialogTitle>
            <DialogDescription>
              {t('pages.products.updateDescription', 'Update the product details')}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdateProduct}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="edit-name">{t('menu.productName')} *</Label>
                <Input
                  id="edit-name"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  required
                  maxLength={200}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-category">{t('pages.products.category', 'Category')} *</Label>
                <Select
                  value={formData.categoryId}
                  onValueChange={(value) => setFormData({ ...formData, categoryId: value })}
                  required
                >
                  <SelectTrigger>
                    <SelectValue placeholder={t("common.placeholders.selectCategory")} />
                  </SelectTrigger>
                  <SelectContent>
                    {categories.map((category) => (
                      <SelectItem key={category.id} value={category.id.toString()}>
                        {category.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-description">{t('menu.description')}</Label>
                <Textarea
                  id="edit-description"
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  maxLength={1000}
                  rows={3}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-imageFile">{t('pages.products.productImage', 'Product Image')}</Label>
                <Input
                  id="edit-imageFile"
                  type="file"
                  accept="image/*"
                  onChange={handleImageChange}
                />
                {imagePreview && (
                  <div className="mt-2">
                    <img
                      src={imagePreview}
                      alt={t('pages.products.preview', 'Preview')}
                      className="w-32 h-32 object-cover rounded-md border"
                    />
                  </div>
                )}
                <p className="text-sm text-muted-foreground">
                  {t('pages.products.orEnterImageUrl', 'Or enter image URL instead:')}
                </p>
                <Input
                  id="edit-imageUrl"
                  type="url"
                  placeholder={t("common.placeholders.imageUrl")}
                  value={formData.imageUrl}
                  onChange={(e) => setFormData({ ...formData, imageUrl: e.target.value })}
                  maxLength={500}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-price">{t('menu.price')} *</Label>
                  <Input
                    id="edit-price"
                    type="number"
                    step="0.01"
                    value={formData.price}
                    onChange={(e) => setFormData({ ...formData, price: e.target.value })}
                    required
                    min="0"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="edit-sortOrder">{t('menu.sortOrder')}</Label>
                  <Input
                    id="edit-sortOrder"
                    type="number"
                    value={formData.sortOrder}
                    onChange={(e) => setFormData({ ...formData, sortOrder: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
              </div>

              <div className="flex gap-4">
                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="edit-inStock"
                    checked={formData.inStock}
                    onChange={(e) => setFormData({ ...formData, inStock: e.target.checked })}
                    className="h-4 w-4"
                  />
                  <Label htmlFor="edit-inStock">{t('menu.inStock')}</Label>
                </div>

                <div className="flex items-center space-x-2">
                  <input
                    type="checkbox"
                    id="edit-featured"
                    checked={formData.featured}
                    onChange={(e) => setFormData({ ...formData, featured: e.target.checked })}
                    className="h-4 w-4"
                  />
                  <Label htmlFor="edit-featured">{t('menu.featured')}</Label>
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditModalOpen(false); resetForm(); setSelectedProduct(null); }}>
                {t('common.cancel')}
              </Button>
              <Button type="submit">
                {t('pages.products.update', 'Update')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('pages.products.deleteProduct', 'Delete Product')}</DialogTitle>
            <DialogDescription>
              {t('pages.products.deleteConfirmation', 'Are you sure you want to delete "{{name}}"? This action cannot be undone.', { name: selectedProduct?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteDialogOpen(false); setSelectedProduct(null); }}>
              {t('pages.products.cancel', 'Cancel')}
            </Button>
            <Button variant="destructive" onClick={handleConfirmDelete}>
              {t('pages.products.delete', 'Delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Product Variants Modal */}
      <Dialog open={variantsModalOpen} onOpenChange={setVariantsModalOpen}>
        <DialogContent className="max-w-4xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>{t('pages.products.productVariantsTitle', 'Product Variants - {{name}}', { name: selectedProductForVariants?.name })}</DialogTitle>
            <DialogDescription>
              {t('pages.products.manageVariants', 'Manage variants for this product')}
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <div className="flex justify-between items-center mb-4">
              <h3 className="text-lg font-semibold">{t('pages.products.variantsCount', 'Variants ({{count}})', { count: variants.length })}</h3>
              <Button onClick={() => setCreateVariantModalOpen(true)} size="sm">
                <Plus className="h-4 w-4 mr-2" />
                {t('pages.products.addVariant', 'Add Variant')}
              </Button>
            </div>
            {variants.length === 0 ? (
              <p className="text-center text-muted-foreground py-8">
                {t('pages.products.noVariantsFound', 'No variants found. Click "Add Variant" to create one.')}
              </p>
            ) : (
              <div className="space-y-3">
                {variants.map((variant) => (
                  <Card key={variant.id}>
                    <CardContent className="p-4">
                      <div className="flex justify-between items-start">
                        <div className="flex-1">
                          <div className="flex items-center gap-2">
                            <h4 className="font-semibold">{variant.name}</h4>
                            <Badge variant={variant.inStock ? 'default' : 'secondary'}>
                              {variant.inStock ? t('pages.products.inStock', 'In Stock') : t('pages.products.outOfStock', 'Out of Stock')}
                            </Badge>
                          </div>
                          {variant.description && (
                            <p className="text-sm text-muted-foreground mt-1">{variant.description}</p>
                          )}
                          <div className="flex items-center gap-4 mt-2">
                            <div className="flex items-center text-green-600 font-semibold">
                              <DollarSign className="h-4 w-4" />
                              <span>{variant.price?.toFixed(2)}</span>
                            </div>
                            <span className="text-sm text-muted-foreground">
                              {t('pages.products.sortOrder', 'Sort Order')}: {variant.sortOrder}
                            </span>
                          </div>
                        </div>
                        <div className="flex gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            onClick={() => handleEditVariantClick(variant)}
                          >
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            className="text-red-600 hover:text-red-700 hover:bg-red-50"
                            onClick={() => handleDeleteVariantClick(variant)}
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                ))}
              </div>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setVariantsModalOpen(false)}>
              {t('pages.products.close', 'Close')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Create Variant Modal */}
      <Dialog open={createVariantModalOpen} onOpenChange={setCreateVariantModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('pages.products.createVariant', 'Create Product Variant')}</DialogTitle>
            <DialogDescription>
              {t('pages.products.addVariantFor', 'Add a new variant for {{name}}', { name: selectedProductForVariants?.name })}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleCreateVariant}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="variant-name">{t('pages.products.variantName', 'Variant Name')} *</Label>
                <Input
                  id="variant-name"
                  value={variantFormData.name}
                  onChange={(e) => setVariantFormData({ ...variantFormData, name: e.target.value })}
                  required
                  maxLength={200}
                  placeholder={t("common.placeholders.variantExample")}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="variant-description">{t('menu.description', 'Description')}</Label>
                <Textarea
                  id="variant-description"
                  value={variantFormData.description}
                  onChange={(e) => setVariantFormData({ ...variantFormData, description: e.target.value })}
                  maxLength={500}
                  rows={3}
                  placeholder={t("common.placeholders.optionalDescription")}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="variant-price">{t('menu.price', 'Price')} *</Label>
                  <Input
                    id="variant-price"
                    type="number"
                    step="0.01"
                    value={variantFormData.price}
                    onChange={(e) => setVariantFormData({ ...variantFormData, price: e.target.value })}
                    required
                    min="0"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="variant-sortOrder">{t('menu.sortOrder', 'Sort Order')}</Label>
                  <Input
                    id="variant-sortOrder"
                    type="number"
                    value={variantFormData.sortOrder}
                    onChange={(e) => setVariantFormData({ ...variantFormData, sortOrder: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
              </div>

              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="variant-inStock"
                  checked={variantFormData.inStock}
                  onChange={(e) => setVariantFormData({ ...variantFormData, inStock: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="variant-inStock">{t('menu.inStock', 'In Stock')}</Label>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateVariantModalOpen(false); resetVariantForm(); }}>
                {t('pages.products.cancel', 'Cancel')}
              </Button>
              <Button type="submit">
                {t('pages.products.createVariant', 'Create Variant')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Edit Variant Modal */}
      <Dialog open={editVariantModalOpen} onOpenChange={setEditVariantModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{t('pages.products.editVariant', 'Edit Product Variant')}</DialogTitle>
            <DialogDescription>
              {t('pages.products.updateVariantFor', 'Update variant details for {{name}}', { name: selectedProductForVariants?.name })}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleUpdateVariant}>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="edit-variant-name">{t('pages.products.variantName', 'Variant Name')} *</Label>
                <Input
                  id="edit-variant-name"
                  value={variantFormData.name}
                  onChange={(e) => setVariantFormData({ ...variantFormData, name: e.target.value })}
                  required
                  maxLength={200}
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-variant-description">{t('menu.description', 'Description')}</Label>
                <Textarea
                  id="edit-variant-description"
                  value={variantFormData.description}
                  onChange={(e) => setVariantFormData({ ...variantFormData, description: e.target.value })}
                  maxLength={500}
                  rows={3}
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-variant-price">{t('menu.price', 'Price')} *</Label>
                  <Input
                    id="edit-variant-price"
                    type="number"
                    step="0.01"
                    value={variantFormData.price}
                    onChange={(e) => setVariantFormData({ ...variantFormData, price: e.target.value })}
                    required
                    min="0"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="edit-variant-sortOrder">{t('menu.sortOrder', 'Sort Order')}</Label>
                  <Input
                    id="edit-variant-sortOrder"
                    type="number"
                    value={variantFormData.sortOrder}
                    onChange={(e) => setVariantFormData({ ...variantFormData, sortOrder: parseInt(e.target.value) || 0 })}
                    min={0}
                  />
                </div>
              </div>

              <div className="flex items-center space-x-2">
                <input
                  type="checkbox"
                  id="edit-variant-inStock"
                  checked={variantFormData.inStock}
                  onChange={(e) => setVariantFormData({ ...variantFormData, inStock: e.target.checked })}
                  className="h-4 w-4"
                />
                <Label htmlFor="edit-variant-inStock">{t('menu.inStock', 'In Stock')}</Label>
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setEditVariantModalOpen(false); resetVariantForm(); setSelectedVariant(null); }}>
                {t('pages.products.cancel', 'Cancel')}
              </Button>
              <Button type="submit">
                {t('pages.products.updateVariant', 'Update Variant')}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Variant Confirmation Dialog */}
      <Dialog open={deleteVariantDialogOpen} onOpenChange={setDeleteVariantDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('pages.products.deleteVariant', 'Delete Variant')}</DialogTitle>
            <DialogDescription>
              {t('pages.products.deleteVariantConfirmation', 'Are you sure you want to delete the variant "{{name}}"? This action cannot be undone.', { name: selectedVariant?.name })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => { setDeleteVariantDialogOpen(false); setSelectedVariant(null); }}>
              {t('pages.products.cancel', 'Cancel')}
            </Button>
            <Button variant="destructive" onClick={handleConfirmDeleteVariant}>
              {t('pages.products.delete', 'Delete')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
