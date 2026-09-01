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
import { seedActiveTopic } from '../../utils/eventNotificationSetup'
import { uniqueMarker } from '../../utils/testData'

/**
 * Registering subscriptions (SubscriptionRegisterDialog.tsx) - duplicate/mixed-mode-conflict
 * detection at the API level. The webhook-verification-dependent tests that used to live here
 * (register+verify, callback-URL validation, webhook intent verification) were removed - they
 * require a network-reachable receiver, and were unreliable on a machine whose LAN IP changes
 * mid-session (see tests/08-event-notifications/README.md, "Webhook-dependent tests").
 */
test.describe('Admin registering Subscriptions', () => {
  test('06.01.07 - An overlapping subscription with equivalent purposes and callback URL is rejected', async ({
    consentAdminEventApi,
  }) => {
    const topic = await seedActiveTopic(consentAdminEventApi, 'duplicate-check')
    const callbackUrl = `https://Example.com/${uniqueMarker('hook')}`
    const first = await consentAdminEventApi.createSubscription({
      topic: topic.name,
      filter: { type: 'specific', purposes: ['Account', 'Profile'] },
      delivery: { mode: 'webhook', callbackUrl, sharedSecret: uniqueMarker('secret') },
    })
    expect(first.status()).toBe(201)

    // Same host with different casing, same purposes with different order/casing/duplicates -
    // CallbackUrlCanonicalizer/PurposeOverlapUtils treat these as equivalent to the original.
    const duplicate = await consentAdminEventApi.createSubscription({
      topic: topic.name,
      filter: { type: 'specific', purposes: ['profile', 'ACCOUNT', 'account'] },
      delivery: { mode: 'webhook', callbackUrl: callbackUrl.toLowerCase(), sharedSecret: uniqueMarker('secret') },
    })
    expect(duplicate.status()).toBe(409)
  })

  test('06.01.08 - The same tenant/group/topic cannot mix webhook and poll delivery modes', async ({
    consentAdminEventApi,
  }) => {
    const topicA = await seedActiveTopic(consentAdminEventApi, 'mixed-mode-a')
    const groupA = uniqueMarker('group')
    const webhookFirst = await consentAdminEventApi.createSubscription({
      topic: topicA.name,
      groupId: groupA,
      filter: { type: 'all' },
      delivery: { mode: 'webhook', callbackUrl: 'https://example.com/hook-a', sharedSecret: uniqueMarker('secret') },
    })
    expect(webhookFirst.status()).toBe(201)
    const pollConflict = await consentAdminEventApi.createSubscription({
      topic: topicA.name,
      groupId: groupA,
      filter: { type: 'all' },
      delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
    })
    expect(pollConflict.status()).toBe(409)

    const topicB = await seedActiveTopic(consentAdminEventApi, 'mixed-mode-b')
    const groupB = uniqueMarker('group')
    const pollFirst = await consentAdminEventApi.createSubscription({
      topic: topicB.name,
      groupId: groupB,
      filter: { type: 'all' },
      delivery: { mode: 'poll', sharedSecret: uniqueMarker('secret') },
    })
    expect(pollFirst.status()).toBe(201)
    const webhookConflict = await consentAdminEventApi.createSubscription({
      topic: topicB.name,
      groupId: groupB,
      filter: { type: 'all' },
      delivery: { mode: 'webhook', callbackUrl: 'https://example.com/hook-b', sharedSecret: uniqueMarker('secret') },
    })
    expect(webhookConflict.status()).toBe(409)
  })

})
