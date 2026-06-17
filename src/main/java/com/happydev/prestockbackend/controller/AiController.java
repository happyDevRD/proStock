package com.happydev.prestockbackend.controller;

import com.happydev.prestockbackend.service.AiAssistantService;
import com.happydev.prestockbackend.service.AnomalyDetectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@PreAuthorize("hasAnyAuthority('ROLE_GESTOR', 'ROLE_ADMIN', 'view.ai')")
public class AiController {

    private final AiAssistantService assistantService;
    private final AnomalyDetectionService anomalyService;

    public AiController(AiAssistantService assistantService, AnomalyDetectionService anomalyService) {
        this.assistantService = assistantService;
        this.anomalyService = anomalyService;
    }

    @GetMapping("/anomalies")
    public List<AnomalyDetectionService.AnomalyDto> anomalies() {
        return anomalyService.detectAll();
    }

    @PostMapping("/assistant/query")
    public AssistantResponse query(@Valid @RequestBody AssistantQuery body) {
        String answer = assistantService.answer(body.question());
        return new AssistantResponse(answer);
    }

    public record AssistantQuery(
            @NotBlank @Size(max = 500) String question
    ) {}

    public record AssistantResponse(String answer) {}
}
