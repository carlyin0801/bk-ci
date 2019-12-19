package com.tencent.devops.repository.pojo

data class GitBranch(
    val name: String,
    val protected: Boolean,
    val id: String,
    val short_id: String
)