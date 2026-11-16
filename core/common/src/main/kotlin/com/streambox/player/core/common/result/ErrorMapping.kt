@file:Suppress("MatchingDeclarationName") // AppException + its mapping function belong together.

package com.streambox.player.core.common.result

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import kotlin.coroutines.cancellation.CancellationException

/** Exception carrying a typed [AppError] across layers that can only throw (streams, interceptors). */
class AppException(val error: AppError, cause: Throwable? = null) : IOException(error.toString(), cause)

/**
 * Maps low-level exceptions to [AppError]. Cancellation is never mapped: it must propagate.
 * `UnknownServiceException` is what OkHttp / Android throw when cleartext is blocked by policy.
 */
@Suppress("CyclomaticComplexMethod") // A flat exception-type table; splitting it would hide the mapping.
fun Throwable.toAppError(): AppError {
    if (this is CancellationException) throw this
    return when (this) {
        is AppException -> error

        is SocketTimeoutException -> AppError.Timeout

        is InterruptedIOException -> AppError.Timeout

        is UnknownHostException, is ConnectException, is NoRouteToHostException -> AppError.NoNetwork

        is UnknownServiceException -> if (message.orEmpty().contains("CLEARTEXT", ignoreCase = true)) {
            AppError.CleartextNotAllowed
        } else {
            AppError.Io(message.orEmpty())
        }

        is FileNotFoundException -> AppError.Io(message.orEmpty())

        is IOException -> AppError.Io(message ?: javaClass.simpleName)

        is SecurityException -> AppError.Io(message ?: "permission denied")

        else -> AppError.Unknown(message ?: javaClass.simpleName)
    }
}
