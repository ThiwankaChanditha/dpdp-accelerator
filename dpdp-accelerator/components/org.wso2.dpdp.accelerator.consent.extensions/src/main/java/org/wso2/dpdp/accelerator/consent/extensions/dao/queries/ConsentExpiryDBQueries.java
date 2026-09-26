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

import org.wso2.dpdp.accelerator.consent.extensions.dao.constants.ConsentExpiryDAOConstants;

/**
 * Baseline SQL for {@code DPDP_CONSENT_EXPIRY_TRACKER}. H2, MySQL and PostgreSQL all use this
 * provider.
 */
public class ConsentExpiryDBQueries {

    public String getUpsertExpiryDeleteQuery() {

        return "DELETE FROM " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " WHERE "
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + " = ?";
    }

    public String getUpsertExpiryInsertQuery() {

        return "INSERT INTO " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " ("
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + ", " + ConsentExpiryDAOConstants.COLUMN_ORG_ID + ", "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + ") VALUES (?, ?, ?)";
    }

    public String getDeleteExpiryQuery() {

        return "DELETE FROM " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " WHERE "
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + " = ?";
    }

    public String getFindDueExpiriesQuery() {

        return "SELECT " + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + ", "
                + ConsentExpiryDAOConstants.COLUMN_ORG_ID + ", " + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME
                + " FROM " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " WHERE "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + " <= ? ORDER BY "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + " ASC, "
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + " ASC LIMIT ?";
    }

    public String getClaimObservedExpiryQuery() {

        return "DELETE FROM " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " WHERE "
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + " = ? AND "
                + ConsentExpiryDAOConstants.COLUMN_ORG_ID + " = ? AND "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + " = ? AND "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + " <= ?";
    }

    public String getFindExpiryQuery() {

        return "SELECT " + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + ", "
                + ConsentExpiryDAOConstants.COLUMN_ORG_ID + ", " + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME
                + " FROM " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " WHERE "
                + ConsentExpiryDAOConstants.COLUMN_ORG_ID + " = ? AND "
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + " = ?";
    }

    public String getFindDueExpiriesAfterQuery() {

        String expiry = ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME;
        String consent = ConsentExpiryDAOConstants.COLUMN_CONSENT_ID;
        return "SELECT " + consent + ", " + ConsentExpiryDAOConstants.COLUMN_ORG_ID + ", " + expiry
                + " FROM " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " WHERE " + expiry
                + " <= ? AND (" + expiry + " > ? OR (" + expiry + " = ? AND " + consent + " > ?))"
                + " ORDER BY " + expiry + " ASC, " + consent + " ASC LIMIT ?";
    }
    public String getReconcileExpiryQuery() {

        return "UPDATE " + ConsentExpiryDAOConstants.EXPIRY_TRACKER_TABLE + " SET "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + " = ? WHERE "
                + ConsentExpiryDAOConstants.COLUMN_CONSENT_ID + " = ? AND "
                + ConsentExpiryDAOConstants.COLUMN_ORG_ID + " = ? AND "
                + ConsentExpiryDAOConstants.COLUMN_EXPIRY_TIME + " = ?";
    }

}
