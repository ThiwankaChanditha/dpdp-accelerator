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

import type { Pagination } from './pagination'

export interface EventRecord {
  deliveryId?: string
  eventId: string
  subscriptionId?: string
  orgId?: string
  topic?: string
  topicId?: string
  payload?: string
  purposes?: string[]
  currentStatus?: string
  deliveryMode?: string
  deliveriesCount?: number
  occurredAt: string | number
  createdAt?: string | number
}

export interface EventDetailRecord extends EventRecord {
  deliveries?: EventRecord[]
}

export interface EventListQueryParams {
  limit: number
  offset: number
  search?: string
  status?: string
  topic?: string
  subscriptionId?: string
  purposes?: string
}

export interface EventListResponse {
  items: EventRecord[]
  total?: number
  pagination?: Pagination
}

export interface EventInput {
  topic: string
  purposes?: string[]
  payload: Record<string, unknown>
}

export interface EventFilters {
  search: string
  status: string
  topic: string
  subscriptionId: string
}
