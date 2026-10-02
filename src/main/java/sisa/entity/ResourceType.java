package sisa.entity;

/**
 * The kinds of bookable resource (report FR-13, section 6.2). CLASSROOM is special:
 * unlike ROOM/LAB/EQUIPMENT it doubles as a real school class — its name is the
 * "Class" students are registered into (see StudentRegistrationService), and its
 * capacity caps how many active students that class can hold.
 */
public enum ResourceType {
    ROOM,
    LAB,
    EQUIPMENT,
    CLASSROOM
}
