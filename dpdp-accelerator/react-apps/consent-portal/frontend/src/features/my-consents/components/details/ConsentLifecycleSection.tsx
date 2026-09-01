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
  Button,
  Card,
  CardHeader,
  Divider,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@wso2/oxygen-ui'
import { History } from '@wso2/oxygen-ui-icons-react'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { ConsentStatusAuditEntry } from '../../../../types/consentHistory'
import { formatEpochTimestamp } from '../../../../utils/dateTime'
import { CONSENT_HISTORY_SCOPES } from '../../../../utils/scopes'
import { useAdminConsentStatusHistoryQuery } from '../../../admin-consents/hooks/useAdminConsentHistoryQueries'
import useAuthorization from '../../../auth/useAuthorization'
import ConsentFullHistoryDialog from '../ConsentFullHistoryDialog'
import { useConsentStatusHistoryQuery } from '../../hooks/useConsentHistoryQueries'
import {
  getConsentHistoryActionPresentation,
  isSystemActor,
} from '../../utils/consentHistoryLabels'
import { getConsentStateChipColor, getConsentStateLabelKey } from '../../utils/statusChip'

interface ConsentLifecycleSectionProps {
  consentId: string
  variant: 'self' | 'admin'
}

const LIFECYCLE_TABLE_COLUMN_COUNT = 4

const DATE_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
}

const TIME_FORMAT_OPTIONS: Intl.DateTimeFormatOptions = {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
}

const STATUS_DOT_COLOR: Record<ReturnType<typeof getConsentStateChipColor>, string> = {
  success: 'success.main',
  warning: 'warning.main',
  error: 'error.main',
  default: 'action.disabled',
}

type TranslateFn = (key: string, options?: Record<string, unknown>) => string

function describeEntry(entry: ConsentStatusAuditEntry, t: TranslateFn): string {
  const presentation = getConsentHistoryActionPresentation(entry.actionType)
  const actor = isSystemActor(entry.actionBy)
    ? t('consentRegistry.history.systemActor')
    : entry.actionBy
  return t('consentRegistry.history.description', {
    action: t(`consentRegistry.history.actions.${presentation.labelKey}`),
    actor,
  })
}

function renderTableBody(
  entries: ConsentStatusAuditEntry[],
  isLoading: boolean,
  isError: boolean,
  t: TranslateFn,
): React.JSX.Element {
  if (isLoading) {
    return (
      <TableRow>
        <TableCell colSpan={LIFECYCLE_TABLE_COLUMN_COUNT}>
          <Stack spacing={1.5}>
            <Skeleton variant="text" width="70%" />
            <Skeleton variant="text" width="55%" />
          </Stack>
        </TableCell>
      </TableRow>
    )
  }

  if (isError) {
    return (
      <TableRow>
        <TableCell colSpan={LIFECYCLE_TABLE_COLUMN_COUNT}>
          <Typography variant="body2" color="error.main" align="center">
            {t('consentRegistry.messages.loadFailed')}
          </Typography>
        </TableCell>
      </TableRow>
    )
  }

  if (entries.length === 0) {
    return (
      <TableRow>
        <TableCell colSpan={LIFECYCLE_TABLE_COLUMN_COUNT}>
          <Typography variant="body2" color="text.secondary" align="center">
            {t('consentRegistry.details.lifecycle.empty')}
          </Typography>
        </TableCell>
      </TableRow>
    )
  }

  return (
    <>
      {entries.map((entry) => {
        const statusLabel = t(
          `consentRegistry.status.${getConsentStateLabelKey(entry.currentStatus)}`,
        )

        return (
          <TableRow key={`${entry.actionTime}-${entry.actionType}-${entry.actionBy}`} hover>
            <TableCell>
              <Stack direction="row" spacing={1} alignItems="center">
                <Box
                  sx={{
                    width: 8,
                    height: 8,
                    borderRadius: '50%',
                    bgcolor: STATUS_DOT_COLOR[getConsentStateChipColor(entry.currentStatus)],
                    flexShrink: 0,
                  }}
                />
                <Typography variant="body2" fontWeight={600}>
                  {statusLabel}
                </Typography>
              </Stack>
            </TableCell>
            <TableCell>
              <Typography variant="body2">
                {formatEpochTimestamp(entry.actionTime, DATE_FORMAT_OPTIONS)}
              </Typography>
            </TableCell>
            <TableCell>
              <Typography variant="body2">
                {formatEpochTimestamp(entry.actionTime, TIME_FORMAT_OPTIONS)}
              </Typography>
            </TableCell>
            <TableCell>
              <Typography variant="body2">{describeEntry(entry, t)}</Typography>
            </TableCell>
          </TableRow>
        )
      })}
    </>
  )
}

