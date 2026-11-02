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

import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import { fetchTopics } from '../api/topicsApi'
import type { TopicListResponse, TopicRecord } from '../../../types/topic'

const PAGE_SIZE = 100

/**
 * Fetches all active topics across all pages up to `total`.
 *
 * Every page fetch explicitly pins `sort: 'name'` to guarantee deterministic,
 * non-overlapping page boundaries across SQL database engines, preventing
 * row jitter and missing/duplicate topics that would arise on tie-breaking
 * status sorts.
 */
export async function fetchAllActiveTopics(): Promise<TopicListResponse> {
  const firstPage = await fetchTopics({
    status: 'ACTIVE',
    sort: 'name',
    limit: PAGE_SIZE,
    offset: 0,
  })

  const total = firstPage.pagination?.totalCount ?? firstPage.total ?? firstPage.items.length
  const allItems = [...firstPage.items]

  if (total > PAGE_SIZE) {
    const remainingOffsets: number[] = []
    for (let offset = PAGE_SIZE; offset < total; offset += PAGE_SIZE) {
      remainingOffsets.push(offset)
    }

    const remainingPages = await Promise.all(
      remainingOffsets.map((offset) =>
        fetchTopics({
          status: 'ACTIVE',
          sort: 'name',
          limit: PAGE_SIZE,
          offset,
        }),
      ),
    )

    for (const page of remainingPages) {
      allItems.push(...page.items)
    }
  }

  // Defensive deduplication by topic name
  const seen = new Set<string>()
  const uniqueItems: TopicRecord[] = []
  for (const item of allItems) {
    if (!seen.has(item.name)) {
      seen.add(item.name)
      uniqueItems.push(item)
    }
  }

  return {
    items: uniqueItems,
    total: uniqueItems.length,
  }
}

export default function useSubscriptionTopicPicker(
  _search?: string,
): UseQueryResult<TopicListResponse> {
  return useQuery({
    queryKey: ['topics', 'picker'],
    queryFn: fetchAllActiveTopics,
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  })
}
