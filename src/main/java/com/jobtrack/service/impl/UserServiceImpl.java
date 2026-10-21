package com.jobtrack.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jobtrack.domain.Role;
import com.jobtrack.domain.User;
import com.jobtrack.repository.DocumentRepository;
import com.jobtrack.repository.InterviewRepository;
import com.jobtrack.repository.JobApplicationRepository;
import com.jobtrack.repository.StatusChangeRepository;
import com.jobtrack.repository.UserRepository;
import com.jobtrack.security.AppUserDetails;
import com.jobtrack.web.form.PasswordChangeForm;
import com.jobtrack.web.form.ProfileForm;
import com.jobtrack.web.form.RegistrationForm;

import com.jobtrack.service.BusinessException;
import com.jobtrack.service.FileStorageService;
import com.jobtrack.service.NotFoundException;
import com.jobtrack.service.Text;
import com.jobtrack.service.UserService;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository users;
    private final JobApplicationRepository applications;
    private final StatusChangeRepository history;
    private final InterviewRepository interviews;
    private final DocumentRepository documents;
    private final FileStorageService storage;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserServiceImpl(UserRepository users, JobApplicationRepository applications, StatusChangeRepository history,
                       InterviewRepository interviews, DocumentRepository documents, FileStorageService storage,
                       PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.applications = applications;
        this.history = history;
        this.interviews = interviews;
        this.documents = documents;
        this.storage = storage;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ Registration / profile

    /** Registration needs only an email and a password, and always creates a regular user. */
    @Transactional
    @Override
    public User register(RegistrationForm form) {
        String email = UserService.normalizeEmail(form.getEmail());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("email", "This email is already registered");
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        u.setRole(Role.USER);
        u.setEnabled(true);
        return users.save(u);
    }

    /** Used to create the initial administrator. */
    @Transactional
    @Override
    public User createAdmin(String email, String rawPassword) {
        User u = new User();
        u.setEmail(UserService.normalizeEmail(email));
        u.setDisplayName("Administrator");
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setRole(Role.ADMIN);
        u.setEnabled(true);
        return users.save(u);
    }

    @Transactional
    @Override
    public void recordLogin(UUID userId) {
        users.updateLastLogin(userId, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    @Override
    public User get(UUID id) {
        return users.findById(id).orElseThrow(NotFoundException::new);
    }

    @Transactional
    @Override
    public void updateProfile(AppUserDetails me, ProfileForm form) {
        User u = get(me.getId());
        String email = UserService.normalizeEmail(form.getEmail());
        if (users.existsByEmailIgnoreCaseAndIdNot(email, u.getId())) {
            throw new BusinessException("email", "This email is used by another account");
        }
        u.setEmail(email);
        u.setDisplayName(Text.trimToNull(form.getDisplayName()));
    }

    @Transactional
    @Override
    public void changePassword(AppUserDetails me, PasswordChangeForm form) {
        User u = get(me.getId());
        if (!passwordEncoder.matches(form.getCurrentPassword(), u.getPasswordHash())) {
            throw new BusinessException("currentPassword", "Current password is incorrect");
        }
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("confirmPassword", "The new passwords do not match");
        }
        u.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
    }

    // ------------------------------------------------------------------ Admin operations

    /** Regular users, for the admin's "User" filter on the applications list. */
    @Transactional(readOnly = true)
    @Override
    public List<User> listRegularUsers() {
        return users.findByRoleOrderByEmailAsc(Role.USER);
    }

    @Transactional
    @Override
    public void setEnabled(UUID userId, boolean enabled, AppUserDetails me) {
        if (userId.equals(me.getId())) {
            throw new BusinessException("You cannot enable or disable your own account");
        }
        User u = get(userId);
        if (!enabled && u.isAdmin() && u.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("At least one active administrator must remain");
        }
        u.setEnabled(enabled);
    }

    /** Deletes the user and all their applications, history, interviews and documents; files are removed after commit. */
    @Transactional
    @Override
    public void deleteUser(UUID userId, AppUserDetails me) {
        if (userId.equals(me.getId())) {
            throw new BusinessException("You cannot delete your own account");
        }
        User u = get(userId);
        if (u.isAdmin() && u.isEnabled() && users.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("At least one active administrator must remain");
        }
        deleteApplicationData(userId);
        users.delete(u); // history rows elsewhere whose changed_by points at this user are handled by ON DELETE SET NULL
    }

    @Transactional
    @Override
    public void deleteApplicationData(UUID userId) {
        List<String> files = documents.findStoredFilenamesByOwnerId(userId);
        history.deleteByOwnerId(userId);
        interviews.deleteByOwnerId(userId);
        documents.deleteByOwnerId(userId);
        applications.deleteByOwnerId(userId);
        storage.deleteAfterCommit(files);
    }
}
