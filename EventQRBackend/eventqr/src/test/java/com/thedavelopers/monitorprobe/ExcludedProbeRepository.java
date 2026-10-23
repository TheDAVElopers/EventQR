package com.thedavelopers.monitorprobe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExcludedProbeRepository extends JpaRepository<ExcludedProbe, UUID> {

    List<ExcludedProbe> findByTokenHash(String tokenHash);
}
