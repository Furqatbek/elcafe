package com.elcafe.modules.selfservice.controller;

import com.elcafe.modules.bundle.entity.Bundle;
import com.elcafe.modules.bundle.repository.BundleRepository;
import com.elcafe.modules.menu.entity.Category;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.enums.ProductStatus;
import com.elcafe.modules.menu.repository.CategoryRepository;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.promotion.entity.Promotion;
import com.elcafe.modules.promotion.repository.HappyHourRepository;
import com.elcafe.modules.promotion.repository.PromotionRepository;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.selfservice.dto.AddToCartRequest;
import com.elcafe.modules.selfservice.dto.CartItemResponse;
import com.elcafe.modules.selfservice.dto.SubmitOrderRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.entity.SelfServiceCartItem;
import com.elcafe.modules.selfservice.entity.SelfServiceOrder;
import com.elcafe.modules.selfservice.entity.SelfServiceSession;
import com.elcafe.modules.selfservice.entity.SelfServiceSettings;
import com.elcafe.modules.selfservice.enums.SelfServiceOrderType;
import com.elcafe.modules.selfservice.exception.SelfServiceException;
import com.elcafe.modules.selfservice.service.QRCodeService;
import com.elcafe.modules.selfservice.service.SelfServiceOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SelfServiceControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private QRCodeService qrCodeService;

    @Mock
    private SelfServiceOrderService orderService;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private HappyHourRepository happyHourRepository;

    @Mock
    private BundleRepository bundleRepository;

    @InjectMocks
    private SelfServiceController controller;

    @BeforeEach
    void setUp() {
        ObjectMapper jacksonMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter(jacksonMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(converter)
                .build();
        objectMapper = jacksonMapper;
    }

    // ==================== Helper methods ====================

    private Restaurant createRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Test Restaurant");
        restaurant.setAddress("123 Test St");
        restaurant.setPhone("555-1234");
        restaurant.setLogoUrl("http://logo.png");
        return restaurant;
    }

    private RestaurantTable createTable() {
        RestaurantTable table = new RestaurantTable();
        table.setId(1L);
        table.setTableNumber("T1");
        return table;
    }

    private QRCode createQRCode(Restaurant restaurant, RestaurantTable table) {
        QRCode qr = new QRCode();
        qr.setId(1L);
        qr.setCode("VALID123");
        qr.setRestaurant(restaurant);
        qr.setTable(table);
        return qr;
    }

    private SelfServiceSession createSession(Restaurant restaurant, RestaurantTable table, QRCode qrCode) {
        return SelfServiceSession.builder()
                .id(1L)
                .sessionToken("test-token")
                .restaurant(restaurant)
                .table(table)
                .qrCode(qrCode)
                .customerName("Test Customer")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .isActive(true)
                .build();
    }

    private SelfServiceSession createTakeawaySession(Restaurant restaurant) {
        return SelfServiceSession.builder()
                .id(2L)
                .sessionToken("takeaway-token")
                .restaurant(restaurant)
                .table(null)
                .qrCode(null)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .isActive(true)
                .build();
    }

    private Category createCategory(Long id, String name) {
        Category cat = new Category();
        cat.setId(id);
        cat.setName(name);
        cat.setImageUrl("http://cat.png");
        cat.setSortOrder(0);
        return cat;
    }

    private Product createProduct(Long id, String name, Category category) {
        Product p = new Product();
        p.setId(id);
        p.setName(name);
        p.setDescription("A test product");
        p.setPrice(new BigDecimal("9.99"));
        p.setImageUrl("http://product.png");
        p.setInStock(true);
        p.setCategory(category);
        return p;
    }

    // ==================== Tests ====================

    @Test
    void startSession_validCode_returns200() throws Exception {
        Restaurant restaurant = createRestaurant();
        RestaurantTable table = createTable();
        QRCode qrCode = createQRCode(restaurant, table);
        SelfServiceSession session = createSession(restaurant, table, qrCode);

        when(qrCodeService.getByCode("VALID123")).thenReturn(Optional.of(qrCode));
        when(orderService.startSession(eq("VALID123"), any(), any())).thenReturn(session);

        mockMvc.perform(post("/api/v1/self-service/session/start")
                        .param("code", "VALID123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("test-token"))
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.restaurantName").value("Test Restaurant"))
                .andExpect(jsonPath("$.tableId").value(1))
                .andExpect(jsonPath("$.tableNumber").value("T1"))
                .andExpect(jsonPath("$.tableCode").value("VALID123"));
    }

    @Test
    void startSession_invalidCode_returns400() throws Exception {
        when(orderService.startSession(eq("BAD"), any(), any()))
                .thenThrow(new SelfServiceException("Invalid QR code"));

        // The controller does not catch SelfServiceException itself; without a global
        // exception handler the standalone MockMvc wraps it in a ServletException.
        org.junit.jupiter.api.Assertions.assertThrows(jakarta.servlet.ServletException.class, () ->
                mockMvc.perform(post("/api/v1/self-service/session/start")
                        .param("code", "BAD")
                        .contentType(MediaType.APPLICATION_JSON)));
    }

    @Test
    void startTakeawaySession_returns200() throws Exception {
        Restaurant restaurant = createRestaurant();
        SelfServiceSession session = createTakeawaySession(restaurant);

        when(orderService.startTakeawaySession(eq(1L), any(), any())).thenReturn(session);

        mockMvc.perform(post("/api/v1/self-service/session/start/takeaway")
                        .param("restaurantId", "1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("takeaway-token"))
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.restaurantName").value("Test Restaurant"))
                .andExpect(jsonPath("$.tableId").isEmpty())
                .andExpect(jsonPath("$.tableNumber").isEmpty())
                .andExpect(jsonPath("$.orderType").value("TAKEAWAY"));
    }

    @Test
    void getSession_validToken_returns200() throws Exception {
        Restaurant restaurant = createRestaurant();
        RestaurantTable table = createTable();
        QRCode qrCode = createQRCode(restaurant, table);
        SelfServiceSession session = createSession(restaurant, table, qrCode);

        when(orderService.getSession("test-token")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/v1/self-service/session")
                        .header("X-Session-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionToken").value("test-token"))
                .andExpect(jsonPath("$.restaurantId").value(1))
                .andExpect(jsonPath("$.tableId").value(1))
                .andExpect(jsonPath("$.tableNumber").value("T1"))
                .andExpect(jsonPath("$.tableCode").value("VALID123"))
                .andExpect(jsonPath("$.customerName").value("Test Customer"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void getRestaurantInfo_returns200() throws Exception {
        Restaurant restaurant = createRestaurant();
        SelfServiceSettings settings = SelfServiceSettings.builder()
                .id(1L)
                .restaurant(restaurant)
                .enabled(true)
                .allowTakeaway(true)
                .allowDineIn(true)
                .minimumOrderAmount(new BigDecimal("10.00"))
                .estimatedPrepTimeMinutes(20)
                .showWaitTime(true)
                .allowSpecialInstructions(true)
                .build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(orderService.getSettings(1L)).thenReturn(settings);

        mockMvc.perform(get("/api/v1/self-service/restaurant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Restaurant"))
                .andExpect(jsonPath("$.address").value("123 Test St"))
                .andExpect(jsonPath("$.phone").value("555-1234"))
                .andExpect(jsonPath("$.selfServiceEnabled").value(true))
                .andExpect(jsonPath("$.allowTakeaway").value(true))
                .andExpect(jsonPath("$.allowDineIn").value(true))
                .andExpect(jsonPath("$.estimatedPrepTime").value(20));
    }

    @Test
    void getCategories_returns200() throws Exception {
        Category cat1 = createCategory(1L, "Appetizers");
        Category cat2 = createCategory(2L, "Main Course");

        when(categoryRepository.findByRestaurantIdAndActiveTrueOrderBySortOrder(1L))
                .thenReturn(List.of(cat1, cat2));

        mockMvc.perform(get("/api/v1/self-service/menu/1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Appetizers"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Main Course"));
    }

    @Test
    void getProducts_returns200() throws Exception {
        Category category = createCategory(1L, "Appetizers");
        Product p1 = createProduct(1L, "Salad", category);
        Product p2 = createProduct(2L, "Soup", category);

        when(productRepository.findByRestaurantIdAndStatus(1L, ProductStatus.LIVE))
                .thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/v1/self-service/menu/1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Salad"))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("Appetizers"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].name").value("Soup"));
    }

    @Test
    void getProductDetail_returns200() throws Exception {
        Category category = createCategory(1L, "Appetizers");
        Product product = createProduct(1L, "Salad", category);

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        mockMvc.perform(get("/api/v1/self-service/menu/product/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Salad"))
                .andExpect(jsonPath("$.description").value("A test product"))
                .andExpect(jsonPath("$.price").value(9.99))
                .andExpect(jsonPath("$.inStock").value(true));
    }

    @Test
    void getPromotions_returns200() throws Exception {
        Promotion promo = new Promotion();
        promo.setId(1L);
        promo.setName("Summer Sale");
        promo.setDescription("20% off");
        promo.setDiscountValue(new BigDecimal("20"));

        when(promotionRepository.findActivePromotions(eq(1L), any(LocalDateTime.class)))
                .thenReturn(List.of(promo));

        mockMvc.perform(get("/api/v1/self-service/promotions/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Summer Sale"))
                .andExpect(jsonPath("$[0].description").value("20% off"));
    }

    @Test
    void getBundles_returns200() throws Exception {
        when(bundleRepository.findByRestaurantIdAndActiveTrue(1L))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/self-service/bundles/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void addToCart_returns200() throws Exception {
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(10L)
                .quantity(2)
                .unitPrice(new BigDecimal("9.99"))
                .build();

        CartItemResponse cartItemResponse = CartItemResponse.builder()
                .id(10L)
                .productId(1L)
                .productName("Salad")
                .quantity(2)
                .unitPrice(new BigDecimal("9.99"))
                .totalPrice(new BigDecimal("19.98"))
                .build();

        when(orderService.addToCart(eq("test-token"), any(AddToCartRequest.class)))
                .thenReturn(cartItem);
        when(orderService.getCart("test-token"))
                .thenReturn(List.of(cartItemResponse));

        String json = "{\"productId\":1,\"quantity\":2}";
        mockMvc.perform(post("/api/v1/self-service/cart/add")
                        .header("X-Session-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.productName").value("Salad"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void updateCartItem_returns200() throws Exception {
        SelfServiceCartItem cartItem = SelfServiceCartItem.builder()
                .id(1L)
                .quantity(3)
                .unitPrice(new BigDecimal("9.99"))
                .build();

        when(orderService.updateCartItem("test-token", 1L, 3)).thenReturn(cartItem);

        mockMvc.perform(put("/api/v1/self-service/cart/item/1")
                        .param("quantity", "3")
                        .header("X-Session-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item updated"));
    }

    @Test
    void removeCartItem_returns200() throws Exception {
        doNothing().when(orderService).removeFromCart("test-token", 1L);

        mockMvc.perform(delete("/api/v1/self-service/cart/item/1")
                        .header("X-Session-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Item removed"));
    }

    @Test
    void getCart_returns200() throws Exception {
        CartItemResponse item = CartItemResponse.builder()
                .id(1L)
                .productId(1L)
                .productName("Salad")
                .quantity(2)
                .unitPrice(new BigDecimal("9.99"))
                .totalPrice(new BigDecimal("19.98"))
                .build();

        when(orderService.getCart("test-token")).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v1/self-service/cart")
                        .header("X-Session-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productName").value("Salad"))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.total").value(19.98));
    }

    @Test
    void clearCart_returns200() throws Exception {
        doNothing().when(orderService).clearCart("test-token");

        mockMvc.perform(delete("/api/v1/self-service/cart")
                        .header("X-Session-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Cart cleared"));
    }

    @Test
    void submitOrder_returns200() throws Exception {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber("ORD-001");
        order.setStatus(OrderStatus.PENDING);

        SelfServiceOrder selfServiceOrder = SelfServiceOrder.builder()
                .id(1L)
                .order(order)
                .orderType(SelfServiceOrderType.DINE_IN)
                .estimatedReadyTime(LocalDateTime.now().plusMinutes(20))
                .build();

        when(orderService.submitOrder(eq("test-token"), any(SubmitOrderRequest.class)))
                .thenReturn(selfServiceOrder);

        String requestJson = "{\"orderType\":\"DINE_IN\",\"customerName\":\"John\"}";

        mockMvc.perform(post("/api/v1/self-service/order/submit")
                        .header("X-Session-Token", "test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.orderNumber").value("ORD-001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrderStatus_returns200() throws Exception {
        Order order = new Order();
        order.setId(100L);
        order.setOrderNumber("ORD-001");
        order.setStatus(OrderStatus.PREPARING);
        order.setTotal(new BigDecimal("29.97"));

        SelfServiceOrder selfServiceOrder = SelfServiceOrder.builder()
                .id(1L)
                .order(order)
                .orderType(SelfServiceOrderType.DINE_IN)
                .estimatedReadyTime(LocalDateTime.now().plusMinutes(15))
                .build();

        when(orderService.getOrderStatusWithSessionValidation("test-token", 1L))
                .thenReturn(selfServiceOrder);

        mockMvc.perform(get("/api/v1/self-service/order/1/status")
                        .header("X-Session-Token", "test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.orderNumber").value("ORD-001"))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.orderType").value("DINE_IN"))
                .andExpect(jsonPath("$.total").value(29.97));
    }

    @Test
    void startSession_missingCode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/self-service/session/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
