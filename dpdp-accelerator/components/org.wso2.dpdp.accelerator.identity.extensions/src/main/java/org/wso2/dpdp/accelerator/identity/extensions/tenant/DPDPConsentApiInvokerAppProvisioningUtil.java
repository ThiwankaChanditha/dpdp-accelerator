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
import org.wso2.carbon.identity.application.common.IdentityApplicationManagementException;
import org.wso2.carbon.identity.application.common.model.AssociatedRolesConfig;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationConfig;
import org.wso2.carbon.identity.application.common.model.InboundAuthenticationRequestConfig;
import org.wso2.carbon.identity.application.common.model.RoleV2;
import org.wso2.carbon.identity.application.common.model.ServiceProvider;
import org.wso2.carbon.identity.oauth.IdentityOAuthAdminException;
import org.wso2.carbon.identity.oauth.OAuthUtil;
import org.wso2.carbon.identity.oauth.dto.OAuthConsumerAppDTO;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.List;

/**
 * Registers the DPDP Consent API Invoker application - a machine-to-machine
 * ({@code client_credentials}) OAuth2 client, distinct from the browser-facing
 * {@link DPDPConsentPortalAppProvisioningUtil}'s app, meant for external systems invoking the
 * consent-mgt v2 consents API directly - it is authorized for the consents resource only, not
 * purposes/elements, which stay portal-only. Unlike the portal, it has no callback URL, no PKCE,
 * and no cookie token binding - none of those apply to a grant type with no browser redirect.
 * API-resource authorization is a separate concern - see {@link DPDPApiResourceProvisioningUtil},
 * whose narrower {@code authorizeConsentAPI} this app uses - as is
 * role creation - see {@link DPDPConsentPortalRoleProvisioningUtil}. Every method here assumes it
 * is already running inside the correct tenant's {@code PrivilegedCarbonContext} flow; that setup
 * lives in the caller ({@link DPDPIdentityExtensionTenantMgtListener}), not here.
 */
public final class DPDPConsentApiInvokerAppProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPConsentApiInvokerAppProvisioningUtil.class);
    static final String APPLICATION_NAME = "DPDP Consent API Invoker";
    private static final String[] GRANT_TYPES = {"client_credentials"};
    private static final String ASSOCIATED_ROLES_ALLOWED_AUDIENCE = "ORGANIZATION";
    private static final String OAUTH_2 = "oauth2";
    private static final String STANDARD_APP = "standardAPP";

    private DPDPConsentApiInvokerAppProvisioningUtil() {

    }

    /**
     * @return the existing application's resource ID, or {@code null} if it has not been
     * created yet for this tenant.
     */
    public static String getApplicationId(String tenantDomain) throws IdentityApplicationManagementException {

        ServiceProvider serviceProvider = DPDPIdentityExtensionDataHolder.getInstance()
                .getApplicationManagementService().getApplicationExcludingFileBasedSPs(APPLICATION_NAME,
                        tenantDomain);
        return serviceProvider == null ? null : serviceProvider.getApplicationResourceId();
    }

    public static String provisionApplication(TenantInfoBean tenantInfoBean) throws IdentityOAuthAdminException,
            IdentityApplicationManagementException {

        String tenantDomain = tenantInfoBean.getTenantDomain();
        String clientId = DPDPIdentityExtensionDataHolder.getInstance().getConfigurationService()
                .getConsentApiInvokerClientId();
        registerOAuthApplication(tenantDomain, clientId);
        return createApplication(tenantInfoBean, clientId);
    }

    /**
     * Configures the application to consume organization-audience roles by setting its Role Audience
     * to Organization and assigning the specified roles - same treatment as the portal app, so a
     * client that authenticates through some other grant later still resolves the same role set.
     */
    public static void associateOrganizationRoles(String tenantDomain, String username, List<RoleV2> roles)
            throws IdentityApplicationManagementException {

        ServiceProvider serviceProvider = DPDPIdentityExtensionDataHolder.getInstance()
                .getApplicationManagementService().getApplicationExcludingFileBasedSPs(APPLICATION_NAME,
                        tenantDomain);

        AssociatedRolesConfig associatedRolesConfig = new AssociatedRolesConfig();
        associatedRolesConfig.setAllowedAudience(ASSOCIATED_ROLES_ALLOWED_AUDIENCE);
        associatedRolesConfig.setRoles(roles.toArray(new RoleV2[0]));
        serviceProvider.setAssociatedRolesConfig(associatedRolesConfig);

        DPDPIdentityExtensionDataHolder.getInstance().getApplicationManagementService()
                .updateApplication(serviceProvider, tenantDomain, username);
        LOG.debug("Set the Role Audience to organization and associated " + roles.size()
                + " role(s) for application: " + APPLICATION_NAME + ", tenant: " + tenantDomain);
    }

    static void registerOAuthApplication(String tenantDomain, String clientId) throws IdentityOAuthAdminException {

        LOG.debug("Registering the OAuth2 application '" + clientId + "' for tenant: " + tenantDomain);
        OAuthConsumerAppDTO dto = new OAuthConsumerAppDTO();
        dto.setApplicationName(APPLICATION_NAME);
        dto.setOauthConsumerKey(clientId);
        dto.setOauthConsumerSecret(OAuthUtil.getRandomNumber());
        dto.setGrantTypes(String.join(" ", GRANT_TYPES));
        // A confidential client authenticating with its own secret - the opposite of the portal's
        // public PKCE client, which bypasses client-credential checks since it can't hold a secret.
        dto.setBypassClientCredentials(false);
        dto.setTokenType("Default");

        DPDPIdentityExtensionDataHolder.getInstance().getOAuthAdminService().registerOAuthApplicationData(dto);
    }

    static String createApplication(TenantInfoBean tenantInfoBean, String clientId)
            throws IdentityApplicationManagementException {

        LOG.debug("Creating the DPDP Consent API Invoker service provider for tenant: "
                + tenantInfoBean.getTenantDomain());
        ServiceProvider serviceProvider = new ServiceProvider();
        serviceProvider.setApplicationName(APPLICATION_NAME);
        serviceProvider.setDescription(
                "Machine-to-machine client for external systems invoking the consent-mgt v2 consents API directly.");

        InboundAuthenticationRequestConfig requestConfig = new InboundAuthenticationRequestConfig();
        requestConfig.setInboundAuthKey(clientId);
        requestConfig.setInboundAuthType(OAUTH_2);
        requestConfig.setInboundConfigType(STANDARD_APP);
        InboundAuthenticationConfig inboundAuthenticationConfig = new InboundAuthenticationConfig();
        inboundAuthenticationConfig.setInboundAuthenticationRequestConfigs(
                new InboundAuthenticationRequestConfig[]{requestConfig});
        serviceProvider.setInboundAuthenticationConfig(inboundAuthenticationConfig);

        return DPDPIdentityExtensionDataHolder.getInstance().getApplicationManagementService()
                .createApplication(serviceProvider, tenantInfoBean.getTenantDomain(), tenantInfoBean.getAdmin());
    }
}
