package com.arclume.api.repository;

import com.arclume.api.domain.EmploymentType;
import com.arclume.api.domain.Job;
import com.arclume.api.domain.WorkMode;
import org.springframework.data.jpa.domain.Specification;

public class JobSpecifications {

    public static Specification<Job> hasTitle(String title) {
        return (root, query, cb) -> title == null || title.trim().isEmpty() ? null :
                cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%");
    }

    public static Specification<Job> hasCompany(String company) {
        return (root, query, cb) -> company == null || company.trim().isEmpty() ? null :
                cb.like(cb.lower(root.get("company")), "%" + company.toLowerCase() + "%");
    }

    public static Specification<Job> hasLocation(String location) {
        return (root, query, cb) -> location == null || location.trim().isEmpty() ? null :
                cb.like(cb.lower(root.get("location")), "%" + location.toLowerCase() + "%");
    }

    public static Specification<Job> hasWorkMode(WorkMode workMode) {
        return (root, query, cb) -> workMode == null ? null :
                cb.equal(root.get("workMode"), workMode);
    }

    public static Specification<Job> hasEmploymentType(EmploymentType employmentType) {
        return (root, query, cb) -> employmentType == null ? null :
                cb.equal(root.get("employmentType"), employmentType);
    }
}
