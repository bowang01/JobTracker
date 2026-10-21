package com.jobtrack.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.jobtrack.domain.User;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.PasswordChangeForm;
import com.jobtrack.web.form.ProfileForm;
import com.jobtrack.web.form.RegistrationForm;

public interface UserService {
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Registration needs only an email and a password, and always creates a regular user. */
    User register(RegistrationForm form);

    /** Used to create the initial administrator. */
    User createAdmin(String email, String rawPassword);

    void recordLogin(UUID userId);

    User get(UUID id);

    void updateProfile(AppUserDetails me, ProfileForm form);

    void changePassword(AppUserDetails me, PasswordChangeForm form);

    /** Regular users, for the admin's "User" filter on the applications list. */
    List<User> listRegularUsers();

    void setEnabled(UUID userId, boolean enabled, AppUserDetails me);

    /** Deletes the user and all their applications, history, interviews and documents; files are removed after commit. */
    void deleteUser(UUID userId, AppUserDetails me);

    /**
     * Deletes all of a user's applications with their history, interviews and documents (files after commit),
     * but keeps the account and its interview questions. Used to reset the demo account.
     */
    void deleteApplicationData(UUID userId);
}
