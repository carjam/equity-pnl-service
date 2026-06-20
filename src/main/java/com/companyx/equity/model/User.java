package com.companyx.equity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
@Entity
@Table(name = "user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    
    private String firstName;
    private String lastName;
    
    @NotNull
    @Column(unique = true)
    private String uid;
    
    @NotNull
    private String password;
    
    @NotNull
    private String role;
    
    @Column(nullable = false)
    private boolean enabled = true;
}