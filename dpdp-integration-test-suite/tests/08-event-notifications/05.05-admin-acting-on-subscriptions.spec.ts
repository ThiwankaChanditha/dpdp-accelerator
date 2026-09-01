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

import { test, expect } from '../../fixtures/auth.fixtures'
import { publishMarkedEvent, seedActiveTopic, seedPollSubscription } from '../../utils/eventNotificationSetup'

/**
 * Acting on subscriptions - re-verification state checks and deletion
 * (SubscriptionServiceImpl.retryVerification/deleteSubscription). The one receiver-dependent
 * test that used to live here (06.02.04, re-verifying a stale webhook subscription) was removed -
 * see tests/08-event-notifications/README.md, "Webhook-dependent tests".
 */
test.describe('Admin acting on Subscriptions', () => {
  test('06.02.05 - An active or deleted subscription cannot be re-verified', async ({ consentAdminEventApi }) => {
    // Two separate topics, not one shared topic: every subscription's groupId is currently
    // forced to the org's own id server-side (see eventNotificationSetup.ts), so two
    // same-filter poll subscriptions on the very same topic would collide as duplicates.
    const activeTopic = await seedActiveTopic(consentAdminEventApi, 'sub-reverify-invalid-active')
    const activeSub = await seedPollSubscription(consentAdminEventApi, activeTopic.name)

    const activeVerify = await consentAdminEventApi.verifySubscription(activeSub.subscriptionId)
    expect(activeVerify.status()).toBe(409)
    expect((await activeVerify.json()).code).toBe('EN-4003')

    const deletedTopic = await seedActiveTopic(consentAdminEventApi, 'sub-reverify-invalid-deleted')
    const toDelete = await seedPollSubscription(consentAdminEventApi, deletedTopic.name)
    const deleteResponse = await consentAdminEventApi.deleteSubscription(toDelete.subscriptionId)
    expect(deleteResponse.status()).toBe(200)

    const deletedVerify = await consentAdminEventApi.verifySubscription(toDelete.subscriptionId)
    expect(deletedVerify.status()).toBe(404)
  })

  test('06.04.01 - Deleting a subscription soft-deletes it while preserving its record', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'sub-delete')
    // No deliveries at all - one of the two preconditions the spreadsheet itself allows
    // ("Subscription with completed/no deliveries") and the simplest to seed reliably.
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)

    const deleteResponse = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
    expect(deleteResponse.status(), await deleteResponse.text()).toBe(200)
    expect((await deleteResponse.json()).status).toBe('deleted')

    // Status changed, but the record itself (and its would-be audit trail) is still readable -
    // "deleted" is a state, not row removal.
    const getResponse = await consentAdminEventApi.getSubscription(subscription.subscriptionId)
    expect(getResponse.ok()).toBe(true)
    expect((await getResponse.json()).status).toBe('deleted')

    const eventsResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
    expect(eventsResponse.ok()).toBe(true)
  })

  test('06.04.02 - A subscription with a pending delivery cannot be deleted', async ({ consentAdminEventApi }) => {
    // A POLL subscription's own delivery stays `pending` until it's consumed via the poll
    // endpoint (confirmed: SubscriptionServiceImpl.deleteSubscription checks
    // subscriptionDAO.hasPendingOrInFlightDeliveries) - the reliable, receiver-free way to get a
    // delivery stuck `pending` for this test.
    const topic = await seedActiveTopic(consentAdminEventApi, 'sub-delete-blocked')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)
    // subscription.groupId is server-forced to the org id (see eventNotificationSetup.ts) -
    // publish using that exact value to get a matching delivery.
    await publishMarkedEvent(consentAdminEventApi, subscription.groupId!, topic.name)

    await expect(async () => {
      const eventsResponse = await consentAdminEventApi.listSubscriptionEvents(subscription.subscriptionId)
      const { items } = (await eventsResponse.json()) as { items: unknown[] }
      expect(items.length).toBeGreaterThan(0)
    }).toPass({ timeout: 10_000 })

    const deleteResponse = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
    expect(deleteResponse.status()).toBe(409)
    expect((await deleteResponse.json()).code).toBe('EN-4090')

    // Unchanged - still active, delivery still pending.
    const getResponse = await consentAdminEventApi.getSubscription(subscription.subscriptionId)
    expect((await getResponse.json()).status).toBe('active')
  })

  test('06.04.03 - Deleting an already-deleted subscription returns not found', async ({ consentAdminEventApi }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'sub-delete-twice')
    const subscription = await seedPollSubscription(consentAdminEventApi, topic.name)

    const firstDelete = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
    expect(firstDelete.status()).toBe(200)

    const secondDelete = await consentAdminEventApi.deleteSubscription(subscription.subscriptionId)
    expect(secondDelete.status()).toBe(404)
  })
})
