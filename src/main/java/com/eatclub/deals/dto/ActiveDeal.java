package com.eatclub.deals.dto;

import com.eatclub.deals.client.model.Deal;
import com.eatclub.deals.client.model.Restaurant;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A single active deal, flattened with its restaurant's details.
 * <p>
 * Note: the JSON field {@code restarantSuburb} is spelled exactly as in the challenge's
 * response contract.
 */
@Schema(description = "A deal that is active at the requested time, together with its restaurant's details. "
        + "All values are passed through from the source feed as strings.")
@JsonPropertyOrder({
        "restaurantObjectId", "restaurantName", "restaurantAddress1", "restarantSuburb",
        "restaurantOpen", "restaurantClose", "dealObjectId", "discount", "dineIn", "lightning", "qtyLeft"
})
public record ActiveDeal(

        @Schema(description = "Restaurant ID", example = "DEA567C5-F64C-3C03-FF00-E3B24909BE00")
        String restaurantObjectId,

        @Schema(description = "Restaurant name", example = "Masala Kitchen")
        String restaurantName,

        @Schema(description = "Restaurant street address", example = "55 Walsh Street")
        String restaurantAddress1,

        @Schema(description = "Restaurant suburb. The field name is misspelled on purpose, to match the response contract.",
                example = "Lower East")
        @JsonProperty("restarantSuburb") String restaurantSuburb,

        @Schema(description = "Restaurant opening time, as given by the source", example = "3:00pm")
        String restaurantOpen,

        @Schema(description = "Restaurant closing time, as given by the source", example = "9:00pm")
        String restaurantClose,

        @Schema(description = "Deal ID", example = "DEA567C5-0000-3C03-FF00-E3B24909BE00")
        String dealObjectId,

        @Schema(description = "Discount percentage, as a string", example = "50")
        String discount,

        @Schema(description = "Whether the deal is valid for dine-in: \"true\" or \"false\"", example = "false")
        String dineIn,

        @Schema(description = "Whether this is a lightning (limited-time) deal: \"true\" or \"false\"", example = "true")
        String lightning,

        @Schema(description = "Number of deals remaining, as a string", example = "5")
        String qtyLeft) {

    /** Builds the response item from the feed's restaurant and deal, passing every value through unchanged. */
    public static ActiveDeal fromFeed(Restaurant restaurant, Deal deal) {
        return new ActiveDeal(
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
