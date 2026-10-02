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

    public List<Resource> allResources() {
        return resourceRepository.findAllByOrderByTypeAscNameAsc();
    }

    public List<Resource> resourcesByType(ResourceType type) {
        return type == null ? allResources() : resourceRepository.findByTypeOrderByNameAsc(type);
    }

    public List<Resource> studentBookableResources() {
        return allResources().stream().filter(Resource::isStudentBookable).toList();
    }

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

    public enum CellState {
        FREE, REQUESTED, BOOKED, TIMETABLED;

        public String cssClass() {
            return switch (this) {
                case FREE -> "approved";
                case REQUESTED -> "pending";
                case BOOKED -> "rejected";
                case TIMETABLED -> "disabled";
            };
        }
    }

    public record Cell(CellState state, String label) {}

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
