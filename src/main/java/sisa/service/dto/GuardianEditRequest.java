package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registrar/Principal edits to the linked guardian's (Parent's) own details. */
@Getter
@Setter
@NoArgsConstructor
public class GuardianEditRequest {
    private String relationshipToStudent;
    private String contactNumber;
}
