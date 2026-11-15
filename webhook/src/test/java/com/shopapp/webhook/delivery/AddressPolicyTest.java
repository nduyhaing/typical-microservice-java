package com.shopapp.webhook.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AddressPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "0.0.0.0", "0.1.2.3",
        "127.0.0.1", "127.255.255.254",
        "10.0.0.1", "10.255.255.255",
        "172.16.0.1", "172.31.255.255",
        "192.168.0.1", "192.168.255.255",
        "169.254.169.254", "169.254.0.1",
        "100.64.0.1", "100.127.255.255",
        "192.0.0.8", "192.0.2.1", "198.18.0.1", "198.19.255.255", "198.51.100.7", "203.0.113.9",
        "224.0.0.1", "239.255.255.250", "240.0.0.1", "255.255.255.255",
        "::", "::1", "::127.0.0.1",
        "::ffff:127.0.0.1", "::ffff:10.1.2.3", "::ffff:169.254.169.254",
        "fc00::1", "fd12:3456:789a::1",
        "fe80::1", "febf::1", "fec0::1",
        "ff02::1", "ff05::2",
        "2001:db8::1", "2001:0:4136:e378:8000:63bf:3fff:fdd2",
        "64:ff9b::a00:1", "64:ff9b::7f00:1",
        "2002:7f00:1::1", "2002:a00:1::1",
        "100::1"})
    void nonPublicAddresses_areRefused(String address) throws Exception {
        assertThat(AddressPolicy.isPublic(InetAddress.getByName(address))).as(address).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "93.184.216.34", "8.8.8.8", "1.1.1.1", "172.15.255.255", "172.32.0.1", "192.169.0.1", "100.63.255.255",
        "100.128.0.1", "169.253.255.255", "223.255.255.254",
        "2606:4700:4700::1111", "2a00:1450:4001:80b::200e", "::ffff:93.184.216.34", "64:ff9b::808:808",
        "2002:808:808::1"})
    void publicAddresses_areAllowed(String address) throws Exception {
        assertThat(AddressPolicy.isPublic(InetAddress.getByName(address))).as(address).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"::ffff:127.0.0.1", "::ffff:10.0.0.1", "::ffff:192.168.1.1"})
    void ipv4MappedIpv6KeptAsIpv6_isCheckedByItsIpv4Part(String address) throws Exception {
        byte[] v4 = InetAddress.getByName(address).getAddress();
        byte[] mapped = new byte[16];
        mapped[10] = (byte) 0xff;
        mapped[11] = (byte) 0xff;
        System.arraycopy(v4, 0, mapped, 12, 4);
        // Inet6Address.getByAddress keeps the mapped form (getByName would turn it into an Inet4Address)
        assertThat(AddressPolicy.isPublic(java.net.Inet6Address.getByAddress(null, mapped, -1))).isFalse();
    }
}
