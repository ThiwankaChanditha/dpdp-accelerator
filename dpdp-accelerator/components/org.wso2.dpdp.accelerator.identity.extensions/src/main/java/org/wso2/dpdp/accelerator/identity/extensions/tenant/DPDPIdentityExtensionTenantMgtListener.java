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
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.application.common.model.RoleV2;
import org.wso2.carbon.identity.organization.management.service.util.OrganizationManagementUtil;
import org.wso2.carbon.stratos.common.beans.TenantInfoBean;
import org.wso2.carbon.stratos.common.exception.StratosException;
import org.wso2.carbon.stratos.common.listeners.TenantMgtListener;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigurationService;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;
import org.wso2.dpdp.accelerator.identity.extensions.notification.EmailTemplateProvisioningUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Registers the DPDP Consent Portal application in every newly created tenant, the same way
 * {@code org.wso2.identity.apps.common.listner.AppPortalTenantMgtListener} registers Console and
 * My Account, and reconciles the Event Notification system topics for that tenant via
 * {@link EventNotificationTopicProvisioner}. The only {@link TenantMgtListener} this module
 * registers - both concerns run from the one tenant-flow to avoid two independent listeners
 * doing their own {@code PrivilegedCarbonContext} setup. Runs in-process, so it never touches
 * the management REST API layer.
 */
public class DPDPIdentityExtensionTenantMgtListener implements TenantMgtListener {

    private static final Log LOG = LogFactory.getLog(DPDPIdentityExtensionTenantMgtListener.class);

    @Override
    public void onTenantCreate(TenantInfoBean tenantInfoBean) throws StratosException {

        String tenantDomain = sanitize(tenantInfoBean.getTenantDomain());
        try {
            if (OrganizationManagementUtil.isOrganization(tenantInfoBean.getTenantId())) {
                LOG.debug("Skipping DPDP Consent Portal provisioning for organization tenant: " + tenantDomain);
                return;
            }
            provisionTenant(tenantInfoBean);
        } catch (Exception e) {
            LOG.error("Error provisioning the DPDP Consent Portal for tenant: " + tenantDomain, e);
            throw new StratosException("Error provisioning the DPDP Consent Portal for tenant: " + tenantDomain, e);
        }
    }

    @Override
    public void onTenantUpdate(TenantInfoBean tenantInfoBean) {

        // Log and continue - a provisioning failure shouldn't block the tenant update itself.
        try {
            if (OrganizationManagementUtil.isOrganization(tenantInfoBean.getTenantId())) {
                LOG.debug("Skipping DPDP Consent Portal provisioning for organization tenant: "
                        + sanitize(tenantInfoBean.getTenantDomain()));
                return;
            }
            provisionTenant(tenantInfoBean);
        } catch (Exception e) {
            LOG.error("Error provisioning the DPDP Consent Portal for tenant: "
                    + sanitize(tenantInfoBean.getTenantDomain()), e);
        }
    }

