package com.abc_bank.abc_bank.auth_users.services;

import com.abc_bank.abc_bank.auth_users.dtos.UserDTO;
import com.abc_bank.abc_bank.auth_users.entity.User;
import com.abc_bank.abc_bank.auth_users.repo.UserRepo;
import com.abc_bank.abc_bank.res.Response;
import com.abc_bank.abc_bank.storage.FileStorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceProfilePictureTest {

    @Mock
    private UserRepo userRepo;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private ModelMapper modelMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(7L).email("jane@example.com").active(true).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("jane@example.com", null, java.util.List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void updateProfilePicture_uploadsFile_savesUrl_andReturnsDto() {
        MultipartFile file = new MockMultipartFile(
                "file", "avatar.png", "image/png", new byte[]{1, 2, 3});
        String uploadedUrl = "https://test-bucket.s3.us-east-1.amazonaws.com/profile-pictures/7/abc-avatar.png";

        when(userRepo.findByEmail("jane@example.com")).thenReturn(Optional.of(user));
        when(fileStorageService.upload(eq(file), eq("profile-pictures/7"))).thenReturn(uploadedUrl);
        when(modelMapper.map(any(User.class), eq(UserDTO.class)))
                .thenAnswer(invocation -> {
                    User u = invocation.getArgument(0);
                    return UserDTO.builder()
                            .id(u.getId())
                            .email(u.getEmail())
                            .profilePictureUrl(u.getProfilePictureUrl())
                            .build();
                });

        Response<UserDTO> response = userService.updateProfilePicture(file);

        assertThat(user.getProfilePictureUrl()).isEqualTo(uploadedUrl);
        assertThat(user.getUpdatedAt()).isNotNull();
        verify(userRepo).save(user);

        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.getData().getProfilePictureUrl()).isEqualTo(uploadedUrl);
    }
}
