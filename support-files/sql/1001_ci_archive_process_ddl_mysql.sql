USE devops_ci_archive_process;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_HISTORY
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_HISTORY` (
  `BUILD_ID` varchar(34) NOT NULL COMMENT '构建ID',
  `PARENT_BUILD_ID` varchar(34) DEFAULT NULL COMMENT '父级构建ID',
  `PARENT_TASK_ID` varchar(34) DEFAULT NULL COMMENT '父级任务ID',
  `BUILD_NUM` int(20) DEFAULT '0' COMMENT '构建次数',
  `PROJECT_ID` varchar(64) NOT NULL COMMENT '项目ID',
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `VERSION` int(11) DEFAULT NULL COMMENT '版本号',
  `START_USER` varchar(64) DEFAULT NULL COMMENT '启动者',
  `TRIGGER` varchar(32) NOT NULL COMMENT '触发器',
  `START_TIME` timestamp NULL DEFAULT NULL COMMENT '开始时间',
  `END_TIME` timestamp NULL DEFAULT NULL COMMENT '结束时间',
  `STATUS` int(11) DEFAULT NULL COMMENT '状态',
  `STAGE_STATUS` text DEFAULT NULL COMMENT '流水线各阶段状态',
  `TASK_COUNT` int(11) DEFAULT NULL COMMENT '流水线任务数量',
  `FIRST_TASK_ID` varchar(34) DEFAULT NULL COMMENT '首次任务id',
  `CHANNEL` varchar(32) DEFAULT NULL COMMENT '项目渠道',
  `TRIGGER_USER` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL COMMENT '触发者',
  `MATERIAL` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '原材料',
  `QUEUE_TIME` timestamp NULL DEFAULT NULL COMMENT '排队开始时间',
  `ARTIFACT_INFO` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '构件列表信息',
  `REMARK` varchar(4096) DEFAULT NULL COMMENT '评论',
  `EXECUTE_TIME` bigint(20) DEFAULT NULL COMMENT '执行时间',
  `BUILD_PARAMETERS` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT '构建环境参数',
  `WEBHOOK_TYPE` varchar(64) DEFAULT NULL COMMENT 'WEBHOOK 类型',
  `RECOMMEND_VERSION` varchar(64) DEFAULT NULL COMMENT '推荐版本号',
  `ERROR_TYPE` int(11) DEFAULT NULL COMMENT '错误类型',
  `ERROR_CODE` int(11) DEFAULT NULL COMMENT '错误码',
  `ERROR_MSG` text COMMENT '错误描述',
  `WEBHOOK_INFO` TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin COMMENT 'WEBHOOK 信息',
  `IS_RETRY` BIT(1) DEFAULT b'0' COMMENT '是否重试',
  `EXECUTE_COUNT` int(11) DEFAULT NULL COMMENT '执行次数',
  `ERROR_INFO` text COMMENT '错误信息',
  `BUILD_MSG` VARCHAR(255) DEFAULT NULL COMMENT '构建信息',
  `BUILD_NUM_ALIAS` VARCHAR(256) COMMENT '自定义构建号',
  `CONCURRENCY_GROUP` varchar(255) DEFAULT NULL COMMENT '并发时,设定的group',
  `UPDATE_TIME` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `VERSION_NAME` varchar(64) DEFAULT NULL COMMENT '版本名称',
  PRIMARY KEY (`BUILD_ID`),
  KEY `STATUS_KEY` (`PROJECT_ID`,`PIPELINE_ID`,`STATUS`),
  KEY `INX_TPBH_PROJECT_PIPELINE_NUM` (`PROJECT_ID`, `PIPELINE_ID`, `BUILD_NUM`),
  KEY `INX_TPBH_PROJECT_PIPELINE_START_TIME` (`PROJECT_ID`, `PIPELINE_ID`, `START_TIME`),
  KEY `inx_tpbh_status` (`STATUS`),
  KEY `inx_tpbh_start_time` (`START_TIME`),
  KEY `inx_tpbh_end_time` (`END_TIME`),
  KEY `IDX_PROJECT_STATUS_GROUP` (`PROJECT_ID`,`STATUS`,`CONCURRENCY_GROUP`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线构建历史表';

-- ----------------------------
-- Table structure for T_PIPELINE_BUILD_SUMMARY
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_SUMMARY` (
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `PROJECT_ID` varchar(64) NOT NULL COMMENT '项目ID',
  `BUILD_NUM` int(11) DEFAULT '0' COMMENT '构建次数',
  `BUILD_NO` int(11) DEFAULT '0' COMMENT '构建号',
  `FINISH_COUNT` int(11) DEFAULT '0' COMMENT '完成次数',
  `RUNNING_COUNT` int(11) DEFAULT '0' COMMENT '运行次数',
  `QUEUE_COUNT` int(11) DEFAULT '0' COMMENT '排队次数',
  `LATEST_BUILD_ID` varchar(34) DEFAULT NULL COMMENT '最近构建ID',
  `LATEST_TASK_ID` varchar(34) DEFAULT NULL COMMENT '最近任务ID',
  `LATEST_START_USER` varchar(64) DEFAULT NULL COMMENT '最近启动者',
  `LATEST_START_TIME` timestamp NULL DEFAULT NULL COMMENT '最近启动时间',
  `LATEST_END_TIME` timestamp NULL DEFAULT NULL COMMENT '最近结束时间',
  `LATEST_TASK_COUNT` int(11) DEFAULT NULL COMMENT '最近任务计数',
  `LATEST_TASK_NAME` varchar(128) DEFAULT NULL COMMENT '最近任务名称',
  `LATEST_STATUS` int(11) DEFAULT NULL COMMENT '最近状态',
  `BUILD_NUM_ALIAS` VARCHAR(256) COMMENT '自定义构建号',
  `DEBUG_BUILD_NUM` int(11) DEFAULT '0' COMMENT '调试构建次数',
  `DEBUG_BUILD_NO` int(11) DEFAULT '0' COMMENT '调试构建号',
  PRIMARY KEY (`PIPELINE_ID`),
  KEY `PRJOECT_ID` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线构建摘要表';

-- ----------------------------
-- Table structure for T_PIPELINE_INFO
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_INFO` (
  `PIPELINE_ID` varchar(34) NOT NULL DEFAULT '' COMMENT '流水线ID',
  `PROJECT_ID` varchar(64) NOT NULL COMMENT '项目ID',
  `PIPELINE_NAME` varchar(255) NOT NULL COMMENT '流水线名称',
  `PIPELINE_DESC` varchar(255) DEFAULT NULL COMMENT '流水线描述',
  `VERSION` int(11) DEFAULT '1' COMMENT '版本号',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `CREATOR` varchar(64) NOT NULL COMMENT '创建者',
  `UPDATE_TIME` timestamp NULL DEFAULT NULL COMMENT '更新时间',
  `LAST_MODIFY_USER` varchar(64) NOT NULL COMMENT '最近修改者',
  `CHANNEL` varchar(32) DEFAULT NULL COMMENT '项目渠道',
  `MANUAL_STARTUP` int(11) DEFAULT '1' COMMENT '是否手工启动',
  `ELEMENT_SKIP` int(11) DEFAULT '0' COMMENT '是否跳过插件',
  `TASK_COUNT` int(11) DEFAULT '0' COMMENT '流水线任务数量',
  `DELETE` bit(1) DEFAULT b'0' COMMENT '是否删除',
  `ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `PIPELINE_NAME_PINYIN` varchar(1300) DEFAULT NULL COMMENT '流水线名称拼音',
  `LATEST_START_TIME` datetime(3) DEFAULT NULL COMMENT '最近启动时间',
  `LATEST_VERSION_STATUS` varchar(64) DEFAULT NULL COMMENT '最新分布版本状态',
  PRIMARY KEY (`PIPELINE_ID`),
  UNIQUE KEY `T_PIPELINE_INFO_NAME_uindex` (`PROJECT_ID`,`PIPELINE_NAME`),
  KEY `PROJECT_ID` (`PROJECT_ID`,`PIPELINE_ID`),
  KEY `ID` (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线信息表';

-- ----------------------------
-- Table structure for T_PIPELINE_LABEL_PIPELINE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_LABEL_PIPELINE` (
  `ID` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `PROJECT_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '项目ID',
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `LABEL_ID` bigint(20) NOT NULL COMMENT '标签ID',
  `CREATE_TIME` datetime NOT NULL COMMENT '创建时间',
  `CREATE_USER` varchar(64) NOT NULL COMMENT '创建者',
  PRIMARY KEY (`ID`),
  UNIQUE KEY `UNI_INX_TPLP_PROJECT_PIPELINE_LABEL` (`PROJECT_ID`, `PIPELINE_ID`,`LABEL_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线-标签映射表';

-- ----------------------------
-- Table structure for T_PIPELINE_RESOURCE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_PIPELINE_RESOURCE` (
  `PROJECT_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '项目ID',
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `VERSION` int(11) NOT NULL DEFAULT '1' COMMENT '版本号',
  `VERSION_NAME` varchar(64) DEFAULT NULL COMMENT '版本名称',
  `MODEL` mediumtext COMMENT '流水线模型',
  `YAML` mediumtext COMMENT 'YAML编排',
  `CREATOR` varchar(64) DEFAULT NULL COMMENT '创建者',
  `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `VERSION_NUM` int(11) DEFAULT NULL COMMENT '流水线发布版本',
  `PIPELINE_VERSION` int(11) DEFAULT '0' COMMENT '流水线模型版本',
  `TRIGGER_VERSION` int(11) DEFAULT '0' COMMENT '触发器模型版本',
  `SETTING_VERSION` int(11) DEFAULT '0' COMMENT '关联的流水线设置版本号',
  PRIMARY KEY (`PIPELINE_ID`,`VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线资源表';

-- ----------------------------
-- Table structure for T_TEMPLATE_PIPELINE
-- ----------------------------

CREATE TABLE IF NOT EXISTS `T_TEMPLATE_PIPELINE` (
  `PROJECT_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '项目ID',
  `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
  `INSTANCE_TYPE` VARCHAR(32) NOT NULL DEFAULT 'CONSTRAINT' COMMENT '实例化类型：FREEDOM 自由模式  CONSTRAINT 约束模式',
  `ROOT_TEMPLATE_ID` VARCHAR(32) NULL COMMENT '源模板ID',
  `VERSION` bigint(20) NOT NULL COMMENT '版本号',
  `VERSION_NAME` varchar(64) NOT NULL COMMENT '版本名称',
  `TEMPLATE_ID` varchar(32) NOT NULL COMMENT '模板ID',
  `CREATOR` varchar(64) NOT NULL COMMENT '创建者',
  `UPDATOR` varchar(64) NOT NULL COMMENT '更新人',
  `CREATED_TIME` datetime NOT NULL COMMENT '创建时间',
  `UPDATED_TIME` datetime NOT NULL COMMENT '更新时间',
  `BUILD_NO` text COMMENT '构建号',
  `PARAM` mediumtext COMMENT '参数',
  `DELETED` bit(1) DEFAULT b'0' COMMENT '流水线已被软删除',
  PRIMARY KEY (`PIPELINE_ID`),
  KEY `TEMPLATE_ID` (`TEMPLATE_ID`),
  KEY `ROOT_TEMPLATE_ID` (`ROOT_TEMPLATE_ID`),
  KEY `INX_TTP_PROJECT_TEMPLATE_VERSION` (`PROJECT_ID`,`TEMPLATE_ID`,`VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线模板-实例映射表';

CREATE TABLE IF NOT EXISTS `T_PIPELINE_RESOURCE_VERSION` (
    `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '项目ID',
    `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
    `VERSION` int(11) NOT NULL DEFAULT '1' COMMENT '版本号',
    `VERSION_NAME` varchar(64) NOT NULL COMMENT '版本名称',
    `MODEL` mediumtext COMMENT '流水线模型',
    `YAML` mediumtext COMMENT 'YAML编排',
    `CREATOR` varchar(64) DEFAULT NULL COMMENT '创建者',
    `CREATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `REFER_FLAG` bit(1) DEFAULT NULL COMMENT '是否还有构建记录引用该版本标识',
    `REFER_COUNT` int(20) DEFAULT NULL COMMENT '关联构建记录总数',
    `VERSION_NUM` int(11) DEFAULT NULL COMMENT '流水线发布版本',
    `PIPELINE_VERSION` int(11) DEFAULT '0' COMMENT '流水线模型版本',
    `TRIGGER_VERSION` int(11) DEFAULT '0' COMMENT '触发器模型版本',
    `SETTING_VERSION` int(11) DEFAULT '0' COMMENT '关联的流水线设置版本号',
    `BASE_VERSION` int(11) DEFAULT NULL COMMENT '草稿的来源版本',
    `DEBUG_BUILD_ID` varchar(64) DEFAULT NULL COMMENT '调试构建ID',
    `STATUS` varchar(16) DEFAULT NULL COMMENT '版本状态',
    `BRANCH_ACTION` varchar(32) DEFAULT NULL COMMENT '分支状态',
    `DESCRIPTION` text COMMENT '版本变更说明',
    `UPDATE_TIME` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`PIPELINE_ID`,`VERSION`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线资源版本表';

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_RECORD_CONTAINER` (
    `BUILD_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '构建ID',
    `PROJECT_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '项目ID',
    `PIPELINE_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL COMMENT '流水线ID',
    `RESOURCE_VERSION` int(11) NOT NULL COMMENT '编排版本',
    `STAGE_ID` varchar(64) CHARACTER SET utf8mb4 NOT NULL DEFAULT '' COMMENT '步骤ID',
    `CONTAINER_ID` varchar(64) CHARACTER SET utf8mb4 NOT NULL DEFAULT '' COMMENT '构建容器ID',
    `EXECUTE_COUNT` int(11) NOT NULL DEFAULT '1' COMMENT '执行次数',
    `STATUS` varchar(32) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '构建状态',
    `CONTAINER_VAR` mediumtext CHARACTER SET utf8mb4 NOT NULL COMMENT '当次执行的变量记录',
    `CONTAINER_TYPE` varchar(45) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '容器类型',
    `CONTAIN_POST_TASK` bit(1) DEFAULT NULL COMMENT '包含POST插件标识',
    `MATRIX_GROUP_FLAG` bit(1) DEFAULT NULL COMMENT '矩阵标识',
    `MATRIX_GROUP_ID` varchar(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '所属的矩阵组ID',
    `START_TIME` datetime(3) NULL DEFAULT NULL COMMENT '开始时间',
    `END_TIME` datetime(3) NULL DEFAULT NULL COMMENT '结束时间',
    `TIMESTAMPS` text CHARACTER SET utf8mb4 COMMENT '运行中产生的时间戳集合',
    PRIMARY KEY (`BUILD_ID`,`CONTAINER_ID`,`EXECUTE_COUNT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流水线构建容器环境表';

CREATE TABLE IF NOT EXISTS  `T_PIPELINE_BUILD_RECORD_MODEL` (
    `BUILD_ID` varchar(34) NOT NULL COMMENT '构建ID',
    `PROJECT_ID` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '项目ID',
    `PIPELINE_ID` varchar(34) NOT NULL DEFAULT '' COMMENT '流水线ID',
    `RESOURCE_VERSION` int(11) NOT NULL COMMENT '编排版本',
    `BUILD_NUM` int(20) NOT NULL COMMENT '构建次数',
    `EXECUTE_COUNT` int(11) NOT NULL COMMENT '执行次数',
    `START_USER` varchar(32) NOT NULL DEFAULT '' COMMENT '启动者',
    `MODEL_VAR` mediumtext NOT NULL COMMENT '当次执行的变量记录',
    `START_TYPE` varchar(32) NOT NULL DEFAULT '' COMMENT '触发方式',
    `QUEUE_TIME` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '触发时间',
    `START_TIME` datetime(3) NULL DEFAULT NULL COMMENT '启动时间',
    `END_TIME` datetime(3) NULL DEFAULT NULL COMMENT '结束时间',
    `STATUS` varchar(32) DEFAULT NULL COMMENT '构建状态',
    `ERROR_INFO` text COMMENT '错误信息',
    `CANCEL_USER` varchar(32) DEFAULT NULL COMMENT '取消者',
    `TIMESTAMPS` text COMMENT '运行中产生的时间戳集合',
    PRIMARY KEY (`BUILD_ID`,`EXECUTE_COUNT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线构建详情表';

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_RECORD_STAGE` (
    `BUILD_ID` varchar(64) CHARACTER SET utf8mb4 NOT NULL COMMENT '构建ID',
    `PROJECT_ID` varchar(64) COLLATE utf8mb4_bin NOT NULL DEFAULT '' COMMENT '项目ID',
    `PIPELINE_ID` varchar(64) CHARACTER SET utf8mb4 NOT NULL DEFAULT '' COMMENT '流水线ID',
    `RESOURCE_VERSION` int(11) DEFAULT NULL COMMENT '编排版本号',
    `STAGE_ID` varchar(64) CHARACTER SET utf8mb4 NOT NULL DEFAULT '' COMMENT '步骤ID',
    `SEQ` int(11) NOT NULL COMMENT '步骤序列',
    `STAGE_VAR` text CHARACTER SET utf8mb4 NOT NULL COMMENT '当次执行的变量记录',
    `STATUS` varchar(32) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '构建状态',
    `EXECUTE_COUNT` int(11) NOT NULL DEFAULT '1' COMMENT '执行次数',
    `START_TIME` datetime(3) NULL DEFAULT NULL COMMENT '开始时间',
    `END_TIME` datetime(3) NULL DEFAULT NULL COMMENT '结束时间',
    `TIMESTAMPS` text CHARACTER SET utf8mb4 COMMENT '运行中产生的时间戳集合',
    PRIMARY KEY (`BUILD_ID`,`STAGE_ID`,`EXECUTE_COUNT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='流水线构建阶段表';

CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_RECORD_TASK` (
    `BUILD_ID` varchar(34) NOT NULL COMMENT '构建ID',
    `PROJECT_ID` varchar(64) NOT NULL COMMENT '项目ID',
    `PIPELINE_ID` varchar(34) NOT NULL COMMENT '流水线ID',
    `RESOURCE_VERSION` int(11) NOT NULL COMMENT '编排版本号',
    `STAGE_ID` varchar(34) NOT NULL DEFAULT '' COMMENT '步骤ID',
    `CONTAINER_ID` varchar(34) NOT NULL COMMENT '构建容器ID',
    `TASK_ID` varchar(34) NOT NULL COMMENT '任务ID',
    `TASK_SEQ` int(11) NOT NULL DEFAULT '1' COMMENT '任务序列',
    `EXECUTE_COUNT` int(11) NOT NULL DEFAULT '1' COMMENT '执行次数',
    `STATUS` varchar(32) DEFAULT NULL COMMENT '构建状态',
    `TASK_VAR` mediumtext NOT NULL COMMENT '当次执行的变量记录',
    `POST_INFO` text DEFAULT NULL COMMENT '市场插件的POST关联信息',
    `CLASS_TYPE` varchar(64) NOT NULL DEFAULT '' COMMENT '项目ID',
    `ATOM_CODE` varchar(128) NOT NULL DEFAULT '' COMMENT '插件的唯一标识',
    `ORIGIN_CLASS_TYPE` varchar(64) DEFAULT NULL COMMENT '所在矩阵组ID',
    `START_TIME` datetime(3) NULL DEFAULT NULL COMMENT '开始时间',
    `END_TIME` datetime(3) NULL DEFAULT NULL COMMENT '结束时间',
    `TIMESTAMPS` text COMMENT '运行中产生的时间戳集合',
    PRIMARY KEY (`BUILD_ID`,`TASK_ID`,`EXECUTE_COUNT`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线构建任务表';

SET FOREIGN_KEY_CHECKS = 1;
