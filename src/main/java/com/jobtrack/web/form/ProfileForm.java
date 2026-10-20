package com.jobtrack.web.form;

import com.jobtrack.domain.User;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProfileForm {

    @Size(max = 100, message = "Display name must be at most 100 characters")
    private String displayName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email address")
    @Size(max = 255, message = "Email must be at most 255 characters")
    private String email;

    public static ProfileForm from(User u) {
        ProfileForm f = new ProfileForm();
        f.displayName = u.getDisplayName();
        f.email = u.getEmail();
        return f;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
