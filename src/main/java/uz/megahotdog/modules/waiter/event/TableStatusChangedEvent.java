package uz.megahotdog.modules.waiter.event;

import uz.megahotdog.modules.restaurant.entity.RestaurantTable.TableStatus;
import uz.megahotdog.modules.waiter.enums.OrderEventType;
import lombok.Getter;

/**
 * Event fired when a table status changes (OPEN, OCCUPIED, CLOSED, etc.)
 */
@Getter
public class TableStatusChangedEvent extends WaiterEvent {

    private final Integer tableNumber;
    private final TableStatus oldStatus;
    private final TableStatus newStatus;

    public TableStatusChangedEvent(
            Object source,
            Long tableId,
            Integer tableNumber,
            Long waiterId,
            String triggeredBy,
            TableStatus oldStatus,
            TableStatus newStatus) {
        super(source, OrderEventType.TABLE_STATUS_CHANGED, null, tableId, waiterId, triggeredBy);
        this.tableNumber = tableNumber;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;

        // Add status transition to metadata
        addMetadata("oldStatus", oldStatus.name());
        addMetadata("newStatus", newStatus.name());
        addMetadata("tableNumber", tableNumber);
    }

    @Override
    public String getEventDescription() {
        return String.format("Table %d status changed from %s to %s by %s",
                tableNumber, oldStatus, newStatus, getTriggeredBy());
    }
}
