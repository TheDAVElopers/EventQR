package com.thedavelopers.monitorprobe;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RedactedProbeRepository extends JpaRepository<RedactedProbe, UUID> {

    List<RedactedProbe> findByName(String name);

    boolean existsByName(String name);
}
