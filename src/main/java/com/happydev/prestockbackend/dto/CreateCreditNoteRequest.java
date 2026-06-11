package com.happydev.prestockbackend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateCreditNoteRequest {
    @Size(max = 500)
    private String reason;

    @NotEmpty
    @Valid
    private List<CreateCreditNoteItemRequest> items = new ArrayList<>();
}
