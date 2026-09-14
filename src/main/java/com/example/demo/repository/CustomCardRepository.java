package com.example.demo.repository;

import com.example.demo.domain.model.CustomCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface CustomCardRepository extends JpaRepository<CustomCard, Integer> {

    List<CustomCard> findByCardIdIn(Collection<Integer> ids);
}
