package com.elcafe.modules.restaurant.service;

import com.elcafe.common.security.service.RestaurantAuthorizationService;
import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.order.enums.OrderStatus;
import com.elcafe.modules.order.repository.OrderRepository;
import com.elcafe.modules.restaurant.dto.FloorLayoutRequest;
import com.elcafe.modules.restaurant.dto.FloorPlanView;
import com.elcafe.modules.restaurant.entity.FloorObject;
import com.elcafe.modules.restaurant.entity.FloorPlan;
import com.elcafe.modules.restaurant.entity.FloorSection;
import com.elcafe.modules.restaurant.entity.RestaurantTable;
import com.elcafe.modules.restaurant.repository.FloorObjectRepository;
import com.elcafe.modules.restaurant.repository.FloorPlanRepository;
import com.elcafe.modules.restaurant.repository.FloorSectionRepository;
import com.elcafe.modules.restaurant.repository.RestaurantTableRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The live floor map (V184): what the room looks like, and who is sitting in it.
 *
 * <p>Two responsibilities that deliberately stay apart. <b>Reading</b> is open to the restaurant's staff
 * — anyone working a service needs to see the room. <b>Writing the layout</b> is restricted to owners and
 * managers at the controller, because moving furniture mid-service is not something a waiter should be
 * able to do by dragging on a tablet.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FloorPlanService {

    /**
     * An order in one of these states means somebody is sitting there. Deliberately excludes COMPLETED,
     * DELIVERED, CANCELLED and REJECTED — those are finished, and a table still showing occupied after
     * the bill is paid is how a host turns guests away from an empty room.
     */
    private static final List<OrderStatus> OCCUPYING_STATUSES = List.of(
            OrderStatus.NEW, OrderStatus.PLACED, OrderStatus.ACCEPTED,
            OrderStatus.PREPARING, OrderStatus.READY);

    /** Geometry bounds. Wide enough for any real room, tight enough that a bad drag cannot store nonsense. */
    private static final int MAX_DIMENSION = 5000;
    private static final int MIN_DIMENSION = 1;

    private final FloorPlanRepository floorPlanRepository;
    private final FloorObjectRepository floorObjectRepository;
    private final FloorSectionRepository floorSectionRepository;
    private final RestaurantTableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final RestaurantAuthorizationService restaurantAuthorizationService;
    private final FloorEventBroadcaster floorEventBroadcaster;

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    /** The map switcher's list. Every restaurant has at least one plan — V184 gave them all a default. */
    @Transactional(readOnly = true)
    public List<FloorPlanView> listPlans() {
        Long tenant = requireTenant();
        return floorPlanRepository.findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(tenant)
                .stream()
                .map(plan -> FloorPlanView.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .displayOrder(plan.getDisplayOrder())
                        .isDefault(plan.getIsDefault())
                        .canvasWidth(plan.getCanvasWidth())
                        .canvasHeight(plan.getCanvasHeight())
                        .build())
                .toList();
    }

    /**
     * One map with everything on it and live occupancy.
     *
     * @param planId the map to open, or null for the restaurant's default.
     */
    @Transactional(readOnly = true)
    public FloorPlanView getPlan(Long planId) {
        Long tenant = requireTenant();
        FloorPlan plan = planId == null
                ? floorPlanRepository.findByRestaurantIdAndIsDefaultTrue(tenant)
                        .orElseThrow(() -> new ResourceNotFoundException("Floor plan", "restaurantId", tenant))
                : floorPlanRepository.findByIdAndRestaurantId(planId, tenant)
                        .orElseThrow(() -> new ResourceNotFoundException("Floor plan", "id", planId));

        List<RestaurantTable> tables =
                tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(plan.getId());

        Map<Long, Order> occupancyByTable = liveOccupancy(tenant);

        return FloorPlanView.builder()
                .id(plan.getId())
                .name(plan.getName())
                .displayOrder(plan.getDisplayOrder())
                .isDefault(plan.getIsDefault())
                .canvasWidth(plan.getCanvasWidth())
                .canvasHeight(plan.getCanvasHeight())
                .sections(floorSectionRepository.findByFloorPlanIdOrderByZIndexAscIdAsc(plan.getId())
                        .stream().map(this::toSectionView).toList())
                .objects(floorObjectRepository.findByFloorPlanIdOrderByZIndexAscIdAsc(plan.getId())
                        .stream().map(this::toObjectView).toList())
                .tables(tables.stream()
                        .map(t -> toTableView(t, occupancyByTable.get(t.getId())))
                        .toList())
                .build();
    }

    /**
     * Which tables currently have somebody at them, resolved in ONE query for the whole restaurant
     * rather than per table — a room with sixty tables would otherwise cost sixty round trips every
     * time the map refreshes, which for a live view is every few seconds.
     *
     * <p>When a table somehow carries more than one open order, the earliest wins: that is the party
     * that has been sitting there, and "seated since" should not jump forward when they order again.
     */
    private Map<Long, Order> liveOccupancy(Long restaurantId) {
        List<Order> open = orderRepository.findByRestaurant_IdAndDiningTableIsNotNullAndStatusIn(
                restaurantId, OCCUPYING_STATUSES);

        return open.stream()
                .filter(o -> o.getDiningTable() != null)
                .collect(Collectors.toMap(
                        o -> o.getDiningTable().getId(),
                        Function.identity(),
                        (a, b) -> seatedAt(a).isBefore(seatedAt(b)) ? a : b));
    }

    private static java.time.OffsetDateTime seatedAt(Order order) {
        return order.getPlacedAt() != null ? order.getPlacedAt() : order.getCreatedAt();
    }

    // ---------------------------------------------------------------------------------------------
    // Writing the layout
    // ---------------------------------------------------------------------------------------------

    /**
     * Save a whole map's layout in one transaction.
     *
     * <p>Tables are updated in place — they exist independently of the map, so one omitted from the
     * request is left alone rather than deleted. Objects and sections are replaced wholesale, because
     * furniture and drawn areas have no life outside the plan: absent from the request means the editor
     * deleted it.
     */
    @Transactional
    public FloorPlanView saveLayout(Long planId, FloorLayoutRequest request) {
        Long tenant = requireWriteTenant();
        FloorPlan plan = floorPlanRepository.findByIdAndRestaurantId(planId, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Floor plan", "id", planId));

        if (request.getTables() != null) {
            for (FloorLayoutRequest.TablePlacement placement : request.getTables()) {
                RestaurantTable table = tableRepository.findById(placement.getId())
                        .filter(t -> t.getRestaurant() != null
                                && tenant.equals(t.getRestaurant().getId()))
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Table", "id", placement.getId()));
                applyTablePlacement(table, placement, plan.getId());
                tableRepository.save(table);
            }
        }

        if (request.getObjects() != null) {
            // Replace wholesale: simplest contract that makes deletion expressible, and the volume here
            // is a room's worth of furniture, not a dataset.
            floorObjectRepository.deleteByFloorPlanId(plan.getId());
            for (FloorLayoutRequest.ObjectPlacement placement : request.getObjects()) {
                floorObjectRepository.save(toObjectEntity(placement, plan, tenant));
            }
        }

        if (request.getSections() != null) {
            floorSectionRepository.deleteByFloorPlanId(plan.getId());
            for (FloorLayoutRequest.SectionShape shape : request.getSections()) {
                floorSectionRepository.save(toSectionEntity(shape, plan, tenant));
            }
        }

        log.info("Floor plan {} layout saved for restaurant {}", plan.getId(), tenant);
        // Every other tablet showing this room repaints itself. Without this, a manager rearranges the
        // floor and the hosts keep seating guests against yesterday's map until they reload.
        floorEventBroadcaster.broadcastLayoutChanged(tenant, plan.getId());
        return getPlan(plan.getId());
    }

    // ---------------------------------------------------------------------------------------------
    // Managing the maps themselves
    // ---------------------------------------------------------------------------------------------

    /** Add a floor, hall or terrace. The first plan a restaurant has is its default; later ones are not. */
    @Transactional
    public FloorPlanView createPlan(FloorPlanView request) {
        Long tenant = requireWriteTenant();
        String name = requireName(request.getName());
        if (floorPlanRepository.existsByRestaurantIdAndName(tenant, name)) {
            throw new BadRequestException("A map called \"" + name + "\" already exists.");
        }
        boolean hasDefault = floorPlanRepository.findByRestaurantIdAndIsDefaultTrue(tenant).isPresent();

        FloorPlan plan = floorPlanRepository.save(FloorPlan.builder()
                .restaurantId(tenant)
                .name(name)
                .displayOrder(request.getDisplayOrder() == null ? 0 : request.getDisplayOrder())
                // Only ever true when there is no default yet — the partial unique index in V184 would
                // reject a second one, and silently stealing the flag from an existing map is worse.
                .isDefault(!hasDefault)
                .canvasWidth(requireDimension(
                        request.getCanvasWidth() == null ? 1200 : request.getCanvasWidth(), "canvasWidth"))
                .canvasHeight(requireDimension(
                        request.getCanvasHeight() == null ? 800 : request.getCanvasHeight(), "canvasHeight"))
                .active(true)
                .build());

        log.info("Floor plan {} created for restaurant {}", plan.getId(), tenant);
        floorEventBroadcaster.broadcastLayoutChanged(tenant, plan.getId());
        return toPlanSummary(plan);
    }

    /**
     * Rename a map or change its canvas.
     *
     * <p>{@code isDefault} is honoured, but turning a plan into the default first clears the flag from
     * whichever plan holds it — V184's partial unique index allows exactly one per restaurant, so setting
     * the new one without clearing the old would fail the write outright.
     */
    @Transactional
    public FloorPlanView updatePlan(Long planId, FloorPlanView request) {
        Long tenant = requireWriteTenant();
        FloorPlan plan = floorPlanRepository.findByIdAndRestaurantId(planId, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Floor plan", "id", planId));

        if (request.getName() != null) {
            String name = requireName(request.getName());
            if (!name.equals(plan.getName())
                    && floorPlanRepository.existsByRestaurantIdAndName(tenant, name)) {
                throw new BadRequestException("A map called \"" + name + "\" already exists.");
            }
            plan.setName(name);
        }
        if (request.getDisplayOrder() != null) plan.setDisplayOrder(request.getDisplayOrder());
        if (request.getCanvasWidth() != null) {
            plan.setCanvasWidth(requireDimension(request.getCanvasWidth(), "canvasWidth"));
        }
        if (request.getCanvasHeight() != null) {
            plan.setCanvasHeight(requireDimension(request.getCanvasHeight(), "canvasHeight"));
        }
        if (Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(plan.getIsDefault())) {
            floorPlanRepository.findByRestaurantIdAndIsDefaultTrue(tenant).ifPresent(current -> {
                current.setIsDefault(false);
                floorPlanRepository.saveAndFlush(current); // flush before claiming the flag
            });
            plan.setIsDefault(true);
        }

        FloorPlan saved = floorPlanRepository.save(plan);
        floorEventBroadcaster.broadcastLayoutChanged(tenant, saved.getId());
        return toPlanSummary(saved);
    }

    /**
     * Remove a map. Its furniture and sections go with it (V184 cascades); its tables do not — they are
     * real seating with orders and history behind them, so they survive with {@code floor_plan_id} nulled
     * and can be placed on another map.
     *
     * <p>The last remaining map cannot be deleted: the page has nothing to draw without one, and a
     * restaurant with zero plans has no default for the tables to come back to.
     */
    @Transactional
    public void deletePlan(Long planId) {
        Long tenant = requireWriteTenant();
        FloorPlan plan = floorPlanRepository.findByIdAndRestaurantId(planId, tenant)
                .orElseThrow(() -> new ResourceNotFoundException("Floor plan", "id", planId));

        List<FloorPlan> all = floorPlanRepository
                .findByRestaurantIdAndActiveTrueOrderByDisplayOrderAscIdAsc(tenant);
        if (all.size() <= 1) {
            throw new BadRequestException("A restaurant needs at least one map. Rename this one instead.");
        }

        tableRepository.findByFloorPlanIdAndActiveTrueOrderByZIndexAscIdAsc(plan.getId())
                .forEach(table -> {
                    table.setFloorPlanId(null);
                    tableRepository.save(table);
                });
        floorObjectRepository.deleteByFloorPlanId(plan.getId());
        floorSectionRepository.deleteByFloorPlanId(plan.getId());
        floorPlanRepository.delete(plan);

        if (Boolean.TRUE.equals(plan.getIsDefault())) {
            // Something has to open by default, or the page 404s on next load.
            all.stream()
                    .filter(p -> !p.getId().equals(plan.getId()))
                    .findFirst()
                    .ifPresent(next -> {
                        next.setIsDefault(true);
                        floorPlanRepository.save(next);
                    });
        }

        log.info("Floor plan {} deleted for restaurant {}", planId, tenant);
        floorEventBroadcaster.broadcastLayoutChanged(tenant, planId);
    }

    private FloorPlanView toPlanSummary(FloorPlan plan) {
        return FloorPlanView.builder()
                .id(plan.getId())
                .name(plan.getName())
                .displayOrder(plan.getDisplayOrder())
                .isDefault(plan.getIsDefault())
                .canvasWidth(plan.getCanvasWidth())
                .canvasHeight(plan.getCanvasHeight())
                .build();
    }

    private static String requireName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BadRequestException("A map needs a name.");
        }
        String trimmed = raw.trim();
        if (trimmed.length() > 100) {
            throw new BadRequestException("Map name must be 100 characters or fewer.");
        }
        return trimmed;
    }

    private void applyTablePlacement(RestaurantTable table, FloorLayoutRequest.TablePlacement p,
                                     Long planId) {
        if (p.getPositionX() != null) table.setPositionX(p.getPositionX());
        if (p.getPositionY() != null) table.setPositionY(p.getPositionY());
        if (p.getWidth() != null) table.setWidth(requireDimension(p.getWidth(), "width"));
        if (p.getHeight() != null) table.setHeight(requireDimension(p.getHeight(), "height"));
        if (p.getRotationDeg() != null) table.setRotationDeg(normaliseRotation(p.getRotationDeg()));
        if (p.getShape() != null) table.setShape(parseShape(p.getShape()));
        if (p.getZIndex() != null) table.setZIndex(p.getZIndex());
        table.setFloorPlanId(planId);
    }

    private FloorObject toObjectEntity(FloorLayoutRequest.ObjectPlacement p, FloorPlan plan, Long tenant) {
        return FloorObject.builder()
                .restaurantId(tenant)
                .floorPlanId(plan.getId())
                .objectType(parseObjectType(p.getObjectType()))
                .label(p.getLabel())
                .positionX(p.getPositionX() == null ? 0 : p.getPositionX())
                .positionY(p.getPositionY() == null ? 0 : p.getPositionY())
                .width(requireDimension(p.getWidth() == null ? 60 : p.getWidth(), "width"))
                .height(requireDimension(p.getHeight() == null ? 60 : p.getHeight(), "height"))
                .rotationDeg(normaliseRotation(p.getRotationDeg() == null ? 0 : p.getRotationDeg()))
                .shape(p.getShape() == null ? FloorObject.Shape.RECTANGLE : parseShape(p.getShape()))
                .zIndex(p.getZIndex() == null ? 0 : p.getZIndex())
                .build();
    }

    private FloorSection toSectionEntity(FloorLayoutRequest.SectionShape s, FloorPlan plan, Long tenant) {
        if (s.getPolygon() == null || s.getPolygon().size() < 3) {
            // Fewer than three points is a line, not an area — it would render as nothing and be
            // impossible to click, so it is rejected rather than stored as an invisible section.
            throw new BadRequestException("A section needs at least three points.");
        }
        if (s.getName() == null || s.getName().isBlank()) {
            throw new BadRequestException("A section needs a name.");
        }
        return FloorSection.builder()
                .restaurantId(tenant)
                .floorPlanId(plan.getId())
                .name(s.getName().trim())
                .polygon(s.getPolygon())
                .fillColor(s.getFillColor())
                .zIndex(s.getZIndex() == null ? 0 : s.getZIndex())
                .build();
    }

    // ---------------------------------------------------------------------------------------------
    // Mapping + validation
    // ---------------------------------------------------------------------------------------------

    private FloorPlanView.TableView toTableView(RestaurantTable table, Order order) {
        return FloorPlanView.TableView.builder()
                .id(table.getId())
                .tableNumber(table.getTableNumber())
                .tableName(table.getTableName())
                .capacity(table.getCapacity())
                .section(table.getSection())
                .status(table.getStatus() != null ? table.getStatus().name() : null)
                .positionX(table.getPositionX())
                .positionY(table.getPositionY())
                .width(table.getWidth())
                .height(table.getHeight())
                .rotationDeg(table.getRotationDeg())
                .shape(table.getShape() != null ? table.getShape().name() : null)
                .zIndex(table.getZIndex())
                .mergedIntoTableId(table.getMergedTable() != null ? table.getMergedTable().getId() : null)
                .occupancy(order == null ? null : toOccupancy(order))
                .build();
    }

    private FloorPlanView.Occupancy toOccupancy(Order order) {
        return FloorPlanView.Occupancy.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .seatedSince(seatedAt(order))
                .orderTotal(order.getTotal())
                // Null for a walk-in: no customer record exists behind them, and the drawer shows the
                // order alone rather than inventing a guest.
                .customerId(order.getCustomer() != null ? order.getCustomer().getId() : null)
                .customerName(order.getCustomer() != null ? displayName(order.getCustomer()) : null)
                .build();
    }

    private static String displayName(com.elcafe.modules.customer.entity.Customer customer) {
        String first = customer.getFirstName() == null ? "" : customer.getFirstName();
        String last = customer.getLastName() == null ? "" : customer.getLastName();
        String name = (first + " " + last).trim();
        return name.isEmpty() ? ("#" + customer.getId()) : name;
    }

    private FloorPlanView.FloorObjectView toObjectView(FloorObject o) {
        return FloorPlanView.FloorObjectView.builder()
                .id(o.getId())
                .objectType(o.getObjectType() != null ? o.getObjectType().name() : null)
                .label(o.getLabel())
                .positionX(o.getPositionX())
                .positionY(o.getPositionY())
                .width(o.getWidth())
                .height(o.getHeight())
                .rotationDeg(o.getRotationDeg())
                .shape(o.getShape() != null ? o.getShape().name() : null)
                .zIndex(o.getZIndex())
                .build();
    }

    private FloorPlanView.Section toSectionView(FloorSection s) {
        return FloorPlanView.Section.builder()
                .id(s.getId())
                .name(s.getName())
                .polygon(s.getPolygon())
                .fillColor(s.getFillColor())
                .zIndex(s.getZIndex())
                .build();
    }

    /** A zero or negative size renders as an invisible, unclickable shape; an enormous one breaks the canvas. */
    private static int requireDimension(int value, String field) {
        if (value < MIN_DIMENSION || value > MAX_DIMENSION) {
            throw new BadRequestException(
                    field + " must be between " + MIN_DIMENSION + " and " + MAX_DIMENSION + " (got " + value + ").");
        }
        return value;
    }

    /** Rotation wraps rather than being rejected — 370° is a legitimate drag, it just means 10°. */
    private static int normaliseRotation(int degrees) {
        int wrapped = degrees % 360;
        return wrapped < 0 ? wrapped + 360 : wrapped;
    }

    private static FloorObject.Shape parseShape(String raw) {
        try {
            return FloorObject.Shape.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown shape: " + raw
                    + " (expected " + Arrays.toString(FloorObject.Shape.values()) + ").");
        }
    }

    private static FloorObject.ObjectType parseObjectType(String raw) {
        if (raw == null) {
            throw new BadRequestException("Object type is required.");
        }
        try {
            return FloorObject.ObjectType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown object type: " + raw
                    + " (expected " + Arrays.toString(FloorObject.ObjectType.values()) + ").");
        }
    }

    /**
     * A floor plan belongs to exactly one restaurant, so a platform account with no tenant has no room
     * to look at — and binding it to null would read as "every restaurant's tables at once".
     */
    private Long requireTenant() {
        Long tenant = restaurantAuthorizationService.currentTenantReadScopeStrict();
        if (tenant == null) {
            throw new BadRequestException(
                    "A floor plan belongs to a restaurant. Sign in with a restaurant-scoped account.");
        }
        return tenant;
    }

    /**
     * The write-time twin. Uses the write scope rather than the read scope so a caller who may only
     * <em>see</em> a restaurant cannot edit its room — the two scopes are separate on purpose.
     */
    private Long requireWriteTenant() {
        Long tenant = restaurantAuthorizationService.currentTenantScopeStrict();
        if (tenant == null) {
            throw new BadRequestException(
                    "A floor plan belongs to a restaurant. Sign in with a restaurant-scoped account.");
        }
        return tenant;
    }
}
