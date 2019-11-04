/*
 * Tencent is pleased to support the open source community by making BK-REPO 蓝鲸制品库 available.
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

package com.tencent.devops.artifactory.service

import com.tencent.devops.artifactory.pojo.CombinationPath
import com.tencent.devops.artifactory.pojo.FileChecksums
import com.tencent.devops.artifactory.pojo.FileDetail
import com.tencent.devops.artifactory.pojo.FileInfo
import com.tencent.devops.artifactory.pojo.PathList
import com.tencent.devops.artifactory.pojo.enums.ArtifactoryType
import com.tencent.devops.artifactory.util.JFrogUtil
import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.util.timestamp
import com.tencent.devops.common.archive.api.JFrogPropertiesApi
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_PIPELINE_ID
import com.tencent.devops.common.archive.constant.ARCHIVE_PROPS_PIPELINE_NAME
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.ws.rs.BadRequestException

@Service
class BuildCustomDirService @Autowired constructor(
    private val jFrogPropertiesApi: JFrogPropertiesApi,
    private val pipelineService: PipelineService,
    private val jFrogService: JFrogService
) {
    fun list(projectId: String, argPath: String): List<FileInfo> {
        logger.info("[$projectId|[$argPath]] the method of being done is: list")
        val path = JFrogUtil.normalize(argPath)
        if (!JFrogUtil.isValid(path)) {
            logger.error("Path $path is not valid")
            throw BadRequestException("非法路径")
        }

        val realPath = JFrogUtil.getCustomDirPath(projectId, path)
        val jFrogFileInfoList = jFrogService.list(realPath, false, 1)

        val fileInfoList = jFrogFileInfoList.map {
            val name = it.uri.removePrefix("/")
            val fullPath = JFrogUtil.compose(path, name, it.folder)
            FileInfo(
                name,
                fullPath,
                it.uri,
                fullPath,
                it.size,
                it.folder,
                LocalDateTime.parse(it.lastModified, DateTimeFormatter.ISO_DATE_TIME).timestamp(),
                ArtifactoryType.CUSTOM_DIR
            )
        }
        return JFrogUtil.sort(fileInfoList)
    }

    fun show(projectId: String, argPath: String): FileDetail {
        logger.info("[$projectId|[$argPath]] the method of being done is: show")
        val path = JFrogUtil.normalize(argPath)
        if (!JFrogUtil.isValid(path)) {
            logger.error("Path $path is not valid")
            throw BadRequestException("非法路径")
        }

        // 项目目录不存在时，创建根目录
        val realPath = JFrogUtil.getCustomDirPath(projectId, path)
        if (JFrogUtil.isRoot(path) && !jFrogService.exist(realPath)) {
            jFrogService.mkdir(realPath)
        }

        val jFrogFileInfo = jFrogService.file(realPath)
        val jFrogProperties = jFrogPropertiesApi.getProperties(realPath)
        val jFrogPropertiesMap = mutableMapOf<String, String>()
        jFrogProperties.map {
            jFrogPropertiesMap[it.key] = it.value.joinToString(",")
        }
        if (jFrogProperties.containsKey(ARCHIVE_PROPS_PIPELINE_ID)) {
            val pipelineId = jFrogProperties[ARCHIVE_PROPS_PIPELINE_ID]!!.first()
            val pipelineName = pipelineService.getPipelineName(projectId, pipelineId)
            jFrogPropertiesMap[ARCHIVE_PROPS_PIPELINE_NAME] = pipelineName
        }

        return if (jFrogFileInfo.checksums == null) {
            FileDetail(
                JFrogUtil.getFileName(path),
                path,
                path,
                path,
                jFrogFileInfo.size,
                LocalDateTime.parse(jFrogFileInfo.created, DateTimeFormatter.ISO_DATE_TIME).timestamp(),
                LocalDateTime.parse(jFrogFileInfo.lastModified, DateTimeFormatter.ISO_DATE_TIME).timestamp(),
                FileChecksums("", "", ""),
                jFrogPropertiesMap
            )
        } else {
            FileDetail(
                JFrogUtil.getFileName(path),
                path,
                path,
                path,
                jFrogFileInfo.size,
                LocalDateTime.parse(jFrogFileInfo.created, DateTimeFormatter.ISO_DATE_TIME).timestamp(),
                LocalDateTime.parse(jFrogFileInfo.lastModified, DateTimeFormatter.ISO_DATE_TIME).timestamp(),
                FileChecksums(
                    jFrogFileInfo.checksums.sha256,
                    jFrogFileInfo.checksums.sha1,
                    jFrogFileInfo.checksums.md5
                ),
                jFrogPropertiesMap
            )
        }
    }

    fun mkdir(projectId: String, argPath: String) {
        logger.info("[$projectId|[$argPath]] the method of being done is: mkdir")
        val path = JFrogUtil.normalize(argPath)
        if (!JFrogUtil.isValid(path)) {
            logger.error("Path $path is not valid")
            throw BadRequestException("非法路径")
        }

        val name = JFrogUtil.getFileName(path)
        val folderPath = JFrogUtil.getCustomDirPath(projectId, path)
        if (jFrogService.exist(folderPath)) {
            val detail = jFrogService.file(folderPath)
            if (detail.checksums != null) {
                logger.error("Destination path $path has same name file")
                throw BadRequestException("文件($name)已存在同名文件")
            } else {
                logger.error("Destination path $path has same name folder")
                throw BadRequestException("文件($name)已存在同名文件夹")
            }
        }

        jFrogService.mkdir(folderPath)
    }

    fun rename(projectId: String, argSrcPath: String, argDestPath: String) {
        logger.info("[$projectId] the method of being done is: rename")
        val srcPath = JFrogUtil.normalize(argSrcPath)
        val destPath = JFrogUtil.normalize(argDestPath)
        if (!JFrogUtil.isValid(srcPath) || !JFrogUtil.isValid(destPath)) {
            logger.error("Path $srcPath or $destPath is not valid")
            throw BadRequestException("非法路径")
        }

        val name = JFrogUtil.getFileName(destPath)
        val realSrcPath = JFrogUtil.getCustomDirPath(projectId, srcPath)
        val realDestPath = JFrogUtil.getCustomDirPath(projectId, destPath)
        if (jFrogService.exist(realDestPath)) {
            logger.error("Destination path $destPath already exist")
            throw OperationException("文件或者文件夹($name)已经存在")
        }

        jFrogService.move(realSrcPath, realDestPath)
    }

    fun copy(projectId: String, combinationPath: CombinationPath) {
        logger.info("[$projectId] the method of being done is: copy")
        val destPath = JFrogUtil.normalize(combinationPath.destPath)
        if (!JFrogUtil.isValid(destPath)) {
            logger.error("Path $destPath is not valid")
            throw BadRequestException("非法路径")
        }

        val folderPath = JFrogUtil.getCustomDirPath(projectId, destPath)
        if (!jFrogService.exist(folderPath)) {
            logger.error("Destination path $destPath doesn't exist")
            throw BadRequestException("文件夹($destPath)不存在")
        }

        combinationPath.srcPaths.map {
            val srcPath = JFrogUtil.normalize(it)
            if (!JFrogUtil.isValid(srcPath)) {
                logger.error("Path $srcPath is not valid")
                throw BadRequestException("非法路径")
            }

            if (JFrogUtil.getParentFolder(srcPath) == destPath) {
                logger.error("Cannot copy in same path ($srcPath, $destPath)")
                throw BadRequestException("不能在拷贝到当前目录")
            }

            val realSrcPath = JFrogUtil.getCustomDirPath(projectId, srcPath)
            if (!jFrogService.exist(realSrcPath)) {
                logger.error("Path $srcPath is not valid")
                throw BadRequestException("文件($srcPath)不存在")
            }

            val realDestPath = JFrogUtil.getCustomDirPath(projectId, destPath)
            jFrogService.copy(realSrcPath, realDestPath)
        }
    }

    fun move(projectId: String, combinationPath: CombinationPath) {
        logger.info("[$projectId] the method of being done is: delete")
        val destPath = JFrogUtil.normalize(combinationPath.destPath)
        if (!JFrogUtil.isValid(destPath)) {
            logger.error("Path $destPath is not valid")
            throw BadRequestException("非法路径")
        }

        combinationPath.srcPaths.map {
            val srcPath = JFrogUtil.normalize(it)
            if (!JFrogUtil.isValid(srcPath)) {
                logger.error("Path $srcPath is not valid")
                throw BadRequestException("非法路径")
            }

            if (srcPath == destPath || JFrogUtil.getParentFolder(srcPath) == destPath) {
                logger.error("Cannot move in same path ($srcPath, $destPath)")
                throw BadRequestException("不能移动到当前目录")
            }

            if (destPath.startsWith(srcPath)) {
                logger.error("Cannot move parent path to sub path ($srcPath, $destPath)")
                throw BadRequestException("不能将父目录移动到子目录")
            }

            val realSrcPath = JFrogUtil.getCustomDirPath(projectId, srcPath)
            val realDestPath = JFrogUtil.getCustomDirPath(projectId, destPath)
            jFrogService.move(realSrcPath, realDestPath)
        }
    }

    fun delete(projectId: String, pathList: PathList) {
        logger.info("[$projectId|$pathList] the method of being done is: delete")
        pathList.paths.map {
            val path = JFrogUtil.normalize(it)
            if (!JFrogUtil.isValid(path)) {
                logger.error("Path $path is not valid")
                throw BadRequestException("非法路径")
            }

            val realPath = JFrogUtil.getCustomDirPath(projectId, path)
            jFrogService.delete(realPath)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(CustomDirService::class.java)
    }
}