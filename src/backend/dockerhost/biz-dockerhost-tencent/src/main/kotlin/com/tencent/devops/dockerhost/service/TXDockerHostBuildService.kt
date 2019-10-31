package com.tencent.devops.dockerhost.service

import com.github.dockerjava.api.exception.NotFoundException
import com.github.dockerjava.api.model.*
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.*
import com.tencent.devops.common.pipeline.type.docker.ImageType
import com.tencent.devops.common.web.mq.alert.AlertLevel
import com.tencent.devops.dispatch.pojo.DockerHostBuildInfo
import com.tencent.devops.dockerhost.config.TXDockerHostConfig
import com.tencent.devops.dockerhost.dispatch.AlertApi
import com.tencent.devops.dockerhost.dispatch.DockerHostBuildResourceApi
import com.tencent.devops.dockerhost.exception.ContainerException
import com.tencent.devops.dockerhost.exception.NoSuchImageException
import com.tencent.devops.dockerhost.pojo.DockerBuildParam
import com.tencent.devops.dockerhost.pojo.DockerBuildParamNew
import com.tencent.devops.dockerhost.pojo.DockerRunParam
import com.tencent.devops.dockerhost.services.DockerHostBuildService
import com.tencent.devops.dockerhost.services.LocalImageCache
import com.tencent.devops.dockerhost.utils.*
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Paths
import java.util.Date
import javax.annotation.PostConstruct
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.first
import kotlin.collections.forEach
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.set
import kotlin.collections.setOf

