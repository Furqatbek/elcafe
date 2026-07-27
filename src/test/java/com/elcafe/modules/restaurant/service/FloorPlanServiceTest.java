package com.elcafe.modules.restaurant.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.dto.FloorLayoutRequest;
import com.elcafe.modules.restaurant.dto.FloorPlanView;
import com.elcafe.modules.restaurant.entity.FloorObject;
import com.elcafe.modules.restaurant.entity.FloorPlan;
import com.elcafe.modules.restaurant.entity.FloorSection;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.FloorObjectRepository;
import com.elcafe.modules.restaurant.repository.FloorPlanRepository;
import com.elcafe.modules.restaurant.repository.FloorSectionRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The live floor map. Three things carry real risk here, and each has its own group below.
 *
 * <p><b>Occupancy</b> is derived from open orders rather than from {@code RestaurantTable.status},
 * because the stored status is set by hand and drifts. If that derivation is wrong the room lies to the
 * host — a paid-up table still showing red is how guests get turned away from an empty restaurant.
 *
 * <p><b>Tenant scoping</b> — a floor map is a picture of somebody's actual premises, so another
 * restaurant's plan id must read as absent, and a table from another restaurant must not be movable.
 *
 * <p><b>Geometry validation</b> — a zero-width table renders as an invisible, unclickable shape, which
 * looks exactly like a table that vanished.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FloorPlanServiceTest {

    private static final Long TENANT = 4L;
    private static final Long OTHER_TENANT = 88L;
    private static final Long PLAN = 11L;

    @Mock private FloorPlanRepository floorPlanRepository;
    @Mock private FloorObjectRepository floorObjectRepository;
    @Mock private FloorSectionRepository floorSectionRepository;
    @Mock private RestaurantTableRepository tableRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private RestaurantAuthorizationService restaurantAuthorizationService;
    @Mock private FloorEventBroadcaster floorEventBroadcaster;

    @InjectMocks private FloorPlanService service;

    private FloorPlan plan;

    @BeforeEach
    void setUp() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(TENANT);
        when(restaurantAuthorizationService.currentTenantScopeStrict()).thenReturn(TENANT);

        plan = FloorPlan.builder()
                .id(PLAN).restaurantId(TENANT).name("Main floor")
                .displayOrder(0).isDefault(true).canvasWidth(1200).canvasHeight(800).active(true)
                .build();

        when(floorPlanRepository.findByIdAndRestaurantId(PLAN, TENANT)).thenReturn(Optional.of(plan));
        when(floorPlanRepository.findByRestaurantIdAndIsDefaultTrue(TENANT)).thenReturn(Optional.of(plan));
        when(floorSectionRepository.findByFloorPlanIdOrderByZIndexAscIdAsc(PLAN)).thenReturn(List.of());
        when(floorObjectRepository.findByFloorPlanIdOrderByZIndexAscIdAsc(PLAN)).thenReturn(List.of());
        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN)).thenReturn(List.of());
        when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(anyLong(), any()))
                .thenReturn(List.of());
        when(tableRepository.save(any(RestaurantTable.class))).thenAnswer(i -> i.getArgument(0));
        when(floorPlanRepository.save(any(FloorPlan.class))).thenAnswer(i -> i.getArgument(0));
    }

    // -------------------------------------------------------------------------------------------
    // Occupancy — who is sitting where
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a table with an open order is occupied, and names the guest")
    void openOrderMakesTableOccupied() {
        RestaurantTable table = table(5L, "12");
        Order order = order(900L, "ORD-900", table, OrderStatus.PREPARING, at(19, 0));
        order.setCustomer(customer(31L, "Nodira", "Karimova"));

        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN))
                .thenReturn(List.of(table));
        when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(eq(TENANT), any()))
                .thenReturn(List.of(order));

        FloorPlanView.TableView view = service.getPlan(PLAN).getTables().get(0);

        assertThat(view.getOccupancy()).isNotNull();
        assertThat(view.getOccupancy().getOrderId()).isEqualTo(900L);
        assertThat(view.getOccupancy().getOrderNumber()).isEqualTo("ORD-900");
        assertThat(view.getOccupancy().getSeatedSince()).isEqualTo(at(19, 0));
        assertThat(view.getOccupancy().getCustomerId()).isEqualTo(31L);
        assertThat(view.getOccupancy().getCustomerName()).isEqualTo("Nodira Karimova");
    }

    /**
     * The whole reason occupancy is derived rather than read from {@code RestaurantTable.status}. A
     * table left marked OCCUPIED after the bill closed is the exact failure this replaces: the query
     * only returns open orders, so the map shows it free regardless of the stale stored status.
     */
    @Test
    @DisplayName("a table whose stored status says OCCUPIED still shows free with no open order")
    void staleStoredStatusDoesNotFakeOccupancy() {
        RestaurantTable table = table(5L, "12");
        table.setStatus(RestaurantTable.TableStatus.OCCUPIED);

        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN))
                .thenReturn(List.of(table));
        when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(eq(TENANT), any()))
                .thenReturn(List.of()); // bill was paid; nothing open

        FloorPlanView.TableView view = service.getPlan(PLAN).getTables().get(0);

        assertThat(view.getStatus()).isEqualTo("OCCUPIED");  // stored status still reported, for context
        assertThat(view.getOccupancy()).isNull();            // but the map draws it free
    }

    /** A walk-in ordered from a waiter: there is no customer record, and the drawer must not invent one. */
    @Test
    @DisplayName("a walk-in occupies the table with no customer attached")
    void walkInHasNoCustomer() {
        RestaurantTable table = table(5L, "12");
        Order order = order(901L, "ORD-901", table, OrderStatus.NEW, at(20, 0));

        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN))
                .thenReturn(List.of(table));
        when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(eq(TENANT), any()))
                .thenReturn(List.of(order));

        FloorPlanView.Occupancy occupancy = service.getPlan(PLAN).getTables().get(0).getOccupancy();

        assertThat(occupancy).isNotNull();
        assertThat(occupancy.getOrderId()).isEqualTo(901L);
        assertThat(occupancy.getCustomerId()).isNull();
        assertThat(occupancy.getCustomerName()).isNull();
    }

    /**
     * A party that orders a second round has two open orders on one table. "Seated since" must keep
     * pointing at when they actually sat down, not jump forward every time they order again — the host
     * uses that number to decide how long a table has been held.
     *
     * <p>Asserted for BOTH row orderings on purpose. The repository makes no ordering promise, and a
     * merge that just keeps whichever row arrives second passes half the time — which is exactly how a
     * "first/last wins" bug survives a green suite until production hands it the other order.
     */
    @Test
    @DisplayName("with two open orders on one table, the earliest wins whichever row comes back first")
    void earliestOpenOrderWins() {
        RestaurantTable table = table(5L, "12");
        Order earlier = order(902L, "ORD-902", table, OrderStatus.PREPARING, at(18, 30));
        Order later = order(903L, "ORD-903", table, OrderStatus.NEW, at(19, 45));

        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN))
                .thenReturn(List.of(table));

        for (List<Order> rows : List.of(List.of(earlier, later), List.of(later, earlier))) {
            when(orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(eq(TENANT), any()))
                    .thenReturn(rows);

            FloorPlanView.Occupancy occupancy = service.getPlan(PLAN).getTables().get(0).getOccupancy();

            assertThat(occupancy.getOrderId())
                    .as("row order %s must not change which order is treated as the seating",
                            rows.stream().map(Order::getOrderNumber).toList())
                    .isEqualTo(902L);
            assertThat(occupancy.getSeatedSince()).isEqualTo(at(18, 30));
        }
    }

    /** Sixty tables must still cost one query, or a live view refreshing every few seconds melts the DB. */
    @Test
    @DisplayName("occupancy for the whole room is resolved in a single query")
    void occupancyIsOneQueryForTheWholeRoom() {
        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN))
                .thenReturn(List.of(table(1L, "1"), table(2L, "2"), table(3L, "3")));

        service.getPlan(PLAN);

        verify(orderRepository, org.mockito.Mockito.times(1))
                .findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(eq(TENANT), any());
    }

    @Test
    @DisplayName("a merged table exposes its parent so the renderer draws one seating unit")
    void mergedTableExposesParent() {
        RestaurantTable parent = table(5L, "12");
        RestaurantTable child = table(6L, "13");
        child.setMergedTable(parent);

        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(PLAN))
                .thenReturn(List.of(child));

        assertThat(service.getPlan(PLAN).getTables().get(0).getMergedIntoTableId()).isEqualTo(5L);
    }

    // -------------------------------------------------------------------------------------------
    // Tenant scoping
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("another restaurant's plan id reads as absent, not as forbidden")
    void otherTenantsPlanIsNotFound() {
        when(floorPlanRepository.findByIdAndRestaurantId(777L, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(777L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * The layout request carries raw table ids. Without a tenant check on each one, a neighbouring
     * restaurant's table could be dragged across the wall into this map.
     */
    @Test
    @DisplayName("a table belonging to another restaurant cannot be placed on this map")
    void cannotPlaceAnotherRestaurantsTable() {
        RestaurantTable foreign = table(70L, "A1");
        foreign.setRestaurant(restaurant(OTHER_TENANT));
        when(tableRepository.findById(70L)).thenReturn(Optional.of(foreign));

        FloorLayoutRequest request = FloorLayoutRequest.builder()
                .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                        .id(70L).positionX(10).positionY(10).build()))
                .build();

        assertThatThrownBy(() -> service.saveLayout(PLAN, request))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(tableRepository, never()).save(any());
    }

    @Test
    @DisplayName("a caller with no restaurant is refused rather than shown every restaurant's room")
    void tenantlessCallerIsRefused() {
        when(restaurantAuthorizationService.currentTenantReadScopeStrict()).thenReturn(null);

        assertThatThrownBy(() -> service.listPlans())
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("restaurant");
    }

    // -------------------------------------------------------------------------------------------
    // Geometry validation
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a zero-width table is refused — it would render as an invisible, unclickable shape")
    void zeroWidthIsRefused() {
        RestaurantTable table = table(5L, "12");
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        FloorLayoutRequest request = FloorLayoutRequest.builder()
                .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                        .id(5L).width(0).build()))
                .build();

        assertThatThrownBy(() -> service.saveLayout(PLAN, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("width");
    }

    @Test
    @DisplayName("an absurdly large table is refused rather than stored and blowing up the canvas")
    void oversizeIsRefused() {
        RestaurantTable table = table(5L, "12");
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        FloorLayoutRequest request = FloorLayoutRequest.builder()
                .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                        .id(5L).height(99_999).build()))
                .build();

        assertThatThrownBy(() -> service.saveLayout(PLAN, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("height");
    }

    /** A drag past a full turn is a legitimate gesture — 370° simply means 10°, so it wraps, not fails. */
    @Test
    @DisplayName("rotation wraps instead of being rejected")
    void rotationWraps() {
        RestaurantTable table = table(5L, "12");
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        service.saveLayout(PLAN, FloorLayoutRequest.builder()
                .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                        .id(5L).rotationDeg(370).build()))
                .build());
        assertThat(table.getRotationDeg()).isEqualTo(10);

        service.saveLayout(PLAN, FloorLayoutRequest.builder()
                .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                        .id(5L).rotationDeg(-90).build()))
                .build());
        assertThat(table.getRotationDeg()).isEqualTo(270);
    }

    @Test
    @DisplayName("an unknown shape is refused with the valid ones named")
    void unknownShapeIsRefused() {
        RestaurantTable table = table(5L, "12");
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        assertThatThrownBy(() -> service.saveLayout(PLAN, FloorLayoutRequest.builder()
                .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                        .id(5L).shape("TRIANGLE").build()))
                .build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("OVAL");
    }

    @Test
    @DisplayName("the corner shapes the operator asked for all round-trip")
    void cornerShapesRoundTrip() {
        RestaurantTable table = table(5L, "12");
        when(tableRepository.findById(5L)).thenReturn(Optional.of(table));

        for (FloorObject.Shape shape : FloorObject.Shape.values()) {
            service.saveLayout(PLAN, FloorLayoutRequest.builder()
                    .tables(List.of(FloorLayoutRequest.TablePlacement.builder()
                            .id(5L).shape(shape.name().toLowerCase()).build())) // case-insensitive
                    .build());
            assertThat(table.getShape()).isEqualTo(shape);
        }
    }

    /** Two points are a line: it would draw as nothing and be impossible to click, so it is not stored. */
    @Test
    @DisplayName("a section with fewer than three points is refused")
    void degenerateSectionIsRefused() {
        FloorLayoutRequest request = FloorLayoutRequest.builder()
                .sections(List.of(FloorLayoutRequest.SectionShape.builder()
                        .name("Bar corner")
                        .polygon(List.of(Map.of("x", 0, "y", 0), Map.of("x", 10, "y", 10)))
                        .build()))
                .build();

        assertThatThrownBy(() -> service.saveLayout(PLAN, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("three points");
        verify(floorSectionRepository, never()).save(any());
    }

    @Test
    @DisplayName("an unnamed section is refused — an unlabelled area cannot be identified on the map")
    void unnamedSectionIsRefused() {
        FloorLayoutRequest request = FloorLayoutRequest.builder()
                .sections(List.of(FloorLayoutRequest.SectionShape.builder()
                        .name("  ")
                        .polygon(List.of(Map.of("x", 0, "y", 0), Map.of("x", 10, "y", 0),
                                Map.of("x", 10, "y", 10)))
                        .build()))
                .build();

        assertThatThrownBy(() -> service.saveLayout(PLAN, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("name");
    }

    // -------------------------------------------------------------------------------------------
    // Save semantics
    // -------------------------------------------------------------------------------------------

    /**
     * Tables outlive the map — they carry orders, QR codes and history — so one omitted from the
     * request must be left exactly as it is. Furniture does not, so an omitted object was deleted.
     */
    @Test
    @DisplayName("omitted tables are untouched while omitted furniture is deleted")
    void tablesUpdateInPlaceButObjectsReplaceWholesale() {
        when(floorObjectRepository.save(any(FloorObject.class))).thenAnswer(i -> i.getArgument(0));

        service.saveLayout(PLAN, FloorLayoutRequest.builder()
                .objects(List.of(FloorLayoutRequest.ObjectPlacement.builder()
                        .objectType("SOFA").positionX(40).positionY(50).build()))
                .build());

        verify(tableRepository, never()).save(any());           // no tables in the request
        verify(floorObjectRepository).deleteByFloorPlanId(PLAN); // old furniture cleared
        verify(floorObjectRepository).save(any(FloorObject.class));
        verify(floorSectionRepository, never()).deleteByFloorPlanId(anyLong()); // sections omitted entirely
    }

    @Test
    @DisplayName("a saved section keeps its polygon verbatim")
    void sectionPolygonIsStoredVerbatim() {
        when(floorSectionRepository.save(any(FloorSection.class))).thenAnswer(i -> i.getArgument(0));
        List<Map<String, Integer>> polygon = List.of(
                Map.of("x", 0, "y", 0), Map.of("x", 200, "y", 0), Map.of("x", 200, "y", 120));

        service.saveLayout(PLAN, FloorLayoutRequest.builder()
                .sections(List.of(FloorLayoutRequest.SectionShape.builder()
                        .name(" Bar corner ").polygon(polygon).fillColor("#f0e6d2").build()))
                .build());

        ArgumentCaptor<FloorSection> saved = ArgumentCaptor.forClass(FloorSection.class);
        verify(floorSectionRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Bar corner");
        assertThat(saved.getValue().getPolygon()).isEqualTo(polygon);
        assertThat(saved.getValue().getRestaurantId()).isEqualTo(TENANT);
        assertThat(saved.getValue().getFloorPlanId()).isEqualTo(PLAN);
    }

    /** Without this, a manager rearranges the room and every other tablet keeps showing yesterday's map. */
    @Test
    @DisplayName("saving a layout tells every other watcher to repaint")
    void savingBroadcasts() {
        service.saveLayout(PLAN, FloorLayoutRequest.builder().build());
        verify(floorEventBroadcaster).broadcastLayoutChanged(TENANT, PLAN);
    }

    // -------------------------------------------------------------------------------------------
    // Managing maps
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("the first map a restaurant has becomes its default; later ones do not steal the flag")
    void onlyTheFirstMapIsDefault() {
        when(floorPlanRepository.findByRestaurantIdAndIsDefaultTrue(TENANT)).thenReturn(Optional.of(plan));

        FloorPlanView created = service.createPlan(
                FloorPlanView.builder().name("Terrace").build());

        assertThat(created.getIsDefault()).isFalse();
    }

    @Test
    @DisplayName("a duplicate map name is refused")
    void duplicateMapNameIsRefused() {
        when(floorPlanRepository.existsByRestaurantIdAndName(TENANT, "Terrace")).thenReturn(true);

        assertThatThrownBy(() -> service.createPlan(FloorPlanView.builder().name("Terrace").build()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("already exists");
    }

    /** The page has nothing to draw with zero maps, and the tables would have no default to return to. */
    @Test
    @DisplayName("the last remaining map cannot be deleted")
    void lastMapCannotBeDeleted() {
        when(floorPlanRepository.findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(TENANT))
                .thenReturn(List.of(plan));

        assertThatThrownBy(() -> service.deletePlan(PLAN))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("at least one map");
        verify(floorPlanRepository, never()).delete(any());
    }

    /**
     * Tables are real seating with orders behind them. Deleting a terrace must unplace them, never
     * cascade them away — V184 nulls floor_plan_id for exactly this reason.
     */
    @Test
    @DisplayName("deleting a map unplaces its tables instead of deleting them")
    void deletingAMapUnplacesItsTables() {
        FloorPlan terrace = FloorPlan.builder()
                .id(12L).restaurantId(TENANT).name("Terrace").isDefault(false).active(true).build();
        when(floorPlanRepository.findByIdAndRestaurantId(12L, TENANT)).thenReturn(Optional.of(terrace));
        when(floorPlanRepository.findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(TENANT))
                .thenReturn(List.of(plan, terrace));

        RestaurantTable table = table(5L, "T1");
        table.setFloorPlanId(12L);
        when(tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(12L))
                .thenReturn(List.of(table));

        service.deletePlan(12L);

        assertThat(table.getFloorPlanId()).isNull();
        verify(tableRepository).save(table);
        verify(tableRepository, never()).delete(any());
        verify(floorObjectRepository).deleteByFloorPlanId(12L);
        verify(floorPlanRepository).delete(terrace);
    }

    // -------------------------------------------------------------------------------------------
    // Fixtures
    // -------------------------------------------------------------------------------------------

    private static OffsetDateTime at(int hour, int minute) {
        return OffsetDateTime.of(2026, 7, 20, hour, minute, 0, 0, ZoneOffset.UTC);
    }

    private RestaurantTable table(Long id, String number) {
        return RestaurantTable.builder()
                .id(id)
                .restaurant(restaurant(TENANT))
                .tableNumber(number)
                .capacity(4)
                .status(RestaurantTable.TableStatus.AVAILABLE)
                .shape(FloorObject.Shape.RECTANGLE)
                .rotationDeg(0)
                .zIndex(0)
                .floorPlanId(PLAN)
                .positionX(0).positionY(0).width(100).height(100)
                .active(true)
                .build();
    }

    private static Restaurant restaurant(Long id) {
        Restaurant r = new Restaurant();
        r.setId(id);
        return r;
    }

    private static Order order(Long id, String number, RestaurantTable table,
                               OrderStatus status, OffsetDateTime placedAt) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNumber(number);
        order.setDiningTable(table);
        order.setStatus(status);
        order.setPlacedAt(placedAt);
        order.setTotal(new BigDecimal("125000"));
        order.setRestaurant(restaurant(TENANT));
        return order;
    }

    private static Customer customer(Long id, String first, String last) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName(first);
        c.setLastName(last);
        return c;
    }
}
