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

import {
  Box,
  Chip,
  Collapse,
  Divider,
  IconButton,
  Stack,
  Tooltip,
  Typography,
} from '@wso2/oxygen-ui'
import { ChevronDown, ChevronUp, Copy, ExternalLink } from '@wso2/oxygen-ui-icons-react'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { getSubscriptionStatusChipColor } from '../utils/subscriptionStatusChip'

interface CompletionEvidenceCardProps {
  completionStatus?: string
  completionEvidence?: string
  deliveryMode?: string
  defaultExpanded?: boolean
}

export default function CompletionEvidenceCard({
  completionStatus,
  completionEvidence,
  deliveryMode,
  defaultExpanded = true,
}: CompletionEvidenceCardProps): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const [expanded, setExpanded] = useState(defaultExpanded)

  if (deliveryMode && deliveryMode.toLowerCase() === 'poll') {
    return null
  }

  if (!completionStatus && !completionEvidence) {
    return null
  }

  const isUrl =
    Boolean(completionEvidence) &&
    (completionEvidence!.startsWith('http://') || completionEvidence!.startsWith('https://'))

  const statusLabel = completionStatus
    ? t(`events.status.${completionStatus.toLowerCase()}`, completionStatus)
    : ''

  return (
    <Box
      sx={{
        borderRadius: 1,
        border: 1,
        borderColor: 'divider',
        bgcolor: 'background.paper',
        overflow: 'hidden',
      }}
    >
      <Box
        onClick={() => setExpanded((prev) => !prev)}
        sx={{
          p: 1.5,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          cursor: 'pointer',
          userSelect: 'none',
          '&:hover': {
            bgcolor: 'action.hover',
          },
        }}
      >
        <Typography variant="subtitle2" fontWeight={600}>
          {t('subscriptions.deliveryHistory.completion')}
        </Typography>
        <IconButton
          size="small"
          aria-label={expanded ? 'Collapse completion evidence' : 'Expand completion evidence'}
          onClick={(e) => {
            e.stopPropagation()
            setExpanded((prev) => !prev)
          }}
        >
          {expanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
        </IconButton>
      </Box>

      <Collapse in={expanded}>
        <Divider />
        <Stack spacing={1.5} sx={{ p: 2 }}>
          {completionStatus ? (
            <Stack direction="row" alignItems="center" spacing={1}>
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ minWidth: 70, fontWeight: 500 }}
              >
                {t('subscriptions.deliveryHistory.attemptStatus')}:
              </Typography>
              <Chip
                size="small"
                label={statusLabel}
                color={getSubscriptionStatusChipColor(completionStatus)}
                sx={{ fontWeight: 600, height: 22 }}
              />
            </Stack>
          ) : null}

          {completionEvidence ? (
            <Stack direction="row" alignItems="flex-start" spacing={1}>
              <Typography
                variant="body2"
                color="text.secondary"
                sx={{ minWidth: 70, fontWeight: 500, pt: 0.25 }}
              >
                {t('subscriptions.deliveryHistory.completion')}:
              </Typography>
              <Box
                sx={{ flex: 1, display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}
              >
                {isUrl ? (
                  <Box
                    component="a"
                    href={completionEvidence}
                    target="_blank"
                    rel="noopener noreferrer"
                    sx={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 0.5,
                      color: 'primary.main',
                      textDecoration: 'none',
                      fontSize: '0.875rem',
                      fontWeight: 500,
                      wordBreak: 'break-all',
                      '&:hover': {
                        textDecoration: 'underline',
                      },
                    }}
                  >
                    {completionEvidence}
                    <ExternalLink size={14} />
                  </Box>
                ) : (
                  <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>
                    {completionEvidence}
                  </Typography>
                )}
                <Tooltip title={t('copyableText.copy')}>
                  <IconButton
                    size="small"
                    aria-label={t('copyableText.copy')}
                    onClick={() => {
                      if (completionEvidence) {
                        navigator.clipboard?.writeText(completionEvidence)
                      }
                    }}
                  >
                    <Copy size={14} />
                  </IconButton>
                </Tooltip>
              </Box>
            </Stack>
          ) : null}
        </Stack>
      </Collapse>
    </Box>
  )
}
