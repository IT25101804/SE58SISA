package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registrars")
@Getter
@Setter
@NoArgsConstructor
public class Registrar {

    @Id
    @Column(length = 20)
    private String registrarId;

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String office;
}
