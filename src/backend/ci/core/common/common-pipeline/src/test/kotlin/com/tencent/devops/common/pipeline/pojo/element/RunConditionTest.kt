package com.tencent.devops.common.pipeline.pojo.element

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RunConditionTest {

    /**
     * [runEvenCancel]是取消传播链路（引擎调度、构建机认领、内置插件收尾、执行记录回写）的唯一判定出口。
     * 漏掉任一取消后仍需运行的条件，对应任务都会在取消到来时被提前置为UNEXEC/CANCELED，连启动的机会都没有。
     */
    @Test
    fun `main step run even cancel`() {
        val runEvenCancelConditions = setOf(RunCondition.PRE_TASK_FAILED_EVEN_CANCEL)
        RunCondition.values().forEach { condition ->
            Assertions.assertEquals(
                condition in runEvenCancelConditions,
                ElementAdditionalOptions(runCondition = condition).runEvenCancel(),
                "runEvenCancel of $condition"
            )
        }
        // 未配置执行条件时按默认的[所有前置插件运行成功时]处理，取消后不再运行
        Assertions.assertFalse(ElementAdditionalOptions().runEvenCancel())
        Assertions.assertFalse(null.runEvenCancel())
    }

    /**
     * 收尾步骤一律放行这道门禁，跑不跑交由[JobPostRunWhen]比对冻结的Job主状态单独判定。
     *
     * 若这里按运行时机放行，收尾段内取消当前卡住的步骤时，后续[JobPostRunWhen.ON_SUCCESS]、
     * [JobPostRunWhen.ON_FAILURE]的步骤会被门禁直接判为UNEXEC，「只杀当前步、继续下一步」就不成立。
     */
    @Test
    fun `job post step always passes cancel gate`() {
        JobPostRunWhen.values().forEach { runWhen ->
            Assertions.assertTrue(
                ElementAdditionalOptions(jobPostStepFlag = true, runWhen = runWhen).runEvenCancel(),
                "runEvenCancel of $runWhen"
            )
        }
        // 不配运行时机的收尾步骤同样放行
        Assertions.assertTrue(ElementAdditionalOptions(jobPostStepFlag = true).runEvenCancel())
        // 收尾步骤上的runCondition一律忽略：这里配了个「取消就不跑」的条件，也不能把它挡在门外
        Assertions.assertTrue(
            ElementAdditionalOptions(
                jobPostStepFlag = true,
                runCondition = RunCondition.PRE_TASK_FAILED_BUT_CANCEL
            ).runEvenCancel()
        )
    }
}
