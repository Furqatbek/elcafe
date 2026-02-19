package com.elcafe.modules.loyalty.service;

import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.customer.entity.Customer;
import com.elcafe.modules.loyalty.dto.CustomerMilestoneProgressResponse;
import com.elcafe.modules.loyalty.dto.MilestoneCreateRequest;
import com.elcafe.modules.loyalty.dto.MilestoneResponse;
import com.elcafe.modules.loyalty.dto.MilestoneUpdateRequest;
import com.elcafe.modules.loyalty.entity.LoyaltyMilestone;
import com.elcafe.modules.loyalty.entity.MilestoneRedemption;
import com.elcafe.modules.loyalty.mapper.MilestoneMapper;
import com.elcafe.modules.loyalty.repository.LoyaltyMilestoneRepository;
import com.elcafe.modules.loyalty.repository.MilestoneRedemptionRepository;
import com.elcafe.modules.menu.entity.Product;
import com.elcafe.modules.menu.repository.ProductRepository;
import com.elcafe.modules.order.entity.Order;
import com.elcafe.modules.restaurant.entity.Restaurant;
import com.elcafe.modules.restaurant.repository.RestaurantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MilestoneService {

    private final LoyaltyMilestoneRepository milestoneRepository;
    private final MilestoneRedemptionRepository redemptionRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;
    private final MilestoneMapper milestoneMapper;

    /**
     * Create a new milestone for a restaurant
     */
    @Transactional
    public MilestoneResponse createMilestone(Long restaurantId, MilestoneCreateRequest request) {
        log.info("Creating milestone '{}' for restaurant {}", request.getName(), restaurantId);

        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant", "id", restaurantId));

        LoyaltyMilestone milestone = LoyaltyMilestone.builder()
                .restaurant(restaurant)
                .name(request.getName())
                .description(request.getDescription())
                .requiredVisits(request.getRequiredVisits())
                .rewardType(request.getRewardType())
                .rewardValue(request.getRewardValue())
                .minOrderAmount(request.getMinOrderAmount() != null ? request.getMinOrderAmount() : BigDecimal.ZERO)
                .isRepeating(request.getIsRepeating())
                .active(true)
                .build();

        if (request.getRewardProductId() != null) {
            Product product = productRepository.findById(request.getRewardProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getRewardProductId()));
            milestone.setRewardProduct(product);
        }

        milestone = milestoneRepository.save(milestone);
        log.info("Milestone created with id {}", milestone.getId());

        return milestoneMapper.toResponse(milestone);
    }

    /**
     * Update an existing milestone
     */
    @Transactional
    public MilestoneResponse updateMilestone(Long milestoneId, MilestoneUpdateRequest request) {
        log.info("Updating milestone {}", milestoneId);

        LoyaltyMilestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));

        if (request.getName() != null) {
            milestone.setName(request.getName());
        }
        if (request.getDescription() != null) {
            milestone.setDescription(request.getDescription());
        }
        if (request.getRequiredVisits() != null) {
            milestone.setRequiredVisits(request.getRequiredVisits());
        }
        if (request.getRewardType() != null) {
            milestone.setRewardType(request.getRewardType());
        }
        if (request.getRewardValue() != null) {
            milestone.setRewardValue(request.getRewardValue());
        }
        if (request.getRewardProductId() != null) {
            Product product = productRepository.findById(request.getRewardProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", "id", request.getRewardProductId()));
            milestone.setRewardProduct(product);
        }
        if (request.getMinOrderAmount() != null) {
            milestone.setMinOrderAmount(request.getMinOrderAmount());
        }
        if (request.getIsRepeating() != null) {
            milestone.setIsRepeating(request.getIsRepeating());
        }
        if (request.getActive() != null) {
            milestone.setActive(request.getActive());
        }

        milestone = milestoneRepository.save(milestone);
        return milestoneMapper.toResponse(milestone);
    }

    /**
     * Get a milestone by ID
     */
    @Transactional(readOnly = true)
    public MilestoneResponse getMilestone(Long milestoneId) {
        LoyaltyMilestone milestone = milestoneRepository.findById(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", "id", milestoneId));
        return milestoneMapper.toResponse(milestone);
    }

    /**
     * Get all milestones for a restaurant
     */
    @Transactional(readOnly = true)
    public List<MilestoneResponse> getMilestonesByRestaurant(Long restaurantId) {
        List<LoyaltyMilestone> milestones = milestoneRepository.findByRestaurant_Id(restaurantId);
        return milestones.stream()
                .map(milestoneMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete a milestone
     */
    @Transactional
    public void deleteMilestone(Long milestoneId) {
        log.info("Deleting milestone {}", milestoneId);
        if (!milestoneRepository.existsById(milestoneId)) {
            throw new ResourceNotFoundException("Milestone", "id", milestoneId);
        }
        milestoneRepository.deleteById(milestoneId);
    }

    /**
     * Process a completed order: increment visit counters for all active milestones
     */
    @Transactional
    public List<MilestoneRedemption> processOrderCompletion(Order order) {
        Long customerId = order.getCustomer().getId();
        Long restaurantId = order.getRestaurant().getId();

        log.info("Processing milestone visits for customer {} at restaurant {}", customerId, restaurantId);

        List<LoyaltyMilestone> activeMilestones = milestoneRepository.findActiveMilestonesForRestaurant(restaurantId);
        List<MilestoneRedemption> completedMilestones = new ArrayList<>();

        for (LoyaltyMilestone milestone : activeMilestones) {
            // Skip non-repeating milestones that are already completed
            MilestoneRedemption redemption = redemptionRepository
                    .findByMilestoneIdAndCustomerId(milestone.getId(), customerId)
                    .orElseGet(() -> {
                        Customer customer = order.getCustomer();
                        return MilestoneRedemption.builder()
                                .milestone(milestone)
                                .customer(customer)
                                .currentVisits(0)
                                .totalCompletions(0)
                                .rewardPending(false)
                                .build();
                    });

            // Skip if non-repeating and already completed once
            if (!milestone.getIsRepeating() && redemption.getTotalCompletions() > 0) {
                continue;
            }

            // Skip if order total is below the milestone's minimum order amount
            if (milestone.getMinOrderAmount() != null
                    && milestone.getMinOrderAmount().compareTo(BigDecimal.ZERO) > 0
                    && order.getTotal().compareTo(milestone.getMinOrderAmount()) < 0) {
                log.debug("Order total {} below milestone '{}' minimum {}, skipping",
                        order.getTotal(), milestone.getName(), milestone.getMinOrderAmount());
                continue;
            }

            // Skip if there's already a pending reward (must redeem before next cycle)
            if (redemption.getRewardPending()) {
                continue;
            }

            boolean milestoneReached = redemption.recordVisit(order, milestone.getRequiredVisits());

            redemption = redemptionRepository.save(redemption);

            if (milestoneReached) {
                log.info("Customer {} reached milestone '{}' (completion #{})",
                        customerId, milestone.getName(), redemption.getTotalCompletions());
                completedMilestones.add(redemption);
            }
        }

        return completedMilestones;
    }

    /**
     * Get customer's progress on all milestones for a restaurant
     */
    @Transactional(readOnly = true)
    public List<CustomerMilestoneProgressResponse> getCustomerProgress(Long customerId, Long restaurantId) {
        List<LoyaltyMilestone> activeMilestones = milestoneRepository.findActiveMilestonesForRestaurant(restaurantId);
        List<MilestoneRedemption> redemptions = redemptionRepository
                .findByCustomerIdAndRestaurantId(customerId, restaurantId);

        return activeMilestones.stream()
                .map(milestone -> {
                    MilestoneRedemption redemption = redemptions.stream()
                            .filter(r -> r.getMilestone().getId().equals(milestone.getId()))
                            .findFirst()
                            .orElse(null);

                    return milestoneMapper.toProgressResponse(milestone, redemption);
                })
                .collect(Collectors.toList());
    }

    /**
     * Get all pending rewards for a customer
     */
    @Transactional(readOnly = true)
    public List<CustomerMilestoneProgressResponse> getPendingRewards(Long customerId) {
        List<MilestoneRedemption> pendingRedemptions = redemptionRepository.findPendingRewards(customerId);

        return pendingRedemptions.stream()
                .map(r -> milestoneMapper.toProgressResponse(r.getMilestone(), r))
                .collect(Collectors.toList());
    }

    /**
     * Redeem a pending milestone reward
     */
    @Transactional
    public CustomerMilestoneProgressResponse redeemReward(Long customerId, Long milestoneId) {
        log.info("Customer {} redeeming reward for milestone {}", customerId, milestoneId);

        MilestoneRedemption redemption = redemptionRepository
                .findByMilestoneIdAndCustomerId(milestoneId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone progress", "milestoneId", milestoneId));

        if (!redemption.getRewardPending()) {
            throw new IllegalStateException("No pending reward to redeem for this milestone");
        }

        LoyaltyMilestone milestone = redemption.getMilestone();
        redemption.redeemReward(milestone.getIsRepeating());
        redemption = redemptionRepository.save(redemption);

        log.info("Customer {} redeemed reward for milestone '{}'", customerId, milestone.getName());

        return milestoneMapper.toProgressResponse(milestone, redemption);
    }
}
