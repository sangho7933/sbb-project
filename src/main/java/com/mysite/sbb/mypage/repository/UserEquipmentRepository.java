package com.mysite.sbb.mypage.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mysite.sbb.mypage.entity.UserEquipment;
import com.mysite.sbb.user.SiteUser;

public interface UserEquipmentRepository extends JpaRepository<UserEquipment, Long> {

	List<UserEquipment> findByUserOrderBySlotCodeAsc(SiteUser user);

	Optional<UserEquipment> findByUserAndSlotCode(SiteUser user, String slotCode);

	long countByUser(SiteUser user);
}
