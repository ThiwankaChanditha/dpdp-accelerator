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

package org.wso2.dpdp.accelerator.complaint.mgt.dao.impl;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.ComplaintDAOException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.exception.DuplicateReferenceIdException;
import org.wso2.dpdp.accelerator.complaint.mgt.dao.model.Complaint;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * Drives addComplaint's collision handling with each driver's own exception shape, which the
 * H2-backed ComplaintDAOImplTest cannot: PostgreSQL's driver reports a unique violation as a plain
 * SQLException (PSQLException) carrying SQLState 23505 and a lowercase constraint name.
 */
class ComplaintDAOImplSqlStateTest {

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    private AutoCloseable mocks;

    @BeforeMethod
    void setUp() throws SQLException {
        mocks = MockitoAnnotations.openMocks(this);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
    }

    @AfterMethod
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void postgresqlReferenceCollisionIsReportedAsDuplicateReferenceId() throws SQLException {
        failInsertWith(new SQLException(
                "ERROR: duplicate key value violates unique constraint \"uq_complaint_reference\"", "23505"));

        expectThrows(DuplicateReferenceIdException.class, this::addComplaint);
    }

    @Test
    void mysqlReferenceCollisionIsReportedAsDuplicateReferenceId() throws SQLException {
        failInsertWith(new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'org1-CMP-2026-1' for key 'COMPLAINT.UQ_COMPLAINT_REFERENCE'", "23000"));

        expectThrows(DuplicateReferenceIdException.class, this::addComplaint);
    }

    @Test
    void otherConstraintViolationIsNotMistakenForAReferenceCollision() throws SQLException {
        failInsertWith(new SQLException(
                "ERROR: duplicate key value violates unique constraint \"complaint_pkey\"", "23505"));

        ComplaintDAOException thrown = expectThrows(ComplaintDAOException.class, this::addComplaint);
        assertEquals(thrown.getClass(), ComplaintDAOException.class);
    }

    @Test
    void nonConstraintFailureNamingTheReferenceConstraintIsStillAGenericFailure() throws SQLException {
        failInsertWith(new SQLException("lock timeout while checking uq_complaint_reference", "55P03"));

        ComplaintDAOException thrown = expectThrows(ComplaintDAOException.class, this::addComplaint);
        assertEquals(thrown.getClass(), ComplaintDAOException.class);
    }

    private void failInsertWith(SQLException exception) throws SQLException {
        when(statement.executeUpdate()).thenThrow(exception);
    }

    private void addComplaint() {
        new ComplaintDAOImpl().addComplaint(connection, new Complaint("c1", "org1", "user1", "user1-name",
                "CMP-2026-1", "DATA_BREACH", "HIGH", "OPEN", "desc", 100L, 100L, 1100L));
    }
}
