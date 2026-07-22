package com.elcafe.modules.menu.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.menu.dto.ProductListDTO;
import com.elcafe.modules.menu.dto.PublicMenuCategoryDTO;
import com.elcafe.modules.menu.entity.AddOnGroup;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.AddOnGroupRepository;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MenuServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private ProductRepository productRepository;
    @Mock private AddOnGroupRepository addOnGroupRepository;
    @Mock private RestaurantRepository restaurantRepository;
    @InjectMocks private MenuService menuService;

    private Restaurant restaurant;
    private Category category;
    private Product product;
    private Product outOfStockProduct;

    @BeforeEach
    void setUp() {
        restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setActive(true);

        product = new Product();
        product.setId(1L);
        product.setName("Steak");
        product.setPrice(new BigDecimal("80000"));
        product.setStatus(ProductStatus.LIVE);
        product.setInStock(true);
        product.setFeatured(false);
        product.setHasVariants(false);
        product.setIsSoldByWeight(false);
        product.setSortOrder(0);

        outOfStockProduct = new Product();
        outOfStockProduct.setId(2L);
        outOfStockProduct.setName("Lobster");
        outOfStockProduct.setPrice(new BigDecimal("200000"));
        outOfStockProduct.setStatus(ProductStatus.LIVE);
        outOfStockProduct.setInStock(false);
        outOfStockProduct.setSortOrder(1);

        category = new Category();
        category.setId(1L);
        category.setName("Main Course");
        category.setActive(true);
        category.setSortOrder(0);
        category.setRestaurant(restaurant);
        category.setProducts(new ArrayList<>(List.of(product, outOfStockProduct)));
    }

    @Nested @DisplayName("Public Menu")
    class PublicMenuTests {

        @Test @DisplayName("getPublicMenu — returns categories with products")
        void getPublicMenu_returnsCategories() {
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(1L))
                    .thenReturn(List.of(category));

            List<PublicMenuCategoryDTO> result = menuService.getPublicMenu(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Main Course");
            assertThat(result.get(0).getProducts()).isNotEmpty();
        }

        @Test @DisplayName("getPublicMenu — inactive restaurant throws")
        void getPublicMenu_inactiveRestaurant_throws() {
            restaurant.setActive(false);
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));

            assertThatThrownBy(() -> menuService.getPublicMenu(1L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("getPublicMenu — filters out-of-stock products")
        void getPublicMenu_filtersInactiveProducts() {
            when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
            when(categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(1L))
                    .thenReturn(List.of(category));

            List<PublicMenuCategoryDTO> result = menuService.getPublicMenu(1L);

            // Only in-stock products (Steak), not Lobster
            assertThat(result.get(0).getProducts()).hasSize(1);
            assertThat(result.get(0).getProducts().get(0).getName()).isEqualTo("Steak");
        }
    }

    @Nested @DisplayName("Category Operations")
    class CategoryTests {

        @Test @DisplayName("createCategory — success")
        void createCategory_success() {
            when(categoryRepository.save(any(Category.class))).thenReturn(category);

            Category result = menuService.createCategory(category);

            assertThat(result.getName()).isEqualTo("Main Course");
            verify(categoryRepository).save(category);
        }

        @Test @DisplayName("updateCategory — success")
        void updateCategory_success() {
            Category update = new Category();
            update.setName("Updated Course");
            update.setActive(true);
            update.setSortOrder(1);

            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
            when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

            Category result = menuService.updateCategory(1L, update);

            assertThat(result.getName()).isEqualTo("Updated Course");
        }

        @Test @DisplayName("getCategoryById — found")
        void getCategoryById_found() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

            Category result = menuService.getCategoryById(1L);

            assertThat(result.getName()).isEqualTo("Main Course");
        }

        @Test @DisplayName("getCategoryById — not found throws")
        void getCategoryById_notFound_throws() {
            when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> menuService.getCategoryById(99L))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test @DisplayName("deleteCategory — success")
        void deleteCategory_success() {
            when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

            menuService.deleteCategory(1L);

            verify(categoryRepository).delete(category);
        }

        @Test @DisplayName("getActiveCategoriesByRestaurant — filters inactive")
        void getActiveCategoriesByRestaurant() {
            when(categoryRepository.findByRestaurant_IdAndActiveTrueOrderBySortOrder(1L))
                    .thenReturn(List.of(category));

            List<Category> result = menuService.getActiveCategoriesByRestaurant(1L);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getActive()).isTrue();
        }

        @Test @DisplayName("getCategoriesByRestaurant — returns all")
        void getCategoriesByRestaurant() {
            when(categoryRepository.findByRestaurant_IdOrderBySortOrder(1L))
                    .thenReturn(List.of(category));

            List<Category> result = menuService.getCategoriesByRestaurant(1L);

            assertThat(result).hasSize(1);
        }
    }

    @Nested @DisplayName("Product Operations")
    class ProductTests {

        @Test @DisplayName("createProduct — success")
        void createProduct_success() {
            when(productRepository.save(any(Product.class))).thenReturn(product);

            Product result = menuService.createProduct(product);

            assertThat(result.getName()).isEqualTo("Steak");
            verify(productRepository).save(product);
        }

        @Test @DisplayName("updateProduct — success")
        void updateProduct_success() {
            Product update = new Product();
            update.setName("Updated Steak");
            update.setPrice(new BigDecimal("90000"));
            update.setStatus(ProductStatus.LIVE);
            update.setInStock(true);

            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            Product result = menuService.updateProduct(1L, update);

            assertThat(result.getName()).isEqualTo("Updated Steak");
            assertThat(result.getPrice()).isEqualByComparingTo("90000");
        }

        @Test @DisplayName("updateProductStock — sets inStock flag")
        void updateProductStock_setsInStock() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            Product result = menuService.updateProductStock(1L, false);

            assertThat(result.getInStock()).isFalse();
        }

        @Test @DisplayName("updateProductStatus — changes status")
        void updateProductStatus_setsStatus() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            Product result = menuService.updateProductStatus(1L, ProductStatus.DRAFT);

            assertThat(result.getStatus()).isEqualTo(ProductStatus.DRAFT);
        }

        @Test @DisplayName("getProductById — found")
        void getProductById_found() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            Product result = menuService.getProductById(1L);

            assertThat(result.getName()).isEqualTo("Steak");
        }

        @Test @DisplayName("toggleProductStatus — LIVE to DRAFT")
        void toggleProductStatus_flips() {
            product.setStatus(ProductStatus.LIVE);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            Product result = menuService.toggleProductStatus(1L);

            assertThat(result.getStatus()).isEqualTo(ProductStatus.DRAFT);
        }

        @Test @DisplayName("toggleProductStatus — DRAFT to LIVE")
        void toggleProductStatus_draftToLive() {
            product.setStatus(ProductStatus.DRAFT);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

            Product result = menuService.toggleProductStatus(1L);

            assertThat(result.getStatus()).isEqualTo(ProductStatus.LIVE);
        }

        @Test @DisplayName("deleteProduct — success")
        void deleteProduct_success() {
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            menuService.deleteProduct(1L);

            verify(productRepository).delete(product);
        }

        @Test @DisplayName("getProductsByCategory — returns list")
        void getProductsByCategory() {
            when(productRepository.findByCategoryIdOrderBySortOrder(1L)).thenReturn(List.of(product));

            List<Product> result = menuService.getProductsByCategory(1L);

            assertThat(result).hasSize(1);
        }

        @Test @DisplayName("getProductsByRestaurant — returns sorted list")
        void getProductsByRestaurant() {
            when(categoryRepository.findByRestaurant_IdOrderBySortOrder(1L)).thenReturn(List.of(category));

            List<ProductListDTO> result = menuService.getProductsByRestaurant(1L);

            assertThat(result).hasSize(2); // both products, sorted by name
        }
    }

    @Nested @DisplayName("AddOnGroup Operations (delegated)")
    class AddOnGroupTests {

        @Test @DisplayName("createAddOnGroup — delegates to repo")
        void createAddOnGroup() {
            AddOnGroup group = AddOnGroup.builder().id(1L).name("Extras").build();
            when(addOnGroupRepository.save(any(AddOnGroup.class))).thenReturn(group);

            AddOnGroup result = menuService.createAddOnGroup(group);

            assertThat(result.getName()).isEqualTo("Extras");
        }

        @Test @DisplayName("getAddOnGroupsByRestaurant — returns active groups")
        void getAddOnGroupsByRestaurant() {
            AddOnGroup group = AddOnGroup.builder().id(1L).name("Extras").active(true).build();
            when(addOnGroupRepository.findByRestaurant_IdAndActiveTrue(1L)).thenReturn(List.of(group));

            List<AddOnGroup> result = menuService.getAddOnGroupsByRestaurant(1L);

            assertThat(result).hasSize(1);
        }

        @Test @DisplayName("deleteAddOnGroup — delegates to repo")
        void deleteAddOnGroup() {
            AddOnGroup group = AddOnGroup.builder().id(1L).name("Extras").build();
            when(addOnGroupRepository.findById(1L)).thenReturn(Optional.of(group));

            menuService.deleteAddOnGroup(1L);

            verify(addOnGroupRepository).delete(group);
        }
    }
}
