import { useState, useEffect } from 'react';
import { useParams, useNavigate, useSearchParams } from 'react-router-dom';
import { selfServiceAPI } from '../../services/api';
import { useCustomer } from './CustomerContext';
import {
  ShoppingCart,
  Plus,
  Minus,
  ChevronLeft,
  Search,
  Clock,
  UtensilsCrossed,
  X,
} from 'lucide-react';

export default function MenuPage() {
  const { restaurantId, tableCode } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { session, cart, startSession, addToCart, loading: sessionLoading } = useCustomer();

  const [restaurant, setRestaurant] = useState(null);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [selectedCategory, setSelectedCategory] = useState(null);
  const [selectedProduct, setSelectedProduct] = useState(null);
  const [quantity, setQuantity] = useState(1);
  const [specialInstructions, setSpecialInstructions] = useState('');
  const [selectedVariant, setSelectedVariant] = useState(null);
  const [selectedModifiers, setSelectedModifiers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showProductModal, setShowProductModal] = useState(false);
  const [addingToCart, setAddingToCart] = useState(false);

  // Initialize session on mount
  useEffect(() => {
    const initSession = async () => {
      if (!session && tableCode) {
        try {
          await startSession(tableCode);
        } catch (err) {
          setError('Invalid QR code or session expired');
        }
      }
      loadRestaurantData();
    };
    initSession();
  }, [tableCode]);

  const loadRestaurantData = async () => {
    setLoading(true);
    try {
      const [restaurantRes, categoriesRes] = await Promise.all([
        selfServiceAPI.getRestaurantInfo(restaurantId),
        selfServiceAPI.getCategories(restaurantId),
      ]);
      setRestaurant(restaurantRes.data);
      setCategories(categoriesRes.data);

      // Load all products
      const productsRes = await selfServiceAPI.getProducts(restaurantId);
      setProducts(productsRes.data);
    } catch (err) {
      setError('Failed to load menu');
    } finally {
      setLoading(false);
    }
  };

  const loadProductsByCategory = async (categoryId) => {
    setLoading(true);
    try {
      const response = await selfServiceAPI.getProducts(restaurantId, categoryId);
      setProducts(response.data);
      setSelectedCategory(categoryId);
    } catch (err) {
      console.error('Failed to load products:', err);
    } finally {
      setLoading(false);
    }
  };

  const handleCategoryClick = (categoryId) => {
    if (categoryId === selectedCategory) {
      setSelectedCategory(null);
      loadRestaurantData();
    } else {
      loadProductsByCategory(categoryId);
    }
  };

  const handleProductClick = async (product) => {
    try {
      const response = await selfServiceAPI.getProductDetails(product.id);
      setSelectedProduct(response.data);
      setQuantity(1);
      setSpecialInstructions('');
      setSelectedVariant(response.data.variants?.[0] || null);
      setSelectedModifiers([]);
      setShowProductModal(true);
    } catch (err) {
      console.error('Failed to load product details:', err);
    }
  };

  const handleAddToCart = async () => {
    if (!session) {
      setError('Please scan the QR code to start ordering');
      return;
    }

    setAddingToCart(true);
    try {
      await addToCart({
        productId: selectedProduct.id,
        variantId: selectedVariant?.id || null,
        quantity,
        specialInstructions: specialInstructions || null,
        modifierIds: selectedModifiers.map(m => m.id),
      });
      setShowProductModal(false);
    } catch (err) {
      setError('Failed to add item to cart');
    } finally {
      setAddingToCart(false);
    }
  };

  const toggleModifier = (modifier) => {
    const isSelected = selectedModifiers.some(m => m.id === modifier.id);
    if (isSelected) {
      setSelectedModifiers(selectedModifiers.filter(m => m.id !== modifier.id));
    } else {
      setSelectedModifiers([...selectedModifiers, modifier]);
    }
  };

  const calculateItemPrice = () => {
    let price = selectedVariant?.price || selectedProduct?.price || 0;
    selectedModifiers.forEach(m => {
      price += m.price || 0;
    });
    return price * quantity;
  };

  const filteredProducts = searchQuery
    ? products.filter(p =>
        p.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
        p.nameRu?.toLowerCase().includes(searchQuery.toLowerCase()) ||
        p.nameUz?.toLowerCase().includes(searchQuery.toLowerCase())
      )
    : products;

  const formatPrice = (price) => {
    return new Intl.NumberFormat('uz-UZ').format(price) + ' UZS';
  };

  if (loading && !restaurant) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  if (error && !restaurant) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
        <div className="bg-white rounded-lg shadow-lg p-6 max-w-sm w-full text-center">
          <div className="text-red-500 text-6xl mb-4">!</div>
          <h2 className="text-xl font-semibold text-gray-800 mb-2">Error</h2>
          <p className="text-gray-600">{error}</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50 pb-24">
      {/* Header */}
      <div className="bg-white shadow-sm sticky top-0 z-40">
        <div className="max-w-lg mx-auto px-4 py-3">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-lg font-bold text-gray-900">{restaurant?.name}</h1>
              {session?.tableNumber && (
                <p className="text-sm text-gray-500">Table {session.tableNumber}</p>
              )}
            </div>
            {restaurant?.logoUrl && (
              <img
                src={restaurant.logoUrl}
                alt={restaurant.name}
                className="w-10 h-10 rounded-full object-cover"
              />
            )}
          </div>
        </div>
      </div>

      {/* Search */}
      <div className="max-w-lg mx-auto px-4 py-3">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 w-5 h-5" />
          <input
            type="text"
            placeholder="Search menu..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
          {searchQuery && (
            <button
              onClick={() => setSearchQuery('')}
              className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-400"
            >
              <X className="w-5 h-5" />
            </button>
          )}
        </div>
      </div>

      {/* Categories */}
      <div className="max-w-lg mx-auto px-4 pb-3">
        <div className="flex gap-2 overflow-x-auto pb-2 scrollbar-hide">
          {categories.map((category) => (
            <button
              key={category.id}
              onClick={() => handleCategoryClick(category.id)}
              className={`flex-shrink-0 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                selectedCategory === category.id
                  ? 'bg-blue-600 text-white'
                  : 'bg-white text-gray-700 hover:bg-gray-100'
              }`}
            >
              {category.name}
            </button>
          ))}
        </div>
      </div>

      {/* Products Grid */}
      <div className="max-w-lg mx-auto px-4">
        {loading ? (
          <div className="flex justify-center py-8">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
          </div>
        ) : filteredProducts.length === 0 ? (
          <div className="text-center py-8 text-gray-500">
            <UtensilsCrossed className="w-12 h-12 mx-auto mb-3 text-gray-300" />
            <p>No items found</p>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-3">
            {filteredProducts.map((product) => (
              <div
                key={product.id}
                onClick={() => handleProductClick(product)}
                className="bg-white rounded-lg shadow-sm overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
              >
                {product.imageUrl ? (
                  <img
                    src={product.imageUrl}
                    alt={product.name}
                    className="w-full h-28 object-cover"
                  />
                ) : (
                  <div className="w-full h-28 bg-gray-100 flex items-center justify-center">
                    <UtensilsCrossed className="w-8 h-8 text-gray-300" />
                  </div>
                )}
                <div className="p-3">
                  <h3 className="font-medium text-gray-900 text-sm line-clamp-2">{product.name}</h3>
                  <p className="text-blue-600 font-semibold text-sm mt-1">
                    {formatPrice(product.price)}
                  </p>
                  {product.preparationTime && (
                    <div className="flex items-center gap-1 text-xs text-gray-500 mt-1">
                      <Clock className="w-3 h-3" />
                      {product.preparationTime} min
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Cart Button */}
      {cart.itemCount > 0 && (
        <div className="fixed bottom-4 left-4 right-4 max-w-lg mx-auto z-50">
          <button
            onClick={() => navigate('/cart')}
            className="w-full bg-blue-600 text-white rounded-lg py-4 px-6 flex items-center justify-between shadow-lg hover:bg-blue-700 transition-colors"
          >
            <div className="flex items-center gap-3">
              <div className="bg-white/20 rounded-full p-2">
                <ShoppingCart className="w-5 h-5" />
              </div>
              <span className="font-medium">{cart.itemCount} items</span>
            </div>
            <span className="font-bold">{formatPrice(cart.total)}</span>
          </button>
        </div>
      )}

      {/* Product Modal */}
      {showProductModal && selectedProduct && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-end justify-center">
          <div className="bg-white w-full max-w-lg rounded-t-2xl max-h-[90vh] overflow-y-auto animate-slide-up">
            {/* Product Image */}
            {selectedProduct.imageUrl ? (
              <div className="relative">
                <img
                  src={selectedProduct.imageUrl}
                  alt={selectedProduct.name}
                  className="w-full h-48 object-cover"
                />
                <button
                  onClick={() => setShowProductModal(false)}
                  className="absolute top-4 right-4 bg-white/90 rounded-full p-2"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            ) : (
              <div className="relative bg-gray-100 h-32 flex items-center justify-center">
                <UtensilsCrossed className="w-12 h-12 text-gray-300" />
                <button
                  onClick={() => setShowProductModal(false)}
                  className="absolute top-4 right-4 bg-white/90 rounded-full p-2"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            )}

            <div className="p-4">
              {/* Product Info */}
              <h2 className="text-xl font-bold text-gray-900">{selectedProduct.name}</h2>
              {selectedProduct.description && (
                <p className="text-gray-600 mt-2 text-sm">{selectedProduct.description}</p>
              )}
              <p className="text-blue-600 font-bold text-lg mt-2">
                {formatPrice(selectedProduct.price)}
              </p>

              {/* Variants */}
              {selectedProduct.variants && selectedProduct.variants.length > 0 && (
                <div className="mt-4">
                  <h3 className="font-semibold text-gray-900 mb-2">Choose Option</h3>
                  <div className="space-y-2">
                    {selectedProduct.variants.map((variant) => (
                      <button
                        key={variant.id}
                        onClick={() => setSelectedVariant(variant)}
                        disabled={!variant.available}
                        className={`w-full p-3 rounded-lg border text-left flex justify-between items-center ${
                          selectedVariant?.id === variant.id
                            ? 'border-blue-600 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        } ${!variant.available ? 'opacity-50 cursor-not-allowed' : ''}`}
                      >
                        <span>{variant.name}</span>
                        <span className="font-semibold">{formatPrice(variant.price)}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Modifiers */}
              {selectedProduct.modifiers && selectedProduct.modifiers.length > 0 && (
                <div className="mt-4">
                  <h3 className="font-semibold text-gray-900 mb-2">Add-ons (Optional)</h3>
                  <div className="space-y-2">
                    {selectedProduct.modifiers.map((modifier) => (
                      <button
                        key={modifier.id}
                        onClick={() => toggleModifier(modifier)}
                        className={`w-full p-3 rounded-lg border text-left flex justify-between items-center ${
                          selectedModifiers.some(m => m.id === modifier.id)
                            ? 'border-blue-600 bg-blue-50'
                            : 'border-gray-200 hover:border-gray-300'
                        }`}
                      >
                        <span>{modifier.name}</span>
                        <span className="font-semibold">+{formatPrice(modifier.price)}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Special Instructions */}
              {restaurant?.allowSpecialInstructions && (
                <div className="mt-4">
                  <h3 className="font-semibold text-gray-900 mb-2">Special Instructions</h3>
                  <textarea
                    value={specialInstructions}
                    onChange={(e) => setSpecialInstructions(e.target.value)}
                    placeholder="Any special requests?"
                    className="w-full p-3 border rounded-lg resize-none"
                    rows={2}
                  />
                </div>
              )}

              {/* Quantity */}
              <div className="mt-4 flex items-center justify-between">
                <span className="font-semibold text-gray-900">Quantity</span>
                <div className="flex items-center gap-4">
                  <button
                    onClick={() => setQuantity(Math.max(1, quantity - 1))}
                    className="w-10 h-10 rounded-full border flex items-center justify-center hover:bg-gray-100"
                  >
                    <Minus className="w-4 h-4" />
                  </button>
                  <span className="text-xl font-semibold w-8 text-center">{quantity}</span>
                  <button
                    onClick={() => setQuantity(quantity + 1)}
                    className="w-10 h-10 rounded-full border flex items-center justify-center hover:bg-gray-100"
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                </div>
              </div>

              {/* Add to Cart Button */}
              <button
                onClick={handleAddToCart}
                disabled={addingToCart || !session}
                className="w-full mt-6 bg-blue-600 text-white rounded-lg py-4 font-semibold hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
              >
                {addingToCart ? (
                  <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                ) : (
                  <>
                    <ShoppingCart className="w-5 h-5" />
                    Add to Cart - {formatPrice(calculateItemPrice())}
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      <style>{`
        @keyframes slide-up {
          from { transform: translateY(100%); }
          to { transform: translateY(0); }
        }
        .animate-slide-up {
          animation: slide-up 0.3s ease-out;
        }
        .scrollbar-hide::-webkit-scrollbar {
          display: none;
        }
        .scrollbar-hide {
          -ms-overflow-style: none;
          scrollbar-width: none;
        }
        .line-clamp-2 {
          display: -webkit-box;
          -webkit-line-clamp: 2;
          -webkit-box-orient: vertical;
          overflow: hidden;
        }
      `}</style>
    </div>
  );
}
