package sisa.service;

import sisa.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Year;

@Service
public class IdGeneratorService {

    private final UserRepository userRepository;

    public IdGeneratorService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String nextId(char prefixLetter) {
        String yy = String.valueOf(Year.now().getValue()).substring(2);
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
