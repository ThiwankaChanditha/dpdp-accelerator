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

package org.wso2.dpdp.accelerator.common.persistence;

/**
 * Substrings matched against {@code DatabaseMetaData.getDatabaseProductName()} to resolve a
 * DB-dialect specific query provider. Shared across every feature's own {@code *QueryFactory}
 * (Complaint, Event Notifications, Consent History, Consent Expiry) so the same literal isn't
 * redefined per module. Only the supported databases - H2, MySQL and PostgreSQL - are listed.
 */
public final class DBDialectConstants {

    public static final String DB_TYPE_H2 = "h2";
    public static final String DB_TYPE_MYSQL = "mysql";
    public static final String DB_TYPE_POSTGRES = "postgres";

    private DBDialectConstants() {
    }
}
