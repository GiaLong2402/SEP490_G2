package com.hbsisms.feature.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.TestFixtures;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.Permission;
import com.hbsisms.feature.role.RoleService;
import com.hbsisms.feature.user.dto.UserRequest;
import com.hbsisms.feature.user.dto.UserResponse;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleService roleService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;
    private AppRole adminRole;
    private AppRole salesRole;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, roleService, passwordEncoder);
        adminRole = TestFixtures.role((short) 1, "admin", Set.of(Permission.ADMIN));
        salesRole = TestFixtures.role((short) 3, "sales", Set.of(Permission.ORDERS));
    }

    @Test
    void createStaff_normalizesFieldsAndHashesPassword() {
        when(roleService.getAssignableStaffRole((short) 3)).thenReturn(salesRole);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("hashed");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.createStaff(
                request(" Mai.Nguyen ", " MAI@HBSISMS.VN ", (short) 3, true, "Passw0rd!"));

        assertThat(response.username()).isEqualTo("mai.nguyen");
        assertThat(response.email()).isEqualTo("mai@hbsisms.vn");
        assertThat(response.userType()).isEqualTo(UserType.STAFF);
        assertThat(response.roleName()).isEqualTo("sales");
    }

    @Test
    void createStaff_withoutPassword_isRejected() {
        assertThatThrownBy(() -> userService.createStaff(request("mai", null, (short) 3, true, null)))
                .isInstanceOf(ApiException.class)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createStaff_duplicateUsername_returns409() {
        when(roleService.getAssignableStaffRole((short) 3)).thenReturn(salesRole);
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(TestFixtures.staff(9L, "mai", salesRole)));

        assertThatThrownBy(() -> userService.createStaff(request("mai", null, (short) 3, true, "Passw0rd!")))
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateStaff_cannotLockSelf() {
        AppUser admin = TestFixtures.staff(1L, "admin", adminRole);
        when(userRepository.findWithRoleById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateStaff(1L, request("admin", null, (short) 1, false, null), 1L))
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    void updateStaff_cannotChangeOwnRole() {
        AppUser admin = TestFixtures.staff(1L, "admin", adminRole);
        when(userRepository.findWithRoleById(1L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.updateStaff(1L, request("admin", null, (short) 3, true, null), 1L))
                .extracting("status").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateStaff_deactivatingOtherUser_revokesTheirTokens() {
        AppUser sales = TestFixtures.staff(5L, "mai", salesRole);
        when(userRepository.findWithRoleById(5L)).thenReturn(Optional.of(sales));
        when(roleService.getAssignableStaffRole((short) 3)).thenReturn(salesRole);
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(sales));
        when(userRepository.saveAndFlush(sales)).thenReturn(sales);

        UserResponse response = userService.updateStaff(5L, request("mai", null, (short) 3, false, null), 1L);

        assertThat(response.isActive()).isFalse();
        verify(userRepository).incrementTokenVersion(5L);
    }

    @Test
    void updateStaff_customerAccount_isNotFound() {
        AppUser customer = TestFixtures.staff(8L, "khach", salesRole);
        customer.setUserType(UserType.CUSTOMER);
        when(userRepository.findWithRoleById(8L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> userService.updateStaff(8L, request("khach", null, (short) 3, true, null), 1L))
                .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static UserRequest request(String username, String email, short roleId, boolean active, String password) {
        return new UserRequest(username, "Nguyễn Thị Mai", email, null, roleId, "Kinh doanh", null, active, password);
    }
}
