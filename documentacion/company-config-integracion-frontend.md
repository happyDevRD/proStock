# Integracion frontend: Company Config

## Flujo recomendado

1. Al abrir la pantalla de configuracion, llamar `GET /api/company-config`.
2. Si responde `404`, mostrar formulario vacio en modo creacion.
3. Si responde `200`, precargar formulario para edicion.
4. Al guardar, enviar `PUT /api/company-config`.

## Modelo sugerido (TypeScript)

```ts
export interface CompanyConfig {
  id?: number;
  rnc: string;
  razonSocial: string;
  nombreComercial?: string;
  direccion: string;
  municipioCodigo: string;
  provinciaCodigo: string;
  actividadEconomica: string;
  numeroTelefono: string;
  correoElectronico: string;
}
```

## Cliente HTTP sugerido

```ts
export async function getCompanyConfig(): Promise<CompanyConfig | null> {
  const response = await fetch("/api/company-config", {
    method: "GET",
    headers: {
      Authorization: `Basic ${btoa("admin:adminpassword")}`
    }
  });

  if (response.status === 404) return null;
  if (!response.ok) throw new Error("No se pudo cargar la configuracion");
  return response.json();
}

export async function saveCompanyConfig(payload: CompanyConfig): Promise<CompanyConfig> {
  const response = await fetch("/api/company-config", {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Basic ${btoa("admin:adminpassword")}`
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) throw new Error("No se pudo guardar la configuracion");
  return response.json();
}
```

## UX recomendada

- Mostrar mensajes por campo cuando el backend responda `VALIDATION_FAILED`.
- Bloquear boton guardar durante la peticion.
- Confirmar visualmente cuando se actualiza correctamente.
