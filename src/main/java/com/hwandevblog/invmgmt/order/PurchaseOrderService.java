package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.common.DuplicateResourceException;
import com.hwandevblog.invmgmt.common.ResourceNotFoundException;
import com.hwandevblog.invmgmt.product.Product;
import com.hwandevblog.invmgmt.product.ProductRepository;
import com.hwandevblog.invmgmt.product.Stock;
import com.hwandevblog.invmgmt.product.StockLedger;
import com.hwandevblog.invmgmt.product.StockLedgerRepository;
import com.hwandevblog.invmgmt.product.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockLedgerRepository stockLedgerRepository;

    public PurchaseOrderService(PurchaseOrderRepository orderRepository,
                                ProductRepository productRepository,
                                StockRepository stockRepository,
                                StockLedgerRepository stockLedgerRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
    }

    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        if (orderRepository.existsByOrderNumber(request.orderNumber())) {
            throw new DuplicateResourceException("Order number already exists: " + request.orderNumber());
        }

        rejectDuplicateProducts(request);

        PurchaseOrder order = PurchaseOrder.create(request.orderNumber());
        request.lines().forEach(line -> {
            Product product = productRepository.findById(line.productId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found: " + line.productId()));
            if (!product.isActive()) {
                throw new IllegalArgumentException("Inactive product cannot be ordered: " + product.getSku());
            }
            order.addLine(product, line.quantity());
        });

        // 주문 생성은 요청 내용을 CREATED 상태로 저장하며, 이 단계에서는 아직 재고를 예약하지 않는다.
        return OrderResponse.from(orderRepository.save(order));
    }

    public OrderResponse get(long orderId) {
        PurchaseOrder order = orderRepository.findWithLinesById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse reserve(long orderId) {
        PurchaseOrder order = findForUpdate(orderId);

        order.reserve();
        order.getLines().forEach(line -> {
            Stock stock = stockRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Stock not found: " + line.getProduct().getId()));
            stock.reserve(line.getQuantity());
            stockLedgerRepository.save(StockLedger.reserve(
                    line.getProduct(),
                    line.getQuantity(),
                    stock.getQuantity(),
                    order.getId()));
        });

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse confirm(long orderId) {
        PurchaseOrder order = findForUpdate(orderId);

        // 재고는 예약 시점에 이미 차감했으므로 확정에서는 주문 상태만 전환한다.
        order.confirm();
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancel(long orderId) {
        PurchaseOrder order = findForUpdate(orderId);

        order.cancel();
        order.getLines().forEach(line -> {
            Stock stock = stockRepository.findById(line.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Stock not found: " + line.getProduct().getId()));
            stock.restore(line.getQuantity());
            stockLedgerRepository.save(StockLedger.cancel(
                    line.getProduct(),
                    line.getQuantity(),
                    stock.getQuantity(),
                    order.getId()));
        });

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse returnItems(long orderId, ReturnOrderRequest request) {
        rejectDuplicateOrderLines(request);
        PurchaseOrder order = findForUpdate(orderId);

        request.lines().forEach(returnLine -> {
            OrderLine orderLine = order.getLines().stream()
                    .filter(line -> line.getId().equals(returnLine.orderLineId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Order line not found in order: " + returnLine.orderLineId()));

            order.returnItem(orderLine, returnLine.quantity());
            Stock stock = stockRepository.findById(orderLine.getProduct().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Stock not found: " + orderLine.getProduct().getId()));
            stock.restore(returnLine.quantity());
            stockLedgerRepository.save(StockLedger.returned(
                    orderLine.getProduct(),
                    returnLine.quantity(),
                    stock.getQuantity(),
                    orderLine.getId()));
        });

        return OrderResponse.from(order);
    }

    private PurchaseOrder findForUpdate(long orderId) {
        // 상태 변경 명령을 주문별로 직렬화해 서로 다른 멱등키의 중복 처리를 막는다.
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void rejectDuplicateProducts(CreateOrderRequest request) {
        // 동일 상품이 여러 줄에 있으면 예약·취소·반품 수량 추적이 모호해지므로 생성 시점에 차단한다.
        Set<Long> productIds = new HashSet<>();
        boolean duplicate = request.lines().stream()
                .map(CreateOrderRequest.Line::productId)
                .anyMatch(productId -> !productIds.add(productId));
        if (duplicate) {
            throw new IllegalArgumentException("An order cannot contain duplicate product lines");
        }
    }

    private void rejectDuplicateOrderLines(ReturnOrderRequest request) {
        Set<Long> orderLineIds = new HashSet<>();
        boolean duplicate = request.lines().stream()
                .map(ReturnOrderRequest.Line::orderLineId)
                .anyMatch(orderLineId -> !orderLineIds.add(orderLineId));
        if (duplicate) {
            throw new IllegalArgumentException("A return cannot contain duplicate order lines");
        }
    }
}
