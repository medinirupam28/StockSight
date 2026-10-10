package com.stocksight.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.stocksight.dto.MovementDtos.MovementRequest;
import com.stocksight.dto.MovementDtos.MovementResponse;
import com.stocksight.dto.MovementDtos.InventoryAnomalyResponse;
import com.stocksight.dto.ProductDtos.ProductRequest;
import com.stocksight.dto.ProductDtos.ProductResponse;
import com.stocksight.error.NotFoundException;
import com.stocksight.model.MovementType;
import com.stocksight.model.Product;
import com.stocksight.model.StockMovement;
import com.stocksight.repository.ProductRepository;
import com.stocksight.repository.StockMovementRepository;

@Service
public class InventoryService {
    private final ProductRepository products; private final StockMovementRepository movements;
    public InventoryService(ProductRepository products, StockMovementRepository movements) {
        this.products=products; this.movements=movements;
    }
    @Transactional(readOnly=true)
    public Page<ProductResponse> listProducts(String q, Pageable pageable) {
        Page<Product> page = (q == null || q.isBlank()) ? products.findAll(pageable)
            : products.findByNameContainingIgnoreCaseOrSkuContainingIgnoreCase(q, q, pageable);
        return page.map(this::productResponse);
    }
        @Transactional(readOnly=true)
        public ProductResponse getProductById(long id) { return productResponse(getProduct(id)); }
        @Transactional(readOnly=true)
        public ProductResponse getProductBySku(String sku) {
            Product p = products.findBySku(sku.trim())
                .orElseThrow(() -> new NotFoundException("Product with SKU " + sku + " not found."));
            return productResponse(p);
        }
    @Transactional
    public ProductResponse createProduct(ProductRequest r) {
        Product p = products.save(new Product(r.name().trim(), r.sku().trim(), r.openingBalance(), r.safetyThreshold()));
        return productResponse(p);
    }
    @Transactional
    public ProductResponse updateProduct(long id, ProductRequest r) {
        Product p = getProduct(id);
        p.setName(r.name().trim()); p.setSku(r.sku().trim());
        // Changing opening balance intentionally changes derived stock; recalculate afterward.
        p.setOpeningBalance(r.openingBalance()); p.setSafetyThreshold(r.safetyThreshold());
        recalculate(p);
        return productResponse(products.save(p));
    }
    @Transactional
    public void deleteProduct(long id) {
        Product p=getProduct(id);
        if (!movements.findByProductIdOrderByBillDateAscIdAsc(id).isEmpty())
            throw new IllegalArgumentException("Cannot delete a product with stock movement history.");
        products.delete(p);
    }
    @Transactional
    public MovementResponse createMovement(MovementRequest r) {
        if (r.deliveryDate().isBefore(r.billDate()))
            throw new IllegalArgumentException("Delivery date cannot be earlier than bill date.");
        Product p=getProduct(r.productId());
        if (r.movementType()==MovementType.OUTWARD && r.quantity()>p.getCurrentStock())
            throw new IllegalArgumentException("Insufficient stock for this outward movement.");
        boolean anomaly = false; String reason = null;
        if (r.movementType()==MovementType.OUTWARD) {
            List<StockMovement> prior = movements.recentOutward(p.getId());
            double average = prior.stream().limit(10).mapToInt(StockMovement::getQuantity).average().orElse(0);
            if (prior.size() >= 3 && r.quantity() > Math.max(average * 2.0, p.getSafetyThreshold())) {
                anomaly = true; reason = "Outward quantity is unusually high compared with recent outward movements.";
            }
            if (r.quantity() > p.getCurrentStock() - p.getSafetyThreshold()) {
                anomaly = true;
                reason = reason == null ? "This movement takes stock below the safety threshold." : reason + " It also takes stock below the safety threshold.";
            }
        }
        StockMovement m = movements.save(new StockMovement(p, r.movementType(), r.quantity(), r.billDate(), r.deliveryDate()));
        recalculate(p);
        products.save(p);
        return new MovementResponse(m.getId(), p.getId(), p.getName(), m.getMovementType(), m.getQuantity(),
            m.getBillDate(), m.getDeliveryDate(), anomaly, reason, p.getCurrentStock());
    }
    @Transactional(readOnly=true)
    public List<MovementResponse> listMovements(Long productId) {
        List<StockMovement> list = productId == null ? movements.findAll() : movements.findByProductIdOrderByBillDateAscIdAsc(productId);
        Map<Long, String> anomalyReasons = detectAnomalies(movements.findAll(), productId, null, null).stream()
            .collect(Collectors.groupingBy(InventoryAnomalyResponse::transactionId,
                Collectors.mapping(InventoryAnomalyResponse::reason, Collectors.joining(" "))));
        return list.stream().map(m -> new MovementResponse(m.getId(), m.getProduct().getId(), m.getProduct().getName(),
            m.getMovementType(), m.getQuantity(), m.getBillDate(), m.getDeliveryDate(), anomalyReasons.containsKey(m.getId()),
            anomalyReasons.get(m.getId()), m.getProduct().getCurrentStock())).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> analytics(long productId) {
        LocalDate to = LocalDate.now();
        return analytics(productId, to.minusDays(29), to);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> analytics(long productId, LocalDate from, LocalDate to) {
        DateRange range = dateRange(from, to);
        return productAnalytics(getProduct(productId), range);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> reorderSuggestions(LocalDate from, LocalDate to) {
        DateRange range = dateRange(from, to);
        return products.findAll().stream().map(p -> productAnalytics(p, range))
            .filter(result -> Boolean.TRUE.equals(result.get("reorderRecommended"))).toList();
    }
    private Map<String,Object> productAnalytics(Product p, DateRange range) {
        long periodOutward = movements.totalOutwardBetween(p.getId(), range.from(), range.to());
        long periodCount = movements.countOutwardBetween(p.getId(), range.from(), range.to());
        long periodDays = ChronoUnit.DAYS.between(range.from(), range.to()) + 1;
        double periodDaily = periodOutward / (double)periodDays;
        boolean sufficient = periodCount >= 3;
        Double daysUntilThreshold = sufficient && periodDaily > 0
            ? Math.max(0, p.getCurrentStock() - p.getSafetyThreshold()) / periodDaily : null;
        boolean reorder = p.getCurrentStock() <= p.getSafetyThreshold()
            || (sufficient && daysUntilThreshold <= periodDays);
        int suggested = sufficient
            ? Math.max(0, (int)Math.ceil(periodOutward + p.getSafetyThreshold() - p.getCurrentStock()))
            : Math.max(0, p.getSafetyThreshold() - p.getCurrentStock());

        LocalDate legacyFrom = range.to().minusDays(29);
        long legacyOutward = movements.totalOutwardBetween(p.getId(), legacyFrom, range.to());
        long legacyCount = movements.countOutwardBetween(p.getId(), legacyFrom, range.to());
        double legacyDaily = legacyOutward / 30.0;
        Double daysUntilStockout = legacyCount >= 3 && legacyDaily > 0 ? p.getCurrentStock() / legacyDaily : null;
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("productId",p.getId()); result.put("productName",p.getName()); result.put("sku",p.getSku());
        result.put("currentStock",p.getCurrentStock()); result.put("safetyThreshold",p.getSafetyThreshold());
        result.put("analysisFrom",range.from()); result.put("analysisTo",range.to());
        result.put("periodDays",periodDays); result.put("totalOutwardQuantity",periodOutward);
        result.put("outwardMovementCount",periodCount); result.put("periodAverageDailyOutward",round(periodDaily));
        result.put("sufficientHistory",sufficient); result.put("estimatedDaysUntilSafetyThreshold",daysUntilThreshold == null ? null : round(daysUntilThreshold));
        result.put("averageDailyOutward30Days",round(legacyDaily)); result.put("estimatedDaysUntilStockout",daysUntilStockout == null ? null : round(daysUntilStockout));
        result.put("reorderRecommended",reorder); result.put("suggestedReorderQuantity",suggested);
        result.put("leadTimeAvailable",false);
        result.put("method",sufficient
            ? "Actual outward quantity in the inclusive date range; reorder cover equals that range; lead time is not configured."
            : "Insufficient history: fewer than 3 outward transactions in the inclusive date range; only the safety threshold is used.");
        return result;
    }
    @Transactional(readOnly=true)
    public List<InventoryAnomalyResponse> listAnomalies(Long productId, LocalDate from, LocalDate to) {
        DateRange range = dateRange(from, to);
        return detectAnomalies(movements.findAll(), productId, range.from(), range.to());
    }
    private List<InventoryAnomalyResponse> detectAnomalies(List<StockMovement> source, Long productId,
                                                             LocalDate from, LocalDate to) {
        List<StockMovement> ordered = source.stream()
            .filter(m -> productId == null || Objects.equals(m.getProduct().getId(), productId))
            .sorted(Comparator.comparing(StockMovement::getBillDate).thenComparing(StockMovement::getId,
                Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        Map<Long,Integer> stockByProduct = new HashMap<>();
        Map<Long,Deque<Integer>> outwardByProduct = new HashMap<>();
        Map<Long,Deque<Integer>> inwardByProduct = new HashMap<>();
        List<InventoryAnomalyResponse> anomalies = new ArrayList<>();
        for (StockMovement movement : ordered) {
            Product product = movement.getProduct();
            Long id = product.getId();
            if (to != null && movement.getBillDate().isAfter(to)) break;
            int before = stockByProduct.getOrDefault(id, product.getOpeningBalance());
            int after = before + (movement.getMovementType() == MovementType.INWARD ? movement.getQuantity() : -movement.getQuantity());
            Deque<Integer> prior = (movement.getMovementType() == MovementType.OUTWARD ? outwardByProduct : inwardByProduct)
                .computeIfAbsent(id, ignored -> new ArrayDeque<>());
            boolean inWindow = (from == null || !movement.getBillDate().isBefore(from))
                && (to == null || !movement.getBillDate().isAfter(to));
            if (inWindow && prior.size() >= 3) {
                double average = prior.stream().mapToInt(Integer::intValue).average().orElse(0);
                boolean spike = movement.getMovementType() == MovementType.OUTWARD
                    ? movement.getQuantity() > Math.max(average * 2, product.getSafetyThreshold())
                    : movement.getQuantity() > average * 2;
                if (spike) anomalies.add(anomaly(movement,
                    movement.getMovementType() == MovementType.OUTWARD ? "CONSUMPTION_SPIKE" : "UNUSUAL_INWARD",
                    "HIGH", "Quantity exceeds the movement baseline from the previous " + prior.size() + " same-type transactions.",
                    before, after));
            }
            if (inWindow && movement.getMovementType() == MovementType.OUTWARD && after < product.getSafetyThreshold()) {
                anomalies.add(anomaly(movement, "LOW_STOCK_MOVEMENT", after <= 0 ? "HIGH" : "MEDIUM",
                    "This outward movement left stock below the configured safety threshold.", before, after));
            }
            prior.addLast(movement.getQuantity());
            if (prior.size() > 10) prior.removeFirst();
            stockByProduct.put(id, after);
        }
        return anomalies;
    }
    private InventoryAnomalyResponse anomaly(StockMovement movement, String type, String severity, String reason,
                                               int before, int after) {
        Product p = movement.getProduct();
        return new InventoryAnomalyResponse(movement.getId(), p.getId(), p.getName(), p.getSku(), type, severity,
            reason, movement.getMovementType(), movement.getQuantity(), movement.getBillDate(), before, after);
    }
    private DateRange dateRange(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (start.isAfter(end)) throw new IllegalArgumentException("Analysis start date must be on or before end date.");
        if (ChronoUnit.DAYS.between(start, end) > 3650)
            throw new IllegalArgumentException("Analysis date range cannot exceed 10 years.");
        return new DateRange(start, end);
    }
    private double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private record DateRange(LocalDate from, LocalDate to) {}
    private Product getProduct(long id) { return products.findById(id).orElseThrow(() -> new NotFoundException("Product " + id + " not found.")); }
    private void recalculate(Product p) {
        long net = movements.calculateNetMovement(p.getId());
        long stock = p.getOpeningBalance() + net;
        if (stock < 0) throw new IllegalArgumentException("Movement history would result in negative stock.");
        if (stock > Integer.MAX_VALUE) throw new IllegalArgumentException("Calculated stock exceeds supported range.");
        p.setCurrentStock((int)stock);
    }
    private ProductResponse productResponse(Product p) {
        return new ProductResponse(p.getId(),p.getName(),p.getSku(),p.getOpeningBalance(),p.getSafetyThreshold(),
            p.getCurrentStock(),p.getCurrentStock()<=p.getSafetyThreshold());
    }
}
