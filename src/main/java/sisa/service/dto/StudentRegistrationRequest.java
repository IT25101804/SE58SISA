package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StudentRegistrationRequest {

    private String fullName;
    private String email;
    private String rawPassword;

    private String admissionYear;
    private String className;
    private String dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContact;

    private String guardianFullName;
    private String guardianUsername;
    private String guardianEmail;
    private String guardianRawPassword;
    private String relationshipToStudent;
    private String guardianContact;
}
