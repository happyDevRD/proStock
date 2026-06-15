package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.entity.AuditLog;
import com.happydev.prestockbackend.repository.AuditLogRepository;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.security.LoginRateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AuditLogController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, LoginRateLimitFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AuditLogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    void list_returnsPagedAuditEntries() throws Exception {
        AuditLog row = new AuditLog();
        row.setId(5L);
        row.setCreatedAt(LocalDateTime.of(2026, 5, 1, 12, 0));
        row.setUsername("admin");
        row.setAction("USER_CREATED");
        row.setEntityType("User");
        row.setEntityId(2L);
        row.setDetails("{\"username\":\"x\"}");

        given(auditLogRepository.findAll(any(Pageable.class))).willReturn(new PageImpl<>(List.of(row)));

        mockMvc.perform(get("/api/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action", is("USER_CREATED")))
                .andExpect(jsonPath("$.content[0].username", is("admin")))
                .andExpect(jsonPath("$.content[0].entityType", is("User")));
    }
}
