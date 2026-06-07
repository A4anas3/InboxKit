package com.gridapp.repository;

import com.gridapp.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE User u SET u.tilesOwned = u.tilesOwned + 1 WHERE u.id = :userId")
    int incrementTilesOwned(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE User u SET u.tilesOwned = GREATEST(u.tilesOwned - 1, 0) WHERE u.id = :userId")
    int decrementTilesOwned(@Param("userId") UUID userId);
}
