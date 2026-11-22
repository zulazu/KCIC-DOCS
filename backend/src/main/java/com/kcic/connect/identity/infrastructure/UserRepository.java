package com.kcic.connect.identity.infrastructure;

import com.kcic.connect.identity.domain.AccountStatus;
import com.kcic.connect.identity.domain.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    @Query("""
            select u from User u
            where lower(u.username) = lower(:identifier)
               or lower(u.email) = lower(:identifier)
            """)
    Optional<User> findByLoginIdentifier(@Param("identifier") String identifier);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("select (count(u) > 0) from User u where lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    @Query("select (count(u) > 0) from User u where lower(u.email) = lower(:email) and u.id <> :id")
    boolean existsByEmailIgnoreCaseAndIdNot(@Param("email") String email, @Param("id") UUID id);

    @Query("""
            select u from User u
            where u.accountStatus = :status
              and (:keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%'))
                   or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<User> searchByStatus(@Param("keyword") String keyword,
                              @Param("status") AccountStatus status,
                              Pageable pageable);

    @Query("""
            select u from User u
            where (:keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%'))
                   or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<User> searchAll(@Param("keyword") String keyword, Pageable pageable);

    long countByAccountStatus(AccountStatus status);

    Optional<User> findByEmployeeId(UUID employeeId);

    boolean existsByEmployeeId(UUID employeeId);

    List<User> findByEmployeeIdIn(Collection<UUID> employeeIds);

    @Query("""
            select u from User u
            where u.employeeId is null
              and (:keyword = '' or lower(u.username) like lower(concat('%', :keyword, '%'))
                   or lower(u.displayName) like lower(concat('%', :keyword, '%'))
                   or lower(coalesce(u.email, '')) like lower(concat('%', :keyword, '%')))
            """)
    Page<User> searchUnlinked(@Param("keyword") String keyword, Pageable pageable);

    @Query("""
            select u.employeeId from User u
            where u.employeeId in :employeeIds
            """)
    List<UUID> findLinkedEmployeeIds(@Param("employeeIds") Collection<UUID> employeeIds);

    @Query("""
            select u.id from User u
            where lower(u.username) like lower(concat('%', :keyword, '%'))
               or lower(u.displayName) like lower(concat('%', :keyword, '%'))
            """)
    List<UUID> findIdsByActorKeyword(@Param("keyword") String keyword);
}
