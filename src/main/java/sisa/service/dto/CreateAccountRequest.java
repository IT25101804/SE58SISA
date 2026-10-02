package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateAccountRequest {
    private String fullName;
    private String email;
    private String rawPassword;
}