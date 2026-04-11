package com.mysite.sbb.user;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<SiteUser, Long> {

	Optional<SiteUser> findByUsername(String username);

	Page<SiteUser> findByUsernameContainingIgnoreCase(String username, Pageable pageable);

	@Query("""
			select count(u)
			from SiteUser u
			where lower(trim(case
				when u.race is null or length(trim(u.race)) = 0 then :defaultRace
				else u.race
			end)) in :raceValues
			""")
	long countByRaceValues(@Param("defaultRace") String defaultRace, @Param("raceValues") Collection<String> raceValues);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update SiteUser u set u.race = :race where u.race is null or trim(u.race) = ''")
	int updateBlankRace(@Param("race") String race);
}
