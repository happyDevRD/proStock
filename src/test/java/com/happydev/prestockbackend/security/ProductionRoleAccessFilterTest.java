package com.happydev.prestockbackend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionRoleAccessFilterTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private int status(String role, String method, String path) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", null, List.of(new SimpleGrantedAuthority(role), new SimpleGrantedAuthority("view.my_work"))));
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        MockHttpServletResponse res = new MockHttpServletResponse();
        new ProductionRoleAccessFilter().doFilter(req, res, new MockFilterChain());
        return res.getStatus();
    }

    @ParameterizedTest
    @CsvSource({
            "GET, /api/my-work, 200",
            "POST, /api/my-work/5/advance, 200",
            "GET, /api/auth/me, 200",
            "POST, /api/auth/logout, 200",
            "GET, /api/features, 200",
            "GET, /api/company-config, 200",
            "PUT, /api/company-config, 403",
            "GET, /api/sales/paginated, 403",
            "GET, /api/customers, 403",
            "GET, /api/service-orders, 403",
            "GET, /api/employees, 403",
            "GET, /uploads/logo.png, 200",
    })
    void productionUsersOnlyReachTheirAllowList(String method, String path, int expected) throws Exception {
        assertEquals(expected, status("ROLE_PRODUCCION", method, path));
    }

    @ParameterizedTest
    @CsvSource({"GET, /api/sales/paginated", "GET, /api/customers", "PUT, /api/company-config"})
    void otherRolesAreNotAffected(String method, String path) throws Exception {
        assertEquals(200, status("ROLE_CASHIER", method, path));
    }
}
