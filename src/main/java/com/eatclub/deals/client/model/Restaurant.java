package com.eatclub.deals.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** A restaurant as returned by the upstream data source. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Restaurant(
        String objectId,
        String name,
        String address1,
        String suburb,
        List<String> cuisines,
        String imageLink,
        String open,
        String close,
        List<Deal> deals) {

    public List<Deal> deals() {
        return deals == null ? List.of() : deals;
    }
}
