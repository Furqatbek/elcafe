package com.elcafe.modules.courier.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.courier.dto.CourierTariffResponse;
import com.elcafe.modules.courier.dto.CreateCourierTariffRequest;
import com.elcafe.modules.courier.dto.UpdateCourierTariffRequest;
import com.elcafe.modules.courier.entity.CourierTariff;
import com.elcafe.modules.courier.enums.TariffType;
import com.elcafe.modules.courier.repository.CourierTariffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourierTariffServiceTest {

    @Mock private CourierTariffRepository courierTariffRepository;
    @InjectMocks private CourierTariffService courierTariffService;

    private CourierTariff tariff;

    @BeforeEach
    void setUp() {
        tariff = CourierTariff.builder().id(1L).name("Standard Bonus").type(TariffType.BONUS)
                .fixedAmount(new BigDecimal("10000")).amountPerOrder(new BigDecimal("2000"))
                .amountPerKilometer(BigDecimal.ZERO).active(true).build();
    }

    @Test @DisplayName("getAllTariffs — paginated")
    void getAllTariffs_returnsPage() {
        when(courierTariffRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(tariff), PageRequest.of(0, 20), 1));
        var result = courierTariffService.getAllTariffs(PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getTariffsByType — filters by type")
    void getTariffsByType_filtersType() {
        when(courierTariffRepository.findByType(TariffType.BONUS, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(tariff), PageRequest.of(0, 20), 1));
        var result = courierTariffService.getTariffsByType(TariffType.BONUS, PageRequest.of(0, 20));
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test @DisplayName("getActiveTariffs — only active")
    void getActiveTariffs_returnsList() {
        when(courierTariffRepository.findByActiveTrue()).thenReturn(List.of(tariff));
        assertThat(courierTariffService.getActiveTariffs()).hasSize(1);
    }

    @Test @DisplayName("getTariffById — found")
    void getTariffById_found() {
        when(courierTariffRepository.findById(1L)).thenReturn(Optional.of(tariff));
        CourierTariffResponse result = courierTariffService.getTariffById(1L);
        assertThat(result.getName()).isEqualTo("Standard Bonus");
    }

    @Test @DisplayName("getTariffById — not found throws")
    void getTariffById_notFound_throws() {
        when(courierTariffRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> courierTariffService.getTariffById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test @DisplayName("createTariff — success")
    void createTariff_success() {
        CreateCourierTariffRequest req = new CreateCourierTariffRequest();
        req.setName("New Tariff"); req.setType(TariffType.BONUS);
        req.setFixedAmount(new BigDecimal("5000"));
        when(courierTariffRepository.existsByNameIgnoreCase("New Tariff")).thenReturn(false);
        when(courierTariffRepository.save(any())).thenAnswer(i -> { CourierTariff t = i.getArgument(0); t.setId(2L); return t; });

        CourierTariffResponse result = courierTariffService.createTariff(req);
        assertThat(result.getName()).isEqualTo("New Tariff");
    }

    @Test @DisplayName("updateTariff — updates selectively")
    void updateTariff_success() {
        UpdateCourierTariffRequest req = new UpdateCourierTariffRequest();
        req.setFixedAmount(new BigDecimal("15000"));
        when(courierTariffRepository.findById(1L)).thenReturn(Optional.of(tariff));
        when(courierTariffRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        CourierTariffResponse result = courierTariffService.updateTariff(1L, req);
        assertThat(result.getFixedAmount()).isEqualByComparingTo("15000");
    }

    @Test @DisplayName("deleteTariff — success")
    void deleteTariff_success() {
        when(courierTariffRepository.existsById(1L)).thenReturn(true);
        courierTariffService.deleteTariff(1L);
        verify(courierTariffRepository).deleteById(1L);
    }
}
