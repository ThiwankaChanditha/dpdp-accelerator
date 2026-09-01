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

const CATEGORY_LABELS = {
  DATA_BREACH: 'Data breach',
  UNAUTHORIZED_DATA_SHARING: 'Unauthorized data sharing',
  CONSENT_WITHDRAWN_DATA_STILL_USED: 'Consent withdrawal issue',
  PURPOSE_VIOLATION: 'Purpose violation',
  DATA_ERASURE_NOT_COMPLETED: 'Data erasure request not fulfilled',
  DATA_CORRECTION_NOT_COMPLETED: 'Data correction request not fulfilled',
  CONSENT_LIFECYCLE_ISSUE: 'Consent manager issue',
  DATA_ACCESS_DENIED: 'Data access request denial',
  EXCESSIVE_DATA_COLLECTION: 'Excessive data collection',
  OTHER: 'Other',
} as const

export type ComplaintCategoryLabel = (typeof CATEGORY_LABELS)[keyof typeof CATEGORY_LABELS]

/**
 * ComplaintSubmitDialog.tsx, opened from ComplaintListPage.tsx. The category Select has no
 * labelId/label association in the component (only a plain, unconnected "Category *" Typography
 * next to it) - unlike every other Select in this suite, `getByRole('combobox', {name: ...})`
 * finds nothing here, so this locates it positionally as the dialog's one and only combobox.
 * Worth a real accessibility fix upstream; not this suite's job to work around more than this.
 */
export class ComplaintSubmitDialog {
  readonly root: Locator
  readonly categorySelect: Locator
  readonly descriptionField: Locator
  readonly uploadButton: Locator
  readonly removeAttachmentButton: Locator
  readonly submitButton: Locator
  readonly cancelButton: Locator
  readonly categoryRequiredError: Locator
  readonly descriptionRequiredError: Locator
  readonly submitFailedAlert: Locator

  constructor(private readonly page: Page) {
    this.root = page.getByRole('dialog')
    this.categorySelect = this.root.getByRole('combobox')
    this.descriptionField = this.root.getByPlaceholder('Describe your complaint in detail')
    this.uploadButton = this.root.getByRole('button', { name: 'Upload files' })
    this.removeAttachmentButton = this.root.getByRole('button', { name: 'Remove attachment' })
    this.submitButton = this.root.getByRole('button', { name: 'Submit complaint' })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.categoryRequiredError = this.root.getByText('Select a category to continue.')
    this.descriptionRequiredError = this.root.getByText('Describe your complaint to continue.')
    this.submitFailedAlert = this.root.getByText('Unable to submit your complaint right now. Please try again.')
  }

  async selectCategory(label: ComplaintCategoryLabel): Promise<void> {
    await this.categorySelect.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async fillDescription(description: string): Promise<void> {
    await this.descriptionField.fill(description)
  }

  /** name/mimeType are set explicitly (Playwright's setInputFiles) - actual byte content doesn't need to be a real file of that type, only the declared metadata does, matching AttachmentPolicy's own server-side check. */
  async attachFile(name: string, mimeType: string, buffer: Buffer = Buffer.from('fake-attachment-content')): Promise<void> {
    await this.root.locator('input[type="file"]').setInputFiles({ name, mimeType, buffer })
  }

  stagedAttachmentName(name: string): Locator {
    return this.root.getByText(name, { exact: true })
  }

  async submit(): Promise<void> {
    await this.submitButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }
}
