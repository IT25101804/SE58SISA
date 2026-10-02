package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One combined form: the new Student's own account/profile fields plus their
 * guardian's fields. Usernames are never chosen here — every account's username
 * is its auto-generated ID (see UserAccountFactory), since the ID is guaranteed
 * unique. {@code guardianUsername} is therefore an existing guardian's ID, typed
 * in to link that Parent instead of creating a new one (report section 6.3 —
 * "when assign student id automatically generate parent id too", but never
 * duplicate an existing guardian); leave it blank to create a brand new guardian.
 */
@Getter
@Setter
@NoArgsConstructor
public class StudentRegistrationRequest {

    // --- Student login (User) ---
    private String fullName;
    private String email;
    private String rawPassword;

    // --- Student profile ---
    private String admissionYear;
    private String className;
    private String dateOfBirth; // yyyy-MM-dd from <input type="date">
    private String gender;
    private String address;
    private String emergencyContact;

    // --- Guardian / Parent ---
    private String guardianFullName;
    private String guardianUsername; // an EXISTING guardian's ID to link (optional) — see class doc
    private String guardianEmail;
    private String guardianRawPassword;
    private String relationshipToStudent;
    private String guardianContact;
}
