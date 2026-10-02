package sisa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Communication module (System Functions doc, Student: "Participate in class
 * discussions"). One shared board per class name — any Student in the class, or
 * that class's Teacher/Class Teacher, may post; see ClassDiscussionController for
 * the exact access rule.
 */
@Entity
@Table(name = "class_discussion_posts")
@Getter
@Setter
@NoArgsConstructor
public class ClassDiscussionPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String className;

    @Column(nullable = false, length = 20)
    private String authorUserId;

    @Column(nullable = false, length = 100)
    private String authorName;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private LocalDateTime postedAt = LocalDateTime.now();
}
