package com.kcic.connect.employee.infrastructure;

import com.kcic.connect.employee.domain.Employee;
import com.kcic.connect.employee.domain.EmploymentStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    @Query("select e from Employee e where lower(e.nip) = lower(:nip)")
    Optional<Employee> findByNipIgnoreCase(@Param("nip") String nip);

    @Query("select e from Employee e where lower(e.nip) in :nips")
    List<Employee> findByNipLowerIn(@Param("nips") Collection<String> lowerCaseNips);

    @Query("select e from Employee e where lower(e.email) in :emails")
    List<Employee> findByEmailLowerIn(@Param("emails") Collection<String> lowerCaseEmails);

    @Query("select (count(e) > 0) from Employee e where lower(e.nip) = lower(:nip)")
    boolean existsByNipIgnoreCase(@Param("nip") String nip);

    @Query("select (count(e) > 0) from Employee e where lower(e.nip) = lower(:nip) and e.id <> :id")
    boolean existsByNipIgnoreCaseAndIdNot(@Param("nip") String nip, @Param("id") UUID id);

    @Query("select (count(e) > 0) from Employee e where lower(e.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("select (count(e) > 0) from Employee e where lower(e.email) = lower(:email) and e.id <> :id")
    boolean existsByEmailIgnoreCaseAndIdNot(@Param("email") String email, @Param("id") UUID id);

    @Query("""
            select e from Employee e
            where (:keyword = '' or lower(e.nip) like lower(concat('%', :keyword, '%'))
                   or lower(e.fullName) like lower(concat('%', :keyword, '%'))
                   or lower(e.email) like lower(concat('%', :keyword, '%')))
              and (:status is null or e.employmentStatus = :status)
              and (:linked is null
                   or (:linked = true and e.id in (select u.employeeId from User u
                                                   where u.employeeId is not null))
                   or (:linked = false and e.id not in (select u.employeeId from User u
                                                        where u.employeeId is not null)))
            """)
    Page<Employee> search(@Param("keyword") String keyword,
                          @Param("status") EmploymentStatus status,
                          @Param("linked") Boolean linked,
                          Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Employee e where e.id = :id")
    Optional<Employee> findByIdForUpdate(@Param("id") UUID id);
}
