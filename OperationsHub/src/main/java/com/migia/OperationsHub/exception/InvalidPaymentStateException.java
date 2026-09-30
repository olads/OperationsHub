package com.migia.OperationsHub.exception;

import com.migia.OperationsHub.model.enums.PaymentStatus;

public class InvalidPaymentStateException extends RuntimeException {
    public InvalidPaymentStateException(PaymentStatus current, PaymentStatus required) {
        super("Invalid payment state transition: current=" + current + ", required=" + required);
    }

    public InvalidPaymentStateException(String message) {
        super(message);
    }
}