    /**
     * Reconciles the Event Notification system topics, then creates the portal app, its API
     * authorization and its roles for one tenant, or repairs whatever's missing if the app
     * already exists. Safe to re-run since every step is idempotent. Topic reconciliation and
     * portal provisioning are each independently toggleable and gated by their own config flag.
     */
    public static void provisionTenant(TenantInfoBean tenantInfoBean) throws Exception {

        String tenantDomain = sanitize(tenantInfoBean.getTenantDomain());

        // Each independently toggleable - checked before starting the tenant flow so a tenant
        // with both disabled never pays for PrivilegedCarbonContext setup at all.
        DPDPConfigurationService configurationService = DPDPIdentityExtensionDataHolder.getInstance()
                .getConfigurationService();
        boolean provisionTopics = configurationService.isEventNotificationSystemTopicsAutoCreateEnabled();
        boolean provisionPortal = configurationService.isConsentPortalProvisioningEnabled();
        if (!provisionTopics && !provisionPortal) {
            LOG.debug("Event Notification system topic provisioning and DPDP Consent Portal provisioning are "
                    + "both disabled; skipping tenant: " + tenantDomain);
            return;
        }

        PrivilegedCarbonContext.startTenantFlow();
        try {
            PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
            carbonContext.setTenantId(tenantInfoBean.getTenantId());
            carbonContext.setTenantDomain(tenantDomain);
            carbonContext.setUsername(tenantInfoBean.getAdmin());

            if (provisionTopics) {
                EventNotificationTopicProvisioner.provisionSystemTopics(tenantDomain);
            }

            if (!provisionPortal) {
                LOG.debug("DPDP Consent Portal provisioning is disabled; skipping tenant: " + tenantDomain);
                return;
            }

            String applicationId = DPDPConsentPortalAppProvisioningUtil.getApplicationId(tenantDomain);
            if (applicationId == null) {
                applicationId = DPDPConsentPortalAppProvisioningUtil.provisionApplication(tenantInfoBean);
            } else {
                LOG.debug("The DPDP Consent Portal application already exists for tenant: " + tenantDomain
                        + "; reconciling its API authorization and roles.");
            }

            DPDPApiResourceProvisioningUtil.registerEventNotificationAPIs(tenantDomain);
            DPDPApiResourceProvisioningUtil.registerConsentHistoryApi(tenantDomain);
            DPDPApiResourceProvisioningUtil.registerAccountSelfServiceApi(tenantDomain);
            DPDPApiResourceProvisioningUtil.registerComplaintManagementApi(tenantDomain);
            EmailTemplateProvisioningUtil.provisionTemplates(tenantDomain);

            List<String> consentMgtScopes = DPDPApiResourceProvisioningUtil
                    .authorizeConsentManagementAPIs(applicationId, tenantDomain);
            List<String> eventNotificationScopes = DPDPApiResourceProvisioningUtil
                    .authorizeEventNotificationAPIs(applicationId, tenantDomain);
            List<String> consentHistoryScopes = DPDPApiResourceProvisioningUtil
                    .authorizeConsentHistoryApi(applicationId, tenantDomain);
            List<String> complaintScopes = DPDPApiResourceProvisioningUtil
                    .authorizeComplaintManagementApi(applicationId, tenantDomain);

            List<String> adminScopes = new ArrayList<>(consentMgtScopes);
            adminScopes.addAll(consentHistoryScopes);
            adminScopes.addAll(eventNotificationScopes);
            // Self-delete goes to the user role only - an admin token must never carry it.
            List<String> userScopes = new ArrayList<>(
                    Arrays.asList(DPDPApiResourceProvisioningUtil.STATUS_HISTORY_VIEW_SELF,
                            DPDPApiResourceProvisioningUtil.HISTORY_VIEW_SELF));
            userScopes.addAll(DPDPApiResourceProvisioningUtil
                    .authorizeAccountSelfServiceApi(applicationId, tenantDomain));
            // The DPO role only ever needs the "any" complaint scopes - never the full admin set,
            // so it's built directly from the constants rather than filtered out of adminScopes.
            List<String> dpoScopes = Arrays.asList(DPDPApiResourceProvisioningUtil.COMPLAINTS_READ_ANY,
                    DPDPApiResourceProvisioningUtil.COMPLAINTS_WRITE_ANY);
            for (String complaintScope : complaintScopes) {
                if (complaintScope.endsWith(":self")) {
                    userScopes.add(complaintScope);
                } else {
                    adminScopes.add(complaintScope);
                }
            }
            List<RoleV2> roles = DPDPConsentPortalRoleProvisioningUtil.createRoles(tenantDomain, adminScopes,
                    userScopes, dpoScopes);
            DPDPConsentPortalAppProvisioningUtil.associateOrganizationRoles(tenantDomain, tenantInfoBean.getAdmin(),
                    roles);

            if (configurationService.isConsentApiInvokerProvisioningEnabled()) {
                String invokerApplicationId = DPDPConsentApiInvokerAppProvisioningUtil.getApplicationId(tenantDomain);
                if (invokerApplicationId == null) {
                    invokerApplicationId = DPDPConsentApiInvokerAppProvisioningUtil
                            .provisionApplication(tenantInfoBean);
                } else {
                    LOG.debug("The DPDP Consent API Invoker application already exists for tenant: " + tenantDomain
                            + "; reconciling its API authorization and roles.");
                }
                // Only the consents resource - this app exists to invoke consent operations
                // directly, not to manage the purposes/elements catalog.
                DPDPApiResourceProvisioningUtil.authorizeConsentAPI(invokerApplicationId, tenantDomain);
                DPDPConsentApiInvokerAppProvisioningUtil.associateOrganizationRoles(tenantDomain,
                        tenantInfoBean.getAdmin(), roles);
            } else {
                LOG.debug("DPDP Consent API Invoker provisioning is disabled; skipping tenant: " + tenantDomain);
            }

            LOG.info("Provisioned the DPDP Consent Portal for tenant: " + tenantDomain);
        } finally {
            PrivilegedCarbonContext.endTenantFlow();
        }
    }

    private static String sanitize(String value) {

        return value == null ? null : value.replaceAll("[\r\n]", "");
    }

    @Override
    public void onTenantDelete(int tenantId) {

    }

    @Override
    public void onTenantRename(int tenantId, String oldDomainName, String newDomainName) {

    }

    @Override
    public void onTenantInitialActivation(int tenantId) {

    }

    @Override
    public void onTenantActivation(int tenantId) {

    }

    @Override
    public void onTenantDeactivation(int tenantId) {

    }

    @Override
    public void onSubscriptionPlanChange(int tenantId, String oldPlan, String newPlan) {

    }

    @Override
    public void onPreDelete(int tenantId) {

    }

    @Override
    public int getListenerOrder() {

        // Runs after AppPortalTenantMgtListener (order 100); no dependency between the two,
        // just avoids racing it.
        return 110;
    }
}
