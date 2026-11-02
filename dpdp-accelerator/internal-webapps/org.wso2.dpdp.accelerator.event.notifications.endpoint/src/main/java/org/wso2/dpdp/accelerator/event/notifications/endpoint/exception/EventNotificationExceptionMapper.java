/**
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 * <p>
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.event.notifications.endpoint.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.dpdp.accelerator.common.util.LogSanitizer;
import org.wso2.dpdp.accelerator.event.notifications.common.exception.service.EventNotificationServiceException;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.constants.EventNotificationEndpointErrorCodes;
import org.wso2.dpdp.accelerator.event.notifications.endpoint.v1.dto.Error;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.UUID;

import javax.validation.ConstraintViolationException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class EventNotificationExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Log LOG = LogFactory.getLog(EventNotificationExceptionMapper.class);

    @Context
    private UriInfo uriInfo;

    public void setUriInfo(UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    @Override
    public Response toResponse(Throwable exception) {

        String traceId = UUID.randomUUID().toString();

        if (exception instanceof EventNotificationServiceException) {
            return handleEventNotificationException((EventNotificationServiceException) exception, traceId);
        }

        Throwable rootCause = unwrap(exception);

        if (rootCause instanceof EventNotificationServiceException) {
            return handleEventNotificationException((EventNotificationServiceException) rootCause, traceId);
        }

        if (rootCause instanceof WebApplicationException) {
            return handleWebApplicationException((WebApplicationException) rootCause, traceId);
        }

        if (rootCause instanceof ConstraintViolationException) {
            return handleConstraintViolation((ConstraintViolationException) rootCause, traceId);
        }

        if (rootCause instanceof JsonProcessingException) {
            return handleJsonProcessingException((JsonProcessingException) rootCause, traceId);
        }

        if (rootCause instanceof IllegalArgumentException) {
            LOG.debug("Invalid request argument [traceId=" + traceId + "]: "
                    + LogSanitizer.sanitize(rootCause.getMessage()));
            return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                    EventNotificationEndpointErrorCodes.INVALID_REQUEST_PARAMETER,
                    "Invalid request parameter", rootCause.getMessage(), traceId);
        }

        LOG.error("Unhandled exception in Event Notification endpoint [traceId=" + traceId + "]", exception);
        return buildResponse(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                EventNotificationEndpointErrorCodes.INTERNAL_SERVER_ERROR,
                "Internal server error", "An unexpected error occurred.", traceId);
    }

    private Response handleEventNotificationException(EventNotificationServiceException ex, String traceId) {
        if (ex.getStatusCode() >= 500) {
            LOG.error("Service error [" + LogSanitizer.sanitize(ex.getCode()) + "] [traceId=" + traceId + "]: "
                    + LogSanitizer.sanitize(ex.getMessage()), ex);
        } else {
            LOG.debug("Service error [" + LogSanitizer.sanitize(ex.getCode()) + "] [traceId=" + traceId + "]: "
                    + LogSanitizer.sanitize(ex.getMessage()));
        }
        return buildResponse(ex.getStatusCode(), ex.getCode(), ex.getMessage(), ex.getDescription(), traceId);
    }

    private Response handleWebApplicationException(WebApplicationException wae, String traceId) {
        int status = wae.getResponse().getStatus();
        LOG.debug("JAX-RS exception [" + status + "] [traceId=" + traceId + "]: "
                + LogSanitizer.sanitize(wae.getMessage()));
        return buildResponse(status, EventNotificationEndpointErrorCodes.forHttpStatus(status),
                wae.getMessage() != null ? wae.getMessage() : Response.Status.fromStatusCode(status).getReasonPhrase(),
                null, traceId);
    }

    private Response handleConstraintViolation(ConstraintViolationException cve, String traceId) {
        String detail = cve.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(cve.getMessage());

        LOG.debug("Validation failure [traceId=" + traceId + "]: " + LogSanitizer.sanitize(detail));
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                EventNotificationEndpointErrorCodes.VALIDATION_FAILURE,
                "Request failed validation", detail, traceId);
    }

    private Response handleJsonProcessingException(JsonProcessingException jpe, String traceId) {
        String detail = jpe.getOriginalMessage();
        if (jpe instanceof UnrecognizedPropertyException) {
            UnrecognizedPropertyException upe = (UnrecognizedPropertyException) jpe;
            detail = "Unrecognized field '" + upe.getPropertyName() + "' in request payload.";
        }

        LOG.debug("Malformed request payload [traceId=" + traceId + "]: " + LogSanitizer.sanitize(detail));
        return buildResponse(Response.Status.BAD_REQUEST.getStatusCode(),
                EventNotificationEndpointErrorCodes.MALFORMED_REQUEST,
                "Malformed request payload", detail, traceId);
    }

    protected Response buildResponse(int status, String code, String message, String description, String traceId) {
        if (isV11Request()) {
            org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Error body =
                    new org.wso2.dpdp.accelerator.event.notifications.endpoint.v1_1.dto.Error()
                            .code(code).message(message).description(description).traceId(traceId);

            return Response.status(status > 0 ? status : 500)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(body)
                    .build();
        }

        Error body = new Error().code(code).message(message).description(description);

        return Response.status(status > 0 ? status : 500)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }

    private boolean isV11Request() {
        if (uriInfo != null && uriInfo.getRequestUri() != null) {
            String path = uriInfo.getRequestUri().getPath();
            return path != null && (path.contains("/v1.1/") || path.endsWith("/v1.1"));
        }
        return false;
    }

    private Throwable unwrap(Throwable exception) {
        Throwable current = exception;
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());

        while (current != null) {
            if (!visited.add(current)) {
                break; // Cycle detected
            }
            if (current instanceof EventNotificationServiceException) {
                return current;
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }
        return current != null ? current : exception;
    }
}
