/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.engine.control

import com.tencent.devops.common.pipeline.NameAndValue
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.JobRunCondition
import com.tencent.devops.common.pipeline.enums.StageRunCondition
import com.tencent.devops.common.pipeline.pojo.element.ElementAdditionalOptions
import com.tencent.devops.common.pipeline.pojo.element.OnCancel
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.common.pipeline.pojo.element.PreFailureSkippedAs
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.config.CommonConfig
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.process.TestBase
import com.tencent.devops.process.utils.TASK_FAIL_RETRY_MAX_COUNT
import com.tencent.devops.process.utils.TASK_FAIL_RETRY_MIN_COUNT
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * @version 1.0
 */
@Suppress("ALL")
class ControlUtilsTest : TestBase() {

    @BeforeEach
    fun setup() {
        val commonConfig: CommonConfig = mockk()
        val redisOperation: RedisOperation = mockk()
        every {
            commonConfig.devopsDefaultLocaleLanguage
        } returns "zh_CN"
        every {
            redisOperation.get(any())
        } returns "zh_CN"
        mockkObject(SpringContextUtil)
        every {
            SpringContextUtil.getBean(CommonConfig::class.java)
        } returns commonConfig
        every {
            SpringContextUtil.getBean(RedisOperation::class.java)
        } returns redisOperation
    }

    @Test
    fun isEnable() {
        Assertions.assertTrue(ControlUtils.isEnable(null))
        Assertions.assertFalse(
            ControlUtils.isEnable(
                additionalOptions = elementAdditionalOptions(
                    enable = false, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                )
            )
        )
        Assertions.assertTrue(
            ControlUtils.isEnable(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
            )
        )
        Assertions.assertTrue(ControlUtils.isEnable(nullObject))
    }

    @Test
    fun continueWhenFailure() {
        Assertions.assertFalse(ControlUtils.continueWhenFailure(nullObject))
        Assertions.assertFalse(
            ControlUtils.continueWhenFailure(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
            )
        )
        Assertions.assertTrue(
            ControlUtils.continueWhenFailure(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(continueWhenFailed = true)
            )
        )
    }

