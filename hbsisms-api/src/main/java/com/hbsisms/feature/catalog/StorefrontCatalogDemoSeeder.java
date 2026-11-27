package com.hbsisms.feature.catalog;

import com.hbsisms.feature.inventory.Location;
import com.hbsisms.feature.inventory.LocationRepository;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.LotRepository;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.inventory.Warehouse;
import com.hbsisms.feature.inventory.WarehouseRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent B2C demo catalog for Inter 2 #23 (Danh mục công khai):
 * category Tôm, published product with tiers, stock on one variant only.
 */
@Slf4j
@Component
@Order(56)
public class StorefrontCatalogDemoSeeder implements ApplicationRunner {

    static final String CAT_TOM = "CAT-TOM";
    static final String PRODUCT_LIVE = "TOM-SU-01";
    static final String SKU_IN_STOCK = "TOM-SU-500";
    static final String SKU_OUT = "TOM-SU-1000";
    static final String LOT_CODE = "LOT-TOM-SU-01";
    static final String FINISHED_WAREHOUSE_CODE = "KHO-TP";

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VolumeTierRepository volumeTierRepository;
    private final WarehouseRepository warehouseRepository;
    private final LocationRepository locationRepository;
    private final LotRepository lotRepository;
    private final StockRepository stockRepository;

    public StorefrontCatalogDemoSeeder(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            VolumeTierRepository volumeTierRepository,
            WarehouseRepository warehouseRepository,
            LocationRepository locationRepository,
            LotRepository lotRepository,
            StockRepository stockRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.volumeTierRepository = volumeTierRepository;
        this.warehouseRepository = warehouseRepository;
        this.locationRepository = locationRepository;
        this.lotRepository = lotRepository;
        this.stockRepository = stockRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Category tom = ensureCategory(CAT_TOM, "Tôm", 1);

        Product live = ensureLiveProduct(tom);
        ProductVariant inStock = ensureVariant(
                live, SKU_IN_STOCK, "Khay 500g", 500, new BigDecimal("189000.00"));
        ensureVariant(live, SKU_OUT, "Khay 1kg", 1000, new BigDecimal("359000.00"));
        ensureTiers(inStock);
        ensureStock(inStock);

        log.info("Storefront demo catalog ready: {} published", PRODUCT_LIVE);
    }

    private Category ensureCategory(String code, String name, int sortOrder) {
        return categoryRepository.findByCodeIgnoreCase(code).orElseGet(() -> {
            Category category = new Category();
            category.setCode(code);
            category.setName(name);
            category.setSortOrder(sortOrder);
            category.setActive(true);
            return categoryRepository.save(category);
        });
    }

    private Product ensureLiveProduct(Category category) {
        return productRepository.findByCodeIgnoreCase(PRODUCT_LIVE).orElseGet(() -> {
            Product product = new Product();
            product.setCode(PRODUCT_LIVE);
            product.setName("Tôm sú đông lạnh");
            product.setSlug("tom-su-dong-lanh");
            product.setCategory(category);
            product.setDescription("Tôm sú đông lạnh đóng khay, dùng demo danh mục công khai.");
            product.setOrigin("Hải Phòng");
            product.setStorageNote("Bảo quản -18°C");
            product.setImageUrl("/images/demo-products/product-tom-su.png");
            product.setGallery(List.of());
            product.setPublished(true);
            product.setActive(true);
            return productRepository.save(product);
        });
    }

    private ProductVariant ensureVariant(
            Product product, String sku, String packLabel, int weightG, BigDecimal listPrice) {
        return productVariantRepository.findBySkuIgnoreCase(sku).orElseGet(() -> {
            ProductVariant variant = new ProductVariant();
            variant.setProduct(product);
            variant.setSku(sku);
            variant.setPackLabel(packLabel);
            variant.setUnitWeightG(weightG);
            variant.setListPrice(listPrice);
            variant.setPackPerCase((short) 10);
            variant.setActive(true);
            return productVariantRepository.save(variant);
        });
    }

    private void ensureTiers(ProductVariant variant) {
        if (!volumeTierRepository.findByVariant_IdOrderByMinQtyKhayAsc(variant.getId()).isEmpty()) {
            return;
        }
        VolumeTier t10 = new VolumeTier();
        t10.setVariant(variant);
        t10.setMinQtyKhay(10);
        t10.setUnitPrice(new BigDecimal("175000.00"));
        volumeTierRepository.save(t10);

        VolumeTier t50 = new VolumeTier();
        t50.setVariant(variant);
        t50.setMinQtyKhay(50);
        t50.setUnitPrice(new BigDecimal("160000.00"));
        volumeTierRepository.save(t50);
    }

    private void ensureStock(ProductVariant variant) {
        if (lotRepository.findByLotCodeIgnoreCase(LOT_CODE).isPresent()) {
            return;
        }
        // Chỉ còn Kho thành phẩm (KHO-TP) + Kho nguyên liệu; hàng tôm nằm ở vị trí nhóm TOM.
        Warehouse warehouse = warehouseRepository.findByCodeIgnoreCase(FINISHED_WAREHOUSE_CODE).orElseGet(() -> {
            Warehouse created = new Warehouse();
            created.setCode(FINISHED_WAREHOUSE_CODE);
            created.setName("Kho thành phẩm");
            created.setAddress("Hải Phòng");
            created.setCapacity(500);
            created.setActive(true);
            return warehouseRepository.save(created);
        });
        Location location = locationRepository.findByWarehouse_IdOrderByCodeAsc(warehouse.getId()).stream()
                .filter(l -> "TOM".equalsIgnoreCase(l.getCode()))
                .findFirst()
                .orElseGet(() -> {
                    Location created = new Location();
                    created.setWarehouse(warehouse);
                    created.setCode("TOM");
                    created.setZone("Tôm");
                    created.setCapacity(70);
                    return locationRepository.save(created);
                });

        Lot lot = new Lot();
        lot.setLotCode(LOT_CODE);
        lot.setVariant(variant);
        lot.setMfgDate(LocalDate.now().minusDays(3));
        lot.setExpDate(LocalDate.now().plusDays(25));
        lot.setReceivedAt(Instant.now());
        lot.setNote("Demo danh mục công khai — còn hàng");
        lot = lotRepository.save(lot);

        Stock stock = new Stock();
        stock.setVariant(variant);
        stock.setLot(lot);
        stock.setLocation(location);
        stock.setQtyAvailable(20);
        stock.setQtyHeld(0);
        stock.setQtyQuarantine(0);
        stock.setQtyExpired(0);
        stockRepository.save(stock);
        log.info("Seeded stock qty=20 for SKU {} at {}/{}", SKU_IN_STOCK, FINISHED_WAREHOUSE_CODE, location.getCode());
    }
}
