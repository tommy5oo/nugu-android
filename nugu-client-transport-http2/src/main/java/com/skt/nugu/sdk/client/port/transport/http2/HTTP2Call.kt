/**
 * Copyright (c) 2020 SK Telecom Co., Ltd. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.skt.nugu.sdk.client.port.transport.http2

import com.skt.nugu.sdk.core.interfaces.message.AsyncKey
import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.Status.Companion.withDescription
import com.skt.nugu.sdk.core.interfaces.message.request.EventMessageRequest
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.*
import com.skt.nugu.sdk.core.interfaces.message.Call as MessageCall

internal class HTTP2Call(
    val timeoutScheduler: ScheduledExecutorService,
    val transport: Transport?,
    val request: MessageRequest,
    val headers: Map<String, String>?,
    listener: MessageSender.OnSendMessageListener
) : MessageCall {
    private val timeoutFutures = ConcurrentHashMap<Int, ScheduledFuture<*>>()
    private var executed = false
    private var canceled = false
    private var completed = false
    private var callback: MessageSender.Callback? = null
    private var eventListener: MessageSender.EventListener? = null
    private var listener: MessageSender.OnSendMessageListener? = listener
    private var callTimeoutMillis = 1000 * 10L
    private var noAck = false
    private var invokeStartEvent = true

    companion object{
        private const val TAG = "HTTP2Call"
    }

    override fun request() = request
    override fun headers() = headers
    override fun enqueue(
        callback: MessageSender.Callback?,
        eventListener: MessageSender.EventListener?
    ): Boolean {
        synchronized(this) {
            if (executed) {
                callback?.onFailure(request(),Status(
                    Status.Code.FAILED_PRECONDITION
                ).withDescription("Already Executed"))
                return false
            }
            if (canceled) {
                callback?.onFailure(request(), Status(
                    Status.Code.CANCELLED
                ).withDescription("Already canceled"))
                return false
            }
            executed = true
        }
        this.callback = callback
        this.eventListener = eventListener

        scheduleTimeout()

        listener?.onPreSendMessage(request())
        if (transport?.send(this) != true) {
            onComplete(Status.FAILED_PRECONDITION.withDescription("send() called while not connected"))
            return false
        }

        if(noAck) {
            onComplete(Status.OK)
        }
        return true
    }

    override fun isCanceled() = synchronized(this) {
        canceled
    }

    override fun cancel() {
        synchronized(this) {
            if (canceled) return // Already canceled.
            canceled = true
        }
        Logger.d(TAG, "cancel")
        onComplete(Status.CANCELLED.withDescription("Client Closed Request"))
    }

    override fun execute(): Status {
        synchronized(this) {
            if (executed) {
                return Status(
                    Status.Code.FAILED_PRECONDITION
                ).withDescription("Already Executed")
            }
            if (canceled) {
                return Status(
                    Status.Code.CANCELLED
                ).withDescription("Already canceled")
            }
            executed = true
        }

        val latch = CountDownLatch(1)
        var result = Status.DEADLINE_EXCEEDED

        this.callback = object : MessageSender.Callback {
            override fun onFailure(request: MessageRequest, status: Status) {
                result = status
                latch.countDown()
            }

            override fun onSuccess(request: MessageRequest) {
                result = Status.OK
                latch.countDown()
            }

            override fun onResponseStart(request: MessageRequest) {
            }
        }

        listener?.onPreSendMessage(request())
        if (transport?.send(this) != true) {
            onComplete(Status.FAILED_PRECONDITION.withDescription("send() called while not connected"))
        }

        if(noAck) {
            onComplete(Status.OK)
        }
        try {
            latch.await(callTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    override fun onStart() {
        if(invokeStartEvent) {
            invokeStartEvent = false
            callback?.onResponseStart(request())
        }
    }

    override fun isCompleted() = synchronized(this) {
        completed
    }

    override fun onComplete(status: Status) {
        synchronized(this) {
            if (completed) return // Already completed.
            completed = true
        }
        cancelScheduledTimeout()

        // Notify Callback
        if (status.isOk()) {
            if(invokeStartEvent) {
                invokeStartEvent = false
                callback?.onResponseStart(request())
            }
            callback?.onSuccess(request())
        } else {
            callback?.onFailure(request(), status)
        }
        callback = null

        // Notify Listener
        listener?.onPostSendMessage(request(), status)
        listener = null
    }

    override fun onAsyncKeyReceived(asyncKey: AsyncKey) {
        eventListener?.onAsyncKeyReceived(request(), asyncKey)
    }

    private val hashCode : Int by lazy {
        when(request) {
            is EventMessageRequest -> request.hashCode()
            else -> -1
        }
    }

    private fun scheduleTimeout() {
        if(hashCode != -1) {
            timeoutFutures[hashCode] = timeoutScheduler.schedule({
                onComplete(Status.DEADLINE_EXCEEDED)
            }, callTimeoutMillis, TimeUnit.MILLISECONDS)
        }
    }

    override fun noAck(): MessageCall {
        noAck = true
        return this
    }

    private fun cancelScheduledTimeout() {
        timeoutFutures.remove(hashCode)?.cancel(true)
    }

    override fun callTimeout(millis: Long): MessageCall {
        callTimeoutMillis = millis
        return this
    }

    override fun callTimeout() = callTimeoutMillis

    override fun reschedule() {
        if(!isCanceled()) {
            cancelScheduledTimeout()
            scheduleTimeout()
        }
    }

    override fun waitForReady() = true

    override fun getLastResponseTimeMillis() = 0L

    override fun getLastRequestTimeMillis() = 0L

    override fun updateLastRequestTimeMillis() {
        // no op
    }
}

