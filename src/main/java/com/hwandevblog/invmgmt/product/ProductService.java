package com.hwandevblog.invmgmt.product;

import com.hwandevblog.invmgmt.common.DuplicateResourceException;
import com.hwandevblog.invmgmt.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final StockRepository stockRepository;
    private final StockLedgerRepository stockLedgerRepository;

    public ProductService(ProductRepository productRepository,
                          StockRepository stockRepository,
                          StockLedgerRepository stockLedgerRepository) {
        this.productRepository = productRepository;
        this.stockRepository = stockRepository;
        this.stockLedgerRepository = stockLedgerRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("SKU already exists: " + request.sku());
        }

        Product product = productRepository.save(Product.create(request.sku(), request.name()));
        Stock stock = stockRepository.save(Stock.initialize(product, request.initialQuantity()));
        stockLedgerRepository.save(StockLedger.initial(product, request.initialQuantity()));
        return ProductResponse.from(product, stock);
    }

    public ProductResponse get(long productId) {
        Stock stock = findStock(productId);
        return ProductResponse.from(stock.getProduct(), stock);
    }

    public List<ProductResponse> list() {
        return stockRepository.findAll().stream()
                .sorted(Comparator.comparing(stock -> stock.getProduct().getSku()))
                .map(stock -> ProductResponse.from(stock.getProduct(), stock))
                .toList();
    }

    public List<StockResponse> listStocks() {
        return stockRepository.findAll().stream()
                .sorted(Comparator.comparing(stock -> stock.getProduct().getSku()))
                .map(StockResponse::from)
                .toList();
    }

    @Transactional
    public ProductResponse update(long productId, UpdateProductRequest request) {
        Stock stock = findStock(productId);
        stock.getProduct().update(request.name(), request.active());
        return ProductResponse.from(stock.getProduct(), stock);
    }

    private Stock findStock(long productId) {
        return stockRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }
}
