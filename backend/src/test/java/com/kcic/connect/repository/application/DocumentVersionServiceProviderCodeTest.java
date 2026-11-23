package com.kcic.connect.repository.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentVersionServiceProviderCodeTest {

    @Test
    void knownProvidersMatchOnExactDomainOrSubdomain() {
        assertThat(DocumentVersionService.providerCode("sharepoint.com")).isEqualTo("SHAREPOINT");
        assertThat(DocumentVersionService.providerCode("kcic.sharepoint.com")).isEqualTo("SHAREPOINT");
        assertThat(DocumentVersionService.providerCode("onedrive.live.com")).isEqualTo("ONEDRIVE");
        assertThat(DocumentVersionService.providerCode("1drv.ms")).isEqualTo("ONEDRIVE");
        assertThat(DocumentVersionService.providerCode("docs.google.com")).isEqualTo("GOOGLE_DRIVE");
        assertThat(DocumentVersionService.providerCode("drive.google.com")).isEqualTo("GOOGLE_DRIVE");
        assertThat(DocumentVersionService.providerCode("office.com")).isEqualTo("OFFICE");
        assertThat(DocumentVersionService.providerCode("word.officeapps.live.com")).isEqualTo("OFFICE");
    }

    @Test
    void lookalikeHostsAreNotLabelledAsTrustedProviders() {
        assertThat(DocumentVersionService.providerCode("evilsharepoint.com")).isEqualTo("EXTERNAL");
        assertThat(DocumentVersionService.providerCode("sharepoint.com.attacker.net")).isEqualTo("EXTERNAL");
        assertThat(DocumentVersionService.providerCode("notonedrive.live.com")).isEqualTo("EXTERNAL");
        assertThat(DocumentVersionService.providerCode("fake1drv.ms")).isEqualTo("EXTERNAL");
        assertThat(DocumentVersionService.providerCode("docs.google.com.evil.io")).isEqualTo("EXTERNAL");
        assertThat(DocumentVersionService.providerCode("maliciousoffice.com")).isEqualTo("EXTERNAL");
    }
}