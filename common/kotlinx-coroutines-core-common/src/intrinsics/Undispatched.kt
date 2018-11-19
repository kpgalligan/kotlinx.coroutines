/*
 * Copyright 2016-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines.intrinsics

import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * Use this function to restart coroutine directly from inside of [suspendCoroutine],
 * when the code is already in the context of this coroutine.
 * It does not use [ContinuationInterceptor] and does not update context of the current thread.
 */
internal fun <T> (suspend () -> T).startCoroutineUnintercepted(completion: Continuation<T>) {
    startDirect(completion) { actualCompletion ->
        startCoroutineUninterceptedOrReturn(actualCompletion)
    }
}

/**
 * Use this function to restart coroutine directly from inside of [suspendCoroutine],
 * when the code is already in the context of this coroutine.
 * It does not use [ContinuationInterceptor] and does not update context of the current thread.
 */
internal fun <R, T> (suspend (R) -> T).startCoroutineUnintercepted(receiver: R, completion: Continuation<T>) {
    startDirect(completion) {  actualCompletion ->
        startCoroutineUninterceptedOrReturn(receiver, actualCompletion)
    }
}

/**
 * Use this function to start new coroutine in [CoroutineStart.UNDISPATCHED] mode &mdash;
 * immediately execute coroutine in the current thread until next suspension.
 * It does not use [ContinuationInterceptor], but updates the context of the current thread for the new coroutine.
 */
internal fun <T> (suspend () -> T).startCoroutineUndispatched(completion: Continuation<T>) {
    startDirect(completion) { actualCompletion ->
        withCoroutineContext(completion.context, null) {
            startCoroutineUninterceptedOrReturn(actualCompletion)
        }
    }
}

/**
 * Use this function to start new coroutine in [CoroutineStart.UNDISPATCHED] mode &mdash;
 * immediately execute coroutine in the current thread until next suspension.
 * It does not use [ContinuationInterceptor], but updates the context of the current thread for the new coroutine.
 */
internal fun <R, T> (suspend (R) -> T).startCoroutineUndispatched(receiver: R, completion: Continuation<T>) {
    startDirect(completion) { actualCompletion ->
        withCoroutineContext(completion.context, null) {
            startCoroutineUninterceptedOrReturn(receiver, actualCompletion)
        }
    }
}

/**
 * Starts given [block] immediately in the current stack-frame until first suspension point.
 * This method supports debug probes and thus can intercept completion, thus completion is provide
 * as the parameter of [block].
 */
private inline fun <T> startDirect(completion: Continuation<T>, block: (Continuation<T>) -> Any?) {
    val actualCompletion = probeCoroutineCreated(completion)
    val value = try {
        block(actualCompletion)
    } catch (e: Throwable) {
        actualCompletion.resumeWithException(e)
        return
    }
    if (value !== COROUTINE_SUSPENDED) {
        @Suppress("UNCHECKED_CAST")
        actualCompletion.resume(value as T)
    }
}

/**
 * Starts this coroutine with the given code [block] in the same context and returns result when it
 * completes without suspension.
 * This function shall be invoked at most once on this coroutine.
 *
 * First, this function initializes parent job from the `parentContext` of this coroutine that was passed to it
 * during construction. Second, it starts the coroutine using [startCoroutineUninterceptedOrReturn].
 */
internal fun <T, R> AbstractCoroutine<T>.startUndispatchedOrReturn(receiver: R, block: suspend R.() -> T): Any? {
    initParentJob()
    return undispatchedResult { block.startCoroutineUninterceptedOrReturn(receiver, this) }
}

private inline fun <T> AbstractCoroutine<T>.undispatchedResult(startBlock: () -> Any?): Any? {
    val result = try {
        startBlock()
    } catch (e: Throwable) {
        CompletedExceptionally(e)
    }
    return when {
        result === COROUTINE_SUSPENDED -> COROUTINE_SUSPENDED
        makeCompletingOnce(result, MODE_IGNORE) -> {
            if (result is CompletedExceptionally) throw result.cause else result
        }
        else -> COROUTINE_SUSPENDED
    }
}
