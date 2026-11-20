package com.eatclub.deals.client.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A deal as returned by the upstream data source.
 * <p>
 * The source is inconsistent about deal hours: some deals use {@code open}/{@code close},
 * some use {@code start}/{@code end}, and some have no hours at all (meaning the deal
 * runs for the restaurant's opening hours). Both spellings are mapped onto
 * {@link #open()} / {@link #close()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Deal(
        String objectId,
        String discount,
        String dineIn,
        String lightning,
        @JsonAlias("start") String open,
        @JsonAlias("end") String close,
        String qtyLeft) {
}
