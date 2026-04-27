package com.happydev.prestockbackend.entity.converter;

import com.happydev.prestockbackend.entity.TipoIngresos;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TipoIngresosConverter implements AttributeConverter<TipoIngresos, String> {

    @Override
    public String convertToDatabaseColumn(TipoIngresos attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TipoIngresos convertToEntityAttribute(String dbData) {
        return TipoIngresos.fromCode(dbData);
    }
}
