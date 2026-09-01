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
import { submitFilterValue } from '../utils/filterCommit'

export const ROWS_PER_PAGE_OPTIONS = [10, 20, 50] as const

/**
 * EventsPage.tsx at /events. There is NO publish-event UI anywhere in this feature (confirmed
 * across the whole `features/events/` tree, and `events.actions.publish`/`events.dialog.publish*`
 * in common.json are dead i18n keys with no component reference) - publishing only happens
 * through EventNotificationApiClient.publishEvent. eventId cells render through CopyableText
 * (truncated visible text, full value on the inner span's aria-label - see TopicsPage.ts's
 * rowByTopicId comment for the same pattern); a row is also clickable as a whole, in addition to
 * its explicit "View Details" action.
 */
export class EventsPage {
  readonly heading: Locator
  readonly table: Locator
  readonly searchInput: Locator
  readonly groupIdInput: Locator
  readonly statusFilter: Locator
  readonly topicFilter: Locator
  readonly searchButton: Locator
  readonly clearFiltersButton: Locator
  readonly emptyState: Locator
  readonly loadFailedAlert: Locator
  readonly retryButton: Locator
  readonly rowsPerPageSelect: Locator
  readonly previousPageButton: Locator
  readonly nextPageButton: Locator

  constructor(private readonly page: Page) {
    this.heading = page.getByRole('heading', { name: 'Events' })
    this.table = page.getByRole('table', { name: 'Published events list table' })
    this.searchInput = page.getByPlaceholder('Search by delivery ID, event ID, or topic...')
    this.groupIdInput = page.getByLabel('Group ID')
    this.statusFilter = page.getByRole('combobox', { name: 'Status' })
    this.topicFilter = page.getByRole('combobox', { name: 'Topic' })
    this.searchButton = page.getByRole('button', { name: 'Search' })
    this.clearFiltersButton = page.getByRole('button', { name: 'Clear', exact: true })
    this.emptyState = page.getByText('No events found.')
    this.loadFailedAlert = page.getByText('Unable to load events.')
    this.retryButton = page.getByRole('button', { name: 'Try again' })
    this.rowsPerPageSelect = page.getByRole('combobox', { name: 'Rows per page' })
    this.previousPageButton = page.getByRole('button', { name: 'Previous' })
    this.nextPageButton = page.getByRole('button', { name: 'Next' })
  }

  async goto(): Promise<void> {
    // No leading slash - see the comment in MyConsentPage.goto() for why.
    await this.page.goto('events')
  }

  async search(term: string): Promise<void> {
    await submitFilterValue(
      this.page,
      this.searchInput,
      async () => {
        await this.searchButton.click()
      },
      'search',
      term,
    )
  }

  async filterByGroupId(groupId: string): Promise<void> {
    await submitFilterValue(
      this.page,
      this.groupIdInput,
      async () => {
        await this.searchButton.click()
      },
      'groupId',
      groupId,
    )
  }

  async filterByStatus(
    label: 'All Statuses' | 'Pending' | 'Delivered' | 'Failed' | 'Completed' | 'Acknowledged',
  ): Promise<void> {
    await this.statusFilter.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async filterByTopic(topicName: 'All Topics' | string): Promise<void> {
    await this.topicFilter.click()
    await this.page.getByRole('option', { name: topicName, exact: true }).click()
  }

  async clearFilters(): Promise<void> {
    await this.clearFiltersButton.click()
  }

  async setRowsPerPage(count: (typeof ROWS_PER_PAGE_OPTIONS)[number]): Promise<void> {
    await this.rowsPerPageSelect.click()
    await this.page.getByRole('option', { name: String(count), exact: true }).click()
  }

  async goToNextPage(): Promise<void> {
    await this.nextPageButton.click()
  }

  async goToPreviousPage(): Promise<void> {
    await this.previousPageButton.click()
  }

  get rows(): Locator {
    return this.table.locator('tbody').getByRole('row')
  }

  rowByEventId(eventId: string): Locator {
    return this.rows.filter({ has: this.page.locator(`[aria-label="${eventId}"]`) })
  }

  viewDetailsButton(row: Locator): Locator {
    return row.getByRole('button', { name: 'View Details' })
  }

  async openDetailsByEventId(eventId: string): Promise<void> {
    await this.viewDetailsButton(this.rowByEventId(eventId)).click()
  }

  /** Clicking anywhere on the row navigates too, not just the explicit action button. */
  async clickRowByEventId(eventId: string): Promise<void> {
    await this.rowByEventId(eventId).click()
  }
}
