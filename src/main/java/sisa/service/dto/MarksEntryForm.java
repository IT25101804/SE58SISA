package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

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
        private String fullName;
        private String marksObtained;
    }
}
