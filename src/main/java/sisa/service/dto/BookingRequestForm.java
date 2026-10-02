package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class BookingRequestForm {
    private Long resourceId;
    private LocalDate bookingDate;
    private int periodNumber;
    private String purpose;
}
