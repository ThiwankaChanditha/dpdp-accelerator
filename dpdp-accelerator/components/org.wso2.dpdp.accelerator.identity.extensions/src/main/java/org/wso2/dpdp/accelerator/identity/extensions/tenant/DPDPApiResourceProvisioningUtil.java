/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.identity.extensions.tenant;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.api.resource.mgt.APIResourceManager;
import org.wso2.carbon.identity.api.resource.mgt.constant.APIResourceManagementConstants;
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.APIResource;
import org.wso2.carbon.identity.application.common.model.AuthorizedAPI;
import org.wso2.carbon.identity.application.common.model.Scope;
import org.wso2.carbon.identity.application.mgt.AuthorizedAPIManagementService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Registers and authorizes every API resource the DPDP Consent Portal application consumes -
 * the IS-native consent-mgt v2 APIs (authorize-only, they already exist), and this
 * accelerator's own Event Notification, Consent History, and Complaint Management APIs
 * (register + authorize, nothing pre-registers these). All four families share the same
 * register-if-absent / authorize-if-absent shape, so that shared logic lives here once instead
 * of being reimplemented per feature. Application/service-provider provisioning is a separate
 * concern - see
 * {@link DPDPConsentPortalAppProvisioningUtil} - as is role creation - see
 * {@link DPDPConsentPortalRoleProvisioningUtil}. Every method here assumes it is already running
 * inside the correct tenant's {@code PrivilegedCarbonContext} flow; that setup lives in the
 * caller ({@link DPDPIdentityExtensionTenantMgtListener}), not here.
 */
