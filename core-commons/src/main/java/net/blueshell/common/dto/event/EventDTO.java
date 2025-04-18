package net.blueshell.common.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.security.Timestamp;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class EventDTO extends BaseDTO {
    private String id;
    private String title;
    private String description;
    private String location;
    private Timestamp startTime;
    private Timestamp endTime;
    private double memberPrice;
    private double publicPrice;
    private String googleId;
    private boolean visible;
    private boolean membersOnly;
    private boolean signUp;
    private List<SignUpFormDTO> signUpForm;
    private int lastEditor;
    private int committee;
    private String banner; // To validate if String
    private List<String> feedbacks;
}
