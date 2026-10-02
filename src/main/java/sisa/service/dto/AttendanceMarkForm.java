package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** One whole class roster's worth of statuses, submitted/edited in a single POST. */
@Getter
@Setter
@NoArgsConstructor
public class AttendanceMarkForm {

    private List<Entry> entries = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Entry {
        private String studentId;
        private String fullName; // display-only, carried through the round trip for the template
        private String status = "PRESENT";
    }
}
