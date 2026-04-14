package com.capgemini.startup.service;

import com.capgemini.startup.dto.StartupRequest;
import com.capgemini.startup.dto.StartupResponse;
import com.capgemini.startup.event.StartupCreatedEvent;
import com.capgemini.startup.entity.Startup;
import com.capgemini.startup.enums.StartupStage;
import com.capgemini.startup.exception.DuplicateResourceException;
import com.capgemini.startup.exception.ResourceNotFoundException;
import com.capgemini.startup.exception.UnauthorizedAccessException;
import com.capgemini.startup.mapper.StartupMapper;
import com.capgemini.startup.repository.StartupFollowerRepository;
import com.capgemini.startup.repository.StartupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for StartupServiceImpl validating core application logic seamlessly.
 */
@ExtendWith(MockitoExtension.class)
class StartupServiceImplTest {

    @Mock private StartupRepository startupRepository;
    @Mock private StartupFollowerRepository startupFollowerRepository;
    @Mock private StartupMapper startupMapper;
    @Mock private RabbitTemplate rabbitTemplate;

    private StartupServiceImpl service;

    private Startup startup;
    private StartupRequest request;
    private StartupResponse responseDto;

    @BeforeEach
    void setUp() {
        // Prepare initial startup test data
        startup = Startup.builder().id(100L).founderId(10L).name("TechCo")
                .stage(StartupStage.MVP).fundingGoal(BigDecimal.valueOf(50000))
                .isApproved(false).isRejected(false).build();

        // Manual Constructor Injection
        service = new StartupServiceImpl(
                startupRepository, 
                startupFollowerRepository, 
                rabbitTemplate, 
                startupMapper, 
                "founderlink.exchange",
                "startup.created",
                "startup.rejected",
                "startup.deleted"
        );

        // Setup request payload representing incoming API call
        request = new StartupRequest();
        request.setName("TechCo");
        request.setStage(StartupStage.MVP);
        request.setFundingGoal(BigDecimal.valueOf(50000));

        // Setup the expected mapper output response
        responseDto = StartupResponse.builder().id(100L).founderId(10L).name("TechCo").build();
    }

    @Test
    void createStartup_shouldSaveAndPublishEvent() {
        // Arrange: Mock the DB save operation and object mapping
        when(startupRepository.save(any())).thenReturn(startup);
        when(startupMapper.toResponse(startup)).thenReturn(responseDto);

        // Act: Execute the service method layer
        StartupResponse result = service.createStartup(10L, request);

        // Assert: Ensure the final output is mapped correctly and events published via MQ
        assertThat(result.getId()).isEqualTo(100L);
        verify(rabbitTemplate).convertAndSend(eq("founderlink.exchange"), eq("startup.created"), any(StartupCreatedEvent.class));
    }

    @Test
    void getStartupById_whenExists_shouldReturnResponse() {
        // Arrange: Mock returning the startup from mock database
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));
        when(startupMapper.toResponse(startup)).thenReturn(responseDto);

        // Act: Fetch by ID
        StartupResponse result = service.getStartupById(100L);

        // Assert: Ensure it was successfully mapped and returned
        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    void getStartupById_whenNotExists_shouldThrowNotFound() {
        // Arrange: Empty return simulates no records mapping to the provided ID
        when(startupRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert: Exception must explicitly trigger to stop the flow safely
        assertThatThrownBy(() -> service.getStartupById(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateStartup_byFounder_shouldUpdateAndReturn() {
        // Arrange: Mock the existing startup lookup and subsequent re-save mechanics
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));
        when(startupRepository.save(any())).thenReturn(startup);
        when(startupMapper.toResponse(startup)).thenReturn(responseDto);

        // Act: Rename the entity values inside request 
        request.setName("NewName");
        StartupResponse result = service.updateStartup(100L, 10L, request);

        // Assert: Action succeeds and DB call verifies interaction
        assertThat(result).isNotNull();
        verify(startupRepository).save(any());
    }

    @Test
    void updateStartup_byNonFounder_shouldThrowUnauthorized() {
        // Arrange: Mock existing record where founder id mismatch triggers
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));

        // Act & Assert: The test proves that authorization rules fire and intercept it early
        assertThatThrownBy(() -> service.updateStartup(100L, 20L, request))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void deleteStartup_byFounder_shouldDelete() {
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));
        service.deleteStartup(100L, 10L, false);
        verify(startupRepository).delete(startup);
    }

    @Test
    void deleteStartup_byAdmin_shouldDelete() {
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));
        service.deleteStartup(100L, 999L, true);
        verify(startupRepository).delete(startup);
        verify(startupFollowerRepository).deleteByStartupId(100L);
    }

    @Test
    void approveStartup_shouldSetApprovedTrue() {
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));
        when(startupRepository.save(any())).thenReturn(startup);
        when(startupMapper.toResponse(startup)).thenReturn(responseDto);
        service.approveStartup(100L);
        assertThat(startup.getIsApproved()).isTrue();
        verify(startupRepository).save(startup);
    }

    @Test
    void rejectStartup_shouldSetRejectedTrueAndPublishEvent() {
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));
        when(startupRepository.save(any())).thenReturn(startup);
        when(startupMapper.toResponse(startup)).thenReturn(responseDto);
        service.rejectStartup(100L);
        assertThat(startup.getIsRejected()).isTrue();
        verify(rabbitTemplate).convertAndSend(eq("founderlink.exchange"), eq("startup.rejected"), any(Object.class));
    }

    @Test
    void deleteStartup_unauthorized_shouldThrow() {
        when(startupRepository.findById(100L)).thenReturn(Optional.of(startup));

        assertThatThrownBy(() -> service.deleteStartup(100L, 999L, false))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void searchStartups_allFilters_shouldReturnPage() {
        when(startupRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(startup)));
        when(startupMapper.toResponse(any())).thenReturn(responseDto);

        var result = service.searchStartups("Tech", StartupStage.MVP, BigDecimal.ZERO, BigDecimal.valueOf(100000), "Berlin", Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void searchStartups_noFilters_shouldReturnPage() {
        when(startupRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(startup)));
        when(startupMapper.toResponse(any())).thenReturn(responseDto);

        var result = service.searchStartups(null, null, null, null, null, Pageable.unpaged());

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void followStartup_success_shouldSave() {
        when(startupRepository.existsById(100L)).thenReturn(true);
        when(startupFollowerRepository.existsByStartupIdAndInvestorId(100L, 2L)).thenReturn(false);

        service.followStartup(100L, 2L);

        verify(startupFollowerRepository).save(any());
    }

    @Test
    void followStartup_notFound_shouldThrow() {
        when(startupRepository.existsById(100L)).thenReturn(false);

        assertThatThrownBy(() -> service.followStartup(100L, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void followStartup_alreadyFollowing_shouldThrow() {
        when(startupRepository.existsById(100L)).thenReturn(true);
        when(startupFollowerRepository.existsByStartupIdAndInvestorId(100L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> service.followStartup(100L, 2L))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void getStartupsByFounderId_shouldReturnList() {
        when(startupRepository.findByFounderId(10L)).thenReturn(List.of(startup));
        when(startupMapper.toResponse(startup)).thenReturn(responseDto);

        var result = service.getStartupsByFounderId(10L);

        assertThat(result).hasSize(1);
    }
}
