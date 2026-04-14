package com.capgemini.investment.controller;

import com.capgemini.investment.config.JwtAuthenticationFilter;
import com.capgemini.investment.config.JwtUtil;
import com.capgemini.investment.config.SecurityConfig;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DemoTokenController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "jwt.secret=9a4f4e35455ad59348807d47d0e8bc16db4901f666f7d9d9552dd5a86c75a03d",
    "jwt.expiration=3600000"
})
class DemoTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() throws Exception {
        doAnswer(inv -> {
            ((FilterChain) inv.getArgument(2)).doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    void getInvestorToken_shouldReturnToken() throws Exception {
        mockMvc.perform(get("/test/token/investor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("INVESTOR"))
                .andExpect(jsonPath("$.userId").value("1"))
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void getFounderToken_shouldReturnToken() throws Exception {
        mockMvc.perform(get("/test/token/founder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("FOUNDER"))
                .andExpect(jsonPath("$.userId").value("2"))
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void getCofounderToken_shouldReturnToken() throws Exception {
        mockMvc.perform(get("/test/token/cofounder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("COFOUNDER"))
                .andExpect(jsonPath("$.userId").value("3"))
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void getAdminToken_shouldReturnToken() throws Exception {
        mockMvc.perform(get("/test/token/admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.userId").value("4"))
                .andExpect(jsonPath("$.token").exists());
    }
}
