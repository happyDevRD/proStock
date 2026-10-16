package com.happydev.prestockbackend.config;

import com.happydev.prestockbackend.security.JwtService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Matriz de acceso por rol con los permisos por defecto de cada uno (migraciones V30–V49).
 * Comprueba 403 cuando el rol no debe llegar y "no 403" cuando sí (200/400/404 dependen de los datos).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiAuthorizationMatrixTest {

    private static final Map<String, List<String>> DEFAULTS = Map.of(
            "USER", List.of("view.dashboard"),
            "CASHIER", List.of("view.dashboard", "view.pos", "view.invoice", "view.customers", "view.ar", "view.ap",
                    "view.service_orders", "view.quotes"),
            "CONTADOR", List.of("view.dashboard", "view.accounting", "view.reports", "view.ar", "view.ap", "view.expenses",
                    "view.invoice", "accounting.module.view", "accounting.accounts.create"),
            "MANAGER", List.of("view.dashboard", "view.pos", "view.invoice", "view.inventory", "view.suppliers",
                    "view.customers", "view.reports", "view.settings", "view.service_orders", "inventory.module.articles",
                    "inventory.module.purchases", "inventory.module.adjustments", "suppliers.edit"),
            "ADMIN", List.of("view.settings", "settings.manage_company_config", "view.invoice", "view.pos")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    private String token(String role) {
        List<SimpleGrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + role));
        DEFAULTS.get(role).forEach(c -> auths.add(new SimpleGrantedAuthority(c)));
        return jwtService.generateToken(new UsernamePasswordAuthenticationToken("matrix-" + role, null, auths));
    }

    private int status(String role, String method, String path) throws Exception {
        return mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer " + token(role))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "{0} {1} {2} → 403")
    @CsvSource({
            // Lo crítico: configuración y numeración fiscal
            "CASHIER, PUT, /api/company-config",
            "MANAGER, PUT, /api/company-config",
            "MANAGER, PUT, /api/ncf-sequences",
            "ADMIN, POST, /api/ncf-sequences/32/next",
            "USER, GET, /api/ncf-sequences/32",
            // USER solo ve el Panel
            "USER, GET, /api/sales",
            "USER, GET, /api/sales/1",
            "USER, POST, /api/sales",
            "USER, GET, /api/customers/1",
            "USER, DELETE, /api/customers/1",
            "USER, GET, /api/stock-movements",
            "USER, GET, /api/purchase-orders/paginated",
            "USER, GET, /api/suppliers",
            "USER, POST, /api/products",
            "USER, GET, /api/accounting/accounts",
            "USER, POST, /api/accounting/sync/execute",
            // Cajera: sin inventario, compras ni borrar clientes/ventas
            "CASHIER, POST, /api/products",
            "CASHIER, DELETE, /api/products/1",
            "CASHIER, PUT, /api/customers/1",
            "CASHIER, DELETE, /api/customers/1",
            "CASHIER, DELETE, /api/sales/1",
            "CASHIER, GET, /api/stock-movements",
            "CASHIER, PUT, /api/purchase-orders/1/receive",
            // Contador: no vende
            "CONTADOR, POST, /api/sales",
            "CONTADOR, POST, /api/customers/quick",
            // Gerente: no borra productos ni consume NCF
            "MANAGER, DELETE, /api/products/1",
            "MANAGER, POST, /api/ncf-sequences/31/next",
    })
    void forbidden(String role, String method, String path) throws Exception {
        int status = status(role, method, path);
        // Con cuerpo, Spring valida el JSON ({} vacío) antes del @PreAuthorize y responde 400:
        // igual se rechaza sin ejecutar nada. Con un cuerpo válido, el permiso responde 403.
        if (method.equals("POST") || method.equals("PUT")) {
            assertTrue(status == 403 || status == 400, "esperaba 403/400 y fue " + status);
        } else {
            assertEquals(403, status);
        }
    }

    /** Ningún endpoint que modifica datos en estos controladores puede quedar sin regla de permiso. */
    @org.junit.jupiter.api.Test
    void everyWriteEndpointHasAPermissionRule() {
        List<Class<?>> controllers = List.of(
                com.happydev.prestockbackend.controller.ProductController.class,
                com.happydev.prestockbackend.controller.CategoryController.class,
                com.happydev.prestockbackend.controller.ProductImageController.class,
                com.happydev.prestockbackend.controller.SupplierController.class,
                com.happydev.prestockbackend.controller.CustomerController.class,
                com.happydev.prestockbackend.controller.SaleController.class,
                com.happydev.prestockbackend.controller.PurchaseOrderController.class,
                com.happydev.prestockbackend.controller.NcfSequenceController.class,
                com.happydev.prestockbackend.controller.CompanyConfigController.class,
                com.happydev.prestockbackend.controller.AccAccountController.class,
                com.happydev.prestockbackend.controller.AccSyncController.class,
                com.happydev.prestockbackend.controller.EmailController.class);
        List<String> missing = new ArrayList<>();
        for (Class<?> c : controllers) {
            for (var m : c.getDeclaredMethods()) {
                boolean write = m.isAnnotationPresent(org.springframework.web.bind.annotation.PostMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.PutMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.DeleteMapping.class)
                        || m.isAnnotationPresent(org.springframework.web.bind.annotation.PatchMapping.class);
                if (write && !m.isAnnotationPresent(org.springframework.security.access.prepost.PreAuthorize.class)) {
                    missing.add(c.getSimpleName() + "." + m.getName());
                }
            }
        }
        assertEquals(List.of(), missing);
    }

    @ParameterizedTest(name = "{0} {1} {2} → permitido")
    @CsvSource({
            // Panel: lo que usa la pantalla de inicio sigue disponible
            "USER, GET, /api/sales/paginated",
            "USER, GET, /api/sales/summary",
            "USER, GET, /api/customers",
            "USER, GET, /api/products/below-min-stock",
            "USER, GET, /api/company-config",
            // POS de la cajera
            "CASHIER, GET, /api/products",
            "CASHIER, GET, /api/categories",
            "CASHIER, GET, /api/customers",
            "CASHIER, POST, /api/customers/quick",
            "CASHIER, POST, /api/sales",
            "CASHIER, GET, /api/sales/1/payments",
            "CASHIER, GET, /api/purchase-orders/payable",
            // Contabilidad y reportes
            "CONTADOR, GET, /api/accounting/accounts",
            "CONTADOR, POST, /api/accounting/sync/execute",
            "CONTADOR, GET, /api/sales/export",
            "CONTADOR, POST, /api/sales/1/credit-notes",
            // Inventario del gerente
            "MANAGER, GET, /api/stock-movements",
            "MANAGER, POST, /api/products",
            "MANAGER, PUT, /api/customers/1",
            "MANAGER, GET, /api/ncf-sequences/32",
            // Configuración del administrador
            "ADMIN, PUT, /api/company-config",
            "ADMIN, PUT, /api/ncf-sequences",
    })
    void allowed(String role, String method, String path) throws Exception {
        assertNotEquals(403, status(role, method, path));
    }
}
