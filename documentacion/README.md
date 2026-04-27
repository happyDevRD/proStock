# Documentacion ProStock

Esta carpeta concentra la documentacion funcional y tecnica del modulo de configuracion de empresa (emisor), requerido para soportar datos fiscales de facturacion.

## Contenido

- `company-config-modulo.md`: alcance, objetivos y comportamiento funcional del modulo.
- `company-config-api.md`: contrato de API para backend/frontend.
- `company-config-validaciones-dgii.md`: reglas de validacion y ejemplos de errores.
- `company-config-integracion-frontend.md`: guia practica para consumo desde UI.

## Contexto

El modulo `CompanyConfig` permite registrar los datos del emisor que la DGII exige en el encabezado y pie de factura:

- RNC
- Razon social
- Nombre comercial (opcional)
- Direccion
- Municipio y provincia (codigos)
- Actividad economica
- Telefono
- Correo electronico
