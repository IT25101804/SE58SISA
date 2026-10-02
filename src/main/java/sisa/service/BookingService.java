package sisa.service;

import sisa.entity.BookingStatus;
import sisa.entity.Resource;
import sisa.entity.ResourceBooking;
import sisa.entity.ResourceType;
import sisa.entity.StudentStatus;
import sisa.entity.TimetableSlot;
import sisa.repository.ResourceBookingRepository;
import sisa.repository.ResourceRepository;
import sisa.repository.StudentRepository;
import sisa.repository.TimetableSlotRepository;
import sisa.service.dto.BookingRequestForm;
import sisa.service.dto.ResourceForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * School Resources & Facilities Management (report FR-13). Create/approve/reject
 * booking requests, and build the availability grid — see BookingConflictChecker for
 * the actual double-booking rule (business rule 3) shared with Module 4's timetable
 * (business rule 4).
 */
@Service
public class BookingService {

    private final ResourceRepository resourceRepository;
    private final ResourceBookingRepository resourceBookingRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final StudentRepository studentRepository;
    private final BookingConflictChecker conflictChecker;

    public BookingService(ResourceRepository resourceRepository, ResourceBookingRepository resourceBookingRepository,
                           TimetableSlotRepository timetableSlotRepository, StudentRepository studentRepository,
                           BookingConflictChecker conflictChecker) {
        this.resourceRepository = resourceRepository;
        this.resourceBookingRepository = resourceBookingRepository;
        this.timetableSlotRepository = timetableSlotRepository;
        this.studentRepository = studentRepository;
        this.conflictChecker = conflictChecker;
    }

    // ---------- catalog (Principal/Registrar, business rule 2) ----------

    public List<Resource> allResources() {
        return resourceRepository.findAllByOrderByTypeAscNameAsc();
    }

    public List<Resource> resourcesByType(ResourceType type) {
        return type == null ? allResources() : resourceRepository.findByTypeOrderByNameAsc(type);
    }

    /** School Resources & Facilities Management (System Functions doc, Student: "Book a study room, if allowed"). */
    public List<Resource> studentBookableResources() {
        return allResources().stream().filter(Resource::isStudentBookable).toList();
    }

    /**
     * Live active-enrollment headcount for every catalogued CLASSROOM, keyed by class
     * name — lets the Labs & Classrooms Availability page show each class's real
     * occupancy (not just its capacity), the same figure StudentRegistrationService
     * enforces at registration.
     */
    public Map<String, Long> classroomEnrollmentCounts() {
        return resourceRepository.findByTypeOrderByNameAsc(ResourceType.CLASSROOM).stream()
                .collect(Collectors.toMap(Resource::getName,
                        r -> studentRepository.countByClassNameIgnoreCaseAndStatus(r.getName(), StudentStatus.ACTIVE)));
    }

    @Transactional
    public Resource saveResource(ResourceForm form) {
        return saveResource(form, false);
    }

    @Transactional
    public Resource saveResource(ResourceForm form, boolean studentBookable) {
        Resource resource = form.getId() != null
                ? resourceRepository.findById(form.getId()).orElseThrow(() -> new IllegalArgumentException("No such resource: " + form.getId()))
                : new Resource();
        ResourceType type = ResourceType.valueOf(form.getType());
        // A CLASSROOM's name is what students get registered into (see StudentRegistrationService)
        // — two classes with the same name would make that lookup ambiguous, so names must be unique
        // among classrooms (unrelated to ROOM/LAB/EQUIPMENT, which were never uniqueness-checked).
        if (type == ResourceType.CLASSROOM) {
            resourceRepository.findByTypeAndNameIgnoreCase(ResourceType.CLASSROOM, form.getName())
                    .filter(duplicate -> !duplicate.getId().equals(resource.getId()))
                    .ifPresent(duplicate -> {
                        throw new IllegalArgumentException("A class named \"" + form.getName() + "\" already exists.");
                    });
        }
        resource.setName(form.getName());
        resource.setType(type);
        resource.setCapacity(form.getCapacity());
        resource.setLocation(form.getLocation());
        resource.setAutoApprove(form.isAutoApprove());
        resource.setStudentBookable(studentBookable);
        return resourceRepository.save(resource);
    }

    /**
     * Deletes a catalog entry (Principal/Registrar, business rule 2). Refused — rather
     * than silently orphaning records or failing on a raw FK-constraint error — when the
     * resource still has booking history or a timetable slot pointing at it; the caller
     * (ResourceCatalogController) surfaces the message as a normal inline form error.
     * Returns the deleted resource's name for the confirmation message.
     */
    @Transactional
    public String deleteResource(Long resourceId) {
        Resource resource = resourceRepository.findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException("No such resource: " + resourceId));

        if (resource.getType() == ResourceType.CLASSROOM) {
            long activeStudents = studentRepository.countByClassNameIgnoreCaseAndStatus(resource.getName(), StudentStatus.ACTIVE);
            if (activeStudents > 0) {
                throw new IllegalArgumentException(resource.getName() + " can't be deleted — it still has "
                        + activeStudents + " active student(s) enrolled. Move or transfer them to another class first.");
            }
        }

        long bookingCount = resourceBookingRepository.countByResource_Id(resourceId);
        if (bookingCount > 0) {
            throw new IllegalArgumentException(resource.getName() + " can't be deleted — it has " + bookingCount
                    + " booking record(s) tied to it. Cancel or clear those first.");
        }
        if (timetableSlotRepository.existsByRoom_Id(resourceId)) {
            throw new IllegalArgumentException(resource.getName()
                    + " can't be deleted — it's still assigned as a room on the timetable. Reassign or clear those slots first.");
        }

