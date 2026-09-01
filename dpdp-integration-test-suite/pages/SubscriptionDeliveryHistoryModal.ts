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

import { type Locator, type Page } from '@playwright/test'

/**
 * The attempts table body is byte-identical between SubscriptionDeliveryHistoryModal.tsx and
 * EventDetailsModal.tsx (same columns, same "#<n>" attempt cell, same completion-evidence
 * banner) - shared here so both page objects (this file and EventDetailsModal.ts) read from one
 * place instead of two copies of the same locators.
 */
export class DeliveryAttemptsTable {
  readonly attemptRows: Locator
  readonly noAttemptsMessage: Locator
  readonly completionBanner: Locator
  readonly closeButton: Locator

  constructor(protected readonly root: Locator) {
    this.attemptRows = this.root.locator('table').locator('tbody').getByRole('row')
    this.noAttemptsMessage = this.root.getByText(/No (execution attempts recorded|delivery attempts recorded)\./)
    this.completionBanner = this.root.getByText('Completion Evidence')
    this.closeButton = this.root.getByRole('button', { name: 'Close' })
  }

  attemptRow(attemptNumber: number): Locator {
    return this.attemptRows.filter({ hasText: `#${String(attemptNumber)}` })
  }

  async close(): Promise<void> {
    await this.closeButton.click()
  }
}

/** SubscriptionDeliveryHistoryModal.tsx - opened from SubscriptionDeliveryEventsTable's "View Delivery Attempts" action. */
export class SubscriptionDeliveryHistoryModal extends DeliveryAttemptsTable {
  readonly title: Locator

  constructor(page: Page) {
    super(page.getByRole('dialog'))
    // exact: true - the dialog's own MuiDialogTitle heading and a nested section heading inside
    // it both render this exact text, so an unscoped substring match resolves to both (confirmed
    // live via a strict-mode violation) - this locator must pick just the outer dialog title.
    this.title = this.root.getByRole('heading', { name: 'Delivery Attempts Chronology', exact: true }).first()
  }
}
