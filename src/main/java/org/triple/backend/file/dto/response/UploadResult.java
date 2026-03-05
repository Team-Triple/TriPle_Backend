package org.triple.backend.file.dto.response;

public sealed interface UploadResult permits UploadSuccess, UploadFailed {

}