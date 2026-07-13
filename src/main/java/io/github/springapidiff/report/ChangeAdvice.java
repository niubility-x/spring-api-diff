package io.github.springapidiff.report;

import io.github.springapidiff.diff.Change;
import io.github.springapidiff.diff.ChangeType;

public final class ChangeAdvice {
    private ChangeAdvice() {
    }

    public static String impact(Change change) {
        ChangeType type = change.type();
        if (type == null) {
            return "Review this API change with known clients before merging.";
        }
        switch (type) {
            case ENDPOINT_REMOVED:
                return "Existing clients calling this endpoint may receive 404 or routing errors.";
            case ENDPOINT_ADDED:
                return "Existing clients are not affected; new clients may start using this endpoint.";
            case PATH_VARIABLE_REMOVED:
                return "Existing route templates and generated clients may no longer match this endpoint.";
            case PATH_VARIABLE_TYPE_CHANGED:
                return "Clients may send values in the old format and fail request binding or validation.";
            case REQUIRED_QUERY_PARAM_ADDED:
                return "Existing clients that do not send this query parameter may start receiving 4xx responses.";
            case QUERY_PARAM_REMOVED:
                return "Clients still sending this parameter may get ignored behavior or validation errors, depending on server handling.";
            case QUERY_PARAM_TYPE_CHANGED:
                return "Clients may send the old query value format and fail request binding or validation.";
            case REQUEST_BODY_FIELD_REMOVED:
                return "Clients still sending this field may lose behavior if the server previously used it.";
            case REQUIRED_REQUEST_BODY_FIELD_ADDED:
                return "Existing clients that do not send this request field may start receiving validation errors.";
            case REQUEST_BODY_FIELD_TYPE_CHANGED:
                return "Clients may send the old JSON value shape and fail deserialization or validation.";
            case RESPONSE_FIELD_REMOVED:
                return "Existing clients that read this response field may show wrong data or fail deserialization.";
            case RESPONSE_FIELD_TYPE_CHANGED:
                return "Existing clients may fail deserialization or parse the response value incorrectly.";
            case RESPONSE_FIELD_ADDED:
                return "Existing clients usually keep working if they ignore unknown response fields.";
            default:
                return "Review this API change with known clients before merging.";
        }
    }

    public static String suggestion(Change change) {
        ChangeType type = change.type();
        if (type == null) {
            return "Confirm the change is intentional and document the migration path.";
        }
        switch (type) {
            case ENDPOINT_REMOVED:
                return "Keep the endpoint during a deprecation period, or coordinate all client upgrades before removal.";
            case ENDPOINT_ADDED:
                return "Document the new endpoint and add tests for its request and response contract.";
            case PATH_VARIABLE_REMOVED:
                return "Keep the old route as an alias when possible, or publish a clear migration path.";
            case PATH_VARIABLE_TYPE_CHANGED:
                return "Prefer accepting both old and new formats during migration, then remove the old format later.";
            case REQUIRED_QUERY_PARAM_ADDED:
                return "Make the parameter optional with a safe default, or introduce a new endpoint version.";
            case QUERY_PARAM_REMOVED:
                return "Keep accepting the parameter as deprecated until callers stop sending it.";
            case QUERY_PARAM_TYPE_CHANGED:
                return "Accept both old and new formats when possible, or add a new parameter name for the new type.";
            case REQUEST_BODY_FIELD_REMOVED:
                return "Keep accepting the field as deprecated, or confirm no clients still depend on it.";
            case REQUIRED_REQUEST_BODY_FIELD_ADDED:
                return "Make the field optional first, provide a default on the server, or introduce a new endpoint version.";
            case REQUEST_BODY_FIELD_TYPE_CHANGED:
                return "Support both JSON shapes during migration, or add a new field name for the new shape.";
            case RESPONSE_FIELD_REMOVED:
                return "Keep returning the field as deprecated until clients stop reading it.";
            case RESPONSE_FIELD_TYPE_CHANGED:
                return "Add a new response field for the new type and keep the old field during migration.";
            case RESPONSE_FIELD_ADDED:
                return "Ensure clients tolerate unknown fields and document the new response field.";
            default:
                return "Confirm the change is intentional and document the migration path.";
        }
    }
}
