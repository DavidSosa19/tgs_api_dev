package com.example.tgs_dev.repository.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterCriteria {
    private String field;
    private FilterOperator operator;
    private Object value;
    private Object valueTo;
}
