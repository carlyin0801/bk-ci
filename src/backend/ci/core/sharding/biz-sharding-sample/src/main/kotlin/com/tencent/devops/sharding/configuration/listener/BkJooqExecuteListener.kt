package com.tencent.devops.sharding.configuration.listener

import com.mysql.cj.jdbc.result.ResultSetImpl
import com.tencent.devops.common.api.util.JsonUtil
import org.jooq.ExecuteContext
import org.jooq.impl.DefaultExecuteListener
import org.jooq.tools.StopWatch
import org.jooq.tools.jdbc.DefaultResultSet
import org.slf4j.LoggerFactory

class BkJooqExecuteListener : DefaultExecuteListener() {

    companion object {
        private val logger = LoggerFactory.getLogger(BkJooqExecuteListener::class.java)
    }

    override fun executeStart(ctx: ExecuteContext) {
        super.executeStart(ctx)
        ctx.data(getStopWatchName(), StopWatch())
    }

    override fun executeEnd(ctx: ExecuteContext) {
        super.executeEnd(ctx)
        val stopWatch = ctx.data(getStopWatchName()) as StopWatch
        val costTime = stopWatch.split() / 1000000 // 单位：毫秒
        logger.info("Bk SQL:[${ctx.query().toString()}] cost $costTime ms,rows size:${ctx.result()?.size}|${ctx.record()?.fieldsRow()?.size()}|${ctx.data().size}")
        if (costTime > 100) {
            logger.warn("Bk Slow SQL:[${ctx.query().toString()}] cost $costTime ms")
        }
    }

    private fun getStopWatchName() = "${this.javaClass.name}.watch"
}

