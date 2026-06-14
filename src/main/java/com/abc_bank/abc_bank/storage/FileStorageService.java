package com.abc_bank.abc_bank.storage;

import org.springframework.web.multipart.MultipartFile;

public interface FileStorageService {

    /**
     * Uploads the given file under the supplied key prefix and returns the
     * publicly accessible URL of the stored object.
     */
    String upload(MultipartFile file, String keyPrefix);
}
