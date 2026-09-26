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

import org.testng.annotations.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.testng.Assert.assertEquals;

public class EscapedLikeDialectTest {

    @Test
    public void h2TreatsPercentAndUnderscoreAsLiterals() throws Exception {
        assertLiteralSearch("jdbc:h2:mem:escaped_like;DB_CLOSE_DELAY=-1");
    }

    private static void assertLiteralSearch(String jdbcUrl) throws Exception {
        try (Connection connection = DriverManager.getConnection(jdbcUrl);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE SEARCH_SAMPLE (SEARCH_VALUE VARCHAR(128))");
            statement.execute("INSERT INTO SEARCH_SAMPLE (SEARCH_VALUE) VALUES ('account_100%')");
            statement.execute("INSERT INTO SEARCH_SAMPLE (SEARCH_VALUE) VALUES ('accountX100Y')");

            String sql = "SELECT COUNT(*) FROM SEARCH_SAMPLE WHERE "
                    + QueryBuilderUtils.buildEscapedLikePredicate("LOWER(SEARCH_VALUE)");
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1,
                        QueryBuilderUtils.buildCaseInsensitiveContainsPattern("_100%"));
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    resultSet.next();
                    assertEquals(resultSet.getInt(1), 1);
                }
            }
        }
    }
}