function ConsentLifecycleSection({
  consentId,
  variant,
}: ConsentLifecycleSectionProps): React.JSX.Element | null {
  const { t } = useTranslation('common')
  const { hasScope } = useAuthorization()
  const [snapshotDialogOpen, setSnapshotDialogOpen] = useState(false)

  const canViewTimeline = hasScope(
    variant === 'admin'
      ? [CONSENT_HISTORY_SCOPES.STATUS_HISTORY_VIEW_ANY]
      : [CONSENT_HISTORY_SCOPES.STATUS_HISTORY_VIEW_SELF],
  )
  const canViewSnapshot = hasScope(
    variant === 'admin'
      ? [CONSENT_HISTORY_SCOPES.HISTORY_VIEW_ANY]
      : [CONSENT_HISTORY_SCOPES.HISTORY_VIEW_SELF],
  )

  const selfTimeline = useConsentStatusHistoryQuery(
    consentId,
    variant === 'self' && canViewTimeline,
  )
  const adminTimeline = useAdminConsentStatusHistoryQuery(
    consentId,
    variant === 'admin' && canViewTimeline,
  )
  const timeline = variant === 'admin' ? adminTimeline : selfTimeline

  const events = useMemo(
    () => [...timeline.entries].sort((first, second) => first.actionTime - second.actionTime),
    [timeline.entries],
  )

  if (!canViewTimeline) {
    return null
  }

  return (
    <Card sx={{ boxShadow: 1 }}>
      <CardHeader
        title={
          <Typography variant="h5" fontWeight={600}>
            {t('consentRegistry.details.section.lifecycle')}
          </Typography>
        }
        action={
          canViewSnapshot ? (
            <Button
              size="small"
              variant="outlined"
              startIcon={<History size={15} />}
              onClick={() => setSnapshotDialogOpen(true)}
            >
              {t('consentRegistry.history.viewFullSnapshot')}
            </Button>
          ) : null
        }
        sx={{ pb: 1 }}
      />
      <Divider />
      <TableContainer>
        <Table
          aria-label={t('consentRegistry.details.lifecycle.tableAriaLabel')}
          sx={{
            tableLayout: 'fixed',
            '& tbody tr:hover': { bgcolor: 'action.hover' },
          }}
        >
          <TableHead>
            <TableRow sx={{ bgcolor: 'action.default' }}>
              <TableCell sx={{ width: '16%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.status')}
              </TableCell>
              <TableCell sx={{ width: '15%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.date')}
              </TableCell>
              <TableCell sx={{ width: '15%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.time')}
              </TableCell>
              <TableCell sx={{ width: '56%', fontWeight: 700 }}>
                {t('consentRegistry.details.table.description')}
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>{renderTableBody(events, timeline.isLoading, timeline.isError, t)}</TableBody>
        </Table>
      </TableContainer>

      {canViewSnapshot ? (
        <ConsentFullHistoryDialog
          open={snapshotDialogOpen}
          consentId={consentId}
          variant={variant}
          onClose={() => setSnapshotDialogOpen(false)}
        />
      ) : null}
    </Card>
  )
}

export default ConsentLifecycleSection
