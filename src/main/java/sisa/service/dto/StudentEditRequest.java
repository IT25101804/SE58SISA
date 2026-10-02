package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registrar/Principal edits to an existing student's own details (not the guardian's). */
@Getter
@Setter
@NoArgsConstructor
public class StudentEditRequest {
    private String fullName;
    private String email;
    private String admissionYear;
    private String className;
    private String dateOfBirth; // yyyy-MM-dd
    private String gender;
    private String address;
    private String emergencyContact;
}
