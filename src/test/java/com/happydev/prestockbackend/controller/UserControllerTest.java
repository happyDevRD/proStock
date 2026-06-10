package com.happydev.prestockbackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.happydev.prestockbackend.dto.UserDto;
import com.happydev.prestockbackend.entity.User;
import com.happydev.prestockbackend.entity.UserRole;
import com.happydev.prestockbackend.exception.GlobalExceptionHandler;
import com.happydev.prestockbackend.security.JwtAuthenticationFilter;
import com.happydev.prestockbackend.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = JwtAuthenticationFilter.class
        )
)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void listUsers_returnsUsers() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setUsername("manager");
        u.setEmail("m@test.local");
        u.setFirstName("Ana");
        u.setLastName("Gomez");
        u.setRole(UserRole.MANAGER);
        u.setPassword("encoded");
        given(userService.getAllUsers()).willReturn(List.of(u));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username", is("manager")))
                .andExpect(jsonPath("$[0].role", is("MANAGER")));
    }

    @Test
    void createUser_validPayload_returnsCreated() throws Exception {
        User saved = new User();
        saved.setId(2L);
        saved.setUsername("newuser");
        saved.setEmail("new@test.local");
        saved.setFirstName("N");
        saved.setLastName("U");
        saved.setRole(UserRole.USER);
        saved.setPassword("encoded");
        given(userService.createUser(any(User.class), anyString())).willReturn(saved);

        UserDto dto = new UserDto();
        dto.setUsername("newuser");
        dto.setEmail("new@test.local");
        dto.setFirstName("N");
        dto.setLastName("U");
        dto.setRole("USER");
        dto.setPassword("secret12");

        Principal admin = () -> "admin";

        mockMvc.perform(post("/api/users")
                        .principal(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username", is("newuser")))
                .andExpect(jsonPath("$.role", is("USER")));
    }

    @Test
    void createUser_withoutPrincipal_returnsUnauthorized() throws Exception {
        UserDto dto = new UserDto();
        dto.setUsername("newuser");
        dto.setEmail("new@test.local");
        dto.setFirstName("N");
        dto.setLastName("U");
        dto.setRole("USER");
        dto.setPassword("secret12");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_withoutPrincipal_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteUser_whenServiceRejects_returns400WithSpanishMessage() throws Exception {
        doThrow(new IllegalArgumentException("No puedes eliminar tu propio usuario."))
                .when(userService).deleteUser(eq(1L), eq("admin"));

        Principal admin = new Principal() {
            @Override
            public String getName() {
                return "admin";
            }
        };

        mockMvc.perform(delete("/api/users/1").principal(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("ILLEGAL_ARGUMENT")))
                .andExpect(jsonPath("$.message", is("No puedes eliminar tu propio usuario.")));
    }
}
