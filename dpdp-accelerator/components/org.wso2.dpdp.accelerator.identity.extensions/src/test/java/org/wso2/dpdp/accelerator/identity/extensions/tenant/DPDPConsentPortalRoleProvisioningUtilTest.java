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

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.carbon.identity.application.common.model.RoleV2;
import org.wso2.carbon.identity.organization.management.service.OrganizationManager;
import org.wso2.carbon.identity.role.v2.mgt.core.RoleManagementService;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Permission;
import org.wso2.carbon.identity.role.v2.mgt.core.model.Role;
import org.wso2.carbon.identity.role.v2.mgt.core.model.RoleBasicInfo;
import org.wso2.dpdp.accelerator.identity.extensions.internal.DPDPIdentityExtensionDataHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class DPDPConsentPortalRoleProvisioningUtilTest {

    private static final String TENANT_DOMAIN = "tenant-a.com";
    private static final String ORGANIZATION_ID = "org-1234";
    private static final String ROLE_AUDIENCE = "organization";
    private static final String ADMIN_ROLE_ID = "role-admin-1234";
    private static final String USER_ROLE_ID = "role-user-1234";
    private static final String DPO_ROLE_ID = "role-dpo-1234";

    @Mock
    private RoleManagementService roleManagementService;

    @Mock
    private OrganizationManager organizationManager;

    @BeforeMethod
    public void setUp() throws Exception {

        MockitoAnnotations.openMocks(this);
        DPDPIdentityExtensionDataHolder.getInstance().setRoleManagementService(roleManagementService);
        DPDPIdentityExtensionDataHolder.getInstance().setOrganizationManager(organizationManager);
        when(organizationManager.resolveOrganizationId(TENANT_DOMAIN)).thenReturn(ORGANIZATION_ID);

        RoleBasicInfo adminRoleBasicInfo = roleBasicInfo(ADMIN_ROLE_ID);
        when(roleManagementService.addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE), anyList(),
                anyList(), anyList(), eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN)))
                .thenReturn(adminRoleBasicInfo);
        RoleBasicInfo userRoleBasicInfo = roleBasicInfo(USER_ROLE_ID);
        when(roleManagementService.addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE), anyList(), anyList(),
                anyList(), eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN)))
                .thenReturn(userRoleBasicInfo);
        RoleBasicInfo dpoRoleBasicInfo = roleBasicInfo(DPO_ROLE_ID);
        when(roleManagementService.addRole(eq(DPDPConsentPortalRoleProvisioningUtil.DPO_ROLE), anyList(), anyList(),
                anyList(), eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN)))
                .thenReturn(dpoRoleBasicInfo);
    }

    @Test
    public void createRolesCreatesAdminRoleAndUserRoleWithTheirRespectiveScopes() throws Exception {

        List<String> adminScopes = Arrays.asList("internal_consent_mgt_consent_view",
                "consent:status-history:view:any", "consent:history:view:any");
        List<String> userScopes = Arrays.asList("consent:status-history:view:self", "consent:history:view:self");
        List<String> dpoScopes = Arrays.asList("complaints:read:any", "complaints:write:any");

        List<RoleV2> roles = DPDPConsentPortalRoleProvisioningUtil.createRoles(TENANT_DOMAIN, adminScopes,
                userScopes, dpoScopes);

        assertEquals(roles.size(), 3);
        assertEquals(roles.get(0).getId(), ADMIN_ROLE_ID);
        assertEquals(roles.get(0).getName(), DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE);
        assertEquals(roles.get(1).getId(), USER_ROLE_ID);
        assertEquals(roles.get(1).getName(), DPDPConsentPortalRoleProvisioningUtil.USER_ROLE);
        assertEquals(roles.get(2).getId(), DPO_ROLE_ID);
        assertEquals(roles.get(2).getName(), DPDPConsentPortalRoleProvisioningUtil.DPO_ROLE);

        ArgumentCaptor<List<Permission>> adminPermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), adminPermissionsCaptor.capture(),
                eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(adminPermissionsCaptor.getValue()), adminScopes);

        ArgumentCaptor<List<Permission>> userPermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), userPermissionsCaptor.capture(),
                eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(userPermissionsCaptor.getValue()), userScopes);

        ArgumentCaptor<List<Permission>> dpoPermissionsCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.DPO_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), dpoPermissionsCaptor.capture(),
                eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(dpoPermissionsCaptor.getValue()), dpoScopes);

        verify(roleManagementService, times(3)).addRole(anyString(), anyList(), anyList(), anyList(), anyString(),
                anyString(), anyString());
    }

    @Test
    public void createRolesHandlesEmptyScopeLists() throws Exception {

        DPDPConsentPortalRoleProvisioningUtil.createRoles(TENANT_DOMAIN, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                eq(Collections.emptyList()), eq(Collections.emptyList()), eq(Collections.emptyList()),
                eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN));
    }

    @Test
    public void createRolesSkipsCreatingRolesThatAlreadyExist() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                ROLE_AUDIENCE, ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(ADMIN_ROLE_ID);
        Role adminRole = roleWithPermissions();
        when(roleManagementService.getRole(ADMIN_ROLE_ID, TENANT_DOMAIN)).thenReturn(adminRole);
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE, ROLE_AUDIENCE,
                ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(false);

        List<RoleV2> roles = DPDPConsentPortalRoleProvisioningUtil.createRoles(TENANT_DOMAIN, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());

        assertEquals(roles.get(0).getId(), ADMIN_ROLE_ID);
        verify(roleManagementService, never()).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE),
                anyList(), anyList(), anyList(), anyString(), anyString(), anyString());
        verify(roleManagementService).addRole(eq(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE), anyList(),
                anyList(), anyList(), eq(ROLE_AUDIENCE), eq(ORGANIZATION_ID), eq(TENANT_DOMAIN));
    }

    /**
     * The actual gap this reconciliation closes: an admin role created under an earlier, smaller
     * desired-scope set (e.g. before consent:history:view:any existed) must pick up the new scope
     * on a later re-provisioning run, not stay stuck with only what it had at creation time.
     */
    @Test
    public void createRolesAddsNewlyDesiredScopesToAnExistingRoleWithoutRemovingItsExistingOnes() throws Exception {

        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE,
                ROLE_AUDIENCE, ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.ADMIN_ROLE, ROLE_AUDIENCE,
                ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(ADMIN_ROLE_ID);
        Role adminRole = roleWithPermissions("consent:status-history:view:any");
        when(roleManagementService.getRole(ADMIN_ROLE_ID, TENANT_DOMAIN)).thenReturn(adminRole);
        when(roleManagementService.isExistingRoleName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE, ROLE_AUDIENCE,
                ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(true);
        when(roleManagementService.getRoleIdByName(DPDPConsentPortalRoleProvisioningUtil.USER_ROLE, ROLE_AUDIENCE,
                ORGANIZATION_ID, TENANT_DOMAIN)).thenReturn(USER_ROLE_ID);
        Role userRole = roleWithPermissions();
        when(roleManagementService.getRole(USER_ROLE_ID, TENANT_DOMAIN)).thenReturn(userRole);

        List<String> adminScopes = Arrays.asList("consent:status-history:view:any", "consent:history:view:any");
        DPDPConsentPortalRoleProvisioningUtil.createRoles(TENANT_DOMAIN, adminScopes, Collections.emptyList(),
                Collections.emptyList());

        ArgumentCaptor<List<Permission>> addedCaptor = ArgumentCaptor.forClass(List.class);
        verify(roleManagementService).updatePermissionListOfRole(eq(ADMIN_ROLE_ID), addedCaptor.capture(),
                eq(Collections.emptyList()), eq(TENANT_DOMAIN));
        assertEquals(permissionNames(addedCaptor.getValue()), Collections.singletonList("consent:history:view:any"));
        verify(roleManagementService, never()).updatePermissionListOfRole(eq(USER_ROLE_ID), anyList(), anyList(),
                anyString());
    }

    private static Role roleWithPermissions(String... permissionNames) {

        Role role = mock(Role.class);
        List<Permission> permissions = new java.util.ArrayList<>();
        for (String name : permissionNames) {
            permissions.add(new Permission(name));
        }
        when(role.getPermissions()).thenReturn(permissions);
        return role;
    }

    private static RoleBasicInfo roleBasicInfo(String id) {

        RoleBasicInfo roleBasicInfo = mock(RoleBasicInfo.class);
        when(roleBasicInfo.getId()).thenReturn(id);
        return roleBasicInfo;
    }

    private static List<String> permissionNames(List<Permission> permissions) {

        List<String> names = new java.util.ArrayList<>();
        for (Permission permission : permissions) {
            names.add(permission.getName());
        }
        return names;
    }
}
