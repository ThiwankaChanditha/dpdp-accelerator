/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 */
package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;

public class DBQueryProviderTest {

    @Test
    public void queryProvidersExposeNonEmptyQueries() throws Exception {
        for (EventNotificationCommonDBQueries provider : new EventNotificationCommonDBQueries[] {
                new EventNotificationCommonDBQueries(), new EventNotificationMysqlDBQueries(),
                new EventNotificationH2DBQueries(), new EventNotificationPostgresDBQueries() }) {
            for (Method method : EventNotificationCommonDBQueries.class.getMethods()) {
                if (method.getDeclaringClass() == EventNotificationCommonDBQueries.class
                        && method.getReturnType() == String.class && method.getParameterCount() == 0) {
                    Assert.assertFalse(((String) method.invoke(provider)).trim().isEmpty(), method.getName());
                }
            }
        }
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("postgres")
                instanceof EventNotificationPostgresDBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("mysql")
                instanceof EventNotificationMysqlDBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider("h2") instanceof EventNotificationH2DBQueries);
        Assert.assertTrue(EventNotificationQueryFactory.getQueryProvider() instanceof EventNotificationH2DBQueries);
    }

    @Test
    public void pendingWebhookClaimHonorsRetrySchedule() {
        String query = new EventNotificationCommonDBQueries().getClaimWebhookDeliveryQuery();

        Assert.assertTrue(query.contains("NEXT_RETRY_AT IS NULL OR NEXT_RETRY_AT <= CURRENT_TIMESTAMP"));
    }

    @Test
    public void transactionalFanOutQueriesUseLocks() {
        EventNotificationCommonDBQueries common = new EventNotificationCommonDBQueries();

        Assert.assertTrue(common.getActiveTopicByOrgAndNameForUpdateQuery().endsWith("FOR UPDATE"));
        Assert.assertTrue(common.getActiveSubscriptionsForFanOutQuery().endsWith("FOR UPDATE"));
        Assert.assertTrue(common.getAddEventQuery().contains("STATUS = 'active'"));
        Assert.assertTrue(common.getLockSubscriptionForVerificationQuery().contains("DELIVERY_MODE = 'webhook'"));
        Assert.assertTrue(common.getLockSubscriptionForVerificationQuery().contains("STATUS = ?"));
        Assert.assertTrue(common.getUpdatePollDeliveryStatusByDeliveryAndSubscriptionQuery()
                .contains("STATUS = 'pending'"));
    }
}
