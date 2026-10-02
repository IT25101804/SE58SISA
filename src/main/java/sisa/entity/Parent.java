package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Parent ID format: P2600001. Auto-generated alongside a Student registration. */
@Entity
@Table(name = "parents")
@Getter
@Setter
@NoArgsConstructor
public class Parent {

    @Id
    @Column(length = 20)
    private String parentId; // P2600001

    @OneToOne
    @JoinColumn(name = "user_id", referencedColumnName = "userId", nullable = false, unique = true)
    private User user;

    private String relationshipToStudent; // Mother / Father / Guardian
    private String contactNumber;
}
