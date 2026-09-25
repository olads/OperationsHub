package com.migia.OperationsHub.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum Role {
    PLATFORM_ADMIN,
    ORGANIZATION_OWNER,
    ORGANIZATION_ADMIN,
    EMPLOYEE,
    CUSTOMER;

    public Set<Permission> getPermissions() {
        switch (this) {
            case PLATFORM_ADMIN:
                return EnumSet.allOf(Permission.class);
            case ORGANIZATION_OWNER:
                return EnumSet.allOf(Permission.class);
            case ORGANIZATION_ADMIN:
                return EnumSet.of(
                        Permission.VIEW_MEMBERS,
                        Permission.INVITE_MEMBERS,
                        Permission.REMOVE_MEMBERS,
                        Permission.CHANGE_MEMBER_ROLE,
                        Permission.VIEW_INVENTORY,
                        Permission.MANAGE_INVENTORY,
                        Permission.VIEW_ORDERS,
                        Permission.MANAGE_ORDERS,
                        Permission.VIEW_INVOICES,
                        Permission.MANAGE_INVOICES,
                        Permission.VIEW_PAYMENTS,
                        Permission.MANAGE_PAYMENTS,
                        Permission.VIEW_CUSTOMERS,
                        Permission.MANAGE_CUSTOMERS,
                        Permission.VIEW_PRODUCTS,
                        Permission.MANAGE_PRODUCTS,
                        Permission.MANAGE_ORGANIZATION_SETTINGS,
                        Permission.MANAGE_EMPLOYEES,
                        Permission.VIEW_EMPLOYEES
                );
            case EMPLOYEE:
                return EnumSet.of(
                        Permission.VIEW_MEMBERS,
                        Permission.VIEW_INVENTORY,
                        Permission.VIEW_ORDERS,
                        Permission.VIEW_INVOICES,
                        Permission.VIEW_PAYMENTS,
                        Permission.VIEW_CUSTOMERS,
                        Permission.VIEW_PRODUCTS,
                        Permission.VIEW_EMPLOYEES
                );
            case CUSTOMER:
                return EnumSet.of(
                        Permission.VIEW_PRODUCTS,
                        Permission.CREATE_ORDER,
                        Permission.VIEW_OWN_ORDERS
                );
            default:
                return EnumSet.noneOf(Permission.class);
        }
    }
}

