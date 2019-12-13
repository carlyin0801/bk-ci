package com.tencent.devops.log.util

import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.BufferedSource

class LogResponseBody : ResponseBody() {
    override fun contentLength(): Long {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }

    override fun contentType(): MediaType? {
        return MediaType.parse("application/json")
    }

    override fun source(): BufferedSource {
        TODO("not implemented") // To change body of created functions use File | Settings | File Templates.
    }
}
