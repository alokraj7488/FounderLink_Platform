package com.capgemini.user.controller;

import com.capgemini.user.dto.UserProfileRequest;
import com.capgemini.user.dto.UserProfileResponse;
import com.capgemini.user.exception.DuplicateResourceException;
import com.capgemini.user.exception.GlobalExceptionHandler;
import com.capgemini.user.exception.ResourceNotFoundException;
import com.capgemini.user.filter.JwtAuthenticationFilter;
import com.capgemini.user.filter.JwtUtil;
import com.capgemini.user.service.UserProfileCommandService;
import com.capgemini.user.service.UserProfileQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserProfileController.class)
@Import({GlobalExceptionHandler.class, com.capgemini.user.config.SecurityConfig.class})
class UserProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private UserProfileCommandService userProfileCommandService;
    @MockitoBean private UserProfileQueryService userProfileQueryService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private JwtUtil jwtUtil;

    private UserProfileRequest validRequest;
    private UserProfileResponse sampleResponse;

    @BeforeEach
    void setUp() throws Exception {
        // Make the mocked filter pass through so requests reach the controller
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        validRequest = UserProfileRequest.builder()
                .name("Alice Founder").email("alice@example.com")
                .bio("Building the future").skills("Java, Spring")
                .experience("5 years").portfolioLinks("https://alice.dev")
                .build();

        sampleResponse = UserProfileResponse.builder()
                .id(10L).userId(1L).name("Alice Founder").email("alice@example.com")
                .bio("Building the future").skills("Java, Spring")
                .experience("5 years").portfolioLinks("https://alice.dev")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    private static UsernamePasswordAuthenticationToken authFor(Long userId, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(userId.toString(), null, authorities);
    }

    @Test
    void createProfile_withValidAuth_shouldReturn201() throws Exception {
        given(userProfileCommandService.createProfile(eq(1L), any(UserProfileRequest.class))).willReturn(sampleResponse);

        mockMvc.perform(post("/users/profile")
                        .with(authentication(authFor(1L, "ROLE_FOUNDER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void getProfile_byId_shouldReturn200() throws Exception {
        given(userProfileQueryService.getProfileByUserId(1L)).willReturn(sampleResponse);

        mockMvc.perform(get("/users/1").with(authentication(authFor(1L, "ROLE_FOUNDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.name").value("Alice Founder"));
    }

    @Test
    void getProfile_whenNotFound_shouldReturn404() throws Exception {
        given(userProfileQueryService.getProfileByUserId(99L))
                .willThrow(new ResourceNotFoundException("Profile not found for user ID: 99"));

        mockMvc.perform(get("/users/99").with(authentication(authFor(99L, "ROLE_FOUNDER"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Profile not found for user ID: 99"));
    }

    @Test
    void updateProfile_withValidAuth_shouldReturn200() throws Exception {
        UserProfileResponse updated = UserProfileResponse.builder()
                .id(10L).userId(1L).name("Alice Updated").email("alice@example.com")
                .bio("New bio").skills("Java, Kotlin").experience("6 years")
                .portfolioLinks("https://alice.io")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
        given(userProfileCommandService.updateProfile(eq(1L), any(UserProfileRequest.class))).willReturn(updated);

        mockMvc.perform(put("/users/1")
                        .with(authentication(authFor(1L, "ROLE_FOUNDER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Updated"));
    }

    @Test
    void getAllProfiles_withAdminRole_shouldReturn200() throws Exception {
        Page<UserProfileResponse> page = new PageImpl<>(List.of(sampleResponse));
        given(userProfileQueryService.getAllProfiles(any(Pageable.class))).willReturn(page);

        mockMvc.perform(get("/users").with(authentication(authFor(1L, "ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("alice@example.com"));
    }

    @Test
    void searchBySkill_withFounderRole_shouldReturn200() throws Exception {
        given(userProfileQueryService.searchBySkill("Java")).willReturn(List.of(sampleResponse));

        mockMvc.perform(get("/users/search").param("skill", "Java")
                        .with(authentication(authFor(1L, "ROLE_FOUNDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].skills").value("Java, Spring"));
    }

    @Test
    void searchBySkill_withInvestorRole_shouldReturn403() throws Exception {
        mockMvc.perform(get("/users/search").param("skill", "Java")
                        .with(authentication(authFor(2L, "ROLE_INVESTOR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void getProfilesBatch_withFounderRole_shouldReturn200() throws Exception {
        given(userProfileQueryService.getProfilesByUserIds(anyList(), anyString())).willReturn(List.of(sampleResponse));

        mockMvc.perform(get("/users/profiles/batch").param("userIds", "1,2").param("skill", "Java")
                        .with(authentication(authFor(1L, "ROLE_COFOUNDER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    @Test
    void createProfile_whenInvalidRequest_shouldReturn400() throws Exception {
        validRequest.setEmail("not-an-email");

        mockMvc.perform(post("/users/profile")
                        .with(authentication(authFor(1L, "ROLE_FOUNDER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createProfile_whenDuplicate_shouldReturn409() throws Exception {
        given(userProfileCommandService.createProfile(eq(1L), any(UserProfileRequest.class)))
                .willThrow(new DuplicateResourceException("Conflict"));

        mockMvc.perform(post("/users/profile")
                        .with(authentication(authFor(1L, "ROLE_FOUNDER"))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isConflict());
    }
}
