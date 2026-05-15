package com.example.tgs_dev.repository.filter;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

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

    /** Converts the pagination and sorting fields into a Spring Data {@link Pageable}. */
    public Pageable toPageable() {
        Sort sort = Sort.by(
                Sort.Direction.fromString(sortDirection),
                sortBy != null ? sortBy : "id"
        );
        return PageRequest.of(page, size, sort);
    }
}
