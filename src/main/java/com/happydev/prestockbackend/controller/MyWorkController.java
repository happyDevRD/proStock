package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.dto.MyWorkDtos.MyWorkNoteRequest;
import com.happydev.prestockbackend.dto.MyWorkDtos.MyWorkResponse;
import com.happydev.prestockbackend.service.MyWorkService;
import com.happydev.prestockbackend.service.ServiceOrderAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/** "Mis trabajos" para fotógrafos y editores: solo sus órdenes y sus pasos. */
@RestController
@RequestMapping("/api/my-work")
@PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'view.my_work')")
public class MyWorkController {

    private final MyWorkService myWorkService;
    private final ServiceOrderAccessGuard accessGuard;

    public MyWorkController(MyWorkService myWorkService, ServiceOrderAccessGuard accessGuard) {
        this.myWorkService = myWorkService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<MyWorkResponse> list(Principal principal) {
        accessGuard.requireEnabled();
        return ResponseEntity.ok(myWorkService.list(principal.getName()));
    }

    /** {@code stage} = etapa destino que vio el usuario (protege de doble clic y pantallas viejas). */
    @PostMapping("/{orderId}/advance")
    public ResponseEntity<Void> advance(@PathVariable Long orderId,
                                        @RequestParam(required = false) String stage,
                                        Principal principal) {
        accessGuard.requireEnabled();
        myWorkService.advance(orderId, stage, principal.getName());
        // Sin cuerpo: la carpeta se mueve después del commit y esta petición tiene la orden en caché;
        // el cliente vuelve a pedir la lista.
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orderId}/notes")
    public ResponseEntity<Void> addNote(@PathVariable Long orderId,
                                        @Valid @RequestBody MyWorkNoteRequest request,
                                        Principal principal) {
        accessGuard.requireEnabled();
        myWorkService.addNote(orderId, request.content(), principal.getName());
        return ResponseEntity.noContent().build();
    }
}
