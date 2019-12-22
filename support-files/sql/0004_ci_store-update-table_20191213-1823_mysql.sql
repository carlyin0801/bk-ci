USE devops_ci_store;
SET NAMES utf8mb4;

CREATE TABLE `T_BUILD_TYPE_OPTIONS` (
  `PROJECT_ID` varchar(64) NOT NULL COMMENT '项目ID',
  `BUILD_TYPE` varchar(45) NOT NULL COMMENT '构建机类型',
  `PIPELINE_ID` varchar(34) NULL COMMENT '流水线ID，如果存在，优先取',
  `OS_LIST` varchar(128) NULL COMMENT '操作系统类型',
  `ENABLE_APP` bit(1) NULL COMMENT '是否支持选择对应的构建依赖',
  `CLICKABLE` bit(1) NULL COMMENT '是否可点击',
  `VISABLE` bit(1) NULL COMMENT '是否页面可见',
  `CREATOR` varchar(50) NOT NULL DEFAULT 'system' COMMENT '创建人',
  `MODIFIER` varchar(50) NOT NULL DEFAULT 'system' COMMENT '最近修改人',
  `CREATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `UPDATE_TIME` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`PROJECT_ID`, `BUILD_TYPE`),
  KEY `inx_tpi_create_time` (`CREATE_TIME`),
  KEY `inx_tpi_pipelineid` (`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 ROW_FORMAT=DYNAMIC COMMENT='BuildType选项控制';