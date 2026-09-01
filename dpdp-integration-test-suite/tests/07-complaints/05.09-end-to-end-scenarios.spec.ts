/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import { test, expect, loginAsUser, loginAsConsentAdmin } from '../../fixtures/auth.fixtures'
import { ComplaintCaseDetailPage } from '../../pages/ComplaintCaseDetailPage'
import { ComplaintDetailPage } from '../../pages/ComplaintDetailPage'
import { ComplaintListPage } from '../../pages/ComplaintListPage'
import { ComplaintQueuePage } from '../../pages/ComplaintQueuePage'
import { ComplaintSubmitDialog } from '../../pages/ComplaintSubmitDialog'
import { uniqueMarker } from '../../utils/testData'

/**
 * End-to-end scenarios driving both surfaces together through the real UI, in the order an actual
 * grievance-redressal case plays out - as opposed to every other file in this directory, which
 * exercises one surface/action in isolation. See tests/06-complaints-api/06.06 for the same
 * scenarios' API-only counterparts (officer-assisted phone intake, category/priority sweep,
 * concurrent replies) - not repeated here since they have no UI of their own to exercise.
 */
test.describe('Real-world complaint scenarios (UI)', () => {
  test('05.09.02 - A citizen replying to a Resolved complaint posts the message but does not reopen it', async ({
    browser,
  }) => {
    // ComplaintDetailPage.tsx's onSend only attaches a toStatus when the complaint is currently
    // WAITING_ON_CLIENT (see the file header comment in 05.04) - RESOLVED isn't handled, even
    // though StatusTransitionValidator.java's own backend rule explicitly allows and documents
    // RESOLVED -> AWAITING_INTERNAL_REVIEW as how a citizen reopens a resolved complaint. As the
    // frontend behaves today, replying to a resolved complaint posts the message but leaves the
    // complaint RESOLVED and hidden from the officer's default queue - this test asserts that
    // actual, current behavior rather than the reopen flow the backend alone would support.
    // Several full-page navigations/reloads happen below, each forcing a silent OIDC re-auth
    // round trip (see the matching comment on 05.05.01) - the default 30s test timeout is too
    // tight once that compounds across this test's many sequential steps.
    test.setTimeout(60_000)
    const dataPrincipalPage = await loginAsUser(browser)
    const officerPage = await loginAsConsentAdmin(browser)

    const listPage = new ComplaintListPage(dataPrincipalPage)
    await listPage.goto()
    await listPage.openSubmitDialog()
    const submitDialog = new ComplaintSubmitDialog(dataPrincipalPage)
    await submitDialog.selectCategory('Other')
    await submitDialog.fillDescription(`Automated UI test: reopen flow ${uniqueMarker('reopen')}`)
    await submitDialog.submit()
    const alertText = await listPage.successAlert.textContent()
    const referenceId = /complaint\s+(\S+)\s+has been submitted/.exec(alertText ?? '')?.[1]
    if (!referenceId) {
      throw new Error(`Could not read a reference id out of the success banner: "${alertText}"`)
    }

    const queuePage = new ComplaintQueuePage(officerPage)
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await queuePage.openByReferenceId(referenceId)
    // Confirms the navigation itself actually landed before asserting on page content - see the
    // matching comment in 05.09.01.
    await expect(officerPage).toHaveURL(/\/complaint-management\/[^/]+$/, { timeout: 15_000 })
    const caseDetailPage = new ComplaintCaseDetailPage(officerPage)
    await caseDetailPage.selectNextStatusBeforeSending('In Progress')
    await caseDetailPage.sendReply(`Reviewing: ${uniqueMarker('reopen-review')}`)
    await caseDetailPage.selectNextStatusBeforeSending('Resolved')
    await caseDetailPage.sendAndConfirmResolve(`Believed resolved: ${uniqueMarker('reopen-resolve')}.`)
    await expect(caseDetailPage.resolvedLockedBanner).toBeVisible()

    // Resolved complaints are hidden from the officer's default queue view.
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await expect(queuePage.rowByReferenceId(referenceId)).not.toBeVisible()

    // Citizen isn't satisfied and replies again. The citizen never left the /complaints list
    // after submitting - openByReferenceId navigates into the complaint's detail page for the
    // first time (see the matching comment in 05.09.01).
    await listPage.openByReferenceId(referenceId)
    await expect(dataPrincipalPage).toHaveURL(/\/complaints\/[^/]+$/, { timeout: 15_000 })
    const detailPage = new ComplaintDetailPage(dataPrincipalPage)
    await expect(detailPage.resolvedBanner).toBeVisible()
    const reopenMessage = `This is still happening, please look again: ${uniqueMarker('reopen-message')}`
    await detailPage.sendReply(reopenMessage)
    await expect(dataPrincipalPage.getByText(reopenMessage)).toBeVisible()
    await expect(detailPage.chipWithLabel('Resolved')).toBeVisible()

    // Still hidden from the officer's default queue view - the reply above left it RESOLVED.
    await queuePage.goto()
    await queuePage.setRowsPerPage(25)
    await expect(queuePage.rowByReferenceId(referenceId)).not.toBeVisible()

    await dataPrincipalPage.context().close()
    await officerPage.context().close()
  })
})
