package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Academic Management module (System Functions doc, Registrar: "Register subjects").
 * A simple reference list the Registrar (and Principal) maintain — not tied to any
 * one class or teacher; TimetableSlot/Exam still carry subject as free text so this
 * does not change any existing behaviour, it only gives the Registrar a place to
 * register the school's official subject list.
 */
@Entity
@Table(name = "subjects", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 20)
    private String code;
}
