package com.lifelog.domain.company.repository;

import com.lifelog.domain.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByNameInOrderByIdAsc(Collection<String> names);
}
