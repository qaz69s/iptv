package com.streambox.player.core.common.result

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import kotlin.coroutines.cancellation.CancellationException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ErrorMappingTest {
    @Test
    fun `network exceptions map to typed errors`() {
        assertEquals(AppError.Timeout, SocketTimeoutException().toAppError())
        assertEquals(AppError.NoNetwork, UnknownHostException("h").toAppError())
        assertEquals(AppError.NoNetwork, ConnectException().toAppError())
        assertEquals(
            AppError.CleartextNotAllowed,
            UnknownServiceException("CLEARTEXT communication not permitted").toAppError(),
        )
        assertEquals(AppError.Http(404), AppException(AppError.Http(404)).toAppError())
        assertEquals(AppError.Io("disk"), IOException("disk").toAppError())
    }

    @Test
    fun `cancellation is rethrown`() {
        assertThrows<CancellationException> { CancellationException("x").toAppError() }
    }
}
