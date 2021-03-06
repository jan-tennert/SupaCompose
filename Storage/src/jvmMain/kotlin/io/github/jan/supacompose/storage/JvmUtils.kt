package io.github.jan.supacompose.storage

import java.io.File
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

suspend fun BucketApi.upload(path: String, file: File) = upload(path, file.readBytes())

suspend fun BucketApi.upload(path: String, file: Path) = upload(path, file.readBytes())

suspend fun BucketApi.downloadTo(path: String, file: File) {
    val bytes = download(path)
    file.writeBytes(bytes)
}

suspend fun BucketApi.downloadTo(path: String, file: Path) {
    val bytes = download(path)
    file.writeBytes(bytes)
}