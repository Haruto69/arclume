package com.arclume.api.service;

import com.arclume.api.domain.StudentProgram;
import com.arclume.api.dto.StudentProgramResponse;
import com.arclume.api.dto.StudentProgramSearchCriteria;
import com.arclume.api.repository.StudentProgramRepository;
import com.arclume.api.repository.StudentProgramSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentProgramService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StudentProgramRepository studentProgramRepository;

    public StudentProgramService(StudentProgramRepository studentProgramRepository) {
        this.studentProgramRepository = studentProgramRepository;
    }

    @Transactional(readOnly = true)
    public Page<StudentProgramResponse> search(StudentProgramSearchCriteria criteria, int page, int size) {
        validate(criteria, page, size);

        Boolean active = criteria.active() == null ? Boolean.TRUE : criteria.active();
        Specification<StudentProgram> specification = Specification.allOf(
                StudentProgramSpecifications.hasKeyword(criteria.keyword()),
                StudentProgramSpecifications.hasCompany(criteria.company()),
                StudentProgramSpecifications.hasProgramType(criteria.programType()),
                StudentProgramSpecifications.hasMode(criteria.mode()),
                StudentProgramSpecifications.hasBenefitType(criteria.benefitType()),
                StudentProgramSpecifications.hasRegion(criteria.region()),
                StudentProgramSpecifications.hasCountry(criteria.country()),
                StudentProgramSpecifications.hasAlwaysOpenStatus(criteria.alwaysOpen()),
                StudentProgramSpecifications.hasActiveStatus(active),
                StudentProgramSpecifications.deadlineOnOrBefore(criteria.applicationDeadlineBefore()),
                StudentProgramSpecifications.deadlineOnOrAfter(criteria.applicationDeadlineAfter())
        );

        Sort sort = Sort.by(
                Sort.Order.desc("alwaysOpen"),
                Sort.Order.asc("applicationDeadline").nullsLast(),
                Sort.Order.asc("title"),
                Sort.Order.asc("id")
        );
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), sort);
        return studentProgramRepository.findAll(specification, pageable).map(StudentProgramResponse::from);
    }

    private void validate(StudentProgramSearchCriteria criteria, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size < 1) {
            throw new IllegalArgumentException("Size must be at least 1");
        }
        if (criteria.applicationDeadlineAfter() != null
                && criteria.applicationDeadlineBefore() != null
                && criteria.applicationDeadlineAfter().isAfter(criteria.applicationDeadlineBefore())) {
            throw new IllegalArgumentException("Application deadline range is invalid");
        }
    }
}