@Component
class TXDockerHostBuildService(
    private val dockerHostConfig: TXDockerHostConfig,
    private val buildService: DockerHostBuildService
) {

    private val logger = LoggerFactory.getLogger(TXDockerHostBuildService::class.java)
    private val dockerHostBuildApi: DockerHostBuildResourceApi =
        DockerHostBuildResourceApi(if ("codecc_build" == dockerHostConfig.runMode) "dispatch-codecc" else "dispatch")
    private val alertApi: AlertApi =
        AlertApi(if ("codecc_build" == dockerHostConfig.runMode) "dispatch-codecc" else "dispatch")
    private val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
        .withDockerHost(dockerHostConfig.dockerHost)
        .withDockerConfig(dockerHostConfig.dockerConfig)
        .withApiVersion(dockerHostConfig.apiVersion)
        .build()
    private val dockerCli = DockerClientBuilder.getInstance(config).build()

    fun startBuild(): DockerHostBuildInfo? {
        val result = dockerHostBuildApi.startBuild(CommonUtils.getInnerIP())
        if (result != null) {
            if (result.isNotOk()) {
                return null
            }
        }
        return result!!.data!!
    }

    fun endBuild(): DockerHostBuildInfo? {
        val result = dockerHostBuildApi.endBuild(CommonUtils.getInnerIP())
        if (result != null) {
            if (result.isNotOk()) {
                return null
            }
        }
        return result!!.data!!
    }

    fun reportContainerId(buildId: String, vmSeqId: Int, containerId: String): Boolean {
        val result = dockerHostBuildApi.reportContainerId(buildId, vmSeqId, containerId)
        if (result != null) {
            if (result.isNotOk()) {
                logger.info("reportContainerId return msg: ${result.message}")
                return false
            }
        }
        return result!!.data!!
    }

    fun rollbackBuild(buildId: String, vmSeqId: Int, shutdown: Boolean): Boolean {
        log(buildId, true, if (shutdown) "构建环境启动后即退出，请检查镜像是否合法或联系【蓝盾助手】查看，构建任务将失败退出" else "启动构建环境失败，构建任务将重试")

        val result = dockerHostBuildApi.rollbackBuild(buildId, vmSeqId, shutdown)
        if (result != null) {
            if (result.isNotOk()) {
                logger.info("rollbackBuild return msg: ${result.message}")
                return false
            }
        }
        return result!!.data!!
    }

    fun createContainer(dockerBuildInfo: DockerHostBuildInfo): String {
        try {
            val authConfig = TXCommonUtils.getAuthConfig(
                dockerBuildInfo.imageType,
                dockerHostConfig,
                dockerBuildInfo.imageName,
                dockerBuildInfo.registryUser,
                dockerBuildInfo.registryPwd
            )
            val imageName = TXCommonUtils.normalizeImageName(dockerBuildInfo.imageName)
            // docker pull
            try {
                LocalImageCache.saveOrUpdate(imageName)
                log(dockerBuildInfo.buildId, "开始拉取镜像，镜像名称：$imageName")
                dockerCli.pullImageCmd(imageName).withAuthConfig(authConfig)
                    .exec(MyPullImageResultCallback(dockerBuildInfo.buildId, dockerHostBuildApi)).awaitCompletion()
                log(dockerBuildInfo.buildId, "拉取镜像成功，准备启动构建环境...")
            } catch (t: Throwable) {
                logger.warn("Fail to pull the image $imageName of build ${dockerBuildInfo.buildId}", t)
                log(dockerBuildInfo.buildId, "拉取镜像失败，错误信息：${t.message}")
                log(dockerBuildInfo.buildId, "尝试使用本地镜像启动...")
            }
            // docker run
            val volumeWs = Volume(dockerHostConfig.volumeWorkspace)
            val volumeProjectShare = Volume(dockerHostConfig.volumeProjectShare)
            val volumeMavenRepo = Volume(dockerHostConfig.volumeMavenRepo)
            val volumeNpmPrefix = Volume(dockerHostConfig.volumeNpmPrefix)
            val volumeNpmCache = Volume(dockerHostConfig.volumeNpmCache)
            val volumeCcache = Volume(dockerHostConfig.volumeCcache)
            val volumeApps = Volume(dockerHostConfig.volumeApps)
            val volumeInit = Volume(dockerHostConfig.volumeInit)
            val volumeLogs = Volume(dockerHostConfig.volumeLogs)
            val volumeGradleCache = Volume(dockerHostConfig.volumeGradleCache)

            val gateway = System.getProperty("soda.gateway", "gw.open.oa.com")
            logger.info("gateway is: $gateway")

            val binds = mutableListOf(
                Bind(
                    "${dockerHostConfig.hostPathMavenRepo}/${dockerBuildInfo.pipelineId}/${dockerBuildInfo.vmSeqId}/",
                    volumeMavenRepo
                ),
                Bind(
                    "${dockerHostConfig.hostPathNpmPrefix}/${dockerBuildInfo.pipelineId}/${dockerBuildInfo.vmSeqId}/",
                    volumeNpmPrefix
                ),
                Bind(
                    "${dockerHostConfig.hostPathNpmCache}/${dockerBuildInfo.pipelineId}/${dockerBuildInfo.vmSeqId}/",
                    volumeNpmCache
                ),
                Bind(
                    "${dockerHostConfig.hostPathCcache}/${dockerBuildInfo.pipelineId}/${dockerBuildInfo.vmSeqId}/",
                    volumeCcache
                ),
                Bind(dockerHostConfig.hostPathApps, volumeApps, AccessMode.ro),
                Bind(dockerHostConfig.hostPathInit, volumeInit, AccessMode.ro),
                Bind(
                    "${dockerHostConfig.hostPathLogs}/${dockerBuildInfo.buildId}/${dockerBuildInfo.vmSeqId}/",
                    volumeLogs
                ),
                Bind(
                    "${dockerHostConfig.hostPathGradleCache}/${dockerBuildInfo.pipelineId}/${dockerBuildInfo.vmSeqId}/",
                    volumeGradleCache
                ),
                Bind(getWorkspace(dockerBuildInfo.pipelineId, dockerBuildInfo.vmSeqId), volumeWs)
            )
            if (enableProjectShare(dockerBuildInfo.projectId)) {
                binds.add(Bind(getProjectShareDir(dockerBuildInfo.projectId), volumeProjectShare))
            }

            val container = dockerCli.createContainerCmd(imageName)
                .withCmd("/bin/sh", ENTRY_POINT_CMD)
                .withEnv(
                    listOf(
                        "$ENV_KEY_PROJECT_ID=${dockerBuildInfo.projectId}",
                        "$ENV_KEY_AGENT_ID=${dockerBuildInfo.agentId}",
                        "$ENV_KEY_AGENT_SECRET_KEY=${dockerBuildInfo.secretKey}",
                        "$ENV_KEY_GATEWAY=$gateway",
                        "TERM=xterm-256color",
                        "landun_env=${dockerHostConfig.landunEnv ?: "prod"}",
                        "$ENV_DOCKER_HOST_IP=${CommonUtils.getInnerIP()}",
                        "$COMMON_DOCKER_SIGN=docker",
                        "$BK_DISTCC_LOCAL_IP=${CommonUtils.getInnerIP()}",
                        // codecc构建机日志落到本地
                        "$ENV_LOG_SAVE_MODE=${if ("codecc_build" == dockerHostConfig.runMode) "LOCAL" else "UPLOAD"}"
                    )
                )
                .withVolumes(volumeWs).withVolumes(volumeApps).withVolumes(volumeInit)
                .withHostConfig(HostConfig().withBinds(binds).withNetworkMode("bridge"))
                .exec()

            logger.info("Created container $container")
            dockerCli.startContainerCmd(container.id).exec()

            return container.id
        } catch (er: Throwable) {
            logger.error(er.toString())
            logger.error(er.cause.toString())
            logger.error(er.message)
            log(dockerBuildInfo.buildId, true, "启动构建环境失败，错误信息:${er.message}")
            if (er is NotFoundException) {
                throw NoSuchImageException("Create container failed: ${er.message}")
            } else {
                alertApi.alert(
                    AlertLevel.HIGH.name, "Docker构建机创建容器失败", "Docker构建机创建容器失败, " +
                        "母机IP:${CommonUtils.getInnerIP()}， 失败信息：${er.message}"
                )
                throw ContainerException("Create container failed")
            }
        }
    }

    fun stopContainer(dockerBuildInfo: DockerHostBuildInfo) {
        try {
            // docker stop
            val containerInfo = dockerCli.inspectContainerCmd(dockerBuildInfo.containerId).exec()
            if ("exited" != containerInfo.state.status) {
                dockerCli.stopContainerCmd(dockerBuildInfo.containerId).withTimeout(30).exec()
            }
        } catch (e: Throwable) {
            logger.error("Stop the container failed, containerId: ${dockerBuildInfo.containerId}, error msg: $e")
        }

        try {
            // docker rm
            dockerCli.removeContainerCmd(dockerBuildInfo.containerId).exec()
        } catch (e: Throwable) {
            logger.error("Stop the container failed, containerId: ${dockerBuildInfo.containerId}, error msg: $e")
        }
    }

    fun getContainerNum(): Int {
        try {
            val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
            val dockerInfo = dockerCli.infoCmd().exec()
            return dockerInfo.containersRunning ?: 0
        } catch (e: Throwable) {
            logger.error("Get container num failed")
        }
        return 0
    }

    fun dockerBuildAndPushImage(
        projectId: String,
        pipelineId: String,
        vmSeqId: String,
        dockerBuildParam: DockerBuildParam,
        buildId: String,
        elementId: String?
    ): Pair<Boolean, String?> {
        try {
            val repoAddr = dockerBuildParam.repoAddr
            val userName = dockerBuildParam.userName
            val password = dockerBuildParam.password

            val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHostConfig.dockerHost)
                .withDockerConfig(dockerHostConfig.dockerConfig)
                .withApiVersion(dockerHostConfig.apiVersion)
                .withRegistryUrl(repoAddr)
                .withRegistryUsername(userName)
                .withRegistryPassword(password)
                .build()

            val dockerCli = DockerClientBuilder.getInstance(config).build()
            val authConfig = AuthConfig()
                .withUsername(userName)
                .withPassword(password)
                .withRegistryAddress(repoAddr)
            val authConfigurations = AuthConfigurations()
            authConfigurations.addConfig(authConfig)

            val workspace = getWorkspace(pipelineId, vmSeqId.toInt())
            val buildDir = Paths.get(workspace + dockerBuildParam.buildDir).normalize().toString()
            val dockerfilePath = Paths.get(workspace + dockerBuildParam.dockerFile).normalize().toString()
            val baseDirectory = File(buildDir)
            val dockerfile = File(dockerfilePath)
            val imageNameTag =
                getImageNameWithTag(repoAddr, projectId, dockerBuildParam.imageName, dockerBuildParam.imageTag)

            logger.info("Build docker image, workspace: $workspace, buildDir:$buildDir, dockerfile: $dockerfilePath")
            logger.info("Build docker image, imageNameTag: $imageNameTag")
            dockerCli.buildImageCmd().withNoCache(true)
                .withPull(true)
                .withBuildAuthConfigs(authConfigurations)
                .withBaseDirectory(baseDirectory)
                .withDockerfile(dockerfile)
                .withTags(setOf(imageNameTag))
                .exec(MyBuildImageResultCallback(buildId, elementId, dockerHostBuildApi))
                .awaitImageId()

            logger.info("Build image success, now push to repo, image name and tag: $imageNameTag")
            dockerCli.pushImageCmd(imageNameTag)
                .withAuthConfig(authConfig)
                .exec(MyPushImageResultCallback(buildId, elementId, dockerHostBuildApi))
                .awaitCompletion()

            logger.info("Push image success, now remove local image, image name and tag: $imageNameTag")

            try {
                dockerCli.removeImageCmd(
                    getImageNameWithTag(
                        repoAddr,
                        projectId,
                        dockerBuildParam.imageName,
                        dockerBuildParam.imageTag
                    )
                ).exec()
                logger.info("Remove local image success")
            } catch (e: Throwable) {
                logger.error("Docker rmi failed, msg: ${e.message}")
            }

            return Pair(true, null)
        } catch (e: Throwable) {
            logger.error("Docker build and push failed, exception: ", e)
            val cause = if (e.cause != null && e.cause!!.message != null) {
                e.cause!!.message!!.removePrefix(getWorkspace(pipelineId, vmSeqId.toInt()))
            } else {
                ""
            }
            return Pair(false, e.message + if (cause.isBlank()) "" else " cause:【$cause】")
        }
    }

    fun dockerBuildAndPushImageNew(
        projectId: String,
        pipelineId: String,
        vmSeqId: String,
        dockerBuildParam: DockerBuildParamNew,
        buildId: String,
        elementId: String?
    ): Pair<Boolean, String?> {
        try {
            val repoAddr = dockerBuildParam.repoAddr
            val userName = dockerBuildParam.userName
            val password = dockerBuildParam.password
            val ticket = dockerBuildParam.ticket
            val args = dockerBuildParam.args
            val host = dockerBuildParam.host
            val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHostConfig.dockerHost)
                .withDockerConfig(dockerHostConfig.dockerConfig)
                .withApiVersion(dockerHostConfig.apiVersion)
                .withRegistryUrl(repoAddr)
                .withRegistryUsername(userName)
                .withRegistryPassword(password)
                .build()

            val dockerCli = DockerClientBuilder.getInstance(config).build()
            val authConfig = AuthConfig()
                .withUsername(userName)
                .withPassword(password)
                .withRegistryAddress(repoAddr)

            val authConfigurations = AuthConfigurations()
            authConfigurations.addConfig(authConfig)

            ticket.forEach { it ->
                val baseConfig = AuthConfig()
                    .withUsername(it.second)
                    .withPassword(it.third)
                    .withRegistryAddress(it.first)
                authConfigurations.addConfig(baseConfig)
            }

            val workspace = getWorkspace(pipelineId, vmSeqId.toInt())
            val buildDir = Paths.get(workspace + dockerBuildParam.buildDir).normalize().toString()
            val dockerfilePath = Paths.get(workspace + dockerBuildParam.dockerFile).normalize().toString()
            val baseDirectory = File(buildDir)
            val dockerfile = File(dockerfilePath)
            val imageNameTag =
                getImageNameWithTagNew(repoAddr, projectId, dockerBuildParam.imageName, dockerBuildParam.imageTag)

            logger.info("Build docker image, workspace: $workspace, buildDir:$buildDir, dockerfile: $dockerfilePath")
            logger.info("Build docker image, imageNameTag: $imageNameTag")
            val step = dockerCli.buildImageCmd().withNoCache(true)
                .withPull(true)
                .withBuildAuthConfigs(authConfigurations)
                .withBaseDirectory(baseDirectory)
                .withDockerfile(dockerfile)
                .withTags(setOf(imageNameTag))
            args.map { it.trim().split("=") }.forEach {
                step.withBuildArg(it.first(), it.last())
            }
            step.exec(MyBuildImageResultCallback(buildId, elementId, dockerHostBuildApi))
                .awaitImageId()

            logger.info("Build image success, now push to repo, image name and tag: $imageNameTag")
            dockerCli.pushImageCmd(imageNameTag)
                .withAuthConfig(authConfig)
                .exec(MyPushImageResultCallback(buildId, elementId, dockerHostBuildApi))
                .awaitCompletion()

            logger.info("Push image success, now remove local image, image name and tag: $imageNameTag")

            try {
                dockerCli.removeImageCmd(
                    getImageNameWithTag(
                        repoAddr,
                        projectId,
                        dockerBuildParam.imageName,
                        dockerBuildParam.imageTag
                    )
                ).exec()
                logger.info("Remove local image success")
            } catch (e: Throwable) {
                logger.error("Docker rmi failed, msg: ${e.message}")
            }

            return Pair(true, null)
        } catch (e: Throwable) {
            logger.error("Docker build and push failed, exception: ", e)
            val cause = if (e.cause != null && e.cause!!.message != null) {
                e.cause!!.message!!.removePrefix(getWorkspace(pipelineId, vmSeqId.toInt()))
            } else {
                ""
            }
            return Pair(false, e.message + if (cause.isBlank()) "" else " cause:【$cause】")
        }
    }

    fun dockerRun(
        projectId: String,
        pipelineId: String,
        vmSeqId: String,
        buildId: String,
        dockerRunParam: DockerRunParam
    ): Pair<String, Int> {
        try {
            val authConfig = TXCommonUtils.getAuthConfig(
                ImageType.THIRD.type,
                dockerHostConfig,
                dockerRunParam.imageName,
                dockerRunParam.registryUser,
                dockerRunParam.registryPwd
            )
            val imageName = TXCommonUtils.normalizeImageName(dockerRunParam.imageName)
            // docker pull
            try {
                LocalImageCache.saveOrUpdate(imageName)
                log(buildId, "开始拉取镜像，镜像名称：$imageName")
                dockerCli.pullImageCmd(imageName).withAuthConfig(authConfig)
                    .exec(MyPullImageResultCallback(buildId, dockerHostBuildApi)).awaitCompletion()
                log(buildId, "拉取镜像成功，准备执行命令...")
            } catch (t: Throwable) {
                logger.warn("Fail to pull the image $imageName of build $buildId", t)
                log(buildId, "拉取镜像失败，错误信息：${t.message}")
                log(buildId, "尝试使用本地镜像执行命令...")
            }
            // docker run
            val volumeWs = Volume(dockerHostConfig.volumeWorkspace)
            val volumeProjectShare = Volume(dockerHostConfig.volumeProjectShare)
            val volumeMavenRepo = Volume(dockerHostConfig.volumeMavenRepo)
            val volumeNpmPrefix = Volume(dockerHostConfig.volumeNpmPrefix)
            val volumeNpmCache = Volume(dockerHostConfig.volumeNpmCache)
            val volumeCcache = Volume(dockerHostConfig.volumeCcache)
            val volumeApps = Volume(dockerHostConfig.volumeApps)
            val volumeInit = Volume(dockerHostConfig.volumeInit)
            val volumeLogs = Volume(dockerHostConfig.volumeLogs)
            val volumeGradleCache = Volume(dockerHostConfig.volumeGradleCache)

            val gateway = System.getProperty("soda.gateway", "gw.open.oa.com")
            logger.info("gateway is: $gateway")

            val binds = mutableListOf(
                Bind("${dockerHostConfig.hostPathMavenRepo}/$pipelineId/$vmSeqId/", volumeMavenRepo),
                Bind("${dockerHostConfig.hostPathNpmPrefix}/$pipelineId/$vmSeqId/", volumeNpmPrefix),
                Bind("${dockerHostConfig.hostPathNpmCache}/$pipelineId/$vmSeqId/", volumeNpmCache),
                Bind("${dockerHostConfig.hostPathCcache}/$pipelineId/$vmSeqId/", volumeCcache),
                Bind(dockerHostConfig.hostPathApps, volumeApps, AccessMode.ro),
                Bind(dockerHostConfig.hostPathInit, volumeInit, AccessMode.ro),
                Bind("${dockerHostConfig.hostPathLogs}/$buildId/$vmSeqId/", volumeLogs),
                Bind("${dockerHostConfig.hostPathGradleCache}/$pipelineId/$vmSeqId/", volumeGradleCache),
                Bind(getWorkspace(pipelineId, vmSeqId.toInt()), volumeWs)
            )
            if (enableProjectShare(projectId)) {
                binds.add(Bind(getProjectShareDir(projectId), volumeProjectShare))
            }

            val env = mutableListOf<String>()
            env.addAll(
                listOf(
                    "$ENV_KEY_PROJECT_ID=$projectId",
                    "$ENV_KEY_GATEWAY=$gateway",
//                    "TERM=xterm-256color",
                    "landun_env=${dockerHostConfig.landunEnv ?: "prod"}",
                    "$ENV_DOCKER_HOST_IP=${CommonUtils.getInnerIP()}",
                    "$BK_DISTCC_LOCAL_IP=${CommonUtils.getInnerIP()}",
                    // codecc构建机日志落到本地
                    "$ENV_LOG_SAVE_MODE=${if ("codecc_build" == dockerHostConfig.runMode) "LOCAL" else "UPLOAD"}"
                )
            )
            if (dockerRunParam.env != null && dockerRunParam.env!!.isNotEmpty()) {
                dockerRunParam.env!!.forEach {
                    env.add(it.key + "=" + (it.value ?: ""))
                }
            }
            logger.info("env is $env")
            val container = dockerCli.createContainerCmd(imageName)
                .withCmd(dockerRunParam.command)
                .withEnv(env)
                .withVolumes(volumeWs).withVolumes(volumeApps).withVolumes(volumeInit)
                .withHostConfig(HostConfig().withBinds(binds).withNetworkMode("bridge"))
                .withWorkingDir(dockerHostConfig.volumeWorkspace)
                .exec()

            logger.info("Created container $container")
            val timestamp = (System.currentTimeMillis() / 1000).toInt()
            dockerCli.startContainerCmd(container.id).exec()

            return Pair(container.id, timestamp)
        } catch (er: Throwable) {
            logger.error(er.toString())
            logger.error(er.cause.toString())
            logger.error(er.message)
            log(buildId, true, "启动容器失败，错误信息:${er.message}")
            alertApi.alert(
                AlertLevel.HIGH.name, "Docker构建机创建容器失败", "Docker构建机创建容器失败, " +
                    "母机IP:${CommonUtils.getInnerIP()}， 失败信息：${er.message}"
            )
            throw ContainerException("启动容器失败，错误信息:${er.message}")
        } finally {
            if (!dockerRunParam.registryUser.isNullOrEmpty()) {
                try {
                    dockerCli.removeImageCmd(dockerRunParam.imageName)
                    logger.info("Delete local image successfully......")
                } catch (e: java.lang.Exception) {
                    logger.info("the exception of deleteing local image is ${e.message}")
                } finally {
                    logger.info("Docker run end......")
                }
            }
        }
    }

    fun dockerStop(projectId: String, pipelineId: String, vmSeqId: String, buildId: String, containerId: String) {
        val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
        try {
            // docker stop
            val containerInfo = dockerCli.inspectContainerCmd(containerId).exec()
            if ("exited" != containerInfo.state.status) {
                dockerCli.stopContainerCmd(containerId).withTimeout(30).exec()
            }
        } catch (e: Throwable) {
            logger.error("Stop the container failed, containerId: $containerId, error msg: $e")
        }

        try {
            // docker rm
            dockerCli.removeContainerCmd(containerId).exec()
        } catch (e: Throwable) {
            logger.error("Stop the container failed, containerId: $containerId, error msg: $e")
        }
    }

    fun getDockerLogs(containerId: String, lastLogTime: Int): List<String> {
        val logs = ArrayList<String>()
        val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
        val logContainerCmd = dockerCli.logContainerCmd(containerId)
            .withStdOut(true)
            .withStdErr(true)
            .withSince(lastLogTime)
            .withTimestamps(true)
        try {
            logContainerCmd.exec(object : LogContainerResultCallback() {
                override fun onNext(item: Frame) {
                    logs.add(item.toString())
                }
            }).awaitCompletion()
        } catch (e: InterruptedException) {
            logger.error("Get docker run log exception: ", e)
        }
        return logs
    }

    fun getDockerRunExitCode(containerId: String): Int {
        val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
        return dockerCli.waitContainerCmd(containerId)
            .exec(WaitContainerResultCallback())
            .awaitStatusCode()
    }

    fun clearContainers() {
        val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
        val containerInfo = dockerCli.listContainersCmd().withStatusFilter(setOf("exited")).exec()
        for (container in containerInfo) {
            logger.info("Clear container, containerId: ${container.id}")
            dockerCli.removeContainerCmd(container.id).exec()
        }
    }

    @PostConstruct
    fun loadLocalImages() {
        try {
            val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
            val imageList = dockerCli.listImagesCmd().withShowAll(true).exec()
            logger.info("load local images, image count: ${imageList.size}")
            imageList.forEach c@{
                it.repoTags?.forEach { image ->
                    LocalImageCache.saveOrUpdate(image)
                }
            }
        } catch (e: java.lang.Exception) {
            logger.error("load local image, exception, msg: ${e.message}")
        }
    }

    fun clearLocalImages() {
        val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)

        val danglingImages = dockerCli.listImagesCmd().withDanglingFilter(true).withShowAll(true).exec()
        danglingImages.forEach {
            try {
                dockerCli.removeImageCmd(it.id).exec()
                logger.info("remove local dangling image success, image id: ${it.id}")
            } catch (e: java.lang.Exception) {
                logger.error("remove local dangling image exception ${e.message}")
            }
        }

        val imageList = dockerCli.listImagesCmd().withShowAll(true).exec()
        imageList.forEach c@{
            it.repoTags.forEach { image ->
                val lastUsedDate = LocalImageCache.getDate(image)
                if (null != lastUsedDate) {
                    if ((Date().time - lastUsedDate.time) / (1000 * 60 * 60 * 24) >= dockerHostConfig.localImageCacheDays) {
                        logger.info("remove local image, ${it.repoTags}")
                        try {
                            dockerCli.removeImageCmd(image).exec()
                            logger.info("remove local image success, image: $image")
                        } catch (e: java.lang.Exception) {
                            logger.error("remove local image exception ${e.message}")
                        }
                        return@c
                    }
                }
            }
        }
    }

    fun isContainerRunning(containerId: String): Boolean {
        val dockerCli = TXCommonUtils.getDockerDefaultClient(dockerHostConfig)
        val inspectContainerResponse = dockerCli.inspectContainerCmd(containerId).exec() ?: return false
        return inspectContainerResponse.state.running ?: false
    }

    fun log(buildId: String, message: String) {
        return log(buildId, false, message)
    }

    fun log(buildId: String, red: Boolean, message: String) {
        logger.info("write log to dispatch, buildId: $buildId, message: $message")
        try {
            dockerHostBuildApi.postLog(buildId, red, message, null)
        } catch (e: Exception) {
            logger.info("write log to dispatch failed")
        }
    }

    private fun getImageNameWithTag(repoAddr: String, projectId: String, imageName: String, imageTag: String): String {
        return "$repoAddr/paas/$projectId/$imageName:$imageTag"
    }

    private fun getImageNameWithTagNew(
        repoAddr: String,
        projectId: String,
        imageName: String,
        imageTag: String
    ): String {
        return "$repoAddr/$imageName:$imageTag"
    }

    private fun getWorkspace(pipelineId: String, vmSeqId: Int): String {
        return "${dockerHostConfig.hostPathWorkspace}/$pipelineId/$vmSeqId/"
    }

    private fun getProjectShareDir(projectCode: String): String {
        return "${dockerHostConfig.hostPathProjectShare}/$projectCode/"
    }

    private fun enableProjectShare(projectCode: String): Boolean {
        if (dockerHostConfig.shareProjectCodeWhiteList.isNullOrBlank()) {
            return false
        }
        val whiteList = dockerHostConfig.shareProjectCodeWhiteList!!.split(",").map { it.trim() }
        return whiteList.contains(projectCode)
    }

    inner class MyBuildImageResultCallback internal constructor(
        private val buildId: String,
        private val elementId: String?,
        private val dockerHostBuildApi: DockerHostBuildResourceApi
    ) : BuildImageResultCallback() {
        override fun onNext(item: BuildResponseItem?) {
            val text = item?.stream
            if (null != text) {
                dockerHostBuildApi.postLog(
                    buildId,
                    false,
                    StringUtils.removeEnd(text, "\n"),
                    elementId
                )
            }
            super.onNext(item)
        }
    }

    inner class MyPushImageResultCallback internal constructor(
        private val buildId: String,
        private val elementId: String?,
        private val dockerHostBuildApi: DockerHostBuildResourceApi
    ) : PushImageResultCallback() {
        private val totalList = mutableListOf<Long>()
        private val step = mutableMapOf<Int, Long>()
        override fun onNext(item: PushResponseItem?) {
            val text = item?.progressDetail
            if (null != text && text.current != null && text.total != null && text.total != 0L) {
                val lays = if (!totalList.contains(text.total!!)) {
                    totalList.add(text.total!!)
                    totalList.size + 1
                } else {
                    totalList.indexOf(text.total!!) + 1
                }
                var currentProgress = text.current!! * 100 / text.total!!
                if (currentProgress > 100) {
                    currentProgress = 100
                }
                if (currentProgress >= step[lays]?.plus(25) ?: 5) {
                    dockerHostBuildApi.postLog(
                        buildId,
                        false,
                        "正在推送镜像,第${lays}层，进度：$currentProgress%",
                        elementId
                    )
                    step[lays] = currentProgress
                }
            }
            super.onNext(item)
        }
    }

    inner class MyPullImageResultCallback internal constructor(
        private val buildId: String,
        private val dockerHostBuildApi: DockerHostBuildResourceApi
    ) : PullImageResultCallback() {
        private val totalList = mutableListOf<Long>()
        private val step = mutableMapOf<Int, Long>()
        override fun onNext(item: PullResponseItem?) {
            val text = item?.progressDetail
            if (null != text && text.current != null && text.total != null && text.total != 0L) {
                val lays = if (!totalList.contains(text.total!!)) {
                    totalList.add(text.total!!)
                    totalList.size + 1
                } else {
                    totalList.indexOf(text.total!!) + 1
                }
                var currentProgress = text.current!! * 100 / text.total!!
                if (currentProgress > 100) {
                    currentProgress = 100
                }

                if (currentProgress >= step[lays]?.plus(25) ?: 5) {
                    dockerHostBuildApi.postLog(
                        buildId,
                        false,
                        "正在拉取镜像,第${lays}层，进度：$currentProgress%"
                    )
                    step[lays] = currentProgress
                }
            }
            super.onNext(item)
        }
    }
}