package com.sourcepoint.cmplibrary.data.network.converter

import com.sourcepoint.cmplibrary.core.layout.model.NativeMessageDto
import com.sourcepoint.cmplibrary.data.network.model.*
import com.sourcepoint.cmplibrary.data.network.model.consent.ConsentResp
import com.sourcepoint.cmplibrary.util.Either

/**
 * Component used to convert the response body of the message call to its DTO
 */
internal interface JsonConverter {
    /**
     * @param body json object
     * @return [Either] object contain either a DTO or an [Throwable]
     */

    fun toUnifiedMessageResp(body: String): Either<UnifiedMessageResp>

    fun toUnifiedMessageResp1203(body: String): Either<UnifiedMessageResp1203>

    fun toNativeMessageResp(body: String): Either<NativeMessageResp>

    fun toNativeMessageRespK(body: String): Either<NativeMessageRespK>

    fun toConsentResp(body: String): Either<ConsentResp>

    fun toNativeMessageDto(body: String): Either<NativeMessageDto>

    fun toConsentAction(json: String): Either<ConsentAction>

    companion object
}
