package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** One exam's whole roster of marks, submitted/edited in a single POST — mirrors AttendanceMarkForm. */
@Getter
@Setter
@NoArgsConstructor
public class MarksEntryForm {

    private List<Entry> entries = new ArrayList<>();

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Entry {
        private String studentId;
        private String fullName; // display-only, carried through the round trip for the template
        private String marksObtained; // string so a blank field doesn't fail binding; parsed on save
    }
}
