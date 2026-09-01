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
 * SubscriptionRegisterDialog.tsx. Its Topic <Select> is populated by a live
 * `fetchTopics({limit:100, offset:0, status:'ACTIVE'})` call on open - only ACTIVE topics ever
 * appear as options, so a test must ensure its topic is ACTIVE (system topics qualify too)
 * before opening this dialog. Shared Secret is pre-filled with a random 32-hex-char value the
 * moment the dialog mounts - `sharedSecretValue()` reads whatever the field currently holds
 * rather than assuming a test-supplied one.
 */
export class SubscriptionRegisterDialog {
  readonly root: Locator
  readonly topicSelect: Locator
  readonly filterModeSelect: Locator
  readonly purposesField: Locator
  readonly deliveryModeSelect: Locator
  readonly callbackUrlField: Locator
  readonly sharedSecretField: Locator
  readonly generateSecretButton: Locator
  readonly submitButton: Locator
  readonly cancelButton: Locator
  readonly topicRequiredError: Locator
  readonly purposesRequiredError: Locator
  readonly callbackUrlRequiredError: Locator
  readonly callbackUrlInvalidError: Locator
  readonly secretRequiredError: Locator

  constructor(private readonly page: Page) {
    this.root = page.getByRole('dialog')
    this.topicSelect = this.root.getByRole('combobox', { name: 'Topic' })
    this.filterModeSelect = this.root.getByRole('combobox', { name: 'Purpose Filter Mode' })
    this.purposesField = this.root.getByLabel('Purposes (comma-separated)')
    this.deliveryModeSelect = this.root.getByRole('combobox', { name: 'Delivery Mode' })
    this.callbackUrlField = this.root.getByLabel('Webhook Callback URL')
    this.sharedSecretField = this.root.getByLabel('Shared Secret')
    this.generateSecretButton = this.root.getByRole('button', { name: 'Generate new secret' })
    this.submitButton = this.root.getByRole('button', { name: /^Register/ })
    this.cancelButton = this.root.getByRole('button', { name: 'Cancel' })
    this.topicRequiredError = this.root.getByText('Topic is required.')
    this.purposesRequiredError = this.root.getByText(
      'Purposes are required when filtering by specific or all-except.',
    )
    this.callbackUrlRequiredError = this.root.getByText('Callback URL is required for webhook subscriptions.')
    this.callbackUrlInvalidError = this.root.getByText('Please provide a valid absolute URL (http:// or https://).')
    this.secretRequiredError = this.root.getByText('Shared secret is required.')
  }

  async selectTopic(name: string): Promise<void> {
    await this.topicSelect.click()
    await this.page.getByRole('option', { name, exact: true }).click()
  }

  async selectFilterMode(label: 'All Events' | 'Specific Purposes' | 'All Except Purposes'): Promise<void> {
    await this.filterModeSelect.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async fillPurposes(commaSeparated: string): Promise<void> {
    await this.purposesField.fill(commaSeparated)
  }

  async selectDeliveryMode(label: 'Webhook' | 'Poll'): Promise<void> {
    await this.deliveryModeSelect.click()
    await this.page.getByRole('option', { name: label, exact: true }).click()
  }

  async fillCallbackUrl(url: string): Promise<void> {
    await this.callbackUrlField.fill(url)
  }

  async fillSharedSecret(secret: string): Promise<void> {
    await this.sharedSecretField.fill(secret)
  }

  async clearSharedSecret(): Promise<void> {
    await this.sharedSecretField.fill('')
  }

  sharedSecretValue(): Promise<string> {
    return this.sharedSecretField.inputValue()
  }

  async submit(): Promise<void> {
    await this.submitButton.click()
  }

  async cancel(): Promise<void> {
    await this.cancelButton.click()
  }
}
