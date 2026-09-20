package uz.megahotdog.modules.instagram.repository;

import uz.megahotdog.modules.instagram.entity.InstagramSubscriber;
import uz.megahotdog.modules.instagram.entity.InstagramSubscriberAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InstagramSubscriberAddressRepository extends JpaRepository<InstagramSubscriberAddress, Long> {

    List<InstagramSubscriberAddress> findAllBySubscriber(InstagramSubscriber subscriber);

    long countBySubscriber(InstagramSubscriber subscriber);
}
