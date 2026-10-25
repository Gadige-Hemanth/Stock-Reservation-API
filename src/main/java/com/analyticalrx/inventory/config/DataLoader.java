package com.analyticalrx.inventory.config;

import com.analyticalrx.inventory.model.Product;
import com.analyticalrx.inventory.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@SuppressWarnings("null")
public class DataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
    private final ProductRepository productRepository;

    public DataLoader(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() == 0) {
            log.info("Database is empty. Seeding initial warehouse products specified in the assignment...");
            List<Product> initialProducts = List.of(
                    new Product("NOTE-001", "Notebook", 10, 0),
                    new Product("PEN-001", "Pen", 5, 0),
                    new Product("BAG-001", "Backpack", 2, 0)
            );
            productRepository.saveAll(initialProducts);
            log.info("Initial seed data successfully inserted (3 products).");
        } else {
            log.info("Existing product catalog detected (count: {}). Skipping seed to preserve live inventory state across restarts.",
                    productRepository.count());
        }
    }
}
