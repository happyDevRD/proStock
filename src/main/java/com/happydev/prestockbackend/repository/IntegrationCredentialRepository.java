package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.IntegrationCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationCredentialRepository extends JpaRepository<IntegrationCredential, Long> {

    List<IntegrationCredential> findByProvider(String provider);

    Optional<IntegrationCredential> findByProviderAndCredentialKey(String provider, String credentialKey);

    void deleteByProviderAndCredentialKey(String provider, String credentialKey);
}
