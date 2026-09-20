package uz.megahotdog.modules.courier.dto;

import uz.megahotdog.modules.courier.enums.CourierType;
import uz.megahotdog.modules.courier.enums.CourierVehicle;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCourierRequest {

    @Email(message = "Invalid email format")
    @Size(max = 100)
    private String email;

    @Size(min = 8, max = 100)
    private String password;

    @Size(max = 100)
    private String firstName;

    @Size(max = 100)
    private String lastName;

    @Size(max = 20)
    private String phone;

    private CourierType courierType;

    private CourierVehicle vehicle;

    @Size(max = 50)
    private String vehiclePlate;

    @Size(max = 100)
    private String licenseNumber;

    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 20)
    private String emergencyContact;

    private Boolean available;

    private Boolean verified;

    private Boolean active;
}
