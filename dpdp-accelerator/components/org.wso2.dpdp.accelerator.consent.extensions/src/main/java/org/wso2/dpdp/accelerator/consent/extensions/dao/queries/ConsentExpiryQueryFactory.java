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

package org.wso2.dpdp.accelerator.consent.extensions.dao.queries;

import org.wso2.dpdp.accelerator.common.persistence.DBDialectConstants;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the consent-expiry query provider for the connected database dialect.
 */
public class ConsentExpiryQueryFactory {

    private static final Map<String, ConsentExpiryDBQueries> PROVIDER_MAP = new ConcurrentHashMap<>();

    private ConsentExpiryQueryFactory() {
    }

    public static ConsentExpiryDBQueries getQueryProvider(String dbType) {

        String key = (dbType != null && !dbType.trim().isEmpty())
                ? dbType.trim().toLowerCase(Locale.ROOT) : DBDialectConstants.DB_TYPE_H2;
        return PROVIDER_MAP.computeIfAbsent(key, k -> {
            if (k.contains(DBDialectConstants.DB_TYPE_H2)) {
                return new ConsentExpiryH2DBQueries();
            }
            return new ConsentExpiryDBQueries();
        });
    }

    public static ConsentExpiryDBQueries getQueryProvider(Connection connection) {

        if (connection != null) {
            try {
                DatabaseMetaData metaData = connection.getMetaData();
                if (metaData != null && metaData.getDatabaseProductName() != null) {
                    return getQueryProvider(metaData.getDatabaseProductName());
                }
            } catch (Exception ignored) {
                // Fall back to the baseline provider.
            }
        }
        return getQueryProvider();
    }

    public static ConsentExpiryDBQueries getQueryProvider() {

        return getQueryProvider(DBDialectConstants.DB_TYPE_H2);
    }
}
