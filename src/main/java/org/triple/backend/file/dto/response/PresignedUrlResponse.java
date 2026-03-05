package org.triple.backend.file.dto.response;

public sealed interface PresignedUrlResponse permits PresignedUrlSuccessDto, PresignedUrlFailedDto{

}
