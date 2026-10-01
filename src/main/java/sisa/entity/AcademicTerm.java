package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Academic Management module (System Functions doc, Registrar: "Manage academic
 * calendar/terms"). Exactly one term may be flagged current at a time — enforced in
 * RegistrarAcademicSetupController#setCurrent, which clears any other current flag
 * in the same transaction.
 */
@Entity
@Table(name = "academic_terms")
@Getter
@Setter
@NoArgsConstructor
public class AcademicTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name; // e.g. "Term 1, 2026"

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    // Column renamed to "is_current" — "current" is a reserved keyword in SQL Server's
    // T-SQL dialect and breaks CREATE TABLE there; the Java field/getter/setter name is
    // unchanged, so no other code needed to change.
    @Column(name = "is_current", nullable = false)
    private boolean current = false;
}
