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

import { AcrylicOrangeTheme, OxygenUIThemeProvider } from '@wso2/oxygen-ui'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AdminConsentFilters from '../features/admin-consents/components/AdminConsentFilters'
import {
  EMPTY_ADMIN_CONSENT_FILTERS,
  getAdminConsentFilters,
  normalizeAdminConsentFilters,
} from '../features/admin-consents/utils/adminConsentFilters'
import i18n from '../i18n/i18n'

function renderFilters(
  filters = EMPTY_ADMIN_CONSENT_FILTERS,
  onFilterChange = vi.fn(),
  onClear = vi.fn(),
): void {
  render(
    <OxygenUIThemeProvider theme={AcrylicOrangeTheme}>
      <I18nextProvider i18n={i18n}>
        <AdminConsentFilters filters={filters} onFilterChange={onFilterChange} onClear={onClear} />
      </I18nextProvider>
    </OxygenUIThemeProvider>,
  )
}

afterEach(cleanup)

describe('administrative consent filters', () => {
  it('trims the supported filter values', () => {
    expect(
      normalizeAdminConsentFilters({
        state: 'ACTIVE',
        consentId: '  consent-1 ',
        userId: ' admin ',
        relation: 'AUTHORIZER',
        serviceId: ' dpdp-portal ',
        purposeId: ' purpose-1 ',
        propertyKey: ' dataCategory ',
        propertyValue: ' personal ',
        createdAfter: ' 2026-01-01 ',
        createdBefore: ' 2026-01-31 ',
      }),
    ).toEqual({
      state: 'ACTIVE',
      consentId: 'consent-1',
      userId: 'admin',
      relation: 'AUTHORIZER',
      serviceId: 'dpdp-portal',
      purposeId: 'purpose-1',
      propertyKey: 'dataCategory',
      propertyValue: 'personal',
      createdAfter: '2026-01-01',
      createdBefore: '2026-01-31',
    })
  })

  it('reads native consent states from the URL and ignores unknown ones', () => {
    expect(getAdminConsentFilters(new URLSearchParams('state=PENDING')).state).toBe('PENDING')
    expect(getAdminConsentFilters(new URLSearchParams('state=CREATED')).state).toBe('All')
    expect(getAdminConsentFilters(new URLSearchParams('userId=admin')).userId).toBe('admin')
  })

  it('falls back to ANY for a missing or unknown relation', () => {
    expect(getAdminConsentFilters(new URLSearchParams('')).relation).toBe('ANY')
    expect(getAdminConsentFilters(new URLSearchParams('relation=BOGUS')).relation).toBe('ANY')
    expect(getAdminConsentFilters(new URLSearchParams('relation=AUTHORIZER')).relation).toBe(
      'AUTHORIZER',
    )
  })

  it('searches by User directly from the main row', () => {
    const onFilterChange = vi.fn()
    renderFilters(EMPTY_ADMIN_CONSENT_FILTERS, onFilterChange)

    expect(screen.getByPlaceholderText('Search by consent ID')).toBeInTheDocument()

    const userId = screen.getByRole('textbox', { name: 'User' })
    expect(userId).toBeEnabled()

    fireEvent.change(userId, { target: { value: ' admin ' } })
    fireEvent.keyDown(userId, { key: 'Enter' })

    expect(onFilterChange).toHaveBeenCalledWith({
      state: 'All',
      consentId: '',
      userId: 'admin',
      relation: 'ANY',
      serviceId: '',
      purposeId: '',
      propertyKey: '',
      propertyValue: '',
      createdAfter: '',
      createdBefore: '',
    })
  })

  it('keeps the relation select usable even before a User is set', () => {
    const onFilterChange = vi.fn()
    renderFilters(EMPTY_ADMIN_CONSENT_FILTERS, onFilterChange)

    const relationSelect = screen.getByRole('combobox', { name: 'Relation' })
    expect(relationSelect).not.toHaveAttribute('aria-disabled', 'true')

    fireEvent.mouseDown(relationSelect)
    fireEvent.click(screen.getByRole('option', { name: 'Authorizer' }))

    // Applied immediately, same as the other single-select filters - the API
    // layer itself drops `relation` whenever `userId` is empty, so selecting
    // one ahead of the User field is harmless.
    expect(onFilterChange).toHaveBeenCalledWith({
      ...EMPTY_ADMIN_CONSENT_FILTERS,
      relation: 'AUTHORIZER',
    })
  })

  it('shows the Relation hint above the field so it never covers its label or value', async () => {
    renderFilters()

    const relationSelect = screen.getByRole('combobox', { name: 'Relation' })
    fireEvent.mouseOver(relationSelect.closest('[aria-label]') as HTMLElement)

    const tooltip = await screen.findByRole('tooltip')
    expect(tooltip).toHaveTextContent('Set a User to filter by relation')
    expect(tooltip.querySelector('.MuiTooltip-tooltipPlacementTop')).not.toBeNull()
    expect(tooltip.querySelector('.MuiTooltip-arrow')).not.toBeNull()
  })

  it('offers service, purpose, a consent-property filter and a created-date range in advanced filters', () => {
    const onFilterChange = vi.fn()
    renderFilters(EMPTY_ADMIN_CONSENT_FILTERS, onFilterChange)

    fireEvent.click(screen.getByRole('button', { name: 'Advanced filters' }))

    const serviceId = screen.getByRole('textbox', { name: 'Service' })
    const purposeId = screen.getByRole('textbox', { name: 'Purpose' })
    const propertyKey = screen.getByRole('textbox', { name: 'Key' })
    const propertyValue = screen.getByRole('textbox', { name: 'Value' })
    expect(serviceId).toBeEnabled()
    expect(purposeId).toBeEnabled()
    expect(propertyKey).toBeEnabled()
    expect(propertyValue).toBeEnabled()
    expect(screen.queryByRole('textbox', { name: 'User' })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /element/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: /group/i })).not.toBeInTheDocument()

    fireEvent.change(serviceId, { target: { value: 'dpdp-portal' } })
    fireEvent.change(purposeId, { target: { value: ' purpose-1 ' } })
    fireEvent.change(propertyKey, { target: { value: ' dataCategory ' } })
    fireEvent.change(propertyValue, { target: { value: ' personal ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Apply' }))

    expect(onFilterChange).toHaveBeenCalledWith({
      state: 'All',
      consentId: '',
      userId: '',
      relation: 'ANY',
      serviceId: 'dpdp-portal',
      purposeId: 'purpose-1',
      propertyKey: 'dataCategory',
      propertyValue: 'personal',
      createdAfter: '',
      createdBefore: '',
    })
  })

  it('lists every native consent state in the state filter', () => {
    renderFilters()

    fireEvent.mouseDown(screen.getByRole('combobox', { name: 'State' }))

    expect(screen.getByRole('option', { name: 'All' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Pending' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Active' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Rejected' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Revoked' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Expired' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: 'Created' })).not.toBeInTheDocument()
  })

  it('disables advanced filters and explains why when a Consent ID filter is active', async () => {
    renderFilters({ ...EMPTY_ADMIN_CONSENT_FILTERS, consentId: 'consent-123' })

    const advancedFiltersButton = screen.getByRole('button', { name: 'Advanced filters' })
    const userId = screen.getByRole('textbox', { name: 'User' })
    const stateSelect = screen.getByRole('combobox', { name: 'State' })
    const relationSelect = screen.getByRole('combobox', { name: 'Relation' })
    expect(advancedFiltersButton).toBeDisabled()
    expect(userId).toBeDisabled()
    expect(stateSelect).toHaveAttribute('aria-disabled', 'true')
    expect(relationSelect).toHaveAttribute('aria-disabled', 'true')

    fireEvent.mouseOver(advancedFiltersButton.parentElement as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use advanced filters.'),
    ).toBeInTheDocument()

    fireEvent.mouseOver(userId.closest('[aria-label]') as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use the User ID filter.'),
    ).toBeInTheDocument()

    fireEvent.mouseOver(stateSelect.closest('[aria-label]') as HTMLElement)
    expect(
      await screen.findByText('Remove the Consent ID filter to use the state filter.'),
    ).toBeInTheDocument()
  })
})
