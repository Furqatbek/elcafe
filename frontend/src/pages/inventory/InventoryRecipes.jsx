import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { inventoryAPI, menuAPI } from '../../services/api';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Label } from '../../components/ui/label';
import { Input } from '../../components/ui/input';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../../components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../../components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../components/ui/table';
import {
  Package,
  Plus,
  Trash2,
  UtensilsCrossed,
  Link2,
} from 'lucide-react';

export default function InventoryRecipes() {
  const { t } = useTranslation();
  const { selectedRestaurant, ingredients } = useInventory();

  // Local state
  const [products, setProducts] = useState([]);
  const [recipes, setRecipes] = useState([]);
  const [recipeModalOpen, setRecipeModalOpen] = useState(false);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [recipeFormData, setRecipeFormData] = useState({
    productId: '',
    ingredientId: '',
    quantityRequired: '',
    unit: 'kg',
  });

  useEffect(() => {
    if (selectedRestaurant) {
      loadProducts();
    }
  }, [selectedRestaurant]);

  const loadProducts = async () => {
    try {
      const response = await menuAPI.getProductsByRestaurant(selectedRestaurant);
      setProducts(response.data.data || []);
    } catch (error) {
      console.error('Failed to load products:', error);
    }
  };

  const loadRecipesForProduct = async (productId) => {
    try {
      const response = await inventoryAPI.getRecipesByProduct(productId);
      setRecipes(response.data.data || []);
    } catch (error) {
      console.error('Failed to load recipes:', error);
    }
  };

  const handleSelectProduct = (product) => {
    setSelectedProduct(product);
    loadRecipesForProduct(product.id);
  };

  const handleAddRecipe = () => {
    setRecipeFormData({
      productId: selectedProduct?.id?.toString() || '',
      ingredientId: '',
      quantityRequired: '',
      unit: 'kg',
    });
    setRecipeModalOpen(true);
  };

  const handleSaveRecipe = async () => {
    try {
      const data = {
        productId: parseInt(recipeFormData.productId),
        ingredientId: parseInt(recipeFormData.ingredientId),
        quantityRequired: parseFloat(recipeFormData.quantityRequired),
        unit: recipeFormData.unit,
      };

      await inventoryAPI.createRecipe(data);
      setRecipeModalOpen(false);
      if (selectedProduct) {
        loadRecipesForProduct(selectedProduct.id);
      }
    } catch (error) {
      console.error('Failed to save recipe:', error);
      alert(t('inventory.recipes.errors.saveFailed', 'Failed to save recipe'));
    }
  };

  const handleDeleteRecipe = async (id) => {
    if (!confirm(t('inventory.recipes.confirmDelete', 'Are you sure you want to delete this recipe?'))) return;

    try {
      await inventoryAPI.deleteRecipe(id);
      if (selectedProduct) {
        loadRecipesForProduct(selectedProduct.id);
      }
    } catch (error) {
      console.error('Failed to delete recipe:', error);
      alert(t('inventory.recipes.errors.deleteFailed', 'Failed to delete recipe'));
    }
  };

  return (
    <InventoryLayout>
      <div className="grid gap-6 md:grid-cols-2">
        {/* Products List */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <UtensilsCrossed className="h-5 w-5" />
              {t('inventory.recipes.selectProduct', 'Select Product')}
            </CardTitle>
            <CardDescription>
              {t('inventory.recipes.selectProductDesc', 'Select a product to view and manage its recipe')}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="space-y-2 max-h-[500px] overflow-y-auto">
              {products.length === 0 ? (
                <p className="text-center text-muted-foreground py-8">
                  {t('inventory.recipes.noProducts', 'No products available')}
                </p>
              ) : (
                products.map((product) => (
                  <div
                    key={product.id}
                    className={`p-3 border rounded-lg cursor-pointer transition-colors ${
                      selectedProduct?.id === product.id
                        ? 'bg-primary/10 border-primary'
                        : 'hover:bg-muted'
                    }`}
                    onClick={() => handleSelectProduct(product)}
                  >
                    <div className="flex items-center gap-3">
                      {product.imageUrl ? (
                        <img
                          src={product.imageUrl}
                          alt={product.name}
                          className="w-12 h-12 rounded object-cover"
                        />
                      ) : (
                        <div className="w-12 h-12 rounded bg-muted flex items-center justify-center">
                          <UtensilsCrossed className="h-6 w-6 text-muted-foreground" />
                        </div>
                      )}
                      <div>
                        <div className="font-medium">{product.name}</div>
                        <div className="text-sm text-muted-foreground">
                          {product.categoryName || 'Uncategorized'}
                        </div>
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </CardContent>
        </Card>

        {/* Recipe Details */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Link2 className="h-5 w-5" />
                  {t('inventory.recipes.ingredientLinks', 'Ingredient Links')}
                </CardTitle>
                <CardDescription>
                  {selectedProduct
                    ? `${t('inventory.recipes.for', 'Recipe for')} ${selectedProduct.name}`
                    : t('inventory.recipes.selectProductFirst', 'Select a product to view its recipe')}
                </CardDescription>
              </div>
              {selectedProduct && (
                <Button onClick={handleAddRecipe} size="sm">
                  <Plus className="h-4 w-4 mr-2" />
                  {t('inventory.recipes.addIngredient', 'Add Ingredient')}
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent>
            {!selectedProduct ? (
              <div className="text-center text-muted-foreground py-12">
                <UtensilsCrossed className="h-12 w-12 mx-auto mb-4 opacity-50" />
                <p>{t('inventory.recipes.selectProductFirst', 'Select a product to view its recipe')}</p>
              </div>
            ) : recipes.length === 0 ? (
              <div className="text-center text-muted-foreground py-12">
                <Package className="h-12 w-12 mx-auto mb-4 opacity-50" />
                <p>{t('inventory.recipes.noIngredients', 'No ingredients linked to this product')}</p>
                <Button onClick={handleAddRecipe} variant="outline" className="mt-4">
                  <Plus className="h-4 w-4 mr-2" />
                  {t('inventory.recipes.addFirst', 'Add first ingredient')}
                </Button>
              </div>
            ) : (
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('inventory.recipes.ingredient', 'Ingredient')}</TableHead>
                    <TableHead>{t('inventory.recipes.quantity', 'Quantity')}</TableHead>
                    <TableHead>{t('inventory.recipes.unit', 'Unit')}</TableHead>
                    <TableHead className="text-right">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {recipes.map((recipe) => (
                    <TableRow key={recipe.id}>
                      <TableCell className="font-medium">{recipe.ingredientName}</TableCell>
                      <TableCell>{recipe.quantityRequired}</TableCell>
                      <TableCell>{recipe.unit}</TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="icon"
                          onClick={() => handleDeleteRecipe(recipe.id)}
                        >
                          <Trash2 className="h-4 w-4 text-red-600" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Add Recipe Modal */}
      <Dialog open={recipeModalOpen} onOpenChange={setRecipeModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('inventory.recipes.addIngredient', 'Add Ingredient')}</DialogTitle>
            <DialogDescription>
              {selectedProduct && `${t('inventory.recipes.for', 'For')} ${selectedProduct.name}`}
            </DialogDescription>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="space-y-2">
              <Label>{t('inventory.recipes.ingredient', 'Ingredient')}</Label>
              <Select
                value={recipeFormData.ingredientId}
                onValueChange={(value) => setRecipeFormData({ ...recipeFormData, ingredientId: value })}
              >
                <SelectTrigger>
                  <SelectValue placeholder={t('inventory.recipes.selectIngredient', 'Select an ingredient')} />
                </SelectTrigger>
                <SelectContent>
                  {ingredients.map((ing) => (
                    <SelectItem key={ing.id} value={ing.id.toString()}>
                      {ing.name} ({ing.unit})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.recipes.quantity', 'Quantity')}</Label>
                <Input
                  type="number"
                  step="0.001"
                  value={recipeFormData.quantityRequired}
                  onChange={(e) => setRecipeFormData({ ...recipeFormData, quantityRequired: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.recipes.unit', 'Unit')}</Label>
                <Select
                  value={recipeFormData.unit}
                  onValueChange={(value) => setRecipeFormData({ ...recipeFormData, unit: value })}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="kg">{t('inventory.units.kg')}</SelectItem>
                    <SelectItem value="g">{t('inventory.units.g')}</SelectItem>
                    <SelectItem value="L">{t('inventory.units.L')}</SelectItem>
                    <SelectItem value="ml">{t('inventory.units.ml')}</SelectItem>
                    <SelectItem value="pieces">{t('inventory.units.pieces')}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRecipeModalOpen(false)}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSaveRecipe} disabled={!recipeFormData.ingredientId || !recipeFormData.quantityRequired}>
              {t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
