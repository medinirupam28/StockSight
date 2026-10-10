package com.stocksight.repository;
import com.stocksight.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
    List<StockMovement> findByProductIdOrderByBillDateAscIdAsc(Long productId);
    @Query("select coalesce(sum(case when m.movementType = 'INWARD' then m.quantity else -m.quantity end), 0) from StockMovement m where m.product.id = :productId")
    Long calculateNetMovement(@Param("productId") Long productId);
    @Query("select coalesce(sum(m.quantity),0) from StockMovement m where m.product.id=:productId and m.movementType='OUTWARD' and m.billDate >= :fromDate")
    Long totalOutwardSince(@Param("productId") Long productId, @Param("fromDate") LocalDate fromDate);
    @Query("select coalesce(sum(m.quantity),0) from StockMovement m where m.product.id=:productId and m.movementType='OUTWARD' and m.billDate between :fromDate and :toDate")
    Long totalOutwardBetween(@Param("productId") Long productId, @Param("fromDate") LocalDate fromDate,
                             @Param("toDate") LocalDate toDate);
    @Query("select count(m) from StockMovement m where m.product.id=:productId and m.movementType='OUTWARD' and m.billDate between :fromDate and :toDate")
    long countOutwardBetween(@Param("productId") Long productId, @Param("fromDate") LocalDate fromDate,
                             @Param("toDate") LocalDate toDate);
    @Query("select m from StockMovement m where m.product.id=:productId and m.movementType='OUTWARD' order by m.billDate desc")
    List<StockMovement> recentOutward(@Param("productId") Long productId);
}
