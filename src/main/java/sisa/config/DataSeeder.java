package sisa.config;

import sisa.entity.AcademicTerm;
import sisa.entity.AccountStatus;
import sisa.entity.LibraryItem;
import sisa.entity.LibraryLoan;
import sisa.entity.Resource;
import sisa.entity.ResourceType;
import sisa.entity.Role;
import sisa.entity.Student;
import sisa.entity.Subject;
import sisa.entity.User;
import sisa.repository.AcademicTermRepository;
import sisa.repository.LibraryItemRepository;
import sisa.repository.LibraryLoanRepository;
import sisa.repository.ResourceRepository;
import sisa.repository.StudentRepository;
import sisa.repository.SubjectRepository;
import sisa.repository.UserRepository;
import sisa.service.AccountAdminService;
import sisa.service.StudentRegistrationService;
import sisa.service.TeacherManagementService;
import sisa.service.dto.CreateAccountRequest;
import sisa.service.dto.StudentRegistrationRequest;
import sisa.service.dto.TeacherAssignmentRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountAdminService accountAdminService;
    private final TeacherManagementService teacherManagementService;
    private final StudentRegistrationService studentRegistrationService;
    private final SubjectRepository subjectRepository;
    private final AcademicTermRepository academicTermRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final LibraryLoanRepository libraryLoanRepository;
    private final StudentRepository studentRepository;
    private final ResourceRepository resourceRepository;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      AccountAdminService accountAdminService, TeacherManagementService teacherManagementService,
                      StudentRegistrationService studentRegistrationService, SubjectRepository subjectRepository,
                      AcademicTermRepository academicTermRepository, LibraryItemRepository libraryItemRepository,
                      LibraryLoanRepository libraryLoanRepository, StudentRepository studentRepository,
                      ResourceRepository resourceRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountAdminService = accountAdminService;
        this.teacherManagementService = teacherManagementService;
        this.studentRegistrationService = studentRegistrationService;
        this.subjectRepository = subjectRepository;
        this.academicTermRepository = academicTermRepository;
        this.libraryItemRepository = libraryItemRepository;
        this.libraryLoanRepository = libraryLoanRepository;
        this.studentRepository = studentRepository;
        this.resourceRepository = resourceRepository;
    }

    private record TeacherSeed(String name, String subject, boolean classTeacher, String className, double monthlySalary) {}
    private record StudentSeed(String name, String className, String gender, String dob, String guardianName) {}

    private static final TeacherSeed[] TEACHERS = {
            new TeacherSeed("Rashmika Jayawardena", "Mathematics", true, "Grade 6 - A", 1250.00),
            new TeacherSeed("Saduni Wickramasinghe", "Science", true, "Grade 7 - A", 1250.00),
            new TeacherSeed("Chamod Fernando", "English", false, null, 1100.00),
            new TeacherSeed("Harshana Peiris", "History", false, null, 1100.00),
    };

    private static final String[] REGISTRARS = {"Malinga Rodrigo", "Chandimal Senanayake"};

    private static final StudentSeed[] STUDENTS = {
            new StudentSeed("Kasun Meepalagama", "Grade 6 - A", "Male", "2015-03-11", "Sunil Meepalagama"),
            new StudentSeed("Venuwarshika Silva", "Grade 6 - A", "Female", "2015-06-22", "Chandrika Silva"),
            new StudentSeed("Dewmini Fernando", "Grade 6 - A", "Female", "2015-01-09", "Nilmini Fernando"),
            new StudentSeed("Thisarani Gunawardena", "Grade 6 - A", "Female", "2015-09-30", "Kumari Gunawardena"),
            new StudentSeed("Ushan Bandara", "Grade 6 - A", "Male", "2015-11-04", "Ranjith Bandara"),
            new StudentSeed("Sayuri Karunaratne", "Grade 7 - A", "Female", "2014-02-17", "Priyanka Karunaratne"),
            new StudentSeed("Habhishyani Dissanayake", "Grade 7 - A", "Female", "2014-05-28", "Manisha Dissanayake"),
            new StudentSeed("Thushara Wijesinghe", "Grade 7 - A", "Male", "2014-07-19", "Nimal Wijesinghe"),
            new StudentSeed("Senath Rathnayake", "Grade 7 - A", "Male", "2014-04-02", "Asela Rathnayake"),
            new StudentSeed("Vishmitha Abeysekera", "Grade 7 - A", "Female", "2014-12-15", "Dilani Abeysekera"),
    };

    @Override
    public void run(String... args) {
        User principal = seedPrincipal();
        if (principal == null) {
            return;
        }

        User firstRegistrar = null;
        for (String name : REGISTRARS) {
            User registrar = createApproved(Role.REGISTRAR, name, principal);
            if (firstRegistrar == null) firstRegistrar = registrar;
        }

        for (TeacherSeed seed : TEACHERS) {
            User teacherUser = createApproved(Role.TEACHER, seed.name(), principal);
            TeacherAssignmentRequest assignment = new TeacherAssignmentRequest();
            assignment.setSubjectSpecialty(seed.subject());
            assignment.setJoiningYear("2026");
            assignment.setClassTeacher(seed.classTeacher());
            assignment.setAssignedClassName(seed.className());
            teacherManagementService.updateAssignment(teacherUser.getUserId(), assignment, principal);
            teacherManagementService.updateSalary(teacherUser.getUserId(), seed.monthlySalary());
        }

        String firstStudentId = null;
        for (StudentSeed seed : STUDENTS) {
            String studentId = seedStudentAndGuardian(seed, firstRegistrar);
            if (firstStudentId == null) firstStudentId = studentId;
        }

        seedAcademicSetup();
        seedLibrary(firstStudentId);
        seedFacilities();

        System.out.println(">>> Seeded demo accounts for " + REGISTRARS.length + " Registrar(s), "
                + TEACHERS.length + " Teacher(s), " + STUDENTS.length + " Student(s) and their Parents — see README.md for logins.");
    }

    private void seedAcademicSetup() {
        for (String name : new String[]{"Mathematics", "Science", "English", "History"}) {
            if (!subjectRepository.existsByNameIgnoreCase(name)) {
                Subject subject = new Subject();
                subject.setName(name);
                subject.setCode(name.substring(0, Math.min(4, name.length())).toUpperCase());
                subjectRepository.save(subject);
            }
        }
        if (academicTermRepository.count() == 0) {
            AcademicTerm term = new AcademicTerm();
            term.setName("Term 1, 2026");
            term.setStartDate(LocalDate.of(2026, 1, 12));
            term.setEndDate(LocalDate.of(2026, 4, 3));
            term.setCurrent(true);
            academicTermRepository.save(term);
        }
    }

    private void seedLibrary(String firstStudentId) {
        LibraryItem algebra = new LibraryItem();
        algebra.setTitle("Introduction to Algebra");
        algebra.setAuthor("R. Jayasinghe");
        algebra.setCategory("Mathematics");
        algebra.setTotalCopies(3);
        algebra.setAvailableCopies(2);
        libraryItemRepository.save(algebra);

        LibraryItem scienceBook = new LibraryItem();
        scienceBook.setTitle("Wonders of Science");
        scienceBook.setAuthor("N. Perera");
        scienceBook.setCategory("Science");
        scienceBook.setTotalCopies(2);
        scienceBook.setAvailableCopies(2);
        libraryItemRepository.save(scienceBook);

        if (firstStudentId != null) {
            Student student = studentRepository.findById(firstStudentId).orElse(null);
            if (student != null) {
                LibraryLoan loan = new LibraryLoan();
                loan.setLibraryItem(algebra);
                loan.setStudent(student);
                loan.setBorrowedAt(LocalDate.of(2026, 9, 1));
                loan.setDueDate(LocalDate.of(2026, 9, 22));
                libraryLoanRepository.save(loan);
            }
        }
    }

    private void seedFacilities() {
        if (resourceRepository.count() > 0) {
            return;
        }
        Resource lab = new Resource();
        lab.setName("Chemistry Lab");
        lab.setType(ResourceType.LAB);
        lab.setCapacity(30);
        lab.setLocation("Science Block");
        lab.setAutoApprove(false);
        resourceRepository.save(lab);

        Resource studyRoom = new Resource();
        studyRoom.setName("Study Room 1");
        studyRoom.setType(ResourceType.ROOM);
        studyRoom.setCapacity(8);
        studyRoom.setLocation("Library, 1st floor");
        studyRoom.setAutoApprove(true);
        studyRoom.setStudentBookable(true);
        resourceRepository.save(studyRoom);

        for (String className : new String[]{"Grade 6 - A", "Grade 7 - A"}) {
            Resource classroom = new Resource();
            classroom.setName(className);
            classroom.setType(ResourceType.CLASSROOM);
            classroom.setCapacity(30);
            classroom.setLocation("Junior Block");
            classroom.setAutoApprove(true);
            resourceRepository.save(classroom);
        }
    }

    private User seedPrincipal() {
        if (userRepository.findByUsername("principal").isPresent()) {
            return null;
        }
        User principal = new User();
        principal.setUserId("PRINCIPAL");
        principal.setUsername("principal");
        principal.setPassword(passwordEncoder.encode("Principal@123"));
        principal.setFullName("Dr. Madhawa Gunasekara");
        principal.setEmail(sisa.service.UserAccountFactory.emailFor("PRINCIPAL"));
        principal.setRole(Role.PRINCIPAL);
        principal.setStatus(AccountStatus.APPROVED);
        principal.setDeletable(false);
        userRepository.save(principal);
        System.out.println(">>> Seeded permanent Principal account (username: principal / password: Principal@123)");
        return principal;
    }

    private static String emailSlug(String fullName) {
        return fullName.toLowerCase().replace(".", "").replace(" ", ".");
    }

    private User createApproved(Role role, String fullName, User createdBy) {
        CreateAccountRequest req = new CreateAccountRequest();
        req.setFullName(fullName);
        req.setEmail(emailSlug(fullName) + "@sisa.edu");
        req.setRawPassword(role == Role.REGISTRAR ? "Registrar@123" : "Teacher@123");

        User created = role == Role.REGISTRAR
                ? accountAdminService.createRegistrar(req, createdBy)
                : accountAdminService.createTeacher(req, createdBy);
        approve(created);
        return created;
    }

    private String seedStudentAndGuardian(StudentSeed seed, User registeredBy) {
        StudentRegistrationRequest req = new StudentRegistrationRequest();
        req.setFullName(seed.name());
        req.setEmail(emailSlug(seed.name()) + "@sisa.edu");
        req.setRawPassword("Student@123");
        req.setAdmissionYear("2026");
        req.setClassName(seed.className());
        req.setDateOfBirth(seed.dob());
        req.setGender(seed.gender());
        req.setAddress("Colombo, Sri Lanka");
        req.setEmergencyContact("0770000000");

        req.setGuardianFullName(seed.guardianName());
        req.setGuardianEmail(emailSlug(seed.guardianName()) + "@sisa.edu");
        req.setGuardianRawPassword("Parent@123");
        req.setRelationshipToStudent(seed.gender().equals("Male") ? "Father" : "Mother");
        req.setGuardianContact("0771000000");

        var result = studentRegistrationService.register(req, registeredBy);
        approve(userRepository.findById(result.studentId()).orElseThrow());
        approve(userRepository.findById(result.parentId()).orElseThrow());
        return result.studentId();
    }

    private void approve(User user) {
        user.setStatus(AccountStatus.APPROVED);
        userRepository.save(user);
    }
}
