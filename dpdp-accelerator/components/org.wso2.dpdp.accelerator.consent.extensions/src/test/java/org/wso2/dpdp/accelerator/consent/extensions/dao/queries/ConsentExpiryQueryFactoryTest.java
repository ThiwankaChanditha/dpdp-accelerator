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

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

public class ConsentExpiryQueryFactoryTest {

    @Test
    public void mysqlAndPostgresqlUseTheBaselineWithLimitForBothDueExpiryQueries() {

        for (String product : new String[]{"MySQL", "PostgreSQL"}) {
            ConsentExpiryDBQueries queries = ConsentExpiryQueryFactory.getQueryProvider(product);
            assertEquals(queries.getClass(), ConsentExpiryDBQueries.class, product);
            assertLimitQueries(queries);
        }
    }

    @Test
    public void resolvesH2ProviderForH2Dialect() {

        assertTrue(ConsentExpiryQueryFactory.getQueryProvider("H2") instanceof ConsentExpiryH2DBQueries);
    }

    @Test
    public void fallsBackToAnsiBaselineForUnrecognizedDialects() {

        assertEquals(ConsentExpiryQueryFactory.getQueryProvider("UnknownDatabase").getClass(),
                ConsentExpiryDBQueries.class);
    }

    @Test
    public void blankAndNullDialectsResolveToTheH2Provider() {

        assertSame(ConsentExpiryQueryFactory.getQueryProvider((String) null), ConsentExpiryQueryFactory.getQueryProvider());
        assertSame(ConsentExpiryQueryFactory.getQueryProvider("  "), ConsentExpiryQueryFactory.getQueryProvider());
        assertTrue(ConsentExpiryQueryFactory.getQueryProvider() instanceof ConsentExpiryH2DBQueries);
    }

    private void assertLimitQueries(ConsentExpiryDBQueries queries) {

        assertLimit(queries.getFindDueExpiriesQuery());
        assertLimit(queries.getFindDueExpiriesAfterQuery());
    }

    private void assertLimit(String query) {

        assertTrue(query.endsWith("ORDER BY EXPIRY_TIME ASC, CONSENT_ID ASC LIMIT ?"), query);
    }
}
