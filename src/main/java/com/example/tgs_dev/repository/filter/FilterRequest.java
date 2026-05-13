package com.example.tgs_dev.repository.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterRequest {
    private List<FilterCriteria> filters = new ArrayList<>();
    private LogicOperator logic = LogicOperator.AND;
    private String sortBy;
    private String sortDirection = "ASC";
    private int page = 0;
    private int size = 20;
}
