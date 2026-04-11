package com.mysite.sbb.user;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserLoginLogRepository extends JpaRepository<UserLoginLog, Long> {

	@Query("""
			select count(distinct log.user.id)
			from UserLoginLog log
			where log.loggedAt >= :start and log.loggedAt < :end
			""")
	long countDistinctUsersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
