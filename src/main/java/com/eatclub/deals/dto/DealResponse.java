package com.eatclub.deals.dto;

import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * A single active deal, flattened with its restaurant's details.
 * <p>
 * Note: the JSON field {@code restarantSuburb} is spelled exactly as in the challenge's
 * response contract.
 */
@JsonPropertyOrder({
        "restaurantObjectId", "restaurantName", "restaurantAddress1", "restarantSuburb",
        "restaurantOpen", "restaurantClose", "dealObjectId", "discount", "dineIn", "lightning", "qtyLeft"
})
public record DealResponse(
        String restaurantObjectId,
        String restaurantName,
        String restaurantAddress1,
        @JsonProperty("restarantSuburb") String restaurantSuburb,
        String restaurantOpen,
        String restaurantClose,
        String dealObjectId,
        String discount,
        String dineIn,
        String lightning,
        String qtyLeft) {

    public static DealResponse from(Restaurant restaurant, Deal deal) {
        return new DealResponse(
                restaurant.objectId(),
                restaurant.name(),
                restaurant.address1(),
                restaurant.suburb(),
                restaurant.open(),
                restaurant.close(),
                deal.objectId(),
                deal.discount(),
                deal.dineIn(),
                deal.lightning(),
                deal.qtyLeft());
    }
}
