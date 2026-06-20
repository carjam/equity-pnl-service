package com.companyx.equity.repository;

import com.companyx.equity.model.Transaction;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.id = :id")
    @QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "false"))
    Optional<Transaction> findByUidAndId(@Param("uid") int uid, @Param("id") int id);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.timestamp < :end ORDER BY t.timestamp")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Transaction> findAllBefore(@Param("uid") int uid, @Param("end") Date end);

    @Query("SELECT t FROM Transaction t WHERE t.user.id = :uid AND t.timestamp BETWEEN :start AND :end ORDER BY t.timestamp")
    @QueryHints({
        @QueryHint(name = "org.hibernate.fetchSize", value = "100"),
        @QueryHint(name = "org.hibernate.readOnly", value = "true")
    })
    List<Transaction> findAllBetween(@Param("uid") int uid, @Param("start") Date start, @Param("end") Date end);
    
    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.user.id = :uid")
    long countByUserId(@Param("uid") int uid);

    @Query("SELECT MIN(t.timestamp) FROM Transaction t WHERE t.user.id = :uid AND t.symbol = :symbol")
    Optional<Date> findEarliestByUserAndSymbol(@Param("uid") int uid, @Param("symbol") String symbol);
}
