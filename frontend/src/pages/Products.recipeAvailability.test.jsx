import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import Products from './Products';
import { menuAPI, restaurantAPI } from '../services/api';

// Benign never-resolving defaults so a test that forgets to stub a call hangs on that call rather
// than leaking a rejected promise into an unrelated assertion.
vi.mock('../services/api', () => {
  const pending = () => vi.fn(() => new Promise(() => {}));
  return {
    menuAPI: {
      getCategories: pending(),
      getProductsByRestaurant: pending(),
      createProduct: pending(),
      updateProduct: pending(),
      deleteProduct: pending(),
      toggleProductStatus: pending(),
    },
    restaurantAPI: { getAll: pending() },
    uploadAPI: { uploadImage: pending() },
    productVariantAPI: { getAllNoPaging: pending() },
    packagingRuleAPI: { getByProduct: pending() },
    inventoryAPI: { getIngredients: pending(), getIngredientCategories: pending() },
    recipesAPI: { recalculateAllCosts: pending() },
  };
});

vi.mock('../lib/errors', () => ({
  notifyError: vi.fn(),
  notifySuccess: vi.fn(),
  notifyWarning: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key, defaultValue) => (typeof defaultValue === 'string' ? defaultValue : key),
  }),
}));

const RESTAURANT = { id: 1, name: 'Test Cafe' };

const product = (overrides) => ({
  id: 10,
  name: 'Osh',
  categoryId: 1,
  categoryName: 'Main',
  price: 30000,
  available: true,
  recipeAvailable: true,
  ...overrides,
});

/**
 * The manual switch and the derived one are separate on purpose, which means the card can say
 * "In Stock" while delivery partners have already stopped showing the dish. That reads as a bug
 * unless the reason is on screen, so the badge is the whole point of keeping them separate.
 */
describe('Products — ingredient-driven availability', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    restaurantAPI.getAll.mockResolvedValue({ data: { data: { content: [RESTAURANT] } } });
    menuAPI.getCategories.mockResolvedValue({ data: { data: [] } });
  });

  it('flags a dish whose ingredients have run short, while its own switch is still on', async () => {
    menuAPI.getProductsByRestaurant.mockResolvedValue({
      data: { data: [product({ recipeAvailable: false })] },
    });

    render(<Products />);

    expect(await screen.findByText('No ingredients')).toBeInTheDocument();
    // Both badges show: the switch is still on, and that is exactly the confusing case.
    expect(screen.getByText('In Stock')).toBeInTheDocument();
  });

  it('says nothing when the kitchen can make the dish', async () => {
    menuAPI.getProductsByRestaurant.mockResolvedValue({
      data: { data: [product()] },
    });

    render(<Products />);

    expect(await screen.findByText('In Stock')).toBeInTheDocument();
    expect(screen.queryByText('No ingredients')).not.toBeInTheDocument();
  });

  it('says nothing for a venue whose backend predates the field', async () => {
    // recipeAvailable absent, not false — an older API must not paint every dish as broken.
    const { recipeAvailable, ...withoutTheField } = product();
    menuAPI.getProductsByRestaurant.mockResolvedValue({
      data: { data: [withoutTheField] },
    });

    render(<Products />);

    await waitFor(() => expect(screen.getByText('Osh')).toBeInTheDocument());
    expect(screen.queryByText('No ingredients')).not.toBeInTheDocument();
  });
});
