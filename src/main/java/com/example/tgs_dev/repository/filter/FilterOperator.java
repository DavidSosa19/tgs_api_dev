package com.example.tgs_dev.repository.filter;

public enum FilterOperator {
    EQUALS, NOT_EQUALS,
    LIKE, NOT_LIKE,
    GREATER_THAN, GREATER_THAN_OR_EQUAL,
    LESS_THAN, LESS_THAN_OR_EQUAL,
    IN, BETWEEN,
    IS_NULL, IS_NOT_NULL
}
