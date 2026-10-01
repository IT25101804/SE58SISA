package sisa.service;

import sisa.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Year;

/**
 * Generates the school's ID formats (report section 6.2 / 6.3):
 *   Student   -> S2600001   (S + admission year + 5-digit sequence)
 *   Teacher   -> T2600001   (T + joining year + 5-digit sequence)
 *   Parent    -> P2600001   (P + joining year + 5-digit sequence)
 *   Registrar -> R2600001   (R + joining year + 5-digit sequence)
 */
@Service
public class IdGeneratorService {

    private final UserRepository userRepository;

    public IdGeneratorService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String nextId(char prefixLetter) {
        String yy = String.valueOf(Year.now().getValue()).substring(2); // "26"
        String prefix = prefixLetter + yy;
        long countSoFar = userRepository.countByUserIdStartingWith(prefix);
        long next = countSoFar + 1;
        return String.format("%s%05d", prefix, next);
    }

    public String nextStudentId()   { return nextId('S'); }
    public String nextTeacherId()   { return nextId('T'); }
    public String nextParentId()    { return nextId('P'); }
    public String nextRegistrarId() { return nextId('R'); }
}
