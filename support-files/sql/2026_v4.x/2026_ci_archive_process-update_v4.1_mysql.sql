USE devops_ci_archive_process;
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS ci_archive_process_schema_update;

DELIMITER <CI_UBF>

CREATE PROCEDURE ci_archive_process_schema_update()
BEGIN

    DECLARE db VARCHAR(100);
    SET AUTOCOMMIT = 0;
    SELECT DATABASE() INTO db;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_SETTING'
                    AND COLUMN_NAME = 'ENV_HASH_ID') THEN
    ALTER TABLE T_PIPELINE_SETTING
        ADD COLUMN `ENV_HASH_ID` varchar(256) COMMENT '环境HashId';
    END IF;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_SETTING_VERSION'
                    AND COLUMN_NAME = 'ENV_HASH_ID') THEN
        ALTER TABLE T_PIPELINE_SETTING_VERSION
            ADD COLUMN `ENV_HASH_ID` varchar(256) COMMENT '环境HashId';
    END IF;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_BUILD_HISTORY'
                    AND COLUMN_NAME = 'NODE_HASH_ID') THEN
        ALTER TABLE T_PIPELINE_BUILD_HISTORY
           ADD COLUMN `NODE_HASH_ID` varchar(256) COMMENT '运行节点HashId';
    END IF;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_BUILD_HISTORY_DEBUG'
                    AND COLUMN_NAME = 'NODE_HASH_ID') THEN
       ALTER TABLE T_PIPELINE_BUILD_HISTORY_DEBUG
          ADD COLUMN `NODE_HASH_ID` varchar(256) COMMENT '运行节点HashId';
    END IF;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_SETTING'
                    AND COLUMN_NAME = 'ENV_NAME') THEN
    ALTER TABLE T_PIPELINE_SETTING
        ADD COLUMN `ENV_NAME` varchar(256) COMMENT '环境名称';
    END IF;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_SETTING_VERSION'
                    AND COLUMN_NAME = 'ENV_NAME') THEN
        ALTER TABLE T_PIPELINE_SETTING_VERSION
            ADD COLUMN `ENV_NAME` varchar(256) COMMENT '环境名称';
    END IF;

    IF NOT EXISTS(SELECT 1
              FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_PIPELINE_BUILD_HISTORY'
                AND COLUMN_NAME = 'TRIGGER_EVENT_TYPE') THEN
       ALTER TABLE `T_PIPELINE_BUILD_HISTORY`
          ADD COLUMN `TRIGGER_EVENT_TYPE` VARCHAR(64) DEFAULT NULL comment '触发事件标识';
    END IF;

    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_BUILD_HISTORY_DEBUG'
                    AND COLUMN_NAME = 'TRIGGER_EVENT_TYPE') THEN
       ALTER TABLE `T_PIPELINE_BUILD_HISTORY_DEBUG`
          ADD COLUMN `TRIGGER_EVENT_TYPE` VARCHAR(64) DEFAULT NULL comment '触发事件标识';
    END IF;

        -- AI自动摘要字段
    IF NOT EXISTS(SELECT 1
                  FROM information_schema.COLUMNS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_INFO'
                    AND COLUMN_NAME = 'AUTO_SUMMARY') THEN
        ALTER TABLE `T_PIPELINE_INFO`
            ADD COLUMN `AUTO_SUMMARY` text DEFAULT NULL COMMENT 'AI自动生成的流水线摘要' AFTER `LOCKED`;
    END IF;

    -- 流水线名称唯一性按渠道隔离，允许 BS 与 CREATIVE_STREAM 使用相同名称
    IF NOT EXISTS(SELECT 1
                  FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_INFO'
                    AND INDEX_NAME = 'UNI_INX_TPI_PROJECT_NAME_CHANNEL') THEN
        ALTER TABLE `T_PIPELINE_INFO`
            ADD UNIQUE KEY `UNI_INX_TPI_PROJECT_NAME_CHANNEL` (`PROJECT_ID`,`CHANNEL`,`PIPELINE_NAME`);
    END IF;

    IF EXISTS(SELECT 1
              FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_PIPELINE_INFO'
                AND INDEX_NAME = 'T_PIPELINE_INFO_NAME_uindex') THEN
        ALTER TABLE `T_PIPELINE_INFO`
            DROP INDEX `T_PIPELINE_INFO_NAME_uindex`;
    END IF;

    IF EXISTS(SELECT 1
              FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_PIPELINE_INFO'
                AND COLUMN_NAME = 'CHANNEL'
                AND IS_NULLABLE = 'YES') THEN
        ALTER TABLE `T_PIPELINE_INFO`
            MODIFY COLUMN `CHANNEL` varchar(32) NOT NULL DEFAULT 'BS' COMMENT '项目渠道';
    END IF;

    -- T_PIPELINE_SETTING 不含渠道字段，名称唯一性由 T_PIPELINE_INFO 按渠道约束
    IF NOT EXISTS(SELECT 1
                  FROM information_schema.STATISTICS
                  WHERE TABLE_SCHEMA = db
                    AND TABLE_NAME = 'T_PIPELINE_SETTING'
                    AND INDEX_NAME = 'UNI_INX_TPS_PROJECT_PIPELINE_NAME') THEN
        ALTER TABLE `T_PIPELINE_SETTING`
            ADD UNIQUE KEY `UNI_INX_TPS_PROJECT_PIPELINE_NAME` (`PROJECT_ID`,`NAME`,`IS_TEMPLATE`,`PIPELINE_ID`);
    END IF;

    IF EXISTS(SELECT 1
              FROM information_schema.STATISTICS
              WHERE TABLE_SCHEMA = db
                AND TABLE_NAME = 'T_PIPELINE_SETTING'
                AND INDEX_NAME = 'PROJECT_ID') THEN
        ALTER TABLE `T_PIPELINE_SETTING`
            DROP INDEX `PROJECT_ID`;
    END IF;

    COMMIT;
END <CI_UBF>
DELIMITER ;
COMMIT;
CALL ci_archive_process_schema_update();
