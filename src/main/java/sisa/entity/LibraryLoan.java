package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One student's borrow of one LibraryItem (System Functions doc, Parent: "View
 * library due dates for their child"). Issued/returned by the Registrar via
 * LibraryController; availableCopies on the linked LibraryItem is kept in sync in
 * LibraryController's @Transactional issue/return handlers.
 */
@Entity
@Table(name = "library_loans")
@Getter
@Setter
@NoArgsConstructor
public class LibraryLoan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "library_item_id", nullable = false)
    private LibraryItem libraryItem;

    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id", referencedColumnName = "studentId", nullable = false)
    private Student student;

    @Column(nullable = false)
    private LocalDate borrowedAt = LocalDate.now();

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false)
    private boolean returned = false;

    private LocalDate returnedAt;
}
