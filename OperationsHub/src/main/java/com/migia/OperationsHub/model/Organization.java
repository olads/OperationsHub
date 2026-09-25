package com.migia.OperationsHub.model;

import com.migia.OperationsHub.model.enums.OrganizationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "organizations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Organization extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrganizationStatus status;

    private String defaultCurrency;
    private String logoUrl;
    private String contactEmail;
    private String contactPhone;
    private String website;

    @Column(nullable = false)
    @Builder.Default
    private boolean ownerInvitationSent = false;
}
