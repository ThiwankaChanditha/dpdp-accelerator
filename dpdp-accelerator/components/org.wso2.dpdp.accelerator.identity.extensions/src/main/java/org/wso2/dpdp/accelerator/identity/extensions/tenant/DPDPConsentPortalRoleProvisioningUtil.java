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
import org.wso2.carbon.identity.application.common.model.RoleV2;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Permission;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Role;
import org.wso2.carbon.identity.role.v2.mgt.core.model.RoleBasicInfo;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates the DPDP Consent Portal admin, user and DPO roles at the organization level, making
 * them manageable from the tenant's User Management > Roles screen.
 */
public final class DPDPConsentPortalRoleProvisioningUtil {

    private static final Log LOG = LogFactory.getLog(DPDPConsentPortalRoleProvisioningUtil.class);
    public static final String ADMIN_ROLE = "dpdp-consent-admin";
    static final String USER_ROLE = "dpdp-consent-user";
    public static final String DPO_ROLE = "dpdp-consent-dpo";
    static final String ROLE_AUDIENCE = "organization";

    private DPDPConsentPortalRoleProvisioningUtil() {

    }

    /**
     * @return the admin, user and DPO roles (in that order) as {@link RoleV2} references for
     * {@code AssociatedRolesConfig}.
     */
    public static List<RoleV2> createRoles(String tenantDomain, List<String> adminScopeNames,
            List<String> userScopeNames, List<String> dpoScopeNames) throws Exception {

        RoleManagementService roleManagementService = DPDPIdentityExtensionDataHolder.getInstance()
                .getRoleManagementService();
        // The audience ID for "organization" audience must be the organization's own ID, not the
        // application's - passing anything else throws INVALID_AUDIENCE.
        String organizationId = DPDPIdentityExtensionDataHolder.getInstance().getOrganizationManager()
                .resolveOrganizationId(tenantDomain);

        RoleV2 adminRole = createRoleIfNotExists(roleManagementService, ADMIN_ROLE, toPermissions(adminScopeNames),
                organizationId, tenantDomain);
        RoleV2 userRole = createRoleIfNotExists(roleManagementService, USER_ROLE, toPermissions(userScopeNames),
                organizationId, tenantDomain);
        RoleV2 dpoRole = createRoleIfNotExists(roleManagementService, DPO_ROLE, toPermissions(dpoScopeNames),
                organizationId, tenantDomain);
        return Arrays.asList(adminRole, userRole, dpoRole);
    }

    private static List<Permission> toPermissions(List<String> scopeNames) {

        List<Permission> permissions = new ArrayList<>();
        for (String scopeName : scopeNames) {
            permissions.add(new Permission(scopeName));
        }
        return permissions;
    }

    /**
     * Creates the role if it doesn't exist yet; if it does, reconciles its permission list to
     * include every desired scope.
    */
    private static RoleV2 createRoleIfNotExists(RoleManagementService roleManagementService, String roleName,
            List<Permission> desiredPermissions, String organizationId, String tenantDomain) throws Exception {

        if (roleManagementService.isExistingRoleName(roleName, ROLE_AUDIENCE, organizationId, tenantDomain)) {
            String roleId = roleManagementService.getRoleIdByName(roleName, ROLE_AUDIENCE, organizationId,
                    tenantDomain);
            reconcilePermissions(roleManagementService, roleId, roleName, desiredPermissions, tenantDomain);
            return new RoleV2(roleId, roleName);
        }
        RoleBasicInfo roleBasicInfo = roleManagementService.addRole(roleName, Collections.emptyList(),
                Collections.emptyList(), desiredPermissions, ROLE_AUDIENCE, organizationId, tenantDomain);
        LOG.debug("Created role '" + roleName + "' with " + desiredPermissions.size() + " permission(s) for tenant: "
                + tenantDomain);
        return new RoleV2(roleBasicInfo.getId(), roleName);
    }

    private static void reconcilePermissions(RoleManagementService roleManagementService, String roleId,
            String roleName, List<Permission> desiredPermissions, String tenantDomain) throws Exception {

        Role role = roleManagementService.getRole(roleId, tenantDomain);
        Set<String> existingPermissionNames = new HashSet<>();
        for (Permission permission : role.getPermissions()) {
            existingPermissionNames.add(permission.getName());
        }

        List<Permission> missingPermissions = new ArrayList<>();
        for (Permission desiredPermission : desiredPermissions) {
            if (!existingPermissionNames.contains(desiredPermission.getName())) {
                missingPermissions.add(desiredPermission);
            }
        }

        if (missingPermissions.isEmpty()) {
            LOG.debug("Role '" + roleName + "' already has every desired permission for tenant: " + tenantDomain);
            return;
        }
        roleManagementService.updatePermissionListOfRole(roleId, missingPermissions, Collections.emptyList(),
                tenantDomain);
        LOG.debug("Added " + missingPermissions.size() + " missing permission(s) to role '" + roleName
                + "' for tenant: " + tenantDomain);
    }
}
