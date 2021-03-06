/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.bk.codecc.task.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.hibernate.validator.constraints.NotBlank;

import java.util.List;

/**
 * 任务代码库配置
 *
 * @version V1.0
 * @date 2019/6/13
 */
@Data
@ApiModel("任务代码库配置")
public class TaskCodeLibraryVO
{
    @ApiModelProperty(value = "任务主键id")
    private Long taskId;

    @ApiModelProperty(value = "项目ID")
    private String projectId;

    @ApiModelProperty(value = "项目名称")
    private String projectName;

    @ApiModelProperty(value = "流水线ID")
    private String pipelineId;

    @ApiModelProperty(value = "流水线名称")
    private String pipelineName;

    @NotBlank(message = "凭证管理的主键id不能为空")
    @ApiModelProperty(value = "凭证管理的主键id")
    private String repoHashId;

    @NotBlank(message = "分支名称不能为空")
    @ApiModelProperty(value = "分支名，默认为master")
    private String branch;

    @NotBlank(message = "代码库类型不能为空")
    @ApiModelProperty(value = "代码库类型")
    private String scmType;

    /**
     * 项目接入的工具列表，查询时使用
     */
    @ApiModelProperty(value = "项目接入的工具列表")
    private List<ToolConfigParamJsonVO> toolConfigList;


}
