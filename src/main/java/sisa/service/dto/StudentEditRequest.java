package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class StudentEditRequest {
    private String fullName;
    private String email;
    private String admissionYear;
    private String className;
    private String dateOfBirth;
    private String gender;
    private String address;
    private String emergencyContact;
}
