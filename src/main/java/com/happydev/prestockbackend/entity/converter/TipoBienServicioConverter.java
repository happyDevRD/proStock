package com.happydev.prestockbackend.entity.converter;

import com.happydev.prestockbackend.entity.TipoBienServicio;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class TipoBienServicioConverter implements AttributeConverter<TipoBienServicio, Integer> {

    @Override
    public Integer convertToDatabaseColumn(TipoBienServicio attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public TipoBienServicio convertToEntityAttribute(Integer dbData) {
        return TipoBienServicio.fromCode(dbData);
    }
}