        String name = resource.getName();
        resourceRepository.delete(resource);
        return name;
    }

    // ---------- booking requests (staff, business rule 1) ----------

    /**
     * The double-booking rule (business rule 3): rejects with a message naming the
     * existing booking/slot if this resource is already taken for this date+period.
     * Auto-approve resources (business rule 2) skip straight to APPROVED.
     */
    @Transactional
    public ResourceBooking requestBooking(BookingRequestForm form, String requestingUserId) {
        Resource resource = resourceRepository.findById(form.getResourceId())
                .orElseThrow(() -> new IllegalArgumentException("No such resource: " + form.getResourceId()));

        conflictChecker.conflictForBooking(resource.getId(), form.getBookingDate(), form.getPeriodNumber(), null)
                .ifPresent(conflict -> {
                    throw new IllegalArgumentException(resource.getName() + " on " + form.getBookingDate() + " period "
                            + form.getPeriodNumber() + ": " + conflict.describe());
                });

        ResourceBooking booking = new ResourceBooking();
        booking.setResource(resource);
        booking.setBookedByUserId(requestingUserId);
        booking.setBookingDate(form.getBookingDate());
        booking.setPeriodNumber(form.getPeriodNumber());
        booking.setPurpose(form.getPurpose());
        booking.setRequestedAt(LocalDateTime.now());

        if (resource.isAutoApprove()) {
            booking.setStatus(BookingStatus.APPROVED);
            booking.setDecidedBy("SYSTEM (auto-approve)");
            booking.setDecidedAt(LocalDateTime.now());
        } else {
            booking.setStatus(BookingStatus.REQUESTED);
        }
        return resourceBookingRepository.save(booking);
    }

    // ---------- approvals (Principal, business rule 2) ----------

    public List<ResourceBooking> pendingApprovals() {
        return resourceBookingRepository.findByStatusOrderByRequestedAtAsc(BookingStatus.REQUESTED);
    }

    @Transactional
    public ResourceBooking approve(Long bookingId, String principalUserId) {
        ResourceBooking booking = resourceBookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("No such booking: " + bookingId));
        booking.setStatus(BookingStatus.APPROVED);
        booking.setDecidedBy(principalUserId);
        booking.setDecidedAt(LocalDateTime.now());
        return resourceBookingRepository.save(booking);
    }

    @Transactional
    public ResourceBooking reject(Long bookingId, String principalUserId) {
        ResourceBooking booking = resourceBookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("No such booking: " + bookingId));
        booking.setStatus(BookingStatus.REJECTED);
        booking.setDecidedBy(principalUserId);
        booking.setDecidedAt(LocalDateTime.now());
        return resourceBookingRepository.save(booking);
    }

    // ---------- availability grid (report FR-13: "resource rows x period columns") ----------

    public enum CellState {
        FREE, REQUESTED, BOOKED, TIMETABLED;

        /** Reuses the existing .status colour classes (report FR-13: "colour-coded free/booked"). */
        public String cssClass() {
            return switch (this) {
                case FREE -> "approved";       // green
                case REQUESTED -> "pending";   // orange
                case BOOKED -> "rejected";     // red
                case TIMETABLED -> "disabled"; // grey
            };
        }
    }

    public record Cell(CellState state, String label) {}

    /**
     * resource -> period -> cell state, for one date. A period shows BOOKED/REQUESTED if
     * an active ResourceBooking covers it, or TIMETABLED if a recurring TimetableSlot
     * uses that room on the matching day-of-week (business rule 4) — otherwise FREE.
     */
    public Map<Resource, Map<Integer, Cell>> availabilityGrid(LocalDate date, ResourceType type) {
        List<Resource> resources = resourcesByType(type);
        List<ResourceBooking> activeBookings = resourceBookingRepository.findByBookingDateAndStatusNot(date, BookingStatus.REJECTED);
        DayOfWeek dayOfWeek = date.getDayOfWeek();

        Map<Resource, Map<Integer, Cell>> grid = new LinkedHashMap<>();
        for (Resource resource : resources) {
            Map<Integer, Cell> row = new LinkedHashMap<>();
            for (int period = 1; period <= TimetableService.MAX_PERIODS; period++) {
                row.put(period, cellFor(resource, period, activeBookings, dayOfWeek));
            }
            grid.put(resource, row);
        }
        return grid;
    }

    private Cell cellFor(Resource resource, int period, List<ResourceBooking> activeBookings, DayOfWeek dayOfWeek) {
        for (ResourceBooking booking : activeBookings) {
            if (booking.getResource().getId().equals(resource.getId()) && booking.getPeriodNumber() == period) {
                return booking.getStatus() == BookingStatus.APPROVED
                        ? new Cell(CellState.BOOKED, "Booked — " + booking.getPurpose())
                        : new Cell(CellState.REQUESTED, "Requested — " + booking.getPurpose());
            }
        }
        List<TimetableSlot> timetableHit = timetableSlotRepository.findByRoom_IdAndDayOfWeekAndPeriodNumber(resource.getId(), dayOfWeek, period);
        if (!timetableHit.isEmpty()) {
            TimetableSlot slot = timetableHit.get(0);
            return new Cell(CellState.TIMETABLED, slot.getSubject() + " (" + slot.getClassName() + ")");
        }
        return new Cell(CellState.FREE, "Free");
    }
}
