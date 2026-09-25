package com.migia.OperationsHub.dto.admin;

import com.migia.OperationsHub.model.enums.OrganizationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateOrganizationStatusRequest {
    @NotNull
    private OrganizationStatus status;
}
