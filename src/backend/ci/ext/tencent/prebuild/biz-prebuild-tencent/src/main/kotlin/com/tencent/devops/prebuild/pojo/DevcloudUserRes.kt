package com.tencent.devops.prebuild.pojo

data class DevcloudUserRes(
    val data: UserResData,
    val actionMessage: String,
    val actionCode: Int
)

data class UserResItem(
    val res_type: String,
    val creator: String,
    val ip: String,
    val createdAt: String,
    val bakOperator: String,
    val assetId: String,
    val operator: String,
    val disk: String,
    val cpu: String,
    val regionName: String,
    val memory: String
)

data class UserResData(
    val items: ArrayList<UserResItem>,
    val total: Int
)

data class SingleDevcloudUserRes(
    val data: UserResItem,
    val actionMessage: String,
    val actionCode: Int
)