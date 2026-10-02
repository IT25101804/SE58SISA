package sisa.service.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ResourceForm {
    private Long id; // null when creating
    private String name;
    private String type; // ROOM / LAB / EQUIPMENT
    private Integer capacity;
    private String location;
    private boolean autoApprove;
}