public final class DPDPApiResourceProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPApiResourceProvisioningUtil.class);

    private static final String AUTHORIZED_API_POLICY = "RBAC";

    /**
     * Every DPDP-owned or DPDP-consumed API resource is tenant-scoped, so they all share the
     * same resource type - not a per-call choice, to keep any future API family from silently
     * drifting to a different type.
     */
    private static final String API_RESOURCE_TYPE = APIResourceManagementConstants.APIResourceTypes.BUSINESS;

    private static final String[] CONSENT_MGT_API_IDENTIFIERS = {
            "/api/identity/consent-mgt/v2.0/consents",
            "/api/identity/consent-mgt/v2.0/purposes",
            "/api/identity/consent-mgt/v2.0/elements"
    };

    private static final String[] EVENT_NOTIFICATION_API_IDENTIFIERS = {
            "/api/dpdp/event-notifications/v1/topics",
            "/api/dpdp/event-notifications/v1/subscriptions",
            "/api/dpdp/event-notifications/v1/events",
            "/api/dpdp/event-notifications/v1/events/poll",
            "/api/dpdp/event-notifications/v1/deliveries"
    };
    private static final String[][] EVENT_NOTIFICATION_API_SCOPES = {
            {"notifications:topics:read", "notifications:topics:write"},
            {"notifications:subscriptions:read", "notifications:subscriptions:write"},
            {"notifications:events:read", "notifications:events:write"},
            {"notifications:events:poll"},
            {"notifications:event-deliveries:complete"}
    };

    private static final String CONSENT_HISTORY_API_IDENTIFIER = "/api/dpdp/consent-mgt/v1";
    private static final String CONSENT_HISTORY_API_NAME = "DPDP Consent History";

    public static final String STATUS_HISTORY_VIEW_ANY = "consent:status-history:view:any";
    public static final String STATUS_HISTORY_VIEW_SELF = "consent:status-history:view:self";
    public static final String HISTORY_VIEW_ANY = "consent:history:view:any";
    public static final String HISTORY_VIEW_SELF = "consent:history:view:self";

    private static final String COMPLAINT_API_IDENTIFIER = "/api/dpdp/complaints";
    private static final String COMPLAINT_API_NAME = "DPDP Complaint Management API";

    public static final String COMPLAINTS_READ_SELF = "complaints:read:self";
    public static final String COMPLAINTS_WRITE_SELF = "complaints:write:self";
    public static final String COMPLAINTS_READ_ANY = "complaints:read:any";
    public static final String COMPLAINTS_WRITE_ANY = "complaints:write:any";
    private static final String ACCOUNT_SELF_SERVICE_API_IDENTIFIER = "/api/dpdp/account/v1/self";
    private static final String ACCOUNT_SELF_SERVICE_API_NAME = "DPDP Account Self-Service";

    /**
     * Guards {@code DELETE /scim2/Me}. The resource below is virtual - it never serves traffic
     * and exists only to mint this scope, which a deployment.toml resource.access_control rule
     * requires in place of the stock internal_user_mgt_delete. That default would also authorize
     * {@code DELETE /scim2/Users/{id}}, letting any portal user delete anybody.
     */
    public static final String ACCOUNT_SELF_DELETE = "account:self:delete";

    private DPDPApiResourceProvisioningUtil() {

    }

    /**
     * Authorizes whichever of the three IS-native consent-mgt APIs aren't already authorized,
     * and returns all their scope names. These are never registered by us - they already exist.
     */
    public static List<String> authorizeConsentManagementAPIs(String applicationId, String tenantDomain)
            throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, CONSENT_MGT_API_IDENTIFIERS);
    }

    /**
     * Authorizes only the IS-native consent-mgt v2 "consents" resource - narrower than
     * {@link #authorizeConsentManagementAPIs}, which also authorizes purposes and elements.
     * For consumers like the Consent API Invoker machine-to-machine app that only ever call
     * consent endpoints directly.
     */
    public static List<String> authorizeConsentAPI(String applicationId, String tenantDomain) throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, new String[]{CONSENT_MGT_API_IDENTIFIERS[0]});
    }

    /**
     * Registers the Event Notification API resources for a tenant when they are not already
     * present. This is intentionally idempotent because tenant provisioning can be retried.
     */
    public static void registerEventNotificationAPIs(String tenantDomain) throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        for (int i = 0; i < EVENT_NOTIFICATION_API_IDENTIFIERS.length; i++) {
            String identifier = EVENT_NOTIFICATION_API_IDENTIFIERS[i];
            List<Scope> scopes = new ArrayList<>();
            for (String scopeName : EVENT_NOTIFICATION_API_SCOPES[i]) {
                scopes.add(new Scope(null, scopeName, scopeName, "Event Notification API scope: " + scopeName));
            }
            registerApiResourceIfAbsent(apiResourceManager, tenantDomain, identifier,
                    "DPDP Event Notification API " + resourceName(identifier),
                    "DPDP Event Notification " + resourceName(identifier) + " API", scopes);
        }
    }

    /**
     * Authorizes the Event Notification API resources for the tenant's portal application.
     * The operation is idempotent and preserves any existing authorization.
     */
    public static List<String> authorizeEventNotificationAPIs(String applicationId, String tenantDomain)
            throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, EVENT_NOTIFICATION_API_IDENTIFIERS);
    }

    /**
     * Registers this accelerator's own {@code /api/dpdp/consent-mgt/v1} API resource with its 4
     * scopes if it doesn't already exist for this tenant.
     */
    public static void registerConsentHistoryApi(String tenantDomain) throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        List<Scope> scopes = Arrays.asList(
                new Scope(null, STATUS_HISTORY_VIEW_ANY, "View any consent's status-audit history",
                        "Read access to any consent's status-audit trail."),
                new Scope(null, STATUS_HISTORY_VIEW_SELF, "View your own consent's status-audit history",
                        "Read access to your own consent's status-audit trail."),
                new Scope(null, HISTORY_VIEW_ANY, "View any consent's full snapshot history",
                        "Read access to any consent's full pre/post-mutation snapshot history."),
                new Scope(null, HISTORY_VIEW_SELF, "View your own consent's full snapshot history",
                        "Read access to your own consent's full pre/post-mutation snapshot history."));
        registerApiResourceIfAbsent(apiResourceManager, tenantDomain, CONSENT_HISTORY_API_IDENTIFIER,
                CONSENT_HISTORY_API_NAME, "Read APIs for DPDP consent status-audit and full-snapshot history.",
                scopes);
    }

    /**
     * Authorizes the application for the Consent History API resource if not already authorized,
     * and returns its scope names.
     */
    public static List<String> authorizeConsentHistoryApi(String applicationId, String tenantDomain)
            throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, new String[]{CONSENT_HISTORY_API_IDENTIFIER});
    }

    /**
     * Registers this accelerator's own {@code /api/dpdp/complaints} API resource with its 4
     * scopes if it doesn't already exist for this tenant.
     */
    public static void registerComplaintManagementApi(String tenantDomain) throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        List<Scope> scopes = Arrays.asList(
                new Scope(null, COMPLAINTS_READ_SELF, "Read own complaints",
                        "View the authenticated Data Principal's own complaints."),
                new Scope(null, COMPLAINTS_WRITE_SELF, "Write own complaints",
                        "Create, comment on, or transition the status of the authenticated Data Principal's own "
                                + "complaints."),
                new Scope(null, COMPLAINTS_READ_ANY, "Read any complaint",
                        "View any complaint in the organization (officer/admin)."),
                new Scope(null, COMPLAINTS_WRITE_ANY, "Write any complaint",
                        "Create, comment on, or transition the status of any complaint in the organization "
                                + "(officer/admin)."));
        registerApiResourceIfAbsent(apiResourceManager, tenantDomain, COMPLAINT_API_IDENTIFIER, COMPLAINT_API_NAME,
                "Grievance redressal API for the DPDP Consent Portal - see complaint-server-API.yaml in "
                        + "org.wso2.dpdp.accelerator.complaint.mgt.endpoint.", scopes);
    }

    /**
     * Authorizes the application for the Complaint Management API resource if not already
     * authorized, and returns its scope names.
     */
    public static List<String> authorizeComplaintManagementApi(String applicationId, String tenantDomain)
            throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, new String[]{COMPLAINT_API_IDENTIFIER});
    }

    /**
     * Registers the virtual account self-service API resource, so the tenant has the
     * {@code account:self:delete} scope. Idempotent, like every other registration here.
     */
    public static void registerAccountSelfServiceApi(String tenantDomain) throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        List<Scope> scopes = Collections.singletonList(
                new Scope(null, ACCOUNT_SELF_DELETE, "Delete your own account",
                        "Permission to delete your own user account."));
        registerApiResourceIfAbsent(apiResourceManager, tenantDomain, ACCOUNT_SELF_SERVICE_API_IDENTIFIER,
                ACCOUNT_SELF_SERVICE_API_NAME, "Self-service operations a user performs on their own account.",
                scopes);
    }

    /**
     * Authorizes the application for the account self-service API resource if not already
     * authorized, and returns its scope names. Kept separate from the administrative
     * authorizations because its scope goes to the user role only.
     */
    public static List<String> authorizeAccountSelfServiceApi(String applicationId, String tenantDomain)
            throws Exception {

        return authorizeAPIs(applicationId, tenantDomain, new String[]{ACCOUNT_SELF_SERVICE_API_IDENTIFIER});
    }

    private static void registerApiResourceIfAbsent(APIResourceManager apiResourceManager, String tenantDomain,
            String identifier, String name, String description, List<Scope> scopes) throws Exception {

        if (apiResourceManager.getAPIResourceByIdentifier(identifier, tenantDomain) != null) {
            LOG.debug("API resource '" + identifier + "' already exists for tenant: " + tenantDomain);
            return;
        }
        APIResource resource = new APIResource.APIResourceBuilder()
                .name(name)
                .identifier(identifier)
                .description(description)
                .type(API_RESOURCE_TYPE)
                .requiresAuthorization(true)
                .scopes(scopes)
                .build();
        apiResourceManager.addAPIResource(resource, tenantDomain);
        LOG.debug("Registered API resource '" + identifier + "' for tenant: " + tenantDomain);
    }

    private static String resourceName(String identifier) {

        String[] parts = identifier.split("/");
        return parts[parts.length - 1];
    }

    private static List<String> authorizeAPIs(String applicationId, String tenantDomain, String[] identifiers)
            throws Exception {

        APIResourceManager apiResourceManager = DPDPIdentityExtensionDataHolder.getInstance().getApiResourceManager();
        AuthorizedAPIManagementService authorizedAPIManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getAuthorizedAPIManagementService();

        List<String> authorizedScopeNames = new ArrayList<>();
        for (String identifier : identifiers) {
            APIResource apiResource = apiResourceManager.getAPIResourceByIdentifier(identifier, tenantDomain);
            if (apiResource == null) {
                throw new IdentityApplicationManagementException("API resource not registered: " + identifier
                        + ". For consent-mgt v2 APIs, confirm [consent_mgt] enable_v2_api = true in this tenant's "
                        + "deployment; for DPDP-owned APIs, confirm registration ran before authorization.");
            }

            AuthorizedAPI existingAuthorization = authorizedAPIManagementService.getAuthorizedAPI(applicationId,
                    apiResource.getId(), tenantDomain);
            List<Scope> scopes;
            if (existingAuthorization != null) {
                scopes = existingAuthorization.getScopes();
                LOG.debug("API '" + identifier + "' is already authorized for application: " + applicationId);
            } else {
                scopes = apiResource.getScopes();
                AuthorizedAPI authorizedAPI = new AuthorizedAPI(applicationId, apiResource.getId(),
                        AUTHORIZED_API_POLICY, scopes, apiResource.getType());
                authorizedAPIManagementService.addAuthorizedAPI(applicationId, authorizedAPI, tenantDomain);
                LOG.debug("Authorized API '" + identifier + "' (" + scopes.size() + " scope(s)) for application: "
                        + applicationId);
            }

            for (Scope scope : scopes) {
                authorizedScopeNames.add(scope.getName());
            }
        }
        return authorizedScopeNames;
    }
}
