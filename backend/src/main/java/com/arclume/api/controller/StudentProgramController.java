package com.arclume.api.controller;

import com.arclume.api.domain.BenefitType;
import com.arclume.api.domain.StudentProgramMode;
import com.arclume.api.domain.StudentProgramType;
import com.arclume.api.dto.StudentProgramResponse;
import com.arclume.api.dto.StudentProgramSearchCriteria;
import com.arclume.api.service.StudentProgramService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/student-programs")
public class StudentProgramController {

    private final StudentProgramService studentProgramService;

    public StudentProgramController(StudentProgramService studentProgramService) {
        this.studentProgramService = studentProgramService;
    }

    @GetMapping
    public ResponseEntity<Page<StudentProgramResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String company,
            @RequestParam(required = false) StudentProgramType programType,
            @RequestParam(required = false) StudentProgramMode mode,
            @RequestParam(required = false) BenefitType benefitType,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) Boolean alwaysOpen,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) LocalDate applicationDeadlineBefore,
            @RequestParam(required = false) LocalDate applicationDeadlineAfter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        StudentProgramSearchCriteria criteria = new StudentProgramSearchCriteria(
                keyword,
                company,
                programType,
                mode,
                benefitType,
                region,
                country,
                alwaysOpen,
                active,
                applicationDeadlineBefore,
                applicationDeadlineAfter
        );
        return ResponseEntity.ok(studentProgramService.search(criteria, page, size));
    }
}
