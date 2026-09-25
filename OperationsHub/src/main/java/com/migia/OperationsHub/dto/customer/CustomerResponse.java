package com.migia.OperationsHub.dto.customer;

import com.migia.OperationsHub.model.enums.CustomerStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerResponse {
    private UUID id;
    private String name;
    private String email;
    private String phone;
    private CustomerStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
