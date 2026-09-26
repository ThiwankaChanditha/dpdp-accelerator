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

package org.wso2.dpdp.accelerator.event.notifications.dao.queries;

import org.wso2.dpdp.accelerator.common.persistence.DBDialectConstants;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory class resolving DB-dialect specific query providers for Event Notification Framework.
 */
public class EventNotificationQueryFactory {

    private static final Map<String, EventNotificationCommonDBQueries> PROVIDER_MAP = new ConcurrentHashMap<>();

    private EventNotificationQueryFactory() {
    }

    public static EventNotificationCommonDBQueries getQueryProvider(String dbType) {
        String key = (dbType != null && !dbType.trim().isEmpty())
                ? dbType.trim().toLowerCase(Locale.ROOT) : DBDialectConstants.DB_TYPE_H2;
        return PROVIDER_MAP.computeIfAbsent(key, k -> {
            if (k.contains(DBDialectConstants.DB_TYPE_POSTGRES)) {
                return new EventNotificationPostgresDBQueries();
            } else if (k.contains(DBDialectConstants.DB_TYPE_MYSQL)) {
                return new EventNotificationMysqlDBQueries();
            } else if (k.contains(DBDialectConstants.DB_TYPE_H2)) {
                return new EventNotificationH2DBQueries();
            }
            return new EventNotificationCommonDBQueries();
        });
    }

    public static EventNotificationCommonDBQueries getQueryProvider(Connection conn) {
        if (conn != null) {
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                if (metaData != null && metaData.getDatabaseProductName() != null) {
                    return getQueryProvider(metaData.getDatabaseProductName());
                }
            } catch (Exception ignored) {
                // Fallback to default
            }
        }
        return getQueryProvider();
    }

    public static EventNotificationCommonDBQueries getQueryProvider() {
        return getQueryProvider(DBDialectConstants.DB_TYPE_H2);
    }
}
