package com.happydev.prestockbackend.feature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Catálogo de features de la Fase 1 de modularización. Las raíces {@code module.*}
 * corresponden a los antiguos valores de {@code AppModule}; las features hijas son
 * funcionalidades concretas que tiene sentido apagar a nivel de instancia.
 *
 * <p>Para agregar una feature nueva basta con añadirla a {@link #ALL} — no requiere
 * migración salvo que una instancia quiera desactivarla (en cuyo caso se crea una
 * fila de override en {@code company_feature_config}).
 */
public final class FeatureCatalog {

    public static final List<FeatureDefinition> ALL = List.of(
            new FeatureDefinition("module.pos", "POS", "Punto de venta",
                    "Acceso al punto de venta y registro de ventas", true, Set.of()),

            new FeatureDefinition("module.invoice", "INVOICE", "Facturación",
                    "Historial y detalle de facturas", true, Set.of()),
            new FeatureDefinition("invoice.ncf", "INVOICE", "Comprobantes fiscales (NCF)",
                    "Generación y control de secuencias de comprobantes fiscales", true,
                    Set.of("module.invoice")),
            new FeatureDefinition("invoice.thermal_receipt", "INVOICE", "Recibo térmico",
                    "Impresión de recibos en formato térmico", true, Set.of("module.invoice")),

            new FeatureDefinition("module.inventory", "INVENTORY", "Inventario",
                    "Gestión de productos y existencias", true, Set.of()),
            new FeatureDefinition("inventory.csv_import", "INVENTORY", "Importación CSV",
                    "Permite importar productos desde un archivo CSV", true,
                    Set.of("module.inventory")),

            new FeatureDefinition("module.suppliers", "SUPPLIERS", "Suplidores",
                    "Gestión de proveedores y compras", true, Set.of()),

            new FeatureDefinition("module.reports", "REPORTS", "Reportes",
                    "Reportes y análisis de ventas e inventario", true, Set.of()),

            new FeatureDefinition("module.accounting", "ACCOUNTING", "Contabilidad",
                    "Plan de cuentas, asientos contables y estados financieros", true, Set.of()),

            new FeatureDefinition("module.service_orders", "SERVICE_ORDERS", "Órdenes de servicio",
                    "Seguimiento de órdenes de servicio (reparación, fotografía, etc.)", false, Set.of()),

            new FeatureDefinition("module.quotes", "QUOTES", "Cotizaciones",
                    "Módulo de cotizaciones / presupuestos y conversión a venta", false, Set.of()),

            new FeatureDefinition("module.locations", "LOCATIONS", "Multi-sucursal",
                    "Gestión de sucursales y almacenes, stock por ubicación y transferencias", false, Set.of()),

            new FeatureDefinition("module.portal", "PORTAL", "Portal del cliente",
                    "Portal público para que clientes vean sus facturas y órdenes de servicio", false, Set.of()),
            new FeatureDefinition("portal.view_invoices", "PORTAL", "Ver facturas en el portal",
                    "Los clientes pueden consultar su historial de facturas desde el portal", true,
                    Set.of("module.portal")),
            new FeatureDefinition("portal.view_service_orders", "PORTAL", "Ver órdenes de servicio en el portal",
                    "Los clientes pueden ver el estado de sus órdenes de servicio desde el portal", true,
                    Set.of("module.portal")),

            new FeatureDefinition("module.expenses", "EXPENSES", "Gastos",
                    "Registro de gastos directos (sin orden de compra): caja chica, servicios, alquileres", true, Set.of()),

            new FeatureDefinition("module.ai", "AI", "IA aplicada",
                    "Análisis inteligente: asistente en lenguaje natural y detección de anomalías", false, Set.of()),
            new FeatureDefinition("ai.assistant", "AI", "Asistente conversacional",
                    "Chatbot que responde preguntas sobre ventas, inventario y reportes en lenguaje natural", true,
                    Set.of("module.ai")),
            new FeatureDefinition("ai.anomalies", "AI", "Detección de anomalías",
                    "Identifica automáticamente patrones inusuales en ventas, descuentos e inventario", true,
                    Set.of("module.ai"))
    );

    private static final Map<String, FeatureDefinition> BY_CODE = ALL.stream()
            .collect(Collectors.toMap(FeatureDefinition::code, f -> f));

    static {
        validateDependencyGraph();
    }

    private FeatureCatalog() {
    }

    public static Optional<FeatureDefinition> byCode(String code) {
        return Optional.ofNullable(BY_CODE.get(code));
    }

    public static boolean exists(String code) {
        return BY_CODE.containsKey(code);
    }

    /** Agrupa el catálogo por categoría, preservando el orden de declaración. */
    public static Map<String, List<FeatureDefinition>> byCategory() {
        return ALL.stream().collect(Collectors.groupingBy(
                FeatureDefinition::category, LinkedHashMap::new, Collectors.toList()));
    }

    private static void validateDependencyGraph() {
        for (FeatureDefinition feature : ALL) {
            for (String dependency : feature.dependsOn()) {
                if (!BY_CODE.containsKey(dependency)) {
                    throw new IllegalStateException(
                            "FeatureCatalog: '" + feature.code() + "' depende de '" + dependency
                                    + "', que no existe en el catálogo");
                }
            }
        }
    }
}
