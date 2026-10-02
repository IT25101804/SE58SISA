package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * School Resources & Facilities Management module (System Functions doc, Student:
 * "Search library resources"; Registrar: catalog upkeep). One row per title/copy
 * group — availableCopies tracks how many of totalCopies are not currently on loan.
 */
@Entity
@Table(name = "library_items")
@Getter
@Setter
@NoArgsConstructor
public class LibraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 150)
    private String author;

    @Column(length = 100)
    private String category;

    @Column(nullable = false)
    private int totalCopies = 1;

    @Column(nullable = false)
    private int availableCopies = 1;
}
