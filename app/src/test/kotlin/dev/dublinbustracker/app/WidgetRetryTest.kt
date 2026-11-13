package dev.dublinbustracker.app

import dev.dublinbustracker.app.widget.retrying
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertNull

class WidgetRetryTest {
    @Test
    fun `a refresh cancelled mid-request is not reported as a failed fetch`() = runBlocking {
        var result: Result<Unit>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) { result = retrying(emptyList()) { awaitCancellation() } }

        job.cancelAndJoin()

        // A returned failure would make the widget mark good data stale and queue a pointless retry.
        assertNull(result)
    }
}
