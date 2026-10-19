package com.migia.OperationsHub.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flow 16 — Spring Actuator Health & Info Endpoints.
 *
 * Verifies:
 *  - 16.1 /actuator/health is accessible without authentication → 200 OK, status = UP
 *  - 16.2 /actuator/info is accessible without authentication → 200 OK
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void tc16_1_actuatorHealth_returns200AndUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void tc16_2_actuatorInfo_returns200() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
