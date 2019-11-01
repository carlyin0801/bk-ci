package com.tencent.devops.log.service.v2

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.log.dao.v2.IndexDaoV2
import com.tencent.devops.log.dao.v2.LogStatusDaoV2
import com.tencent.devops.log.model.v2.IndexAndType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit

@Service
class IndexServiceV2 @Autowired constructor(
    private val dslContext: DSLContext,
    private val indexDaoV2: IndexDaoV2,
    private val logStatusDaoV2: LogStatusDaoV2
) {

    companion object {
        private val logger = LoggerFactory.getLogger(IndexServiceV2::class.java)
    }

    private val indexCache = CacheBuilder.newBuilder()
        .maximumSize(100000)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build<String/*BuildId*/, String/*IndexName*/>(
            object : CacheLoader<String, String>() {
                override fun load(buildId: String): String {
                    return dslContext.transactionResult { configuration ->
                        val context = DSL.using(configuration)
                        val indexName = indexDaoV2.getIndexName(context, buildId)
                        if (indexName.isNullOrBlank()) {
                            logger.warn("[$buildId] Fail to get the index")
                            throw RuntimeException("Fail to get index")
                        } else {
                            indexName!!
                        }
                    }
                }
            }
        )

    fun getIndexAndType(buildId: String): IndexAndType {
        val index = indexCache.get(buildId)
        if (index.isNullOrBlank()) {
            throw OperationException("Fail to get the index of build $buildId")
        }
        return IndexAndType(index!!, index)
    }

    fun getAndAddLineNum(buildId: String, size: Int): Long? {
        val startLineNum = indexDaoV2.updateLastLineNum(dslContext, buildId, size)
        if (startLineNum == null) {
            logger.warn("[$buildId|$size] Fail to get and add the line num")
            return null
        }
        return startLineNum
    }

    fun finish(buildId: String, tag: String?, jobId: String?, executeCount: Int?, finish: Boolean) {
        logStatusDaoV2.finish(dslContext, buildId, tag, jobId, executeCount, finish)
    }

    fun isFinish(buildId: String, tag: String?, jobId: String?, executeCount: Int?): Boolean {
        return if (jobId.isNullOrBlank()) {
            logStatusDaoV2.isFinish(dslContext, buildId, tag, executeCount)
        } else {
            val logStatusList = logStatusDaoV2.listFinish(dslContext, buildId, tag, executeCount)
            logStatusList?.firstOrNull { it.jobId == jobId && it.tag.startsWith("stopVM-") }?.finished == true
        }
    }
}