package com.happydev.prestockbackend.entity.converter;

import com.happydev.prestockbackend.entity.IndicadorFacturacion;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class IndicadorFacturacionConverter implements AttributeConverter<IndicadorFacturacion, Integer> {

    @Override
    public Integer convertToDatabaseColumn(IndicadorFacturacion attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public IndicadorFacturacion convertToEntityAttribute(Integer dbData) {
        return IndicadorFacturacion.fromCode(dbData);
    }
}
