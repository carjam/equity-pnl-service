package com.companyx.equity.repository;

import com.companyx.equity.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.id = :id")
    Optional<Transaction> findByUidAndId(int uid, int id);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.timestamp < :end ORDER BY t.timestamp")
    List<Transaction> findAllBefore(int uid, Date end);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.timestamp BETWEEN :start AND :end ORDER BY t.timestamp")
    List<Transaction> findAllBetween(int uid, Date start, Date end);
}
