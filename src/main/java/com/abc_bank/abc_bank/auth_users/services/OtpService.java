package com.abc_bank.abc_bank.auth_users.services;

import com.abc_bank.abc_bank.auth_users.entity.User;

public interface OtpService {

    /**
     * Generates a one-time code for the given user, persists it (hashed) and
     * emails it to the user. Any previously issued codes for the user are removed.
     *
     * @return the challengeId the client must present when verifying the code
     */
    String issueOtp(User user);

    /**
     * Validates the supplied code against the challenge. On success the code is
     * marked as used and the associated user is returned. On failure a
     * BadRequestException is thrown.
     */
    User verifyOtp(String challengeId, String code);

    /**
     * Re-issues a fresh code for the user associated with the given challenge.
     *
     * @return the new challengeId
     */
    String resendOtp(String challengeId);
}
