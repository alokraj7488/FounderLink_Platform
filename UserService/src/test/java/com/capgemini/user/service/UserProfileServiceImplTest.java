package com.capgemini.user.service;

import com.capgemini.user.dto.UserProfileRequest;
import com.capgemini.user.dto.UserProfileResponse;
import com.capgemini.user.entity.UserProfile;
import com.capgemini.user.exception.DuplicateResourceException;
import com.capgemini.user.exception.ResourceNotFoundException;
import com.capgemini.user.mapper.UserProfileMapper;
import com.capgemini.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private UserProfileMapper userProfileMapper;

    @InjectMocks
    private UserProfileServiceImpl service;

    private UserProfileRequest request;
    private UserProfile profile;
    private UserProfileResponse responseDto;

    @BeforeEach
    void setUp() {
        request = new UserProfileRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setBio("Entrepreneur");
        request.setSkills("Java");

        profile = UserProfile.builder()
                .id(1L).userId(10L).name("Alice").email("alice@example.com").build();

        responseDto = UserProfileResponse.builder()
                .id(1L).userId(10L).name("Alice").email("alice@example.com").build();
    }

    // --- createProfile ---

    @Test
    void createProfile_withValidData_shouldReturnResponse() {
        // Prepare mock data and behaviors
        when(userProfileRepository.existsByUserId(10L)).thenReturn(false);
        when(userProfileRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userProfileRepository.save(any())).thenReturn(profile);
        when(userProfileMapper.toResponse(profile)).thenReturn(responseDto);

        // Execute the unit under test
        UserProfileResponse result = service.createProfile(10L, request);

        // Verify the expected results
        assertThat(result.getName()).isEqualTo("Alice");
        verify(userProfileRepository).save(any());
    }

    @Test
    void createProfile_whenProfileExists_shouldThrowDuplicateException() {
        // Prepare mock data and behaviors
        when(userProfileRepository.existsByUserId(10L)).thenReturn(true);

        // Verify that the correct exception is thrown
        assertThatThrownBy(() -> service.createProfile(10L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void createProfile_whenEmailExists_shouldThrowDuplicateException() {
        // Prepare mock data and behaviors
        when(userProfileRepository.existsByUserId(10L)).thenReturn(false);
        when(userProfileRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Verify that the correct exception is thrown
        assertThatThrownBy(() -> service.createProfile(10L, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // --- getProfileByUserId ---

    @Test
    void getProfileByUserId_whenExists_shouldReturnProfile() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));
        when(userProfileMapper.toResponse(profile)).thenReturn(responseDto);

        // Execute the unit under test
        UserProfileResponse result = service.getProfileByUserId(10L);

        // Verify the expected results
        assertThat(result.getUserId()).isEqualTo(10L);
    }

    @Test
    void getProfileByUserId_whenNotExists_shouldThrowNotFound() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserId(99L)).thenReturn(Optional.empty());

        // Verify that the correct exception is thrown
        assertThatThrownBy(() -> service.getProfileByUserId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- updateProfile ---

    @Test
    void updateProfile_whenProfileExists_shouldUpdateAndReturn() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));
        when(userProfileRepository.findByEmail("alice@example.com")).thenReturn(Optional.empty());
        when(userProfileRepository.save(any())).thenReturn(profile);
        when(userProfileMapper.toResponse(any())).thenReturn(responseDto);

        // Execute the unit under test
        UserProfileResponse result = service.updateProfile(10L, request);

        // Verify the expected results
        assertThat(result).isNotNull();
        verify(userProfileRepository).save(any());
    }

    @Test
    void updateProfile_whenProfileNotExists_shouldCreateNew() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userProfileRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(userProfileRepository.save(any())).thenReturn(profile);
        when(userProfileMapper.toResponse(any())).thenReturn(responseDto);

        // Execute the unit under test
        UserProfileResponse result = service.updateProfile(10L, request);

        // Verify the expected results
        assertThat(result).isNotNull();
    }

    @Test
    void updateProfile_whenEmailConflict_shouldThrowDuplicateException() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.of(profile));
        UserProfile otherProfile = UserProfile.builder().userId(20L).email("alice@example.com").build();
        when(userProfileRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(otherProfile));

        // Verify that the correct exception is thrown
        assertThatThrownBy(() -> service.updateProfile(10L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already in use");
    }

    @Test
    void updateProfile_whenNewProfileAndEmailExists_shouldThrowDuplicateException() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userProfileRepository.existsByEmail("alice@example.com")).thenReturn(true);

        // Verify that the correct exception is thrown
        assertThatThrownBy(() -> service.updateProfile(10L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Email already in use");
    }

    // --- searchBySkill ---

    @Test
    void searchBySkill_withNullKeyword_shouldReturnEmptyList() {
        // Execute the unit under test
        List<UserProfileResponse> result = service.searchBySkill(null);
        // Verify the expected results
        assertThat(result).isEmpty();
    }

    @Test
    void searchBySkill_withEmptyKeyword_shouldReturnEmptyList() {
        // Execute the unit under test
        List<UserProfileResponse> result = service.searchBySkill("   ");
        // Verify the expected results
        assertThat(result).isEmpty();
    }

    @Test
    void searchBySkill_withValidKeyword_shouldReturnResults() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findBySkillsContaining("Java")).thenReturn(List.of(profile));
        when(userProfileMapper.toResponse(profile)).thenReturn(responseDto);

        // Execute the unit under test
        List<UserProfileResponse> result = service.searchBySkill("Java");

        // Verify the expected results
        assertThat(result).hasSize(1);
    }

    // --- getProfilesByUserIds ---

    @Test
    void getProfilesByUserIds_withNullList_shouldReturnEmpty() {
        // Execute the unit under test
        List<UserProfileResponse> result = service.getProfilesByUserIds(null, null);
        // Verify the expected results
        assertThat(result).isEmpty();
    }

    @Test
    void getProfilesByUserIds_withEmptyList_shouldReturnEmpty() {
        // Execute the unit under test
        List<UserProfileResponse> result = service.getProfilesByUserIds(List.of(), null);
        // Verify the expected results
        assertThat(result).isEmpty();
    }

    @Test
    void getProfilesByUserIds_withSkillFilter_shouldFilterResults() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserIdInAndSkillsContaining(List.of(10L), "Java"))
                .thenReturn(List.of(profile));
        when(userProfileMapper.toResponse(profile)).thenReturn(responseDto);

        // Execute the unit under test
        List<UserProfileResponse> result = service.getProfilesByUserIds(List.of(10L), "Java");

        // Verify the expected results
        assertThat(result).hasSize(1);
    }

    @Test
    void getProfilesByUserIds_withoutSkillFilter_shouldReturnAll() {
        // Prepare mock data and behaviors
        when(userProfileRepository.findByUserIdIn(List.of(10L))).thenReturn(List.of(profile));
        when(userProfileMapper.toResponse(profile)).thenReturn(responseDto);

        // Execute the unit under test
        List<UserProfileResponse> result = service.getProfilesByUserIds(List.of(10L), null);

        // Verify the expected results
        assertThat(result).hasSize(1);
    }

    @Test
    void getAllProfiles_shouldReturnPagedResults() {
        // Prepare mock data and behaviors
        org.springframework.data.domain.Page<UserProfile> page = new org.springframework.data.domain.PageImpl<>(List.of(profile));
        when(userProfileRepository.findAll(any(org.springframework.data.domain.Pageable.class))).thenReturn(page);
        when(userProfileMapper.toResponse(profile)).thenReturn(responseDto);

        // Execute the unit under test
        org.springframework.data.domain.Page<UserProfileResponse> result = service.getAllProfiles(org.springframework.data.domain.PageRequest.of(0, 10));

        // Verify the expected results
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("alice@example.com");
    }
}
