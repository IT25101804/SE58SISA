package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Registrar ID format: R2600001. Only the Principal can create Registrar accounts. */
@Entity
@Table(name = "registrars")
@Getter
@Setter
@NoArgsConstructor
public class Registrar {

    @Id
    @Column(length = 20)
    private String registrarId; // R2600001

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String office; // e.g. Front Office, Admissions
}
