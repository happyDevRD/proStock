package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.AccAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccAccountRepository extends JpaRepository<AccAccount, Long> {

    List<AccAccount> findByActiveTrue();

    Optional<AccAccount> findByCode(String code);

    List<AccAccount> findByAllowsTransactionsTrueAndActiveTrue();
}
