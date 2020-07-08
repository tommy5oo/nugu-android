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
package com.skt.nugu.sdk.client.port.transport.grpc2

import com.skt.nugu.sdk.core.interfaces.message.MessageRequest
import com.skt.nugu.sdk.core.interfaces.message.MessageSender
import com.skt.nugu.sdk.core.interfaces.transport.Transport
import com.skt.nugu.sdk.core.interfaces.message.Status
import com.skt.nugu.sdk.core.interfaces.message.Status.Companion.withDescription
import com.skt.nugu.sdk.core.utils.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.skt.nugu.sdk.core.interfaces.message.Call as MessageCall

internal class Grpc2Call(val transport: Transport?, val request: MessageRequest, val listener: MessageSender.OnSendMessageListener) :
    MessageCall {
    private var executed = false
    private var canceled = false
    private var callback: MessageSender.Callback? = null
    private val callTimeoutMillis = 1000 * 10L
    companion object{
        private const val TAG = "GrpcCall"
    }

    override fun request() = request

    override fun enqueue(callback: MessageSender.Callback) {
        this.callback = callback

        synchronized(this) {
            if (executed) {
                result(Status(
                    Status.Code.FAILED_PRECONDITION
                ).withDescription("Already Executed"))
                return
            }
            executed = true
        }

        if (transport?.send(this) != true) {
            result(Status.FAILED_PRECONDITION)
        }
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
    }

    override fun execute(): Status {
        synchronized(this) {
            if (executed) {
                return Status(
                    Status.Code.FAILED_PRECONDITION
                ).withDescription("Already Executed")
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
        }

        if (transport?.send(this) != true) {
            return Status.FAILED_PRECONDITION
        }

        try {
            latch.await(callTimeoutMillis, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return result
    }

    override fun result(status: Status) {
        var newStatus = status
        if (isCanceled()) {
            newStatus = Status.CANCELLED
        }
        callback?.let {
            if (newStatus.isOk()) {
                it.onSuccess(request())
            } else {
                it.onFailure(request(), newStatus)
            }
            listener.onPostSendMessage(request(), newStatus)
        }
        callback = null
    }

}