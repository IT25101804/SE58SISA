package sisa.service;

import jakarta.persistence.EntityManager;
import sisa.entity.AcademicTerm;
import sisa.entity.Subject;
import sisa.repository.AcademicTermRepository;
import sisa.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Updates for the Principal's Subjects & Terms page. Exams, timetable periods, assignments
 * and teacher specialties store a subject by its name, so renaming a subject renames it in
 * all of those too, in the same transaction.
 */
@Service
public class AcademicSetupService {

    private final SubjectRepository subjectRepository;
    private final AcademicTermRepository academicTermRepository;
    private final EntityManager entityManager;

    public AcademicSetupService(SubjectRepository subjectRepository, AcademicTermRepository academicTermRepository,
                                EntityManager entityManager) {
        this.subjectRepository = subjectRepository;
        this.academicTermRepository = academicTermRepository;
        this.entityManager = entityManager;
    }

    /** Returns how many exams, timetable periods, assignments and teachers were moved to the new name. */
    @Transactional
    public int updateSubject(Long id, String name, String code) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such subject."));
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Subject name can't be empty.");
        }
        String newName = name.trim();
        String oldName = subject.getName();
        if (!newName.equalsIgnoreCase(oldName) && subjectRepository.existsByNameIgnoreCase(newName)) {
            throw new IllegalArgumentException("\"" + newName + "\" is already registered.");
        }

        subject.setName(newName);
        subject.setCode(code != null && !code.isBlank() ? code.trim() : null);
        subjectRepository.saveAndFlush(subject);

        if (newName.equals(oldName)) return 0;
        int moved = 0;
        for (String jpql : new String[]{
                "update Exam e set e.subject = :newName where e.subject = :oldName",
                "update TimetableSlot t set t.subject = :newName where t.subject = :oldName",
                "update Assignment a set a.subject = :newName where a.subject = :oldName",
                "update Teacher t set t.subjectSpecialty = :newName where t.subjectSpecialty = :oldName"}) {
            moved += entityManager.createQuery(jpql)
                    .setParameter("newName", newName)
                    .setParameter("oldName", oldName)
                    .executeUpdate();
        }
        return moved;
    }

    @Transactional
    public AcademicTerm updateTerm(Long id, String name, String startDate, String endDate) {
        AcademicTerm term = academicTermRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such term."));
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Term name can't be empty.");
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);
        if (end.isBefore(start)) throw new IllegalArgumentException("The end date must be after the start date.");
        term.setName(name.trim());
        term.setStartDate(start);
        term.setEndDate(end);
        return academicTermRepository.saveAndFlush(term);
    }
}
