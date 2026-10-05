package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;

@Entity
@Table(name = "timetable_slots",
        uniqueConstraints = @UniqueConstraint(name = "uk_timetable_class_day_period", columnNames = {"class_name", "day_of_week", "period_number"}))
@Getter
@Setter
@NoArgsConstructor
public class TimetableSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "class_name", nullable = false, length = 60)
    private String className;

    @Column(nullable = false, length = 80)
    private String subject;

    @ManyToOne(optional = false)
    @JoinColumn(name = "teacher_id", referencedColumnName = "teacherId", nullable = false)
    private Teacher teacher;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @ManyToOne
    @JoinColumn(name = "room_resource_id")
    private Resource room;

    public String getRoomName() {
        return room == null ? null : room.getName();
    }
}

/*
CREATE TABLE timetable_slots (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,

    class_name VARCHAR(60) NOT NULL,

    subject VARCHAR(80) NOT NULL,

    teacher_id BIGINT NOT NULL,

    day_of_week VARCHAR(10) NOT NULL,

    period_number INT NOT NULL,

    room_resource_id BIGINT NULL,

    CONSTRAINT uk_timetable_class_day_period
        UNIQUE (class_name, day_of_week, period_number),

    CONSTRAINT fk_timetable_teacher
        FOREIGN KEY (teacher_id)
        REFERENCES teacher(teacherId),

    CONSTRAINT fk_timetable_room
        FOREIGN KEY (room_resource_id)
        REFERENCES resource(id)
);

*/