package com.migia.OperationsHub.dto.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class FieldViolation {
    private final String field;
    private final String message;
}
