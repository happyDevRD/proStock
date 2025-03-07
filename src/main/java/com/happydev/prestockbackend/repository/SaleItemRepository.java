package com.happydev.prestockbackend.repository;

import com.happydev.prestockbackend.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long>{

}
