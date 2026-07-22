package com.elcafe.modules.restaurant.mapper;

import com.elcafe.modules.restaurant.dto.CreateWorkingHoursRequest;
import com.elcafe.modules.restaurant.dto.UpdateWorkingHoursRequest;
import com.elcafe.modules.restaurant.dto.WorkingHoursResponse;
import com.elcafe.modules.restaurant.entity.WorkingHours;
import org.springframework.stereotype.Component;

@Component
public class WorkingHoursMapper {

    public WorkingHoursResponse toResponse(WorkingHours workingHours) {
        if (workingHours == null) {
            return null;
        }

        return WorkingHoursResponse.builder()
                .id(workingHours.getId())
                .restaurantId(workingHours.getRestaurant().getId())
                .restaurantName(workingHours.getRestaurant().getName())
                .userId(workingHours.getUser().getId())
                .userName(workingHours.getUser().getFirstName() + " " + workingHours.getUser().getLastName())
                .dayOfWeek(workingHours.getDayOfWeek())
                .startTime(workingHours.getStartTime())
                .endTime(workingHours.getEndTime())
                .notes(workingHours.getNotes())
                .active(workingHours.getActive())
                .build();
    }

    public WorkingHours toEntity(CreateWorkingHoursRequest request) {
        if (request == null) {
            return null;
        }

        return WorkingHours.builder()
                .dayOfWeek(request.getDayOfWeek())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .notes(request.getNotes())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();
    }

    public void updateEntity(WorkingHours workingHours, UpdateWorkingHoursRequest request) {
        if (request == null || workingHours == null) {
            return;
        }

        workingHours.setDayOfWeek(request.getDayOfWeek());
        workingHours.setStartTime(request.getStartTime());
        workingHours.setEndTime(request.getEndTime());
        workingHours.setNotes(request.getNotes());
        if (request.getActive() != null) {
            workingHours.setActive(request.getActive());
        }
    }
}
