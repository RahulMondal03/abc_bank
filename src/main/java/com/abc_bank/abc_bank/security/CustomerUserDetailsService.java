package com.abc_bank.abc_bank.security;


import com.abc_bank.abc_bank.auth_users.entity.User;
import com.abc_bank.abc_bank.auth_users.repo.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerUserDetailsService implements UserDetailsService {

    private final UserRepo userRepo ;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Use the Spring contract type so the provider masks "user not found" as bad credentials
        // (prevents account enumeration on login).
        User user=userRepo.findByEmail(username)
                .orElseThrow(()-> new UsernameNotFoundException("invalid email or password"));
        return AuthUser.builder()
                .user(user)
                .build();


    }
}
