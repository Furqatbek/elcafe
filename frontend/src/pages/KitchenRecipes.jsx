import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import { Badge } from '../components/ui/badge';
import {
  Soup,
  Plus,
  Search,
  Clock,
  Users,
  ChefHat,
  BookOpen,
  Edit,
  Trash2,
} from 'lucide-react';

export default function KitchenRecipes() {
  const { t } = useTranslation();
  const [recipes, setRecipes] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');

  // Placeholder recipes for demonstration
  const placeholderRecipes = [
    {
      id: 1,
      name: 'Classic Margherita Pizza',
      category: 'Main Course',
      prepTime: 20,
      cookTime: 15,
      servings: 4,
      difficulty: 'Easy',
      ingredients: [
        { name: 'Pizza Dough', quantity: '1', unit: 'piece' },
        { name: 'Tomato Sauce', quantity: '200', unit: 'ml' },
        { name: 'Mozzarella Cheese', quantity: '250', unit: 'g' },
        { name: 'Fresh Basil', quantity: '10', unit: 'leaves' },
      ],
    },
    {
      id: 2,
      name: 'Caesar Salad',
      category: 'Appetizer',
      prepTime: 15,
      cookTime: 0,
      servings: 2,
      difficulty: 'Easy',
      ingredients: [
        { name: 'Romaine Lettuce', quantity: '1', unit: 'head' },
        { name: 'Parmesan Cheese', quantity: '50', unit: 'g' },
        { name: 'Caesar Dressing', quantity: '100', unit: 'ml' },
        { name: 'Croutons', quantity: '50', unit: 'g' },
      ],
    },
  ];

  const getDifficultyColor = (difficulty) => {
    switch (difficulty) {
      case 'Easy': return 'bg-green-100 text-green-800';
      case 'Medium': return 'bg-yellow-100 text-yellow-800';
      case 'Hard': return 'bg-red-100 text-red-800';
      default: return 'bg-gray-100 text-gray-800';
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold flex items-center gap-2">
            <Soup className="h-8 w-8" />
            {t('kitchen.recipes.title') || 'Kitchen Recipes'}
          </h1>
          <p className="text-muted-foreground mt-1">
            {t('kitchen.recipes.subtitle') || 'Manage your recipe collection and cooking instructions'}
          </p>
        </div>
        <Button className="flex items-center gap-2">
          <Plus className="h-4 w-4" />
          {t('kitchen.recipes.addNew') || 'Add Recipe'}
        </Button>
      </div>

      {/* Search Bar */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-center gap-4">
            <div className="flex-1">
              <Label htmlFor="search" className="mb-2 flex items-center gap-2">
                <Search className="h-4 w-4" />
                {t('common.search') || 'Search'}
              </Label>
              <Input
                id="search"
                type="text"
                placeholder={t('kitchen.recipes.searchPlaceholder') || 'Search recipes by name or category...'}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Coming Soon Notice */}
      <Card className="bg-gradient-to-r from-purple-50 to-pink-50 border-purple-200">
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-purple-900">
            <BookOpen className="h-5 w-5" />
            {t('common.comingSoon') || 'Coming Soon'}
          </CardTitle>
          <CardDescription>
            {t('kitchen.recipes.comingSoonDesc') || 'The Recipe Management feature is under development. You will soon be able to create, edit, and manage your kitchen recipes with detailed instructions and ingredient lists.'}
          </CardDescription>
        </CardHeader>
      </Card>

      {/* Placeholder Recipe Cards */}
      <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        {placeholderRecipes.map((recipe) => (
          <Card key={recipe.id} className="hover:shadow-lg transition-shadow">
            <CardHeader>
              <div className="flex justify-between items-start">
                <div className="flex-1">
                  <CardTitle className="text-lg">{recipe.name}</CardTitle>
                  <CardDescription className="mt-1">
                    <Badge variant="secondary" className="text-xs">
                      {recipe.category}
                    </Badge>
                  </CardDescription>
                </div>
                <Badge className={getDifficultyColor(recipe.difficulty)}>
                  {recipe.difficulty}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Time & Servings Info */}
              <div className="grid grid-cols-3 gap-2 text-sm">
                <div className="flex items-center gap-1">
                  <Clock className="h-4 w-4 text-gray-500" />
                  <span className="text-muted-foreground">{recipe.prepTime}m prep</span>
                </div>
                <div className="flex items-center gap-1">
                  <ChefHat className="h-4 w-4 text-gray-500" />
                  <span className="text-muted-foreground">{recipe.cookTime}m cook</span>
                </div>
                <div className="flex items-center gap-1">
                  <Users className="h-4 w-4 text-gray-500" />
                  <span className="text-muted-foreground">{recipe.servings} servings</span>
                </div>
              </div>

              {/* Ingredients */}
              <div className="space-y-2">
                <div className="text-sm font-semibold text-gray-700">
                  {t('kitchen.recipes.ingredients') || 'Ingredients'}:
                </div>
                <ul className="text-sm text-gray-600 space-y-1">
                  {recipe.ingredients.slice(0, 3).map((ingredient, idx) => (
                    <li key={idx}>
                      • {ingredient.quantity} {ingredient.unit} {ingredient.name}
                    </li>
                  ))}
                  {recipe.ingredients.length > 3 && (
                    <li className="text-blue-600">
                      + {recipe.ingredients.length - 3} more...
                    </li>
                  )}
                </ul>
              </div>

              {/* Actions */}
              <div className="flex gap-2 pt-2">
                <Button variant="outline" size="sm" className="flex-1">
                  <Edit className="h-4 w-4 mr-2" />
                  {t('common.edit') || 'Edit'}
                </Button>
                <Button variant="outline" size="sm">
                  <Trash2 className="h-4 w-4" />
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {/* Empty State */}
      {placeholderRecipes.length === 0 && (
        <Card>
          <CardContent className="pt-6">
            <div className="text-center py-12">
              <Soup className="h-12 w-12 text-muted-foreground mx-auto mb-4" />
              <p className="text-lg font-medium">
                {t('kitchen.recipes.noRecipes') || 'No Recipes Found'}
              </p>
              <p className="text-muted-foreground mt-2">
                {t('kitchen.recipes.noRecipesDesc') || 'Get started by creating your first recipe'}
              </p>
              <Button className="mt-4">
                <Plus className="h-4 w-4 mr-2" />
                {t('kitchen.recipes.addFirst') || 'Add Your First Recipe'}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
