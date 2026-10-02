package sisa.entity;

/** Lifecycle of a Student's school record — independent of the linked User's login/approval status. */
public enum StudentStatus {
    ACTIVE,
    TRANSFERRED,
    ARCHIVED
}
