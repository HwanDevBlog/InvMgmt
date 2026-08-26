package com.hwandevblog.invmgmt.order;

import com.hwandevblog.invmgmt.common.DuplicateResourceException;
import com.hwandevblog.invmgmt.common.ResourceNotFoundException;
import com.hwandevblog.invmgmt.product.Product;
import com.hwandevblog.invmgmt.product.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class PurchaseOrderService {

    private final PurchaseOrderRepository orderRepository;
    private final ProductRepository productRepository;

    public PurchaseOrderService(PurchaseOrderRepository orderRepository,
                                ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
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
}
