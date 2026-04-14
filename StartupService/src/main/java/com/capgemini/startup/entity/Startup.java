package com.capgemini.startup.entity;

import com.capgemini.startup.enums.StartupStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "startups")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Startup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String industry;

    @Column(columnDefinition = "TEXT")
    private String problemStatement;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal fundingGoal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StartupStage stage;

    private String location;

    @Column(nullable = false)
    private Long founderId;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isApproved = false;

    @Column(columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean isRejected = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
