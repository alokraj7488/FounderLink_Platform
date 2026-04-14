package com.capgemini.team.feign;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDTO {

    private Long id;
    private String name;
    private String email;
    private String role;
    private String bio;
    private String skills;
    private String experience;
    private String portfolioLinks;
}
