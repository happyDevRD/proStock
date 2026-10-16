package com.happydev.prestockbackend.security;

/**
 * Reglas de acceso de la API, en un solo lugar. Cada endpoint exige los mismos permisos
 * que la interfaz usa para mostrar la pantalla que lo llama (ver lib/permissions.ts del
 * frontend), así ningún rol pierde lo que usa y nadie más llega por la API.
 *
 * <p>GESTOR siempre pasa. Se evita {@code ROLE_ADMIN} salvo donde no hay permiso
 * específico, para que la matriz de permisos siga controlando a ADMIN.
 *
 * <p>Las lecturas que alimentan el Panel (ventas recientes, clientes, stock bajo)
 * aceptan {@code view.dashboard} para no dejar el Panel vacío.
 */
public final class Authz {

    private Authz() {}

    // Productos y categorías
    public static final String PRODUCT_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.pos', 'view.inventory', 'view.reports', 'view.suppliers', 'view.quotes', 'view.service_orders', 'locations.view')";
    public static final String PRODUCT_EDIT = "hasAnyAuthority('ROLE_GESTOR', 'inventory.module.articles')";
    public static final String PRODUCT_DELETE = "hasAnyAuthority('ROLE_GESTOR', 'inventory.delete_item')";
    public static final String PRODUCT_IMPORT = "hasAnyAuthority('ROLE_GESTOR', 'inventory.import_csv', 'view.setup')";
    public static final String PRODUCT_BULK_PRICE = "hasAnyAuthority('ROLE_GESTOR', 'inventory.bulk_promo_price')";
    public static final String STOCK_ADJUST = "hasAnyAuthority('ROLE_GESTOR', 'inventory.module.adjustments')";
    public static final String LOW_STOCK = "hasAnyAuthority('ROLE_GESTOR', 'view.inventory', 'view.dashboard')";
    public static final String CATEGORY_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.pos', 'view.inventory', 'view.reports', 'view.suppliers', 'view.quotes', 'view.service_orders')";
    public static final String CATEGORY_CREATE = "hasAnyAuthority('ROLE_GESTOR', 'inventory.module.articles', 'view.setup')";

    // Inventario y compras
    public static final String STOCK_MOVEMENTS = "hasAnyAuthority('ROLE_GESTOR', 'view.inventory')";
    public static final String PURCHASE_READ = "hasAnyAuthority('ROLE_GESTOR', 'inventory.module.purchases', 'view.inventory', 'view.ap')";
    public static final String PURCHASE_EDIT = "hasAnyAuthority('ROLE_GESTOR', 'inventory.module.purchases')";
    public static final String PAYABLES = "hasAnyAuthority('ROLE_GESTOR', 'view.ap')";

    // Proveedores
    public static final String SUPPLIER_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.suppliers', 'view.inventory', 'view.ap', 'view.expenses')";
    public static final String SUPPLIER_CREATE = "hasAnyAuthority('ROLE_GESTOR', 'suppliers.edit', 'inventory.module.purchases', 'expenses.create', 'view.setup')";
    public static final String SUPPLIER_EDIT = "hasAnyAuthority('ROLE_GESTOR', 'suppliers.edit')";

    // Clientes
    public static final String CUSTOMER_LIST = "hasAnyAuthority('ROLE_GESTOR', 'view.customers', 'view.pos', 'view.invoice', 'view.service_orders', 'view.reports', 'view.ar', 'view.quotes', 'view.dashboard')";
    public static final String CUSTOMER_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.customers', 'view.pos', 'view.invoice', 'view.service_orders', 'view.reports', 'view.ar', 'view.quotes')";
    public static final String CUSTOMER_CREATE = "hasAnyAuthority('ROLE_GESTOR', 'view.pos', 'view.customers', 'quotes.create', 'view.service_orders')";
    public static final String CUSTOMER_QUICK = "hasAnyAuthority('ROLE_GESTOR', 'view.pos')";
    /** Sin permiso propio: la interfaz lo limita a estos roles (CustomersView). */
    public static final String CUSTOMER_EDIT = "hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'ROLE_MANAGER')";

    // Ventas y facturas
    public static final String SALES_RECENT = "hasAnyAuthority('ROLE_GESTOR', 'view.pos', 'view.invoice', 'view.customers', 'view.dashboard')";
    public static final String SALES_LIST = "hasAnyAuthority('ROLE_GESTOR', 'view.invoice')";
    public static final String SALES_SUMMARY = "hasAnyAuthority('ROLE_GESTOR', 'view.invoice', 'view.reports', 'view.dashboard')";
    public static final String SALES_EXPORT = "hasAnyAuthority('ROLE_GESTOR', 'view.reports')";
    public static final String SALE_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.pos', 'view.invoice')";
    public static final String SALE_WRITE = "hasAnyAuthority('ROLE_GESTOR', 'view.pos')";
    public static final String SALE_DELETE = "hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN')";
    public static final String SALE_PAYMENT_CREATE = "hasAnyAuthority('ROLE_GESTOR', 'view.pos', 'view.invoice', 'view.ar')";
    public static final String SALE_PAYMENTS = "hasAnyAuthority('ROLE_GESTOR', 'view.invoice', 'view.ar')";
    public static final String SALE_SERVICE_ORDER = "hasAnyAuthority('ROLE_GESTOR', 'view.service_orders', 'view.pos')";
    public static final String CREDIT_NOTES = "hasAnyAuthority('ROLE_GESTOR', 'view.invoice')";
    public static final String INVOICE_LIST = "hasAnyAuthority('ROLE_GESTOR', 'view.invoice', 'view.ar', 'view.reports', 'view.service_orders', 'view.dashboard')";
    public static final String INVOICE_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.invoice')";

    // Configuración fiscal y de la empresa
    public static final String NCF_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.settings', 'view.setup', 'settings.manage_company_config')";
    public static final String COMPANY_CONFIG_EDIT = "hasAnyAuthority('ROLE_GESTOR', 'settings.manage_company_config')";
    /** Consume un número fiscal real: solo soporte. */
    public static final String NCF_CONSUME = "hasAuthority('ROLE_GESTOR')";

    // Contabilidad y permisos
    public static final String ACCOUNTING_READ = "hasAnyAuthority('ROLE_GESTOR', 'accounting.module.view')";
    public static final String ACCOUNTING_SYNC = "hasAnyAuthority('ROLE_GESTOR', 'accounting.accounts.create')";
    public static final String PERMISSIONS_READ = "hasAnyAuthority('ROLE_GESTOR', 'view.settings', 'settings.manage_permissions', 'users.manage')";
}
