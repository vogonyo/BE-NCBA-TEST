package com.backend.ncba.BE_Demo.service;

import com.backend.ncba.BE_Demo.exception.ResourceNotFoundException;
import com.backend.ncba.BE_Demo.model.*;
import com.backend.ncba.BE_Demo.service.impl.CountryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountryServiceImplTest {

    @Mock
    private ReferenceDataCache referenceDataCache;

    @InjectMocks
    private CountryServiceImpl service;

    private CachedData cachedData;

    @BeforeEach
    void setUp() {
        List<ContinentDto> continents = List.of(
                new ContinentDto("AF", "Africa"),
                new ContinentDto("NA", "North America")
        );
        List<CurrencyDto> currencies = List.of(
                new CurrencyDto("KES", "Kenyan Shilling"),
                new CurrencyDto("USD", "US Dollar"),
                new CurrencyDto("NGN", "Nigerian Naira")
        );
        List<CountryDto> countries = List.of(
                new CountryDto("KE", "Kenya", "Nairobi", "254", "AF", "Africa",
                        "KES", "Kenyan Shilling", "http://flag.ke",
                        List.of(new LanguageDto("SW", "Swahili"), new LanguageDto("EN", "English"))),
                new CountryDto("NG", "Nigeria", "Abuja", "234", "AF", "Africa",
                        "NGN", "Nigerian Naira", "http://flag.ng",
                        List.of(new LanguageDto("EN", "English"))),
                new CountryDto("US", "United States", "Washington DC", "1", "NA", "North America",
                        "USD", "US Dollar", "http://flag.us",
                        List.of(new LanguageDto("EN", "English")))
        );
        cachedData = new CachedData(countries, continents, currencies);
        when(referenceDataCache.getData()).thenReturn(cachedData);
    }

    // ── getCountries ──────────────────────────────────────────────────────────

    @Test
    void getCountries_noFilter_returnsAllSortedByName() {
        PagedResponse<CountryDto> resp = service.getCountries(null, null, null, null, 0, 10, "name", "ASC");
        assertThat(resp.totalElements()).isEqualTo(3);
        assertThat(resp.content()).extracting(CountryDto::name)
                .containsExactly("Kenya", "Nigeria", "United States");
    }

    @Test
    void getCountries_filterByName_returnsMatch() {
        PagedResponse<CountryDto> resp = service.getCountries("ken", null, null, null, 0, 10, "name", "ASC");
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.content().get(0).isoCode()).isEqualTo("KE");
    }

    @Test
    void getCountries_filterByContinent_returnsMatch() {
        PagedResponse<CountryDto> resp = service.getCountries(null, "AF", null, null, 0, 10, "name", "ASC");
        assertThat(resp.totalElements()).isEqualTo(2);
        assertThat(resp.content()).extracting(CountryDto::continentCode).containsOnly("AF");
    }

    @Test
    void getCountries_filterByCurrency_returnsMatch() {
        PagedResponse<CountryDto> resp = service.getCountries(null, null, "USD", null, 0, 10, "name", "ASC");
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.content().get(0).isoCode()).isEqualTo("US");
    }

    @Test
    void getCountries_filterByLanguage_returnsMatch() {
        PagedResponse<CountryDto> resp = service.getCountries(null, null, null, "SW", 0, 10, "name", "ASC");
        assertThat(resp.totalElements()).isEqualTo(1);
        assertThat(resp.content().get(0).isoCode()).isEqualTo("KE");
    }

    @Test
    void getCountries_pagination_returnsCorrectPage() {
        PagedResponse<CountryDto> firstPage  = service.getCountries(null, null, null, null, 0, 2, "name", "ASC");
        PagedResponse<CountryDto> secondPage = service.getCountries(null, null, null, null, 1, 2, "name", "ASC");

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.first()).isTrue();
        assertThat(firstPage.last()).isFalse();
        assertThat(secondPage.content()).hasSize(1);
        assertThat(secondPage.last()).isTrue();
    }

    @Test
    void getCountries_sortDescending_returnsReversedOrder() {
        PagedResponse<CountryDto> resp = service.getCountries(null, null, null, null, 0, 10, "name", "DESC");
        assertThat(resp.content()).extracting(CountryDto::name)
                .containsExactly("United States", "Nigeria", "Kenya");
    }

    @Test
    void getCountries_invalidSortField_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.getCountries(null, null, null, null, 0, 10, "badField", "ASC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid sort field");
    }

    // ── getCountryByIsoCode ───────────────────────────────────────────────────

    @Test
    void getCountryByIsoCode_validCode_returnsCountry() {
        CountryDto result = service.getCountryByIsoCode("KE");
        assertThat(result.name()).isEqualTo("Kenya");
    }

    @Test
    void getCountryByIsoCode_caseInsensitive_returnsCountry() {
        CountryDto result = service.getCountryByIsoCode("ke");
        assertThat(result.isoCode()).isEqualTo("KE");
    }

    @Test
    void getCountryByIsoCode_unknownCode_throwsNotFound() {
        assertThatThrownBy(() -> service.getCountryByIsoCode("XX"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getCountriesByCurrency ────────────────────────────────────────────────

    @Test
    void getCountriesByCurrency_validCode_returnsList() {
        List<CountryDto> result = service.getCountriesByCurrency("USD");
        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("United States");
    }

    @Test
    void getCountriesByCurrency_unknownCode_throwsNotFound() {
        assertThatThrownBy(() -> service.getCountriesByCurrency("XXX"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── reference data lists ─────────────────────────────────────────────────

    @Test
    void getContinents_returnsAll() {
        assertThat(service.getContinents()).hasSize(2);
    }

    @Test
    void getCurrencies_returnsAll() {
        assertThat(service.getCurrencies()).hasSize(3);
    }

    @Test
    void getLanguages_returnsDistinctSorted() {
        // English appears in KE, NG, US → should appear only once
        List<LanguageDto> langs = service.getLanguages();
        long englishCount = langs.stream().filter(l -> l.isoCode().equals("EN")).count();
        assertThat(englishCount).isEqualTo(1);
    }
}
