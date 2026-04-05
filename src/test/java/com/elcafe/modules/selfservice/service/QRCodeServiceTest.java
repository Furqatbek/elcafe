package com.elcafe.modules.selfservice.service;

import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.selfservice.dto.CreateQRCodeRequest;
import com.elcafe.modules.selfservice.entity.QRCode;
import com.elcafe.modules.selfservice.enums.QRCodeType;
import com.elcafe.modules.selfservice.repository.QRCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class QRCodeServiceTest {

    @Mock
    private QRCodeRepository qrCodeRepository;

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantTableRepository tableRepository;

    @InjectMocks
    private QRCodeService qrCodeService;

    private Restaurant restaurant;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(qrCodeService, "selfServiceBaseUrl", "http://localhost:3000/order");

        restaurant = Restaurant.builder().id(1L).build();
        table = RestaurantTable.builder().id(10L).tableNumber("T1").restaurant(restaurant).build();
    }

    @Test
    void createQRCode_valid_createsWithUniqueCode() {
        CreateQRCodeRequest request = new CreateQRCodeRequest();
        request.setRestaurantId(1L);
        request.setTableId(10L);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table));
        when(qrCodeRepository.findByTableIdAndIsActiveTrue(10L)).thenReturn(Optional.empty());
        when(qrCodeRepository.existsByCode(anyString())).thenReturn(false);
        when(qrCodeRepository.save(any(QRCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QRCode result = qrCodeService.createQRCode(request);

        assertNotNull(result);
        assertNotNull(result.getCode());
        assertEquals(8, result.getCode().length());
        assertTrue(result.getShortUrl().contains("1"));
        assertTrue(result.getShortUrl().contains(result.getCode()));
        verify(qrCodeRepository).save(any(QRCode.class));
    }

    @Test
    void createQRCode_tableAlreadyHasQR_throwsException() {
        CreateQRCodeRequest request = new CreateQRCodeRequest();
        request.setRestaurantId(1L);
        request.setTableId(10L);

        QRCode existingQR = QRCode.builder().id(99L).code("EXISTING1").build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(tableRepository.findById(10L)).thenReturn(Optional.of(table));
        when(qrCodeRepository.findByTableIdAndIsActiveTrue(10L)).thenReturn(Optional.of(existingQR));

        assertThrows(RuntimeException.class, () -> qrCodeService.createQRCode(request));
    }

    @Test
    void createQRCode_restaurantNotFound_throwsException() {
        CreateQRCodeRequest request = new CreateQRCodeRequest();
        request.setRestaurantId(999L);

        when(restaurantRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> qrCodeService.createQRCode(request));
    }

    @Test
    void generateForAllTables_createsForUnassigned() {
        RestaurantTable table1 = RestaurantTable.builder().id(1L).tableNumber("T1").restaurant(restaurant).build();
        RestaurantTable table2 = RestaurantTable.builder().id(2L).tableNumber("T2").restaurant(restaurant).build();
        RestaurantTable table3 = RestaurantTable.builder().id(3L).tableNumber("T3").restaurant(restaurant).build();

        QRCode existingQR = QRCode.builder().id(50L).code("EXISTQR1").build();

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(tableRepository.findByRestaurantId(1L)).thenReturn(List.of(table1, table2, table3));
        when(qrCodeRepository.findByTableIdAndIsActiveTrue(1L)).thenReturn(Optional.of(existingQR));
        when(qrCodeRepository.findByTableIdAndIsActiveTrue(2L)).thenReturn(Optional.empty());
        when(qrCodeRepository.findByTableIdAndIsActiveTrue(3L)).thenReturn(Optional.empty());
        when(qrCodeRepository.existsByCode(anyString())).thenReturn(false);
        when(qrCodeRepository.save(any(QRCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<QRCode> result = qrCodeService.generateForAllTables(1L);

        assertEquals(2, result.size());
        verify(qrCodeRepository, times(2)).save(any(QRCode.class));
    }

    @Test
    void getByCode_found_returnsQRCode() {
        QRCode qrCode = QRCode.builder().id(1L).code("ABCD1234").build();
        when(qrCodeRepository.findByCode("ABCD1234")).thenReturn(Optional.of(qrCode));

        Optional<QRCode> result = qrCodeService.getByCode("ABCD1234");

        assertTrue(result.isPresent());
        assertEquals("ABCD1234", result.get().getCode());
    }

    @Test
    void getByRestaurant_returnsPaginated() {
        Pageable pageable = PageRequest.of(0, 10);
        QRCode qr1 = QRCode.builder().id(1L).code("CODE0001").build();
        QRCode qr2 = QRCode.builder().id(2L).code("CODE0002").build();
        Page<QRCode> page = new PageImpl<>(List.of(qr1, qr2), pageable, 2);

        when(qrCodeRepository.findByRestaurantIdOrderByCreatedAtDesc(1L, pageable)).thenReturn(page);

        Page<QRCode> result = qrCodeService.getByRestaurant(1L, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
    }

    @Test
    void getActiveByRestaurant_returnsOnlyActive() {
        QRCode qr1 = QRCode.builder().id(1L).code("ACTIVE01").isActive(true).build();
        QRCode qr2 = QRCode.builder().id(2L).code("ACTIVE02").isActive(true).build();

        when(qrCodeRepository.findByRestaurantIdAndIsActiveTrue(1L)).thenReturn(List.of(qr1, qr2));

        List<QRCode> result = qrCodeService.getActiveByRestaurant(1L);

        assertEquals(2, result.size());
        verify(qrCodeRepository).findByRestaurantIdAndIsActiveTrue(1L);
    }

    @Test
    void recordScan_incrementsCountAndTimestamp() {
        QRCode qrCode = QRCode.builder().id(1L).code("SCAN0001").scanCount(5).build();
        when(qrCodeRepository.findByCode("SCAN0001")).thenReturn(Optional.of(qrCode));
        when(qrCodeRepository.save(any(QRCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        qrCodeService.recordScan("SCAN0001");

        assertEquals(6, qrCode.getScanCount());
        assertNotNull(qrCode.getLastScannedAt());
        verify(qrCodeRepository).save(qrCode);
    }

    @Test
    void toggleActive_flipsStatus() {
        QRCode qrCode = QRCode.builder().id(1L).code("TOGGLE01").isActive(true).build();
        when(qrCodeRepository.findById(1L)).thenReturn(Optional.of(qrCode));
        when(qrCodeRepository.save(any(QRCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QRCode result = qrCodeService.toggleActive(1L);

        assertFalse(result.getIsActive());
        verify(qrCodeRepository).save(qrCode);
    }

    @Test
    void delete_removesQRCode() {
        qrCodeService.delete(1L);

        verify(qrCodeRepository).deleteById(1L);
    }

    @Test
    void generateQRCodeImage_returnsBase64() {
        QRCode qrCode = QRCode.builder()
                .id(1L)
                .code("IMAGE001")
                .shortUrl("http://localhost:3000/order/menu/1/IMAGE001")
                .build();
        when(qrCodeRepository.findByCode("IMAGE001")).thenReturn(Optional.of(qrCode));

        String result = qrCodeService.generateQRCodeImage("IMAGE001", 200, 200);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void generateQRCodeImage_codeNotFound_throwsException() {
        when(qrCodeRepository.findByCode("NOTFOUND")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> qrCodeService.generateQRCodeImage("NOTFOUND", 200, 200));
    }

    @Test
    void getStats_returnsActiveCountAndTotalScans() {
        when(qrCodeRepository.countActiveByRestaurant(1L)).thenReturn(5L);
        when(qrCodeRepository.getTotalScans(1L)).thenReturn(100L);

        QRCodeService.QRCodeStats stats = qrCodeService.getStats(1L);

        assertEquals(5L, stats.getActiveQRCodes());
        assertEquals(100L, stats.getTotalScans());
    }

    @Test
    void getCurrentUrl_buildsCorrectUrl() {
        QRCode qrCode = QRCode.builder()
                .id(1L)
                .code("URLCODE1")
                .restaurant(restaurant)
                .build();

        String url = qrCodeService.getCurrentUrl(qrCode);

        assertEquals("http://localhost:3000/order/menu/1/URLCODE1", url);
    }
}
