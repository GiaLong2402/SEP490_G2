package com.hbsisms.feature.inventory;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.inventory.dto.StocktakeInput;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class StocktakeServiceTest {

    @Mock
    private StocktakeRepository stocktakeRepository;
    @Mock
    private StocktakeLineRepository stocktakeLineRepository;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private WarehouseRepository warehouseRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private StockService stockService;
    @Mock
    private UserRepository userRepository;

    private StocktakeService service;

    @BeforeEach
    void setUp() {
        service = new StocktakeService(
                stocktakeRepository,
                stocktakeLineRepository,
                stockRepository,
                locationRepository,
                warehouseRepository,
                approvalTaskRepository,
                stockService,
                userRepository);
    }

    @Test
    void create_rejectsWhenNoStockInScope() {
        AppUser actor = new AppUser();
        ReflectionTestUtils.setField(actor, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(actor));

        Warehouse warehouse = new Warehouse();
        ReflectionTestUtils.setField(warehouse, "id", 1L);
        warehouse.setActive(true);
        Location location = new Location();
        ReflectionTestUtils.setField(location, "id", 10L);
        location.setWarehouse(warehouse);
        location.setZone("Kho lạnh");
        when(locationRepository.findAllWithWarehouse()).thenReturn(List.of(location));
        when(stockRepository.findAllWithDetails()).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(new StocktakeInput("Kho lạnh", null), 1L))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }
}
