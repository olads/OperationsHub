package com.migia.OperationsHub.dto.organization;

import lombok.Data;

@Data
public class UpdateOrganizationSettingsRequest {
    private String defaultCurrency;
    private String logoUrl;
    private String contactEmail;
    private String contactPhone;
    private String website;
}
