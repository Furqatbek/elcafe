package com.elcafe.modules.waiter.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import com.elcafe.modules.waiter.dto.*;
import com.elcafe.modules.waiter.entity.Waiter;
import com.elcafe.modules.waiter.entity.WaiterTable;
import com.elcafe.modules.waiter.repository.WaiterRepository;
import com.elcafe.modules.waiter.repository.WaiterTableRepository;
import com.elcafe.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.List;
import java.util.Optional;

import static com.elcafe.modules.waiter.helper.TestDataFactory.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaiterServiceTest {

    @Mock private WaiterRepository waiterRepository;
    @Mock private WaiterTableRepository waiterTableRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private JwtUtil jwtUtil;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private com.elcafe.modules.auth.service.LoginAttemptService loginAttemptService;

    @InjectMocks private WaiterService waiterService;

    private Waiter waiter;
    private RestaurantTable table;

    @BeforeEach
    void setUp() {
        waiter = createWaiter();
        table = createTable();
    }

    // ==================== getAllWaiters ====================

    @Test
    void getAllWaiters_returnsPagedResults() {
        Page<Waiter> page = new PageImpl<>(List.of(waiter));
        when(waiterRepository.findAll(any(PageRequest.class))).thenReturn(page);
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        Page<WaiterResponse> result = waiterService.getAllWaiters(PageRequest.of(0, 10));
        assertEquals(1, result.getTotalElements());
        assertEquals(waiter.getName(), result.getContent().get(0).getName());
    }

    @Test
    void getAllWaiters_returnsEmptyPage() {
        when(waiterRepository.findAll(any(PageRequest.class))).thenReturn(Page.empty());
        assertEquals(0, waiterService.getAllWaiters(PageRequest.of(0, 10)).getTotalElements());
    }

    // ==================== getById ====================

    @Test
    void getById_returnsWaiter() {
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(1L)).thenReturn(2L);

        WaiterResponse result = waiterService.getById(1L);
        assertEquals(waiter.getName(), result.getName());
        assertEquals(2, result.getActiveTablesCount());
    }

    @Test
    void getById_notFound_throws() {
        when(waiterRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> waiterService.getById(99L));
    }

    // ==================== getActiveWaiters ====================

    @Test
    void getActiveWaiters_returnsActiveWaiters() {
        when(waiterRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(waiter));
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        List<WaiterResponse> result = waiterService.getActiveWaiters();
        assertEquals(1, result.size());
    }

    // ==================== createWaiter ====================

    @Test
    void createWaiter_success() {
        CreateWaiterRequest request = createWaiterRequest("New Waiter", "9999");
        when(restaurantAuthorizationService.getCurrentUserRestaurantId()).thenReturn(1L);
        when(waiterRepository.existsByRestaurantIdAndPinCode(1L, "9999")).thenReturn(false);
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> {
            Waiter w = i.getArgument(0); w.setId(2L); return w;
        });
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        WaiterResponse result = waiterService.createWaiter(request);
        assertEquals("New Waiter", result.getName());
        verify(waiterRepository).save(any(Waiter.class));
    }

    @Test
    void createWaiter_duplicatePin_throws() {
        CreateWaiterRequest request = createWaiterRequest("Dup", "1234");
        when(restaurantAuthorizationService.getCurrentUserRestaurantId()).thenReturn(1L);
        when(waiterRepository.existsByRestaurantIdAndPinCode(1L, "1234")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> waiterService.createWaiter(request));
        verify(waiterRepository, never()).save(any());
    }

    @Test
    void createWaiter_duplicateEmail_throws() {
        CreateWaiterRequest request = createWaiterRequest("Dup", "5555");
        request.setEmail("taken@test.com");
        when(restaurantAuthorizationService.getCurrentUserRestaurantId()).thenReturn(1L);
        when(waiterRepository.existsByRestaurantIdAndPinCode(1L, "5555")).thenReturn(false);
        when(waiterRepository.existsByRestaurantIdAndEmail(1L, "taken@test.com")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> waiterService.createWaiter(request));
    }

    // ==================== updateWaiter ====================

    @Test
    void updateWaiter_updatesName() {
        UpdateWaiterRequest request = new UpdateWaiterRequest();
        request.setName("Updated Name");
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        WaiterResponse result = waiterService.updateWaiter(1L, request);
        assertEquals("Updated Name", result.getName());
    }

    @Test
    void updateWaiter_duplicatePin_throws() {
        UpdateWaiterRequest request = new UpdateWaiterRequest();
        request.setPinCode("9999");
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterRepository.existsByRestaurantIdAndPinCode(1L, "9999")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> waiterService.updateWaiter(1L, request));
    }

    @Test
    void updateWaiter_pinChange_bumpsTokenVersion() {
        UpdateWaiterRequest request = new UpdateWaiterRequest();
        request.setPinCode("9999");
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterRepository.existsByRestaurantIdAndPinCode(1L, "9999")).thenReturn(false);
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        waiterService.updateWaiter(1L, request);
        // §3.5: a new PIN revokes sessions opened with the old one (0 -> 1).
        assertEquals(1, waiter.getTokenVersion().intValue());
    }

    @Test
    void updateWaiter_deactivate_bumpsTokenVersion() {
        UpdateWaiterRequest request = new UpdateWaiterRequest();
        request.setActive(false);
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        waiterService.updateWaiter(1L, request);
        assertEquals(1, waiter.getTokenVersion().intValue());
    }

    @Test
    void updateWaiter_nameOnly_doesNotBumpTokenVersion() {
        UpdateWaiterRequest request = new UpdateWaiterRequest();
        request.setName("Renamed");
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterRepository.save(any(Waiter.class))).thenAnswer(i -> i.getArgument(0));
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        waiterService.updateWaiter(1L, request);
        // A non-credential edit must not log the waiter out.
        assertEquals(0, waiter.getTokenVersion().intValue());
    }

    @Test
    void updateWaiter_notFound_throws() {
        when(waiterRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> waiterService.updateWaiter(99L, new UpdateWaiterRequest()));
    }

    // ==================== deleteWaiter ====================

    @Test
    void deleteWaiter_unassignsTablesAndDeletes() {
        WaiterTable assignment = WaiterTable.builder().waiter(waiter).table(table).active(true).build();
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(waiterTableRepository.findByWaiterIdAndActiveTrue(1L)).thenReturn(List.of(assignment));

        waiterService.deleteWaiter(1L);

        assertFalse(assignment.getActive());
        assertFalse(waiter.getActive());
        // §3.5: soft-delete (deactivation) bumps the token version to revoke outstanding tokens.
        assertEquals(1, waiter.getTokenVersion().intValue());
        verify(waiterTableRepository).saveAll(anyList());
        // deleteWaiter is a SOFT delete (mark inactive + save) to preserve linked history —
        // see WaiterService.deleteWaiter. (Was asserting a hard delete() that never happens.)
        verify(waiterRepository).save(waiter);
    }

    @Test
    void deleteWaiter_notFound_throws() {
        when(waiterRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> waiterService.deleteWaiter(99L));
    }

    // ==================== authenticate ====================

    @Test
    void authenticate_validPin_returnsToken() {
        WaiterAuthRequest request = new WaiterAuthRequest();
        request.setRestaurantId(1L);
        request.setPinCode("1234");
        when(waiterRepository.findByRestaurantIdAndPinCode(1L, "1234")).thenReturn(Optional.of(waiter));
        when(jwtUtil.generateWaiterAccessToken(anyString(), anyLong(), anyString(), any(), anyInt())).thenReturn("jwt-token");
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        WaiterAuthResponse result = waiterService.authenticate(request);
        assertEquals("jwt-token", result.getToken());
        assertEquals(waiter.getId(), result.getWaiterId());
    }

    @Test
    void authenticate_invalidPin_throws() {
        WaiterAuthRequest request = new WaiterAuthRequest();
        request.setRestaurantId(1L);
        request.setPinCode("0000");
        when(waiterRepository.findByRestaurantIdAndPinCode(1L, "0000")).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> waiterService.authenticate(request));
    }

    @Test
    void authenticate_inactiveWaiter_throws() {
        waiter.setActive(false);
        WaiterAuthRequest request = new WaiterAuthRequest();
        request.setRestaurantId(1L);
        request.setPinCode("1234");
        when(waiterRepository.findByRestaurantIdAndPinCode(1L, "1234")).thenReturn(Optional.of(waiter));
        assertThrows(BadRequestException.class, () -> waiterService.authenticate(request));
    }

    @Test
    void authenticate_usesEmailAsIdentifier() {
        waiter.setEmail("waiter@test.com");
        WaiterAuthRequest request = new WaiterAuthRequest();
        request.setRestaurantId(1L);
        request.setPinCode("1234");
        when(waiterRepository.findByRestaurantIdAndPinCode(1L, "1234")).thenReturn(Optional.of(waiter));
        when(jwtUtil.generateWaiterAccessToken(eq("waiter@test.com"), anyLong(), anyString(), any(), anyInt())).thenReturn("tok");
        when(waiterTableRepository.countByWaiterIdAndActiveTrue(anyLong())).thenReturn(0L);

        waiterService.authenticate(request);
        verify(jwtUtil).generateWaiterAccessToken(eq("waiter@test.com"), eq(1L), eq("WAITER"), any(), eq(0));
    }

    // ==================== assignToTable ====================

    @Test
    void assignToTable_success() {
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(tableRepository.findById(1L)).thenReturn(Optional.of(table));
        when(waiterTableRepository.findByTableIdAndActiveTrue(1L)).thenReturn(Optional.empty());

        waiterService.assignToTable(1L, 1L);

        ArgumentCaptor<WaiterTable> captor = ArgumentCaptor.forClass(WaiterTable.class);
        verify(waiterTableRepository).save(captor.capture());
        assertTrue(captor.getValue().getActive());
    }

    @Test
    void assignToTable_waiterNotFound_throws() {
        when(waiterRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> waiterService.assignToTable(99L, 1L));
    }

    @Test
    void assignToTable_tableNotFound_throws() {
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(tableRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> waiterService.assignToTable(1L, 99L));
    }

    @Test
    void assignToTable_alreadyAssigned_throws() {
        when(waiterRepository.findById(1L)).thenReturn(Optional.of(waiter));
        when(tableRepository.findById(1L)).thenReturn(Optional.of(table));
        when(waiterTableRepository.findByTableIdAndActiveTrue(1L))
                .thenReturn(Optional.of(WaiterTable.builder().active(true).build()));
        assertThrows(BadRequestException.class, () -> waiterService.assignToTable(1L, 1L));
    }

    // ==================== unassignFromTable ====================

    @Test
    void unassignFromTable_success() {
        WaiterTable assignment = WaiterTable.builder().waiter(waiter).table(table).active(true).build();
        when(waiterTableRepository.findByWaiterIdAndTableIdAndActiveTrue(1L, 1L))
                .thenReturn(Optional.of(assignment));
        waiterService.unassignFromTable(1L, 1L);
        verify(waiterTableRepository).save(assignment);
    }

    // ==================== getActiveTables ====================

    @Test
    void getActiveTables_returnsSorted() {
        RestaurantTable table2 = createTable(2L, "T2", RestaurantTable.TableStatus.OCCUPIED);
        WaiterTable wt1 = WaiterTable.builder().waiter(waiter).table(table).active(true).build();
        WaiterTable wt2 = WaiterTable.builder().waiter(waiter).table(table2).active(true).build();
        when(waiterTableRepository.findByWaiterIdAndActiveTrue(1L)).thenReturn(List.of(wt2, wt1));

        List<RestaurantTable> result = waiterService.getActiveTables(1L);
        assertEquals(2, result.size());
        assertEquals("T1", result.get(0).getTableNumber());
        assertEquals("T2", result.get(1).getTableNumber());
    }
}
