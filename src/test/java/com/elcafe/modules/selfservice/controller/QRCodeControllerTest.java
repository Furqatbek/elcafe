package com.elcafe.modules.selfservice.controller;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.modules.selfservice.dto.CreateQRCodeRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.entity.SelfServiceSettings;
import com.elcafe.modules.selfservice.enums.QRCodeType;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QRCodeControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private QRCodeService qrCodeService;

    @Mock
    private SelfServiceOrderService orderService;

    @Mock
    private RestaurantAuthorizationService restaurantAuthService;

    @InjectMocks
    private QRCodeController controller;

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

    // ==================== Helper Methods ====================

    private QRCode createQRCode() {
        QRCode qrCode = new QRCode();
        qrCode.setId(1L);
        qrCode.setCode("ABCD1234");
        qrCode.setName("Table 1");
        qrCode.setQrType(QRCodeType.TABLE);
        qrCode.setIsActive(true);
        qrCode.setScanCount(0);
        qrCode.setShortUrl("http://localhost:3000/order/menu/1/ABCD1234");
        qrCode.setCreatedAt(LocalDateTime.now());
        qrCode.setUpdatedAt(LocalDateTime.now());
        return qrCode;
    }

    private SelfServiceSettings createSettings() {
        SelfServiceSettings settings = new SelfServiceSettings();
        settings.setId(1L);
        settings.setEnabled(true);
        settings.setRequirePayment(false);
        settings.setAllowTakeaway(true);
        settings.setAllowDineIn(true);
        settings.setAutoAcceptOrders(false);
        settings.setEstimatedPrepTimeMinutes(15);
        settings.setShowWaitTime(true);
        settings.setAllowSpecialInstructions(true);
        settings.setMaxItemsPerOrder(20);
        return settings;
    }

    // ==================== Tests ====================

    @Test
    void createQRCode_returns200() throws Exception {
        QRCode qrCode = createQRCode();
        when(qrCodeService.createQRCode(any(CreateQRCodeRequest.class))).thenReturn(qrCode);

        mockMvc.perform(post("/api/v1/qr-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"restaurantId\": 1, \"tableId\": 1, \"name\": \"Table 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.code").value("ABCD1234"));
    }

    @Test
    void generateForAllTables_returns200() throws Exception {
        List<QRCode> qrCodes = List.of(createQRCode());
        when(qrCodeService.generateForAllTables(1L)).thenReturn(qrCodes);

        mockMvc.perform(post("/api/v1/qr-codes/restaurant/1/generate-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.generated").value(1))
                .andExpect(jsonPath("$.qrCodes").isArray());
    }

    @Test
    void getByRestaurant_returnsPaginated() throws Exception {
        List<QRCode> list = List.of(createQRCode());
        Page<QRCode> page = new PageImpl<>(list, PageRequest.of(0, 20), list.size());
        when(qrCodeService.getByRestaurant(eq(1L), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/v1/qr-codes/restaurant/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getById_found_returns200() throws Exception {
        QRCode qrCode = createQRCode();
        when(qrCodeService.getById(1L)).thenReturn(Optional.of(qrCode));

        mockMvc.perform(get("/api/v1/qr-codes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(qrCodeService.getById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/qr-codes/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("QR code not found"));
    }

    @Test
    void toggleActive_returns200() throws Exception {
        QRCode qrCode = createQRCode();
        qrCode.setIsActive(false);
        when(qrCodeService.toggleActive(1L)).thenReturn(qrCode);

        mockMvc.perform(patch("/api/v1/qr-codes/1/toggle"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void deleteQRCode_returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/qr-codes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("QR code deleted"));
    }

    @Test
    void getImage_returns200() throws Exception {
        QRCode qrCode = createQRCode();
        when(qrCodeService.getById(1L)).thenReturn(Optional.of(qrCode));
        when(qrCodeService.generateQRCodeImage("ABCD1234", 300, 300)).thenReturn("base64data");
        when(qrCodeService.getCurrentUrl(qrCode)).thenReturn("http://localhost:3000/order/menu/1/ABCD1234");

        mockMvc.perform(get("/api/v1/qr-codes/1/image"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("ABCD1234"))
                .andExpect(jsonPath("$.url").value("http://localhost:3000/order/menu/1/ABCD1234"))
                .andExpect(jsonPath("$.image").value("data:image/png;base64,base64data"));
    }

    @Test
    void getStats_returns200() throws Exception {
        QRCodeService.QRCodeStats stats = QRCodeService.QRCodeStats.builder()
                .activeQRCodes(5)
                .totalScans(100)
                .build();
        when(qrCodeService.getStats(1L)).thenReturn(stats);

        mockMvc.perform(get("/api/v1/qr-codes/restaurant/1/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeQRCodes").value(5))
                .andExpect(jsonPath("$.totalScans").value(100));
    }

    @Test
    void getSettings_returns200() throws Exception {
        SelfServiceSettings settings = createSettings();
        when(orderService.getSettings(1L)).thenReturn(settings);

        mockMvc.perform(get("/api/v1/qr-codes/restaurant/1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void saveSettings_returns200() throws Exception {
        SelfServiceSettings settings = createSettings();
        when(orderService.saveSettings(eq(1L), any(SelfServiceSettings.class))).thenReturn(settings);

        mockMvc.perform(post("/api/v1/qr-codes/restaurant/1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(settings)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.enabled").value(true));
    }
}
