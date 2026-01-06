import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { recipesAPI, inventoryAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import {
  Soup,
  Plus,
  Edit,
  Trash2,
  Package,
  ChefHat,
  Search,
  RefreshCw,
  AlertCircle,
} from 'lucide-react';

export default function Recipes() {
  const { t } = useTranslation();
  const [products, setProducts] = useState([]);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [productIngredients, setProductIngredients] = useState([]);
  const [availableIngredients, setAvailableIngredients] = useState([]);
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState(1);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecipe, setEditingRecipe] = useState(null);
  const [formData, setFormData] = useState({
    ingredientId: '',
    quantityRequired: '',
    unit: '',
    notes: '',
    optional: false,
  });
  const [ingredientSearch, setIngredientSearch] = useState('');
  const [ingredientUnitFilter, setIngredientUnitFilter] = useState('all');
  const [productCategoryFilter, setProductCategoryFilter] = useState('all');

  useEffect(() => {
    loadRestaurants();
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadProducts();
      loadIngredients();
    }
  }, [selectedRestaurant]);

  useEffect(() => {
    if (selectedProduct) {
      loadProductRecipe();
    }
  }, [selectedProduct]);

  const loadRestaurants = async () => {
    try {
      const response = await fetch('/api/v1/restaurants?page=0&size=100');
      const result = await response.json();
      const restaurantList = result.data?.content || [];
      setRestaurants(restaurantList);
      if (restaurantList.length > 0) {
        setSelectedRestaurant(restaurantList[0].id);
      }
    } catch (error) {
      console.error('Failed to load restaurants:', error);
    }
  };

  const loadProducts = async () => {
    setLoading(true);
    try {
      const response = await fetch(`/api/v1/products/restaurant/${selectedRestaurant}`);
      const result = await response.json();
      setProducts(result.data || []);
      if (result.data?.length > 0) {
        setSelectedProduct(result.data[0].id);
      }
    } catch (error) {
      console.error('Failed to load products:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadIngredients = async () => {
    try {
      const response = await inventoryAPI.getIngredients(selectedRestaurant);
      setAvailableIngredients(response.data.data || []);
    } catch (error) {
      console.error('Failed to load ingredients:', error);
    }
  };

  const loadProductRecipe = async () => {
    try {
      const response = await recipesAPI.getProductRecipe(selectedProduct);
      setProductIngredients(response.data.data || []);
    } catch (error) {
      console.error('Failed to load product recipe:', error);
      setProductIngredients([]);
    }
  };

  const handleAddIngredient = () => {
    setEditingRecipe(null);
    setFormData({
      ingredientId: '',
      quantityRequired: '',
      unit: '',
      notes: '',
      optional: false,
    });
    setIngredientSearch('');
    setIngredientUnitFilter('all');
    setModalOpen(true);
  };

  // Get unique units from available ingredients for filter
  const availableUnits = [...new Set(availableIngredients.map((ing) => ing.unit).filter(Boolean))].sort();

  // Filter ingredients based on search and unit filter
  const filteredIngredients = availableIngredients.filter((ingredient) => {
    const matchesSearch = ingredient.name.toLowerCase().includes(ingredientSearch.toLowerCase());
    const matchesUnit = ingredientUnitFilter === 'all' || ingredient.unit === ingredientUnitFilter;
    return matchesSearch && matchesUnit;
  });

  const handleEditRecipe = (recipe) => {
    setEditingRecipe(recipe);
    setFormData({
      ingredientId: recipe.ingredient.id.toString(),
      quantityRequired: recipe.quantityRequired,
      unit: recipe.unit || '',
      notes: recipe.notes || '',
      optional: recipe.optional,
    });
    // Clear filters so the current ingredient is visible in dropdown
    setIngredientSearch('');
    setIngredientUnitFilter('all');
    setModalOpen(true);
  };

  const handleSaveRecipe = async () => {
    try {
      const data = {
        productId: selectedProduct,
        ingredientId: parseInt(formData.ingredientId),
        quantityRequired: parseFloat(formData.quantityRequired),
        unit: formData.unit,
        notes: formData.notes,
        optional: formData.optional,
      };

      if (editingRecipe) {
        await recipesAPI.updateRecipe(editingRecipe.id, data);
      } else {
        await recipesAPI.createRecipe(data);
      }

      setModalOpen(false);
      loadProductRecipe();
    } catch (error) {
      console.error('Failed to save recipe:', error);
      alert(t('recipes.errors.saveFailed'));
    }
  };

  const handleDeleteRecipe = async (id) => {
    if (!confirm(t('recipes.confirmDelete'))) return;

    try {
      await recipesAPI.deleteRecipe(id);
      loadProductRecipe();
    } catch (error) {
      console.error('Failed to delete recipe:', error);
      alert(t('recipes.errors.deleteFailed'));
    }
  };

  // Get unique categories from products for filter
  const productCategories = [...new Set(products.map((p) => p.category?.name || p.categoryName).filter(Boolean))].sort();

  // Filter products based on search and category filter
  const filteredProducts = products.filter((product) => {
    const matchesSearch = product.name.toLowerCase().includes(searchTerm.toLowerCase());
    const categoryName = product.category?.name || product.categoryName;
    const matchesCategory = productCategoryFilter === 'all' || categoryName === productCategoryFilter;
    return matchesSearch && matchesCategory;
  });

  const currentProduct = products.find((p) => p.id === selectedProduct);
  const totalIngredients = productIngredients.length;
  const optionalIngredients = productIngredients.filter((pi) => pi.optional).length;
  const requiredIngredients = totalIngredients - optionalIngredients;

  if (loading && products.length === 0) {
    return <div className="flex justify-center items-center h-64">{t('common.loading')}</div>;
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold">{t('recipes.title')}</h1>
          <p className="text-muted-foreground mt-1">{t('recipes.subtitle')}</p>
        </div>
        <div className="flex gap-3 items-center">
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
          <Button onClick={loadProducts} variant="outline" size="icon">
            <RefreshCw className="h-4 w-4" />
          </Button>
        </div>
      </div>

      <div className="grid gap-6 md:grid-cols-3">
        {/* Products List */}
        <Card className="md:col-span-1">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Package className="h-5 w-5" />
              {t('recipes.products')}
            </CardTitle>
            <CardDescription>{t('recipes.selectProduct')}</CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-4">
              {/* Filter Zone - Highlighted */}
              <div className="bg-gray-50 border border-gray-200 rounded-lg p-3 space-y-3">
                <p className="text-xs font-medium text-gray-600 uppercase tracking-wide">
                  {t('recipes.filterProducts', 'Filter Products')}
                </p>
                <div className="relative">
                  <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder={t('recipes.searchProducts')}
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="pl-10 bg-white"
                  />
                </div>
                <Select
                  value={productCategoryFilter}
                  onValueChange={setProductCategoryFilter}
                >
                  <SelectTrigger className="w-full bg-white">
                    <SelectValue placeholder={t('recipes.filterByCategory')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">{t('common.allCategories')}</SelectItem>
                    {productCategories.map((category) => (
                      <SelectItem key={category} value={category}>
                        {category}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {filteredProducts.length > 0 && (
                  <p className="text-xs text-muted-foreground">
                    {t('recipes.productsFound', { count: filteredProducts.length })}
                  </p>
                )}
              </div>

              {/* Products List */}
              <div className="space-y-2 max-h-[400px] overflow-y-auto">
                {filteredProducts.map((product) => (
                  <button
                    key={product.id}
                    onClick={() => setSelectedProduct(product.id)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors ${
                      selectedProduct === product.id
                        ? 'bg-blue-50 border-blue-300'
                        : 'hover:bg-gray-50'
                    }`}
                  >
                    <div className="font-medium">{product.name}</div>
                    <div className="text-sm text-muted-foreground">
                      {product.category?.name || 'Uncategorized'}
                    </div>
                    {product.price && (
                      <div className="text-sm font-semibold mt-1">${product.price}</div>
                    )}
                  </button>
                ))}
                {filteredProducts.length === 0 && (
                  <div className="text-center py-8 text-muted-foreground">
                    {t('recipes.noProducts')}
                  </div>
                )}
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Recipe Details */}
        <Card className="md:col-span-2">
          <CardHeader>
            <div className="flex justify-between items-start">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <ChefHat className="h-5 w-5" />
                  {currentProduct?.name || t('recipes.noProductSelected')}
                </CardTitle>
                <CardDescription>{t('recipes.recipeDescription')}</CardDescription>
              </div>
              {selectedProduct && (
                <Button onClick={handleAddIngredient}>
                  <Plus className="h-4 w-4 mr-2" />
                  {t('recipes.addIngredient')}
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {/* Stats */}
            {selectedProduct && (
              <div className="grid gap-4 md:grid-cols-3 mb-6">
                <div className="bg-blue-50 p-4 rounded-lg">
                  <div className="text-sm text-blue-600 font-medium">{t('recipes.stats.total')}</div>
                  <div className="text-2xl font-bold text-blue-700">{totalIngredients}</div>
                </div>
                <div className="bg-green-50 p-4 rounded-lg">
                  <div className="text-sm text-green-600 font-medium">{t('recipes.stats.required')}</div>
                  <div className="text-2xl font-bold text-green-700">{requiredIngredients}</div>
                </div>
                <div className="bg-gray-50 p-4 rounded-lg">
                  <div className="text-sm text-gray-600 font-medium">{t('recipes.stats.optional')}</div>
                  <div className="text-2xl font-bold text-gray-700">{optionalIngredients}</div>
                </div>
              </div>
            )}

            {/* Ingredients Table */}
            {selectedProduct ? (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('recipes.fields.ingredient')}</TableHead>
                    <TableHead>{t('recipes.fields.quantity')}</TableHead>
                    <TableHead>{t('recipes.fields.unit')}</TableHead>
                    <TableHead>{t('recipes.fields.type')}</TableHead>
                    <TableHead>{t('recipes.fields.notes')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {productIngredients.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={6} className="text-center py-8">
                        <AlertCircle className="h-8 w-8 text-muted-foreground mx-auto mb-2" />
                        <p className="text-muted-foreground">{t('recipes.noIngredients')}</p>
                        <p className="text-sm text-muted-foreground mt-1">
                          {t('recipes.noIngredientsDesc')}
                        </p>
                      </TableCell>
                    </TableRow>
                  ) : (
                    productIngredients.map((recipe) => (
                      <TableRow key={recipe.id}>
                        <TableCell className="font-medium">
                          <div>
                            <div>{recipe.ingredient.name}</div>
                            <div className="text-xs text-muted-foreground">
                              {recipe.ingredient.sku || ''}
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>{recipe.quantityRequired}</TableCell>
                        <TableCell>{recipe.unit || recipe.ingredient.unit}</TableCell>
                        <TableCell>
                          <Badge variant={recipe.optional ? 'secondary' : 'default'}>
                            {recipe.optional ? t('recipes.optional') : t('recipes.required')}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          {recipe.notes && (
                            <div className="text-sm text-muted-foreground max-w-[200px] truncate">
                              {recipe.notes}
                            </div>
                          )}
                        </TableCell>
                        <TableCell className="text-right">
                          <div className="flex justify-end gap-2">
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleEditRecipe(recipe)}
                            >
                              <Edit className="h-4 w-4" />
                            </Button>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => handleDeleteRecipe(recipe.id)}
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
            ) : (
              <div className="text-center py-12">
                <Package className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
                <p className="text-lg font-medium">{t('recipes.selectProductPrompt')}</p>
                <p className="text-muted-foreground mt-2">{t('recipes.selectProductDesc')}</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Add/Edit Recipe Modal */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editingRecipe ? t('recipes.editIngredient') : t('recipes.addIngredient')}
            </DialogTitle>
            <DialogDescription>
              {editingRecipe ? t('recipes.editDescription') : t('recipes.addDescription')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            {/* Show current ingredient when editing */}
            {editingRecipe && (
              <div className="bg-blue-50 border border-blue-200 rounded-lg p-3">
                <p className="text-sm text-blue-600 font-medium">{t('recipes.currentIngredient', 'Current Ingredient')}</p>
                <p className="text-lg font-bold text-blue-800">{editingRecipe.ingredient.name}</p>
                <p className="text-sm text-blue-600">
                  {editingRecipe.ingredient.unit} • SKU: {editingRecipe.ingredient.sku || 'N/A'}
                </p>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="ingredient">
                {editingRecipe
                  ? t('recipes.changeIngredient', 'Change Ingredient (optional)')
                  : t('recipes.fields.ingredient') + ' *'}
              </Label>

              {/* Search and Filter Controls */}
              <div className="flex gap-2 mb-2">
                <div className="relative flex-1">
                  <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                  <Input
                    placeholder={t('recipes.searchIngredients')}
                    value={ingredientSearch}
                    onChange={(e) => setIngredientSearch(e.target.value)}
                    className="pl-10"
                  />
                </div>
                <Select
                  value={ingredientUnitFilter}
                  onValueChange={setIngredientUnitFilter}
                >
                  <SelectTrigger className="w-[120px]">
                    <SelectValue placeholder={t('recipes.filterByUnit')} />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">{t('common.all')}</SelectItem>
                    {availableUnits.map((unit) => (
                      <SelectItem key={unit} value={unit}>
                        {unit}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {/* Ingredient Select */}
              <Select
                value={formData.ingredientId}
                onValueChange={(value) => {
                  const ingredient = availableIngredients.find((ing) => ing.id === parseInt(value));
                  setFormData({
                    ...formData,
                    ingredientId: value,
                    unit: ingredient?.unit || '',
                  });
                }}
              >
                <SelectTrigger id="ingredient">
                  <SelectValue placeholder={t('recipes.placeholders.selectIngredient')} />
                </SelectTrigger>
                <SelectContent className="max-h-[200px]">
                  {filteredIngredients.length === 0 ? (
                    <div className="py-2 px-3 text-sm text-muted-foreground text-center">
                      {t('recipes.noIngredientsFound')}
                    </div>
                  ) : (
                    filteredIngredients.map((ingredient) => (
                      <SelectItem key={ingredient.id} value={ingredient.id.toString()}>
                        {ingredient.name} ({ingredient.unit})
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>

              {filteredIngredients.length > 0 && (
                <p className="text-xs text-muted-foreground">
                  {t('recipes.ingredientsFound', { count: filteredIngredients.length })}
                </p>
              )}
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="quantity">{t('recipes.fields.quantity')} *</Label>
                <Input
                  id="quantity"
                  type="number"
                  step="0.001"
                  value={formData.quantityRequired}
                  onChange={(e) => setFormData({ ...formData, quantityRequired: e.target.value })}
                  placeholder="0.000"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="unit">{t('recipes.fields.unit')}</Label>
                <Input
                  id="unit"
                  value={formData.unit}
                  onChange={(e) => setFormData({ ...formData, unit: e.target.value })}
                  placeholder={t('recipes.placeholders.unit')}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="notes">{t('recipes.fields.notes')}</Label>
              <Input
                id="notes"
                value={formData.notes}
                onChange={(e) => setFormData({ ...formData, notes: e.target.value })}
                placeholder={t('recipes.placeholders.notes')}
              />
            </div>

            <div className="flex items-center space-x-2">
              <input
                type="checkbox"
                id="optional"
                checked={formData.optional}
                onChange={(e) => setFormData({ ...formData, optional: e.target.checked })}
                className="h-4 w-4"
              />
              <Label htmlFor="optional" className="font-normal cursor-pointer">
                {t('recipes.fields.optional')}
              </Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveRecipe} disabled={!formData.ingredientId || !formData.quantityRequired}>
              {editingRecipe ? t('common.save') : t('common.add')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
