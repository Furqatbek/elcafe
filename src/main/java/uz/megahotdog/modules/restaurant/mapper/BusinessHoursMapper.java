package uz.megahotdog.modules.restaurant.mapper;

import uz.megahotdog.modules.restaurant.dto.BusinessHoursResponse;
import uz.megahotdog.modules.restaurant.dto.CreateBusinessHoursRequest;
import uz.megahotdog.modules.restaurant.dto.UpdateBusinessHoursRequest;
import uz.megahotdog.modules.restaurant.entity.BusinessHours;
import org.springframework.stereotype.Component;

@Component
public class BusinessHoursMapper {

    public BusinessHoursResponse toResponse(BusinessHours hours) {
        return BusinessHoursResponse.builder()
                .id(hours.getId())
                .restaurantId(hours.getRestaurant() != null ? hours.getRestaurant().getId() : null)
                .dayOfWeek(hours.getDayOfWeek())
                .openTime(hours.getOpenTime())
                .closeTime(hours.getCloseTime())
                .closed(hours.getClosed())
                .build();
    }

    public BusinessHours toEntity(CreateBusinessHoursRequest request) {
        return BusinessHours.builder()
                .dayOfWeek(request.getDayOfWeek())
                .openTime(request.getOpenTime())
                .closeTime(request.getCloseTime())
                .closed(request.getClosed() != null ? request.getClosed() : false)
                .build();
    }

    public void updateEntityFromRequest(BusinessHours hours, UpdateBusinessHoursRequest request) {
        if (request.getDayOfWeek() != null) {
            hours.setDayOfWeek(request.getDayOfWeek());
        }
        if (request.getOpenTime() != null) {
            hours.setOpenTime(request.getOpenTime());
        }
        if (request.getCloseTime() != null) {
            hours.setCloseTime(request.getCloseTime());
        }
        if (request.getClosed() != null) {
            hours.setClosed(request.getClosed());
        }
    }
}
