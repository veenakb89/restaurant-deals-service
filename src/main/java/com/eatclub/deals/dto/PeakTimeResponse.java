package com.eatclub.deals.dto;

import com.eatclub.deals.time.TimeOfDayFormatter;
import com.eatclub.deals.time.TimeWindow;
import io.swagger.v3.oas.annotations.media.Schema;

/** Response body of {@code GET /api/v1/deals/peak-time}: the window during which the most deals are active. */
@Schema(description = "The time window during which the most deals are active. The start is inclusive and the end "
        + "exclusive. Equal start and end mean all day. Both are null when no deal is ever active.")
public record PeakTimeResponse(

        @Schema(description = "Start of the peak window (inclusive)", example = "6:00pm", nullable = true)
        String peakTimeStart,

        @Schema(description = "End of the peak window (exclusive)", example = "9:00pm", nullable = true)
        String peakTimeEnd) {

    /** No deal is ever active, so there is no peak. */
    public static final PeakTimeResponse NO_PEAK = new PeakTimeResponse(null, null);

    /** The response for a peak window, with times in the feed's format (e.g. {@code 6:00pm}). */
    public static PeakTimeResponse fromWindow(TimeWindow window) {
        return new PeakTimeResponse(TimeOfDayFormatter.format(window.start()), TimeOfDayFormatter.format(window.end()));
    }
}
