package com.hbsisms.feature.inventory;

import com.hbsisms.feature.catalog.Category;
import com.hbsisms.feature.catalog.CategoryRepository;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one demo category/product/variant when catalog is empty so inventory receive can be tested
 * before the full products module lands.
 */
@Slf4j
@Component
@Order(50)
public class InventoryDemoCatalogSeeder implements ApplicationRunner {

    private final ProductVariantRepository productVariantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public InventoryDemoCatalogSeeder(
            ProductVariantRepository productVariantRepository,
            ProductRepository productRepository,
            CategoryRepository categoryRepository) {
        this.productVariantRepository = productVariantRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productVariantRepository.count() > 0) {
            publishDemoProductIfNeeded();
            return;
        }
        Category category = new Category();
        category.setCode("DEMO-SEA");
        category.setName("Hải sản demo");
        category.setActive(true);
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setCode("SP-DEMO-01");
        product.setName("Tôm sú demo");
        product.setSlug("tom-su-demo");
        product.setCategory(category);
        product.setDescription("Tôm sú demo để kiểm thử cổng bán hàng và nhập kho.");
        product.setActive(true);
        product.setPublished(true);
        product = productRepository.save(product);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSku("TOM-SU-05");
        variant.setPackLabel("Khay 0,5kg");
        variant.setUnitWeightG(500);
        variant.setListPrice(new BigDecimal("180000.00"));
        variant.setPackPerCase((short) 10);
        variant.setActive(true);
        productVariantRepository.save(variant);

        log.info("Seeded demo catalog variant SKU=TOM-SU-05 for inventory testing");
    }

    /** Local DB đã seed trước đó: bật publish + slug để storefront gọi được. */
    private void publishDemoProductIfNeeded() {
        productRepository.findByCodeIgnoreCase("SP-DEMO-01").ifPresent(product -> {
            boolean changed = false;
            if (!product.isPublished()) {
                product.setPublished(true);
                changed = true;
            }
            if (product.getSlug() == null || product.getSlug().isBlank()) {
                product.setSlug("tom-su-demo");
                changed = true;
            }
            if (changed) {
                productRepository.save(product);
                log.info("Published demo catalog product SP-DEMO-01 for B2C storefront");
            }
        });
    }
}
