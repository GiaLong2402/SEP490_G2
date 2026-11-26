package com.hbsisms.feature.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.approval.ApprovalStatus;
import com.hbsisms.feature.approval.ApprovalTask;
import com.hbsisms.feature.approval.ApprovalTaskRepository;
import com.hbsisms.feature.approval.ApprovalType;
import com.hbsisms.feature.customer.dto.CustomerCreditRequest;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomerCreditServiceTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ApprovalTaskRepository approvalTaskRepository;
    @Mock
    private UserRepository userRepository;

    private CustomerCreditService service;

    @BeforeEach
    void setUp() {
        service = new CustomerCreditService(customerRepository, approvalTaskRepository, userRepository);
    }

    @Test
    void increaseLimitOpensApprovalAndKeepsLiveLimit() {
        Customer customer = b2b(new BigDecimal("5000000"), (short) 15);
        when(customerRepository.findWithAccountById(1L)).thenReturn(Optional.of(customer));
        when(approvalTaskRepository.findByRefTypeAndRefIdAndStatus(
                        eq(CustomerCreditService.REF_TYPE), eq(1L), eq(ApprovalStatus.PENDING)))
                .thenReturn(Optional.empty());
        AppUser actor = new AppUser();
        ReflectionTestUtils.setField(actor, "id", 9L);
        when(userRepository.findById(9L)).thenReturn(Optional.of(actor));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateCredit(
                1L,
                new CustomerCreditRequest(true, new BigDecimal("10000000"), (short) 15),
                9L);

        assertThat(customer.getCreditLimit()).isEqualByComparingTo("5000000");
        assertThat(customer.getPendingCreditLimit()).isEqualByComparingTo("10000000");
        assertThat(customer.hasPendingCreditTerms()).isTrue();

        ArgumentCaptor<ApprovalTask> captor = ArgumentCaptor.forClass(ApprovalTask.class);
        verify(approvalTaskRepository).save(captor.capture());
        assertThat(captor.getValue().getApprovalType()).isEqualTo(ApprovalType.CREDIT_TERMS);
        assertThat(captor.getValue().getRefType()).isEqualTo(CustomerCreditService.REF_TYPE);
    }

    @Test
    void decreaseLimitAppliesImmediately() {
        Customer customer = b2b(new BigDecimal("10000000"), (short) 30);
        when(customerRepository.findWithAccountById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateCredit(
                1L,
                new CustomerCreditRequest(true, new BigDecimal("3000000"), (short) 15),
                9L);

        assertThat(customer.getCreditLimit()).isEqualByComparingTo("3000000");
        assertThat(customer.getNetDays()).isEqualTo((short) 15);
        assertThat(customer.hasPendingCreditTerms()).isFalse();
    }

    @Test
    void approveAppliesPending() {
        Customer customer = b2b(new BigDecimal("5000000"), (short) 15);
        customer.setPendingCreditEnabled(true);
        customer.setPendingCreditLimit(new BigDecimal("12000000"));
        customer.setPendingNetDays((short) 30);
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyApprovalDecision(1L, true);

        assertThat(customer.getCreditLimit()).isEqualByComparingTo("12000000");
        assertThat(customer.getNetDays()).isEqualTo((short) 30);
        assertThat(customer.hasPendingCreditTerms()).isFalse();
    }

    @Test
    void rejectClearsPendingWithoutChangingLive() {
        Customer customer = b2b(new BigDecimal("5000000"), (short) 15);
        customer.setPendingCreditLimit(new BigDecimal("12000000"));
        when(customerRepository.findById(1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyApprovalDecision(1L, false);

        assertThat(customer.getCreditLimit()).isEqualByComparingTo("5000000");
        assertThat(customer.hasPendingCreditTerms()).isFalse();
    }

    @Test
    void secondProposeWhilePendingConflicts() {
        Customer customer = b2b(new BigDecimal("5000000"), (short) 15);
        customer.setPendingCreditLimit(new BigDecimal("8000000"));
        when(customerRepository.findWithAccountById(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> service.updateCredit(
                        1L,
                        new CustomerCreditRequest(true, new BigDecimal("9000000"), (short) 15),
                        9L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    private static Customer b2b(BigDecimal limit, short netDays) {
        Customer c = new Customer();
        ReflectionTestUtils.setField(c, "id", 1L);
        c.setCode("KH-B001");
        c.setCustomerType(CustomerType.B2B);
        c.setName("Nhà hàng Biển Xanh");
        c.setCreditEnabled(true);
        c.setCreditLimit(limit);
        c.setNetDays(netDays);
        c.setCurrentDebt(BigDecimal.ZERO);
        return c;
    }
}
