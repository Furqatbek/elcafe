package uz.megahotdog.modules.menu.repository;

import uz.megahotdog.modules.menu.entity.AddOnGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddOnGroupRepository extends JpaRepository<AddOnGroup, Long> {

    List<AddOnGroup> findByRestaurant_IdAndActiveTrue(Long restaurantId);

    List<AddOnGroup> findByRestaurant_Id(Long restaurantId);

    Optional<AddOnGroup> findByIdAndRestaurantId(Long id, Long restaurantId);

    boolean existsByRestaurantIdAndName(Long restaurantId, String name);
}
