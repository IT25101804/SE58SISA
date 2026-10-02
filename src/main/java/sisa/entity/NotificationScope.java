package sisa.entity;

/** Who an announcement fans out to — see AnnouncementService for the resolution logic. */
public enum NotificationScope {
    SCHOOL,
    CLASS,
    STUDENT,      // one student + their linked parent
    TEACHERS,
    TEACHER,      // one specific teacher
    ONE_STUDENT,  // one student only
    GUARDIANS     // one student's linked parent only
}
