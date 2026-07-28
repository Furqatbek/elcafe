package com.elcafe.common.channel;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * The shared CRUD skeleton for a per-tenant channel's message templates. SMS and Telegram templates
 * are the same resource wearing different columns — list, fetch-by-id, create (with a duplicate-name
 * guard and tenant binding), update (re-checking the name only when it changes), delete, toggle-active,
 * and preview — and they had drifted into two copies that each had to be fixed twice. This centralises
 * the flow so a template-CRUD change lands once.
 *
 * <p>Tenant scoping is unchanged: reads go through the plain {@link JpaRepository} finders and are
 * confined to the caller's restaurant by the Hibernate {@code restaurantFilter}, exactly as before;
 * writes bind the new row to the caller's restaurant via {@link #requireWritableTenant()}. Everything a
 * channel does differently — the extra list endpoints (active / by-type / search), the entity builder,
 * the response mapping, the not-found label — stays in the concrete subclass behind the hooks below.
 *
 * <p>Deliberately NOT extended by Instagram: {@code InstagramTemplateService} scopes every read with an
 * explicit {@code restaurantId} predicate (belt-and-suspenders, not filter-only) and checks name
 * uniqueness per-restaurant, so it is a different — stricter — model and folding it in here would
 * either weaken it or need its own hooks. That harmonisation is a separate step.
 *
 * @param <E>   the channel's template entity
 * @param <Req> its create/update request DTO
 * @param <Resp> its response DTO
 */
public abstract class AbstractChannelTemplateService<E extends ChannelTemplate, Req, Resp> {

    // ------------------------------------------------------------------ hooks the channel must provide

    /** The channel's Spring Data repository (tenant-scoped by the {@code restaurantFilter}). */
    protected abstract JpaRepository<E, Long> repository();

    /** Resource label for {@link ResourceNotFoundException}, e.g. {@code "SmsTemplate"}. */
    protected abstract String resourceName();

    /** Whether a template with this name already exists in the caller's restaurant. */
    protected abstract boolean existsByName(String name);

    /** The restaurant a new template binds to, or a {@link BadRequestException} if the caller has none. */
    protected abstract Long requireWritableTenant();

    /** The name carried by a request, for the duplicate-name guard. */
    protected abstract String nameOf(Req request);

    /** Build a new entity from the request, bound to {@code restaurantId}. */
    protected abstract E buildNew(Req request, Long restaurantId);

    /** Copy the request's fields onto an existing entity (update). */
    protected abstract void applyUpdate(E entity, Req request);

    /** Map an entity to its response DTO. */
    protected abstract Resp toResponse(E entity);

    // ------------------------------------------------------------------ shared CRUD

    @Transactional(readOnly = true)
    public Page<Resp> getAllTemplates(Pageable pageable) {
        return repository().findAll(pageable).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Resp getTemplateById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Transactional
    public Resp createTemplate(Req request) {
        String name = nameOf(request);
        if (existsByName(name)) {
            throw new BadRequestException("Template with name '" + name + "' already exists");
        }
        // requireWritableTenant() is resolved after the name check, matching the previous per-channel order.
        E entity = repository().save(buildNew(request, requireWritableTenant()));
        return toResponse(entity);
    }

    @Transactional
    public Resp updateTemplate(Long id, Req request) {
        E entity = findOrThrow(id);
        String newName = nameOf(request);
        if (!entity.getName().equals(newName) && existsByName(newName)) {
            throw new BadRequestException("Template with name '" + newName + "' already exists");
        }
        applyUpdate(entity, request);
        return toResponse(repository().save(entity));
    }

    @Transactional
    public void deleteTemplate(Long id) {
        repository().delete(findOrThrow(id));
    }

    @Transactional
    public Resp toggleTemplateStatus(Long id) {
        E entity = findOrThrow(id);
        entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
        return toResponse(repository().save(entity));
    }

    /** Render a stored template against sample data. Read-only and non-persisting — never counts as usage. */
    @Transactional(readOnly = true)
    public String previewTemplate(Long id, Map<String, String> sampleData) {
        return findOrThrow(id).render(sampleData);
    }

    /** Fetch-by-id confined to the caller's restaurant by the {@code restaurantFilter}, or 404. */
    protected E findOrThrow(Long id) {
        return repository().findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(resourceName(), "id", id));
    }
}
