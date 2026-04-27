package com.happydev.prestockbackend.dto;

import com.happydev.prestockbackend.entity.TipoIdentificacion;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerDto {
    private Long id;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phoneNumber;
    private String address;

    @NotBlank(message = "RNC/Cedula es obligatorio")
    @Pattern(regexp = "^\\d{9}(\\d{2})?$", message = "RNC/Cedula debe tener 9 u 11 digitos")
    private String rncCedula;

    @NotNull(message = "Tipo de identificacion es obligatorio")
    private TipoIdentificacion tipoIdentificacion;
}
