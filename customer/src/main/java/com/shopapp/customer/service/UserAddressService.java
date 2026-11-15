package com.shopapp.customer.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.customer.model.UserAddress;
import com.shopapp.customer.repository.UserAddressRepository;
import com.shopapp.customer.utils.Constants;
import com.shopapp.customer.viewmodel.address.ActiveAddressVm;
import com.shopapp.customer.viewmodel.address.AddressDetailVm;
import com.shopapp.customer.viewmodel.address.AddressPostVm;
import com.shopapp.customer.viewmodel.address.AddressVm;
import com.shopapp.customer.viewmodel.useraddress.UserAddressVm;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user's address book (YAS UserAddressService). The {@code id} in paths is the location address id
 * (YAS); a user only sees and changes their own links, another user's id answers 404 (decisions.md 3c-31). The first
 * address becomes the default; deleting the default makes the oldest remaining address the default.
 */
@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressRepository userAddressRepository;
    private final LocationService locationService;

    public List<ActiveAddressVm> getUserAddressList() {
        String userId = AuthenticationUtils.extractUserId();
        List<UserAddress> links = userAddressRepository.findAllByUserIdOrderByIdAsc(userId);
        Map<Long, AddressDetailVm> addresses = locationService
            .getAddressesByIdList(links.stream().map(UserAddress::getAddressId).toList())
            .stream()
            .collect(Collectors.toMap(AddressDetailVm::id, Function.identity()));
        return links.stream()
            .filter(link -> addresses.containsKey(link.getAddressId()))
            .map(link -> ActiveAddressVm.of(addresses.get(link.getAddressId()),
                Boolean.TRUE.equals(link.getIsActive())))
            .sorted(Comparator.comparing(ActiveAddressVm::isActive).reversed())
            .toList();
    }

    public AddressDetailVm getAddressDefault() {
        UserAddress link = userAddressRepository.findByUserIdAndIsActiveTrue(AuthenticationUtils.extractUserId())
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.USER_ADDRESS_NOT_FOUND));
        return locationService.getAddressById(link.getAddressId());
    }

    /**
     * Creates the address in location, then saves the link. If the link cannot be saved, the location address is
     * deleted again (best effort) so no orphan is left behind (decisions.md 3c-32).
     */
    public UserAddressVm createAddress(AddressPostVm addressPostVm) {
        String userId = AuthenticationUtils.extractUserId();
        boolean isFirstAddress = !userAddressRepository.existsByUserId(userId);
        AddressVm address = locationService.createAddress(addressPostVm);
        try {
            UserAddress link = userAddressRepository.saveAndFlush(
                UserAddress.builder().userId(userId).addressId(address.id()).isActive(isFirstAddress).build());
            return UserAddressVm.fromModel(link, address);
        } catch (RuntimeException ex) {
            locationService.deleteAddressQuietly(address.id());
            throw ex;
        }
    }

    /**
     * Deletes the link and the location address. The location call runs before commit, so when location is down
     * the link stays (500, nothing changed).
     */
    @Transactional
    public void deleteAddress(Long id) {
        String userId = AuthenticationUtils.extractUserId();
        UserAddress link = getOwnLink(userId, id);
        userAddressRepository.delete(link);
        userAddressRepository.flush();
        if (Boolean.TRUE.equals(link.getIsActive())) {
            userAddressRepository.findFirstByUserIdOrderByIdAsc(userId).ifPresent(next -> {
                next.setIsActive(true);
                userAddressRepository.flush();
            });
        }
        locationService.deleteAddress(id);
    }

    @Transactional
    public void chooseDefaultAddress(Long id) {
        String userId = AuthenticationUtils.extractUserId();
        getOwnLink(userId, id);
        userAddressRepository.clearDefault(userId);
        userAddressRepository.makeDefault(userId, id);
    }

    private UserAddress getOwnLink(String userId, Long addressId) {
        return userAddressRepository.findOneByUserIdAndAddressId(userId, addressId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.USER_ADDRESS_NOT_FOUND));
    }
}