    @Test
    fun checkSkipCondition() {
        val buildId = "b-x1x2x3x4x6x2"
        val conditions = mutableListOf(NameAndValue("key1", "a"), NameAndValue("key2", "b"))
        val variables = mutableMapOf("key1" to "a", "key2" to "b")
        Assertions.assertTrue(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN
            )
        )
        Assertions.assertFalse(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_VARIABLE_MATCH
            )
        )

        conditions.clear()
        conditions.add(NameAndValue("key2", "a"))
        conditions.add(NameAndValue("key3", "3"))
        variables.clear()
        variables["key3"] = "un"
        Assertions.assertFalse(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN
            )
        )
        Assertions.assertTrue(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_VARIABLE_MATCH
            )
        )
        Assertions.assertFalse(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_CONDITION_MATCH,
                customCondition = "key3=='un'"
            )
        )
        Assertions.assertFalse(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_CONDITION_MATCH,
                customCondition = "key3==key3"
            )
        )
        Assertions.assertFalse(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_CONDITION_MATCH,
                customCondition = "true==true"
            )
        )
        Assertions.assertTrue(
            ControlUtils.checkJobSkipCondition(
                conditions = conditions,
                variables = variables,
                buildId = buildId,
                runCondition = JobRunCondition.CUSTOM_CONDITION_MATCH,
                customCondition = "a==a"
            )
        )
    }

    @Test
    fun pauseBeforeExec() {
        var pauseFlag = false
        Assertions.assertFalse(ControlUtils.pauseBeforeExec(null, pauseFlag))
        Assertions.assertTrue(
            ControlUtils.pauseBeforeExec(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(pauseBeforeExec = true),
                alreadyPauseFlag = pauseFlag
            )
        )
        pauseFlag = false
        Assertions.assertTrue(
            ControlUtils.pauseBeforeExec(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(pauseBeforeExec = true),
                alreadyPauseFlag = pauseFlag
            )
        )
        pauseFlag = true
        Assertions.assertFalse(
            ControlUtils.pauseBeforeExec(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(pauseBeforeExec = true),
                alreadyPauseFlag = pauseFlag
            )
        )
        Assertions.assertFalse(
            ControlUtils.pauseBeforeExec(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(pauseBeforeExec = true),
                alreadyPauseFlag = pauseFlag
            )
        )
        Assertions.assertFalse(
            ControlUtils.pauseBeforeExec(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(pauseBeforeExec = null),
                alreadyPauseFlag = pauseFlag
            )
        )
    }

    @Test
    fun retryWhenFailure() {
        val nullObject = null
        var retryCount = 0
        Assertions.assertFalse(ControlUtils.retryWhenFailure(nullObject, retryCount))
        Assertions.assertFalse(
            ControlUtils.retryWhenFailure(
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY)
                    .copy(retryWhenFailed = false),
                retryCount = retryCount
            )
        )

        Assertions.assertFalse(
            ControlUtils.retryWhenFailure(
                additionalOptions = elementAdditionalOptions(
                    enable = false, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                retryCount = retryCount
            )
        )

        var setRetryCount = TASK_FAIL_RETRY_MAX_COUNT + 100 // 故意设置超过最大值，验证会被强制改回
        retryCount = 0
        while (retryCount < TASK_FAIL_RETRY_MAX_COUNT) {
            Assertions.assertTrue(
                ControlUtils.retryWhenFailure(
                    additionalOptions = elementAdditionalOptions(
                        retryCount = setRetryCount, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                    ).copy(retryWhenFailed = true),
                    retryCount = retryCount
                )
            )
            retryCount++
        }

        // exceed max retry count
        Assertions.assertFalse(
            ControlUtils.retryWhenFailure(
                additionalOptions = elementAdditionalOptions(
                    retryCount = setRetryCount, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ).copy(retryWhenFailed = true),
                retryCount = retryCount
            )
        )

        setRetryCount = 0 // 故意设置小于最小值，验证会被强制改回
        retryCount = 0
        while (retryCount < TASK_FAIL_RETRY_MIN_COUNT) {

            Assertions.assertTrue(
                ControlUtils.retryWhenFailure(
                    additionalOptions = elementAdditionalOptions(
                        retryCount = setRetryCount, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                    ).copy(retryWhenFailed = true),
                    retryCount = retryCount
                )
            )
            retryCount++
        }

        // exceed max retry count
        Assertions.assertFalse(
            ControlUtils.retryWhenFailure(
                additionalOptions = elementAdditionalOptions(
                    retryCount = setRetryCount, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ).copy(retryWhenFailed = true),
                retryCount = retryCount
            )
        )
    }

    @Test
    fun checkCustomVariableSkip() {
        variables["a"] = "1"
        val customeVarabiles = mutableListOf(NameAndValue("a", "1"))
        // 自定义变量全部满足时不运行
        Assertions.assertTrue(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN, customVariables = customeVarabiles
                )
            )
        )
        // 自定义变量全部满足时不运行, 不满足，运行。。。
        variables["a"] = "2"
        Assertions.assertFalse(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN, customVariables = customeVarabiles
                )
            )
        )
        // 自定义变量全部满足时运行
        variables["a"] = "1"
        Assertions.assertFalse(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH, customVariables = customeVarabiles
                )
            )
        )
        // 自定义变量全部满足时运行, 不满足不运行。。。
        variables["a"] = "2"
        Assertions.assertTrue(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH, customVariables = customeVarabiles
                )
            )
        )
        // 支持变量
        variables["a"] = "1"; variables["var_1"] = "1"
        customeVarabiles.add(NameAndValue("a", "\${var_1}"))
        Assertions.assertFalse(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH, customVariables = customeVarabiles
                )
            )
        )
        variables["a"] = "1"; variables["var_1"] = "1"
        customeVarabiles.add(NameAndValue("a", "\${var_1}"))
        Assertions.assertTrue(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN, customVariables = customeVarabiles
                )
            )
        )
        variables["a"] = "2"; variables["var_1"] = "1"
        customeVarabiles.add(NameAndValue("a", "\${var_1}"))
        Assertions.assertFalse(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN, customVariables = customeVarabiles
                )
            )
        )
        variables["a"] = "2"; variables["var_1"] = "1"
        customeVarabiles.add(NameAndValue("a", "\${var_1}"))
        Assertions.assertTrue(
            ControlUtils.checkCustomVariableSkip(
                buildId = buildId, variables = variables,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH, customVariables = customeVarabiles
                )
            )
        )
    }

    @Test
    fun checkStageSkipCondition() {

        val conditions = mutableListOf(
            NameAndValue("a", "1")
        )

        variables["a"] = "2"
        // 条件匹配就跳过
        Assertions.assertFalse(
            ControlUtils.checkStageSkipCondition(
                conditions = conditions, variables = variables, buildId = buildId,
                runCondition = StageRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN
            )
        )

        variables["a"] = "1"
        // 条件匹配就跳过
        Assertions.assertTrue(
            ControlUtils.checkStageSkipCondition(
                conditions = conditions, variables = variables, buildId = buildId,
                runCondition = StageRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN
            )
        )

        variables["a"] = "1"
        // 条件匹配就跳过
        Assertions.assertFalse(
            ControlUtils.checkStageSkipCondition(
                conditions = conditions, variables = variables, buildId = buildId,
                runCondition = StageRunCondition.CUSTOM_VARIABLE_MATCH
            )
        )

        variables["a"] = "2"
        // 条件匹配就跳过
        Assertions.assertTrue(
            ControlUtils.checkStageSkipCondition(
                conditions = conditions, variables = variables, buildId = buildId,
                runCondition = StageRunCondition.CUSTOM_VARIABLE_MATCH
            )
        )

        run other@{
            Assertions.assertFalse(
                ControlUtils.checkStageSkipCondition(
                    conditions = conditions, variables = variables, buildId = buildId,
                    runCondition = StageRunCondition.CUSTOM_CONDITION_MATCH,
                    customCondition = "a==a"
                )
            )
            Assertions.assertFalse(
                ControlUtils.checkStageSkipCondition(
                    conditions = conditions, variables = variables, buildId = buildId,
                    runCondition = StageRunCondition.AFTER_LAST_FINISHED
                )
            )
        }
    }

    @Test
    fun `when container fail`() {
        val fail = BuildStatus.FAILED
        val failed = true
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_SUCCESS),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.CUSTOM_VARIABLE_MATCH),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )

        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.CUSTOM_CONDITION_MATCH),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 已下线的取值由 RunCondition.parse 兜底为默认意图，行为与[前序成功时]一致
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.parse("OTHER_TASK_RUNNING")!!),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
    }

    @Test
    fun `when container running`() {
        variables["a"] = "b"
        val fail = BuildStatus.RUNNING
        val failed = false
        // 成功不跳过
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_SUCCESS),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 满足执行条件 而不跳过
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "b"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 不满足执行条件 而跳过
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "a"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 满足不执行的条件 而跳过
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "b"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 不满足不执行的条件 而不跳过
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "a"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
    }

    @Test
    fun `when container success`() {
        variables["a"] = "b"
        val fail = BuildStatus.SUCCEED
        val failed = false
        // 成功不跳过
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_SUCCESS),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 满足执行条件 而不跳过
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "b"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 不满足执行条件 而跳过
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "a"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 满足不执行的条件 而跳过
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "b"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
        // 不满足不执行的条件 而不跳过
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN,
                    customVariables = mutableListOf(NameAndValue(key = "a", value = "a"))
                ),
                containerFinalStatus = fail, variables = variables, hasFailedTaskInSuccessContainer = failed
            )
        )
    }

    @Test
    fun checkTaskConditionSkip() {
        val variables = mutableMapOf<String, String>()

        // null check
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = null,
                containerFinalStatus = BuildStatus.RUNNING,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )

        // RunCondition.PRE_TASK_FAILED_ONLY & RUNNING
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                containerFinalStatus = BuildStatus.RUNNING,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )
        // RunCondition.PRE_TASK_FAILED_ONLY & FAIL
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                containerFinalStatus = BuildStatus.FAILED,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )
        // RunCondition.PRE_TASK_FAILED_ONLY & FAIL
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                containerFinalStatus = BuildStatus.FAILED,
                variables = variables,
                hasFailedTaskInSuccessContainer = false
            )
        )

        // RunCondition.PRE_TASK_FAILED_ONLY & disable
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = false, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                containerFinalStatus = BuildStatus.FAILED,
                variables = variables,
                hasFailedTaskInSuccessContainer = false
            )
        )

        // RunCondition.PRE_TASK_FAILED_ONLY & SUCCEED
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                containerFinalStatus = BuildStatus.SUCCEED,
                variables = variables,
                hasFailedTaskInSuccessContainer = false
            )
        )

        // 取消不是失败。即使前面有失败继续，也不再领取「失败才运行」的插件
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY
                ),
                containerFinalStatus = BuildStatus.CANCELED,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP
                ),
                containerFinalStatus = BuildStatus.CANCELED,
                variables = variables,
                hasFailedTaskInSuccessContainer = false
            )
        )
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP
                ),
                containerFinalStatus = BuildStatus.FAILED,
                variables = variables,
                hasFailedTaskInSuccessContainer = false
            )
        )
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    enable = true, runCondition = RunCondition.PRE_TASK_FAILED_EVEN_CANCEL
                ),
                containerFinalStatus = BuildStatus.CANCELED,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )
    }

    @Test
    fun `from scope only watches listed steps`() {
        val preTasks = listOf(
            preTask(stepId = "build", status = BuildStatus.SUCCEED),
            preTask(stepId = "lint", status = BuildStatus.FAILED)
        )
        // 名单内有失败：不跳过
        Assertions.assertFalse(
            skip(
                elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP,
                    fromStepIds = listOf("lint")
                ),
                preTasks
            )
        )
        // 名单内全成功：跳过，哪怕 Job 里别处有失败
        Assertions.assertTrue(
            skip(
                elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP,
                    fromStepIds = listOf("build")
                ),
                preTasks
            )
        )
        // 「前序成功时」+ 指定前序：名单内有失败就跳过
        Assertions.assertTrue(
            skip(
                elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_SUCCESS,
                    fromStepIds = listOf("lint")
                ),
                preTasks
            )
        )
        Assertions.assertFalse(
            skip(
                elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_SUCCESS,
                    fromStepIds = listOf("build")
                ),
                preTasks
            )
        )
    }

    @Test
    fun `skipped-as decides whether a skipped failure counts`() {
        val skipped = listOf(preTask(stepId = "flaky", status = BuildStatus.FAILED, failureSkipped = true))
        // 默认算成功：「前序失败时」判定为没有失败，跳过
        Assertions.assertTrue(
            skip(
                elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP,
                    fromStepIds = listOf("flaky")
                ),
                skipped
            )
        )
        // 显式算失败：不跳过
        Assertions.assertFalse(
            skip(
                elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP,
                    fromStepIds = listOf("flaky"),
                    preFailureSkippedAs = PreFailureSkippedAs.FAILURE
                ),
                skipped
            )
        )
    }

    @Test
    fun `on-cancel runs on cancel even when intent is failure`() {
        // 旧枚举表达不了的新组合：status=failure + on-cancel=continue
        Assertions.assertFalse(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP,
                    onCancel = OnCancel.CONTINUE
                ),
                containerFinalStatus = BuildStatus.CANCELED,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )
        // 不开 on-cancel 时取消就跳过
        Assertions.assertTrue(
            ControlUtils.checkTaskSkip(
                buildId = buildId,
                additionalOptions = elementAdditionalOptions(
                    runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP
                ),
                containerFinalStatus = BuildStatus.CANCELED,
                variables = variables,
                hasFailedTaskInSuccessContainer = true
            )
        )
    }

    /**
     * 表达式含状态函数才免掉「前序成功」这道门。
     * 不含的那一支是存量的全部，必须逐格与改造前一致。
     */
    @Test
    fun `expression without status function still requires previous success`() {
        val options = elementAdditionalOptions(
            runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
            customCondition = "variables.DO_DEPLOY == 'true'"
        )
        // 前序成功 + 表达式成立：跑
        Assertions.assertFalse(expressionSkip(options, BuildStatus.RUNNING))
        // 前序失败 / 取消：连求值都不做，直接跳过——与改造前同
        Assertions.assertTrue(expressionSkip(options, BuildStatus.FAILED))
        Assertions.assertTrue(expressionSkip(options, BuildStatus.CANCELED))
        Assertions.assertTrue(expressionSkip(options, BuildStatus.TERMINATE))
    }

    @Test
    fun `expression with status function skips the previous-success gate`() {
        val cancelled = elementAdditionalOptions(
            runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
            customCondition = "cancelled()"
        )
        // 只在取消时跑：四意图覆盖不到的长尾，这是本能力的核心用例
        Assertions.assertTrue(expressionSkip(cancelled, BuildStatus.RUNNING))
        Assertions.assertTrue(expressionSkip(cancelled, BuildStatus.FAILED))
        Assertions.assertFalse(expressionSkip(cancelled, BuildStatus.CANCELED))

        // 失败或取消都清理，等价于 when: {status: failure, on-cancel: continue}
        val cleanup = elementAdditionalOptions(
            runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
            customCondition = "failure() || cancelled()"
        )
        Assertions.assertTrue(expressionSkip(cleanup, BuildStatus.RUNNING))
        Assertions.assertFalse(expressionSkip(cleanup, BuildStatus.FAILED))
        Assertions.assertFalse(expressionSkip(cleanup, BuildStatus.CANCELED))
    }

    @Test
    fun `bare status function matches the corresponding intent`() {
        listOf(BuildStatus.RUNNING, BuildStatus.FAILED, BuildStatus.CANCELED).forEach { status ->
            Assertions.assertEquals(
                expressionSkip(elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_SUCCESS), status),
                expressionSkip(
                    elementAdditionalOptions(
                        runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
                        customCondition = "success()"
                    ),
                    status
                ),
                "success() 与 when: success 在 $status 下结果应一致"
            )
            Assertions.assertEquals(
                expressionSkip(
                    elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP),
                    status
                ),
                expressionSkip(
                    elementAdditionalOptions(
                        runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
                        customCondition = "failure()"
                    ),
                    status
                ),
                "failure() 与 when: failure 在 $status 下结果应一致"
            )
            Assertions.assertEquals(
                expressionSkip(
                    elementAdditionalOptions(runCondition = RunCondition.PRE_TASK_FAILED_EVEN_CANCEL),
                    status
                ),
                expressionSkip(
                    elementAdditionalOptions(
                        runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
                        customCondition = "always()"
                    ),
                    status
                ),
                "always() 与 when: always 在 $status 下结果应一致"
            )
        }
    }

    @Test
    fun `evaluation error counts as condition not met`() {
        val broken = elementAdditionalOptions(
            runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
            customCondition = "cancelled() && nosuchfn(x)"
        )
        Assertions.assertTrue(expressionSkip(broken, BuildStatus.CANCELED))
    }

    @Test
    fun `when and if together take AND`() {
        val options = elementAdditionalOptions(
            runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP
        ).apply { ifExpression = "variables.DO_DEPLOY == 'true'" }
        // 意图成立（前序失败）且表达式成立：跑
        Assertions.assertFalse(expressionSkip(options, BuildStatus.FAILED))
        // 意图不成立：不必求值，直接跳过
        Assertions.assertTrue(expressionSkip(options, BuildStatus.RUNNING))

        // 意图成立但表达式不成立：被 AND 收窄，跳过
        val narrowed = elementAdditionalOptions(
            runCondition = RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP
        ).apply { ifExpression = "variables.DO_DEPLOY == 'no'" }
        Assertions.assertTrue(expressionSkip(narrowed, BuildStatus.FAILED))
    }

    @Test
    fun `legacy step does not gain an extra AND condition`() {
        // ifExpression 是新字段，存量必为 null；customCondition 的残值不应被当成 AND 条件
        val stale = elementAdditionalOptions(
            runCondition = RunCondition.PRE_TASK_FAILED_EVEN_CANCEL,
            customCondition = "variables.DO_DEPLOY == 'no'"
        )
        Assertions.assertFalse(expressionSkip(stale, BuildStatus.CANCELED))
        Assertions.assertFalse(expressionSkip(stale, BuildStatus.FAILED))
    }

    private fun expressionSkip(options: ElementAdditionalOptions, containerFinalStatus: BuildStatus) =
        ControlUtils.checkTaskSkip(
            buildId = buildId,
            additionalOptions = options,
            containerFinalStatus = containerFinalStatus,
            variables = variables + mapOf("variables.DO_DEPLOY" to "true"),
            hasFailedTaskInSuccessContainer = false
        )

    private fun skip(options: ElementAdditionalOptions, preTasks: List<PreTaskStatus>) = ControlUtils.checkTaskSkip(
        buildId = buildId,
        additionalOptions = options,
        containerFinalStatus = BuildStatus.RUNNING,
        variables = variables,
        hasFailedTaskInSuccessContainer = preTasks.any { it.status.isFailure() },
        preTasks = preTasks
    )

    private fun preTask(stepId: String, status: BuildStatus, failureSkipped: Boolean = false) = PreTaskStatus(
        taskId = "t-$stepId",
        stepId = stepId,
        taskName = stepId,
        status = status,
        failureSkipped = failureSkipped
    )
}
