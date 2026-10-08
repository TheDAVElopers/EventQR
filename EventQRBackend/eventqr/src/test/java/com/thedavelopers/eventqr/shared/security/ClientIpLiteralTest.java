package com.thedavelopers.eventqr.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpLiteralTest {

    @Test
    void acceptsWellFormedIpv4AndIpv6Literals() {
        assertThat(ClientIp.parseLiteral("203.0.113.9")).containsExactly(203, 0, 113, 9);
        assertThat(ClientIp.parseLiteral("0.0.0.0")).hasSize(4);
        assertThat(ClientIp.parseLiteral("255.255.255.255")).hasSize(4);
        assertThat(ClientIp.parseLiteral("2001:db8::1")).hasSize(16);
        assertThat(ClientIp.parseLiteral("::")).hasSize(16);
        assertThat(ClientIp.parseLiteral("::1")).hasSize(16);
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6:7:8")).hasSize(16);
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6:7::")).hasSize(16);
        assertThat(ClientIp.parseLiteral("::ffff:203.0.113.9")).hasSize(16);
        assertThat(ClientIp.parseLiteral("64:ff9b::192.0.2.33")).hasSize(16);
    }

    @Test
    void ipv6BytesAreLaidOutCorrectly() {
        assertThat(ClientIp.parseLiteral("2001:db8::1"))
                .containsExactly(0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);
        assertThat(ClientIp.parseLiteral("::ffff:1.2.3.4"))
                .containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xff, 0xff, 1, 2, 3, 4);
    }

    @Test
    void rejectsHostnamesAndMalformedLiterals() {
        String[] bad = {
                "1:2:3", "evil.example.com", "localhost", "example.com:80", "1.2.3", "1.2.3.4.5", "256.1.1.1",
                "01234.1.1.1", "0x7f.0.0.1", ":::", "1::2::3", "12345::1", "g::1", "1:2:3:4:5:6:7:8:9",
                "1:2:3:4:5:6:7:8::", "::1%eth0", "[::1]", "2001:db8::1/64", "1.2.3.4:80", "::1.2.3", "1.2.3.4::1",
                "", " ", "a".repeat(100_000), ":".repeat(5_000), "1.".repeat(5_000)
        };
        for (String value : bad) {
            assertThat(ClientIp.parseLiteral(value)).as("parseLiteral(%s)", value.length() > 40 ? "<long>" : value).isNull();
        }
        assertThat(ClientIp.parseLiteral(null)).isNull();
    }

    @Test
    void ipv4LeadingZerosAreRejectedBecauseLegacyParsersReadThemAsOctal() {
        for (String value : new String[] {"001.2.3.4", "1.02.3.4", "1.2.3.04", "00.0.0.0", "010.0.0.1"}) {
            assertThat(ClientIp.parseLiteral(value)).as(value).isNull();
        }
        assertThat(ClientIp.parseLiteral("0.0.0.0")).hasSize(4);
        assertThat(ClientIp.parseLiteral("10.0.0.1")).hasSize(4);
        assertThat(ClientIp.canonicalize("001.2.3.4")).isNull();
        assertThat(ClientIp.canonicalize("1.2.3.4")).isEqualTo("1.2.3.4");
    }

    @Test
    void edgeSpellingsOfIpv6() {
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6:7::")).containsExactly(0, 1, 0, 2, 0, 3, 0, 4, 0, 5, 0, 6, 0, 7, 0, 0);
        assertThat(ClientIp.parseLiteral("::2:3:4:5:6:7:8")).hasSize(16);
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6:7:8::")).isNull();
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6::7:8:9")).isNull();
        assertThat(ClientIp.parseLiteral("1::")).hasSize(16);
        assertThat(ClientIp.parseLiteral("1:")).isNull();
        assertThat(ClientIp.parseLiteral(":1")).isNull();
    }

    @Test
    void bracketedFormsAreRejected() {
        for (String value : new String[] {"[::1]", "[2001:db8::1]", "[::1]:443", "[1.2.3.4]", "[::ffff:1.2.3.4]"}) {
            assertThat(ClientIp.parseLiteral(value)).as(value).isNull();
            assertThat(ClientIp.canonicalize(value)).as(value).isNull();
        }
    }

    @Test
    void embeddedIpv4FormsAreAcceptedOnlyAsTheTrailingGroups() {
        assertThat(ClientIp.parseLiteral("::1.2.3.4")).containsExactly(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 4);
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6:1.2.3.4")).hasSize(16);
        assertThat(ClientIp.parseLiteral("1:2:3:4:5:6:7:1.2.3.4")).isNull();
        assertThat(ClientIp.parseLiteral("1.2.3.4:5::")).isNull();
        assertThat(ClientIp.parseLiteral("::1.2.3.04")).isNull();
        assertThat(ClientIp.parseLiteral("::1.2.3.4.5")).isNull();
    }

    @Test
    void canonicalizeFoldsIpv4MappedIpv6AndCompressesAndLowercasesIpv6() {
        assertThat(ClientIp.canonicalize("::ffff:1.2.3.4")).isEqualTo("1.2.3.4");
        assertThat(ClientIp.canonicalize("0:0:0:0:0:ffff:102:304")).isEqualTo("1.2.3.4");
        assertThat(ClientIp.canonicalize("::FFFF:0102:0304")).isEqualTo("1.2.3.4");
        assertThat(ClientIp.canonicalize("2001:0DB8:0000:0000:0000:0000:0000:0001")).isEqualTo("2001:db8::1");
        assertThat(ClientIp.canonicalize("2001:db8:0:0:1:0:0:1")).isEqualTo("2001:db8::1:0:0:1");
        assertThat(ClientIp.canonicalize("2001:db8:0:1:1:1:1:1")).isEqualTo("2001:db8:0:1:1:1:1:1");
        assertThat(ClientIp.canonicalize("1:2:3:4:5:6:7::")).isEqualTo("1:2:3:4:5:6:7:0");
        assertThat(ClientIp.canonicalize("::")).isEqualTo("::");
        assertThat(ClientIp.canonicalize("0:0:0:0:0:0:0:1")).isEqualTo("::1");
        assertThat(ClientIp.canonicalize("1::")).isEqualTo("1::");
        assertThat(ClientIp.canonicalize("64:ff9b::192.0.2.33")).isEqualTo("64:ff9b::c000:221");
        assertThat(ClientIp.canonicalize("evil.example.com")).isNull();
        assertThat(ClientIp.canonicalize(null)).isNull();
    }

    @Test
    void fromReturnsOneCanonicalStringPerAddressWhateverTheSpelling() {
        assertThat(fromXff("::ffff:1.2.3.4")).isEqualTo("1.2.3.4");
        assertThat(fromXff("1.2.3.4")).isEqualTo("1.2.3.4");
        assertThat(fromXff("2001:0DB8::0001")).isEqualTo(fromXff("2001:db8:0:0:0:0:0:1")).isEqualTo("2001:db8::1");
        // A leading-zero spelling is not a valid literal, so it is skipped like any other junk.
        assertThat(fromXff("001.2.3.4")).isEqualTo("127.0.0.1");
        MockHttpServletRequest noInfo = new MockHttpServletRequest();
        noInfo.setRemoteAddr("not-an-ip");
        assertThat(ClientIp.from(noInfo)).isEqualTo(ClientIp.UNKNOWN);
    }

    private static String fromXff(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", value);
        return ClientIp.from(request);
    }
}
