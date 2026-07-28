package com.elcafe.common.channel;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read / cancel / delete flow shared by the SMS and Telegram campaign services, which had it
 * byte-for-byte in two copies — including the two lifecycle guards ("can't cancel a completed
 * campaign", "can't delete one that is mid-send") that must stay identical and previously had to be
 * kept in sync by hand.
 *
 * <p>Scope is deliberately narrow. Only the parts that are genuinely the same across channels live
 * here; {@code createCampaign} / {@code updateCampaign} / {@code sendCampaignNow} stay in the concrete
 * services because their recipient-list building, audience validation, and send mechanics are
 * irreducibly channel-specific. {@code getCampaignsByStatus} also stays put — it is a one-liner keyed
 * on a module's {@code CampaignStatus} enum, and pulling it up would drag that enum into {@code common}.
 *
 * <p>Tenant scoping is unchanged: the repository finders are confined to the caller's restaurant by
 * the Hibernate {@code restaurantFilter}, exactly as in the previous per-channel code.
 *
 * @param <E>    the channel's campaign entity
 * @param <Resp> its response DTO
 */
public abstract class AbstractChannelCampaignService<E extends ChannelCampaign, Resp> {

    /** The channel's Spring Data repository (tenant-scoped by the {@code restaurantFilter}). */
    protected abstract JpaRepository<E, Long> repository();

    /** Resource label for {@link ResourceNotFoundException}, e.g. {@code "SmsCampaign"}. */
    protected abstract String resourceName();

    /** Fetch-by-id with the template eagerly joined (returns {@code null} when absent), per channel. */
    protected abstract E findByIdWithTemplate(Long id);

    /** Map an entity to its response DTO. */
    protected abstract Resp toResponse(E entity);

    @Transactional(readOnly = true)
    public Page<Resp> getAllCampaigns(Pageable pageable) {
        return repository().findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Resp getCampaignById(Long id) {
        E campaign = findByIdWithTemplate(id);
        if (campaign == null) {
            throw new ResourceNotFoundException(resourceName(), "id", id);
        }
        return toResponse(campaign);
    }

    @Transactional
    public Resp cancelCampaign(Long id) {
        E campaign = findOrThrow(id);
        if (!campaign.isCancellable()) {
            throw new BadRequestException("Cannot cancel completed campaign");
        }
        campaign.markCancelled();
        return toResponse(repository().save(campaign));
    }

    @Transactional
    public void deleteCampaign(Long id) {
        E campaign = findOrThrow(id);
        if (!campaign.isDeletable()) {
            throw new BadRequestException("Cannot delete campaign that is currently sending");
        }
        repository().delete(campaign);
    }

    protected E findOrThrow(Long id) {
        return repository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName(), "id", id));
    }
}
