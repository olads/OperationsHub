package com.migia.OperationsHub.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an org slug in the URL cannot be resolved to a known Organization.
 * Returns 404 (not 403) to avoid confirming or denying that a tenant exists.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String slug) {
        super("No organization found for slug: '" + slug + "'");
    }
}
