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
import { selectMuiOption } from '../utils/muiSelect'
import { submitFilterValue } from '../utils/filterCommit'
import { ConsentRegistryTable } from './ConsentRegistryTable'

/** A user's own consent list at /consents, labelled "My Consents" in the sidebar. */
export class MyConsentPage extends ConsentRegistryTable {
  readonly serviceSearch: Locator
  readonly clearFiltersButton: Locator

  constructor(page: Page) {
    super(page)
    this.serviceSearch = page.getByPlaceholder('Search by service')
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear all filters' })
  }

  async goto(): Promise<void> {
    await this.page.goto('consents')
  }

  async approveFromList(consentId: string): Promise<void> {
    await this.rowByConsentId(consentId).getByRole('button', { name: 'Approve' }).click()
  }

  async searchByService(serviceId: string): Promise<void> {
    await submitFilterValue(
      this.page,
      this.serviceSearch,
      async () => {
        await this.serviceSearch.press('Enter')
      },
      'serviceId',
      serviceId,
    )
  }

  async filterByState(stateLabel: string): Promise<void> {
    await selectMuiOption(this.page, 'consent-state', stateLabel)
  }

  async clearFilters(): Promise<void> {
    await this.clearFiltersButton.click()
  }
}
