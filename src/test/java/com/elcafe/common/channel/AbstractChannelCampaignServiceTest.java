package com.elcafe.common.channel;

import com.elcafe.exception.BadRequestException;
import com.elcafe.exception.ResourceNotFoundException;
import com.elcafe.modules.sms.entity.SmsCampaign;
import com.elcafe.modules.sms.enums.CampaignStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shared campaign read/cancel/delete flow, exercised through a minimal subclass over a real
 * {@link SmsCampaign} so the actual {@link ChannelCampaign} lifecycle rules run. Pins the two guards
 * that used to be copy-pasted per channel: a completed campaign cannot be cancelled, and a sending one
 * cannot be deleted.
 */
@ExtendWith(MockitoExtension.class)
class AbstractChannelCampaignServiceTest {

    @Mock JpaRepository<SmsCampaign, Long> repo;

    /** Concrete subclass wiring the base to the mock repo; findByIdWithTemplate stands in as a finder. */
    private AbstractChannelCampaignService<SmsCampaign, SmsCampaign> service;

    @BeforeEach
    void setUp() {
        service = new AbstractChannelCampaignService<>() {
            @Override protected JpaRepository<SmsCampaign, Long> repository() { return repo; }
            @Override protected String resourceName() { return "SmsCampaign"; }
            @Override protected SmsCampaign findByIdWithTemplate(Long id) { return repo.findById(id).orElse(null); }
            @Override protected SmsCampaign toResponse(SmsCampaign entity) { return entity; }
        };
    }

    private static SmsCampaign campaign(CampaignStatus status) {
        return SmsCampaign.builder().id(1L).name("Promo").status(status).build();
    }

    @Test
    @DisplayName("getCampaignById — a missing id is 404")
    void getById_notFound() {
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCampaignById(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cancelCampaign — a completed campaign cannot be cancelled")
    void cancel_completed_throws() {
        when(repo.findById(1L)).thenReturn(Optional.of(campaign(CampaignStatus.COMPLETED)));
        assertThatThrownBy(() -> service.cancelCampaign(1L)).isInstanceOf(BadRequestException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("cancelCampaign — an in-flight campaign moves to CANCELLED")
    void cancel_draft_marksCancelled() {
        SmsCampaign c = campaign(CampaignStatus.DRAFT);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        when(repo.save(any(SmsCampaign.class))).thenAnswer(i -> i.getArgument(0));

        service.cancelCampaign(1L);
        assertThat(c.getStatus()).isEqualTo(CampaignStatus.CANCELLED);
    }

    @Test
    @DisplayName("deleteCampaign — a sending campaign cannot be deleted")
    void delete_sending_throws() {
        when(repo.findById(1L)).thenReturn(Optional.of(campaign(CampaignStatus.SENDING)));
        assertThatThrownBy(() -> service.deleteCampaign(1L)).isInstanceOf(BadRequestException.class);
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("deleteCampaign — a draft campaign is deleted")
    void delete_draft_deletes() {
        SmsCampaign c = campaign(CampaignStatus.DRAFT);
        when(repo.findById(1L)).thenReturn(Optional.of(c));
        service.deleteCampaign(1L);
        verify(repo).delete(c);
    }
}
