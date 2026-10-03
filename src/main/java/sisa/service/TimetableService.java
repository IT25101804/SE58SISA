package sisa.service;

import sisa.entity.Resource;
import sisa.entity.Teacher;
import sisa.entity.TimetableSlot;
import sisa.repository.ResourceRepository;
import sisa.repository.TeacherRepository;
import sisa.repository.TimetableSlotRepository;
import sisa.service.dto.TimetableSlotForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TimetableService {

    public static final int MAX_PERIODS = 8;
    public static final DayOfWeek[] SCHOOL_DAYS = {
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    };

    private final TimetableSlotRepository timetableSlotRepository;
    private final TeacherRepository teacherRepository;
    private final ResourceRepository resourceRepository;
    private final TimetableConflictChecker conflictChecker;

    public TimetableService(TimetableSlotRepository timetableSlotRepository, TeacherRepository teacherRepository,
                            ResourceRepository resourceRepository, TimetableConflictChecker conflictChecker) {
        this.timetableSlotRepository = timetableSlotRepository;
        this.teacherRepository = teacherRepository;
        this.resourceRepository = resourceRepository;
        this.conflictChecker = conflictChecker;
    }

    public List<TimetableSlot> slotsForClass(String className) {
        return timetableSlotRepository.findByClassNameOrderByPeriodNumberAsc(className);
    }

    public List<TimetableSlot> slotsForTeacher(String teacherId) {
        return timetableSlotRepository.findByTeacher_TeacherIdOrderByPeriodNumberAsc(teacherId);
    }

    public Map<Integer, Map<DayOfWeek, TimetableSlot>> asGrid(List<TimetableSlot> slots) {
        Map<Integer, Map<DayOfWeek, TimetableSlot>> grid = new HashMap<>();
        for (TimetableSlot slot : slots) {
            grid.computeIfAbsent(slot.getPeriodNumber(), p -> new HashMap<>()).put(slot.getDayOfWeek(), slot);
        }
        return grid;
    }

    @Transactional
    public TimetableSlot upsertSlot(TimetableSlotForm form) {
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(form.getDayOfWeek());
        TimetableSlot existingForClass = timetableSlotRepository
                .findByClassNameAndDayOfWeekAndPeriodNumber(form.getClassName(), dayOfWeek, form.getPeriodNumber())
                .orElse(null);
        return write(form, existingForClass);
    }

    @Transactional
    public TimetableSlot saveSlot(TimetableSlotForm form) {
        if (form.getDayOfWeek() == null || form.getDayOfWeek().isBlank()) {
            throw new IllegalArgumentException("Choose a day.");
        }
        if (form.getTeacherId() == null || form.getTeacherId().isBlank()) {
            throw new IllegalArgumentException("Choose a teacher.");
        }
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(form.getDayOfWeek());
        Teacher teacher = teacherRepository.findById(form.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("No such teacher: " + form.getTeacherId()));
        if (teacher.getSubjectSpecialty() == null || !teacher.getSubjectSpecialty().trim().equalsIgnoreCase(form.getSubject().trim())) {
            throw new IllegalArgumentException(teacher.getUser().getFullName() + " does not teach " + form.getSubject()
                    + " — choose a teacher whose subject specialty is " + form.getSubject() + ".");
        }

        TimetableSlot target = null;
        if (form.getSlotId() != null) {
            target = timetableSlotRepository.findById(form.getSlotId())
                    .filter(s -> s.getClassName().equals(form.getClassName()))
                    .orElseThrow(() -> new IllegalArgumentException("That slot no longer exists."));
        }
        Long ownId = target == null ? null : target.getId();
        timetableSlotRepository
                .findByClassNameAndDayOfWeekAndPeriodNumber(form.getClassName(), dayOfWeek, form.getPeriodNumber())
                .filter(taken -> !taken.getId().equals(ownId))
                .ifPresent(taken -> {
                    throw new IllegalArgumentException(form.getClassName() + " already has " + taken.getSubject() + " on "
                            + dayOfWeek + " period " + form.getPeriodNumber()
                            + " — use Edit or Delete on that slot instead.");
                });
        return write(form, target);
    }

    private TimetableSlot write(TimetableSlotForm form, TimetableSlot existing) {
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(form.getDayOfWeek());
        Teacher teacher = teacherRepository.findById(form.getTeacherId())
                .orElseThrow(() -> new IllegalArgumentException("No such teacher: " + form.getTeacherId()));
        Long excludingId = existing == null ? null : existing.getId();

        conflictChecker.conflictFor(teacher.getTeacherId(), dayOfWeek, form.getPeriodNumber(), excludingId)
                .ifPresent(conflict -> {
                    throw new IllegalArgumentException(
                            teacher.getUser().getFullName() + " is already teaching " + conflict.getClassName()
                                    + " (" + conflict.getSubject() + ") on " + dayOfWeek + " period " + form.getPeriodNumber()
                                    + " — choose a different period or teacher.");
                });

        Resource room = null;
        if (form.getRoomResourceId() != null) {
            Resource resolvedRoom = resourceRepository.findById(form.getRoomResourceId())
                    .orElseThrow(() -> new IllegalArgumentException("No such resource: " + form.getRoomResourceId()));

            conflictChecker.roomConflictFor(resolvedRoom.getId(), dayOfWeek, form.getPeriodNumber(), excludingId)
                    .ifPresent(conflict -> {
                        throw new IllegalArgumentException(resolvedRoom.getName() + " on " + dayOfWeek + " period "
                                + form.getPeriodNumber() + ": " + conflict.describe());
                    });
            room = resolvedRoom;
        }

        TimetableSlot slot = existing != null ? existing : new TimetableSlot();
        slot.setClassName(form.getClassName());
        slot.setSubject(form.getSubject());
        slot.setTeacher(teacher);
        slot.setDayOfWeek(dayOfWeek);
        slot.setPeriodNumber(form.getPeriodNumber());
        slot.setRoom(room);
        return timetableSlotRepository.save(slot);
    }

    @Transactional
    public void deleteSlot(Long id) {
        timetableSlotRepository.deleteById(id);
    }
}
