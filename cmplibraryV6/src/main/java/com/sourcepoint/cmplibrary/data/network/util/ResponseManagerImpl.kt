package com.sourcepoint.cmplibrary.data.network.util

import com.sourcepoint.cmplibrary.data.network.converter.JsonConverter
import com.sourcepoint.cmplibrary.data.network.model.NativeMessageResp
import com.sourcepoint.cmplibrary.data.network.model.NativeMessageRespK
import com.sourcepoint.cmplibrary.data.network.model.UnifiedMessageResp
import com.sourcepoint.cmplibrary.data.network.model.UnifiedMessageResp1203
import com.sourcepoint.cmplibrary.data.network.model.consent.ConsentResp
import com.sourcepoint.cmplibrary.exception.InvalidRequestException
import com.sourcepoint.cmplibrary.exception.InvalidResponseWebMessageException
import com.sourcepoint.cmplibrary.util.Either
import com.sourcepoint.cmplibrary.util.check
import okhttp3.Response

/**
 * Factory method for creating a concrete instance of ResponseManager
 * @param jsonConverter abject used for converting a string to a DTO
 * @return an implementation of [ResponseManager]
 */
internal fun ResponseManager.Companion.create(
    jsonConverter: JsonConverter
): ResponseManager = ResponseManagerImpl(jsonConverter)

/**
 * An implementation od the [ResponseManager] interface
 */
private class ResponseManagerImpl(val jsonConverter: JsonConverter) : ResponseManager {

    /**
     * @param r http response
     * @return an [Either] object of a [MessageResp] type parameter
     */
    override fun parseResponse(r: Response): Either<UnifiedMessageResp> = check {
        val body = r.body()?.byteStream()?.reader()?.readText() ?: fail("Body Response")
        if (r.isSuccessful) {
            when (val either: Either<UnifiedMessageResp> = jsonConverter.toUnifiedMessageResp(body)) {
                is Either.Right -> either.r
                is Either.Left -> throw either.t
            }
        } else {
            throw InvalidRequestException(description = body)
        }
    }

    override fun parseResponse1203(r: Response): Either<UnifiedMessageResp1203> = check {
        val body = r.body()?.byteStream()?.reader()?.readText() ?: fail("Body Response")
        if (r.isSuccessful) {
            when (val either: Either<UnifiedMessageResp1203> = jsonConverter.toUnifiedMessageResp1203(body)) {
                is Either.Right -> either.r
                is Either.Left -> throw either.t
            }
        } else {
            throw InvalidRequestException(description = body)
        }
    }

    override fun parseNativeMessRes(r: Response): Either<NativeMessageResp> = check {
        val body = r.body()?.byteStream()?.reader()?.readText() ?: fail("Body Response")
        if (r.isSuccessful) {
            when (val either: Either<NativeMessageResp> = jsonConverter.toNativeMessageResp(body)) {
                is Either.Right -> either.r
                is Either.Left -> throw either.t
            }
        } else {
            throw InvalidRequestException(description = body)
        }
    }

    override fun parseNativeMessResK(r: Response): Either<NativeMessageRespK> = check {
        val body = r.body()?.byteStream()?.reader()?.readText() ?: fail("Body Response")
        if (r.isSuccessful) {
            when (val either: Either<NativeMessageRespK> = jsonConverter.toNativeMessageRespK(body)) {
                is Either.Right -> either.r
                is Either.Left -> throw either.t
            }
        } else {
            throw InvalidRequestException(description = body)
        }
    }

    override fun parseConsentRes(r: Response): Either<ConsentResp> = check {
        val body = r.body()?.byteStream()?.reader()?.readText() ?: fail("Body Response")
        if (r.isSuccessful) {
            when (val either: Either<ConsentResp> = jsonConverter.toConsentResp(body)) {
                is Either.Right -> either.r
                is Either.Left -> throw either.t
            }
        } else {
            throw InvalidRequestException(description = body)
        }
    }

    private fun fail(param: String): Nothing {
        throw InvalidResponseWebMessageException(description = "$param object is null")
    }
}
