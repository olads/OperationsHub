package com.migia.OperationsHub.exception;

import com.migia.OperationsHub.model.enums.OrderStatus;

public class InvalidOrderStateException extends RuntimeException {
    public InvalidOrderStateException(OrderStatus current, OrderStatus required) {
        super("Invalid order state transition: current=" + current + ", required=" + required);
    }

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
