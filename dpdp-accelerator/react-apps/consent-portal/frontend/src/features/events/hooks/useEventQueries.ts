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

import { keepPreviousData, type UseQueryResult, useQuery } from '@tanstack/react-query'
import type { EventFilters, EventListQueryParams, EventRecord } from '../../../types/event'
import type { SubscriptionEventHistoryRecord } from '../../../types/subscription'
import {
  fetchEventById,
  fetchEventDeliveries,
  fetchEventDeliveryHistory,
  fetchEvents,
} from '../api/eventsApi'

export interface EventListResult {
  rows: EventRecord[]
  total: number
  hasNextPage: boolean
}

function toListParams(
  filters: EventFilters,
  page: number,
  rowsPerPage: number,
): EventListQueryParams {
  return {
    search: filters.search.trim() || undefined,
    status: filters.status && filters.status !== 'All' ? filters.status : undefined,
    topic: filters.topic && filters.topic !== 'All' ? filters.topic : undefined,
    subscriptionId: filters.subscriptionId.trim() || undefined,
    limit: rowsPerPage,
    offset: page * rowsPerPage,
  }
}

export function useEventsQuery(
  filters: EventFilters,
  page: number,
  rowsPerPage: number,
): UseQueryResult<EventListResult> {
  const params = toListParams(filters, page, rowsPerPage)

  return useQuery({
    queryKey: ['events', params],
    queryFn: async (): Promise<EventListResult> => {
      const response = await fetchEvents(params)
      const items = response.items ?? []
      const total = response.pagination?.totalCount ?? response.total ?? items.length

      return {
        rows: items,
        total,
        hasNextPage: params.offset + items.length < total,
      }
    },
    placeholderData: keepPreviousData,
  })
}

export function useEventDeliveryHistoryQuery(
  deliveryId?: string,
): UseQueryResult<SubscriptionEventHistoryRecord | null> {
  return useQuery({
    queryKey: ['events', 'history', deliveryId],
    queryFn: async (): Promise<SubscriptionEventHistoryRecord | null> => {
      if (!deliveryId) return null
      return fetchEventDeliveryHistory(deliveryId)
    },
    enabled: Boolean(deliveryId),
  })
}

export function useEventDetailQuery(eventId?: string): UseQueryResult<EventRecord | null> {
  return useQuery({
    queryKey: ['events', 'detail', eventId],
    queryFn: async (): Promise<EventRecord | null> => {
      if (!eventId) return null
      return fetchEventById(eventId)
    },
    enabled: Boolean(eventId),
  })
}

export function useEventDeliveriesQuery(
  eventId?: string,
  page = 0,
  rowsPerPage = 20,
): UseQueryResult<EventListResult> {
  return useQuery({
    queryKey: ['events', 'deliveries', eventId, page, rowsPerPage],
    queryFn: async (): Promise<EventListResult> => {
      if (!eventId) {
        return { rows: [], total: 0, hasNextPage: false }
      }
      const response = await fetchEventDeliveries(eventId, rowsPerPage, page * rowsPerPage)
      const items = response.items ?? []
      const total = response.pagination?.totalCount ?? response.total ?? items.length
      return {
        rows: items,
        total,
        hasNextPage: page * rowsPerPage + items.length < total,
      }
    },
    enabled: Boolean(eventId),
    placeholderData: keepPreviousData,
  })
}
