package com.example.tgs_dev.controller.exception;

import org.springframework.http.HttpStatus;

public class BusinessException extends AppException {
    public BusinessException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT);
    }
}
