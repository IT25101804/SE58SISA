package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A bookable room, lab, or piece of equipment (report FR-13, section 6.2). The
 * Registrar assigns classrooms and keeps this catalog up to date; the Principal can
 * also manage it (business rule 2) — see ResourceCatalogController, scoped to
 * /registrar/** which SecurityConfig already restricts to PRINCIPAL/REGISTRAR.
 * A CLASSROOM-typed entry here is not just a catalog row — it's a real class of the
 * school (its name is what "Class" means at student registration, and its capacity
 * caps how many active students can be enrolled in it); see StudentRegistrationService.
 */
@Entity
@Table(name = "resources")
@Getter
@Setter
@NoArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResourceType type;

    /** Seats/head-count; not meaningful for a single piece of EQUIPMENT — leave null. */
    private Integer capacity;

    @Column(length = 100)
    private String location;

    /**
     * When true, a booking request for this resource is granted immediately instead of
     * waiting in the Principal's approval queue (business rule 2's "some resource types
     * can be marked auto-approve to skip this step" — set per-resource by whoever manages
     * the catalog, Principal or Registrar).
     */
    @Column(nullable = false)
    private boolean autoApprove = false;

    /**
     * School Resources & Facilities Management (System Functions doc, Student: "Book
     * a study room, if allowed"): Student accounts may only book resources flagged
     * true here — set per-resource by the Registrar/Principal on the catalog page.
     * Staff (Teacher/Registrar/Principal) may still book any resource regardless of
     * this flag, unchanged.
     */
    @Column(nullable = false)
    private boolean studentBookable = false;
}
