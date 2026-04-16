package com.example.loadforcasting.systemflow;

import com.example.loadforcasting.Entity.User;
import com.example.loadforcasting.systemflow.support.AbstractSystemFlowTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserManagementApiSystemFlowTest extends AbstractSystemFlowTest {

    @Test
    void userManagementApi_CreateListUpdateDeleteAgainstRealRepository() throws Exception {
        String createResponse = mockMvc.perform(post("/api/users")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice Operator",
                                  "email": "alice@example.com",
                                  "role": "User",
                                  "status": "Active",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Operator"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        int userId = objectMapper.readTree(createResponse).get("id").asInt();
        assertEquals(1, userRepository.count());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(userId))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"));

        mockMvc.perform(put("/api/users/" + userId)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Alice Admin",
                                  "email": "alice@example.com",
                                  "role": "Admin",
                                  "status": "Inactive",
                                  "password": "secret123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Admin"))
                .andExpect(jsonPath("$.status").value("Inactive"));

        User updated = userRepository.findById(userId).orElseThrow();
        assertEquals("Alice Admin", updated.getName());
        assertEquals("Admin", updated.getRole());
        assertEquals("Inactive", updated.getStatus());

        mockMvc.perform(delete("/api/users/" + userId))
                .andExpect(status().isOk());

        assertFalse(userRepository.findById(userId).isPresent());
    }
}
