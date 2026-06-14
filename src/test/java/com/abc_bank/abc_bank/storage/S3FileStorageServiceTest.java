package com.abc_bank.abc_bank.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3FileStorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private S3FileStorageService storageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(storageService, "bucket", "test-bucket");
        ReflectionTestUtils.setField(storageService, "region", "us-east-1");
    }

    @Test
    void upload_putsObjectToS3_andReturnsPublicUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        String url = storageService.upload(file, "profile-pictures/1");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        PutObjectRequest request = requestCaptor.getValue();

        assertThat(request.bucket()).isEqualTo("test-bucket");
        assertThat(request.contentType()).isEqualTo("image/png");
        assertThat(request.key()).startsWith("profile-pictures/1/").endsWith("-avatar.png");
        assertThat(url)
                .startsWith("https://test-bucket.s3.us-east-1.amazonaws.com/profile-pictures/1/")
                .endsWith("-avatar.png")
                .contains(request.key());
    }

    @Test
    void upload_sanitizesFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "my pic (1)!.jpg", "image/jpeg", new byte[]{9});

        String url = storageService.upload(file, "profile-pictures/2");

        assertThat(url).endsWith("-my_pic__1__.jpg");
    }
}
