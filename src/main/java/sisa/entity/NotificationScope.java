package sisa.entity;

/** Who an announcement fans out to — see AnnouncementService for the resolution logic. */
public enum NotificationScope {
    SCHOOL,
    CLASS,
    STUDENT,
    TEACHERS
}
