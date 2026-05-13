package com.example.tgs_dev.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Converter
@Component
public class JSONConverter implements AttributeConverter<List<Integer>, String> {

    private final JsonMapper jsonMapper;

    public JSONConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String convertToDatabaseColumn(List<Integer> list) {
        return jsonMapper.writeValueAsString(list);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String json) {
        return jsonMapper.readValue(json, new TypeReference<List<Integer>>() {});
    }
}
