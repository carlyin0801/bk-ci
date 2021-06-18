export function getPipelineStatusClass (status) {
    const statusMap = {
        DEPENDENT_WAITING: 'waiting',
        WAITING: 'waiting',
        REVIEWING: 'waiting',
        CANCELED: 'canceled',
        REVIEW_ABORT: 'canceled',
        TRY_FINALLY: 'canceled',
        QUEUE_CACHE: 'canceled',
        UNEXEC: 'canceled',
        SKIP: 'skip',
        FAILED: 'danger',
        HEARTBEAT_TIMEOUT: 'danger',
        QUALITY_CHECK_FAIL: 'danger',
        QUEUE_TIMEOUT: 'danger',
        EXEC_TIMEOUT: 'danger',
        TERMINATE: 'danger',
        SUCCEED: 'success',
        REVIEW_PROCESSED: 'success',
        STAGE_SUCCESS: 'success',
        PAUSE: 'pause',
        RUNNING: 'running',
        PREPARE_ENV: 'running',
        QUEUE: 'running',
        LOOP_WAITING: 'running',
        CALL_WAITING: 'running'
    }
    return statusMap[status]
}

export function getPipelineStatusShapeIconCls (status) {
    const iconName = 'bk-icon'
    const iconMap = {
        RUNNING: 'icon-circle-2-1 executing',
        PREPARE_ENV: 'icon-circle-2-1 executing',
        QUEUE: 'icon-circle-2-1 executing',
        LOOP_WAITING: 'icon-circle-2-1 executing',
        CALL_WAITING: 'icon-circle-2-1 executing',
        DEPENDENT_WAITING: 'icon-clock',
        WAITING: 'icon-clock',
        CANCELED: 'icon-exclamation-circle-shape',
        TERMINATE: 'icon-exclamation-circle-shape',
        REVIEWING: 'icon-exclamation-triangle-shape',
        REVIEW_ABORT: 'icon-exclamation-triangle-shape',
        FAILED: 'icon-close-circle-shape',
        HEARTBEAT_TIMEOUT: 'icon-close-circle-shape',
        QUEUE_TIMEOUT: 'icon-close-circle-shape',
        EXEC_TIMEOUT: 'icon-close-circle-shape',
        SUCCEED: 'icon-check-circle-shape'
    }
    return [iconName, iconMap[status]]
}

export function getPipelineStatusCircleIconCls (status) {
    const iconName = 'bk-icon'
    const iconMap = {
        RUNNING: 'icon-circle-2-1 executing',
        PREPARE_ENV: 'icon-circle-2-1 executing',
        QUEUE: 'icon-circle-2-1 executing',
        LOOP_WAITING: 'icon-circle-2-1 executing',
        CALL_WAITING: 'icon-circle-2-1 executing',
        DEPENDENT_WAITING: 'icon-clock',
        WAITING: 'icon-clock',
        CANCELED: 'icon-exclamation',
        TERMINATE: 'icon-exclamation',
        REVIEWING: 'icon-exclamation-triangle',
        REVIEW_ABORT: 'icon-exclamation-triangle',
        FAILED: 'icon-close',
        HEARTBEAT_TIMEOUT: 'icon-close',
        QUEUE_TIMEOUT: 'icon-close',
        EXEC_TIMEOUT: 'icon-close',
        SUCCEED: 'icon-check-1'
    }
    return [iconName, iconMap[status]]
}

export function getPipelineStatusIconCls (status) {
    const iconName = 'bk-icon'
    const iconMap = {
        SUCCEED: 'icon-check-circle',
        FAILED: 'icon-close-circle',
        SKIP: 'icon-redo-arrow',
        RUNNING: 'icon-circle-2-1 executing'
    }
    return [iconName, iconMap[status]]
}
