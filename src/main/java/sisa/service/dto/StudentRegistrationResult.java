package sisa.service.dto;

/** Returned so the "New Student" page can show both generated IDs as .id-badge chips. */
public record StudentRegistrationResult(String studentId, String parentId, boolean parentReused) {
}
