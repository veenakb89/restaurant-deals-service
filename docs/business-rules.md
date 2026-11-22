# Business rules: questions and answers

How the service decides which deals are active and when the peak is. **Every rule here is an assumption, made where the task didn't say, and describes how the code behaves today. All of them are to be confirmed with the product owner.** The request and response format (accepted `timeOfDay` values, field names, order) is in [api.md](api.md).

**Contents:** [When is a deal active?](#when-is-a-deal-active) · [Times and boundaries](#times-and-boundaries) · [Availability](#availability) · [Peak time](#peak-time) · [Data quality](#data-quality)

## When is a deal active?

A deal is only ever active while its restaurant is open. A deal's own hours must have both `open` and `close`, or neither.

| Question | Input | Expected output | Explanation |
|---|---|---|---|
| A deal has no hours of its own. When is it active? | Masala Kitchen 3pm–9pm, its second deal has no hours | Active 3pm–9pm | The deal uses its restaurant's hours. |
| Some deals use `start`/`end`, others `open`/`close`. Are they handled differently? | Kekou deal `start` 2pm, `end` 9pm | Active 2pm–9pm | No. Both spellings mean the deal's hours. |
| A deal starts before its restaurant opens. When is it active? | Vrindavan 6pm–9pm, deal 3pm–9pm | Active 6pm–9pm | It starts when the restaurant opens. |
| A deal ends after its restaurant closes. When is it active? | Restaurant 9am–10pm, deal 5pm–11pm | Active 5pm–10pm | It stops when the restaurant closes. |
| A deal's hours are entirely outside its restaurant's hours. Is it active? | Restaurant 9am–5pm, deal 6pm–8pm | Never active | The restaurant is closed for the whole deal. |
| A deal has only `open` or only `close`, or one of them is blank. When is it active? | Restaurant 9am–10pm, deal `open` 6pm and no `close` | **Option 1 (current):** active 9am–10pm.<br>**Option 2:** active 6pm–10pm.<br>Either way, a warning is logged. | **Option 1:** ignore the partial value, so the deal runs for its restaurant's hours, the same as a deal with no hours. The value isn't parsed, so even an unreadable one like `"abc"` is ignored.<br>**Option 2:** fill in the missing side from the restaurant: no `close` means the restaurant's closing time, and no `open` means its opening time. This uses the data given, but needs extra rules, e.g. for an `open` after the restaurant closes.<br>No deal in the current feed has partial hours. |
| A restaurant has no opening hours, or blank ones. Is it open? | Restaurant with no `open`/`close`, deal with no hours | Active all day | The restaurant is treated as open all day. |
| A restaurant has only an opening time. Is it open? | Restaurant `open` 9am, no `close` | Active all day | Incomplete restaurant hours count as no hours. |

Times in "Expected output" include the start and exclude the end: "Active 6pm–10pm" means active at 6:00pm and closed at 10:00pm.

## Times and boundaries

**Q: Is a deal active at exactly its opening time?**
A: Yes. A restaurant open 3pm–9pm is open at 3:00pm. Example: Masala Kitchen (3pm–9pm) is in the 3:00pm results.

**Q: Is a deal active at exactly its closing time?**
A: No. A restaurant open 3pm–9pm is closed at 9:00pm. Examples: OzzyThai (8am–3pm) isn't in the 3:00pm results, and Masala Kitchen isn't in the 9:00pm results.

**Q: How are hours that cross midnight handled, e.g. 6pm–2am?**
A: The window continues past midnight: open from 6:00pm through 1:59am, closed from 2:00am. This works for both restaurant hours and deal hours.

**Q: The opening and closing times are the same, e.g. 3pm–3pm. When is it open?**
A: All day. For a deal, that means it's active for all of its restaurant's opening hours. For a restaurant, it means open around the clock.

**Q: Which date and time zone does `timeOfDay` use?**
A: None. `timeOfDay` is only a time of day, compared directly with the times in the feed, which also have no date or time zone. The day of the week isn't considered.

## Availability

**Q: A deal has `qtyLeft` of `"0"`. Is it returned?**
A: No. Any deal with `qtyLeft` of 0 or less (e.g. `"0"`, `"-1"`) is left out. Surrounding spaces are ignored, so `" 0 "` is also left out. No deal in the current feed has 0.

**Q: A deal's `qtyLeft` is missing, blank, or not a number (e.g. `"abc"`). Is it returned?**
A: Yes, it's kept. If the value isn't a number, a warning is logged; if it's missing or blank, nothing is logged.

## Peak time

**Q: What does the peak count: deals, or the quantity left?**
A: Deals: each active, not-sold-out deal counts once. This is the biggest decision for this API and the first one to confirm with the product owner.

| Option | How it works | Pros | Cons |
|---|---|---|---|
| **Count deals** (chosen) | Each active deal counts 1; sold-out deals (`qtyLeft` 0 or less) are excluded | Matches "when are the most deals available". Consistent with `GET /api/v1/deals`: asking for any time inside the peak returns the most deals. | A deal with 1 left counts the same as one with 50 left |
| Weight by `qtyLeft` | A deal counts as its quantity left, e.g. 8 left counts 8 | Measures capacity: when the most customers can redeem a deal | `qtyLeft` is today's stock, not the stock at that time of day. One large deal can outweigh many small ones. Missing or non-numeric values need a made-up weight. The peak may not be when `GET /api/v1/deals` returns the most deals. |
The task asks when "most deals are available", so restaurants aren't counted: a restaurant with two active deals counts twice, and the two deals that share an ID (Kekou and Gyoza Gyoza) count as two.

**Q: How is the peak worked out?**
A: With the same rules as `GET /api/v1/deals`, so asking for any time inside the peak returns the most deals. A deal counts only while both it and its restaurant are open, sold-out deals don't count, and unreadable deals are skipped. The number of active deals can only change at an opening or closing time, so the count is taken at each of those times. Neighbouring stretches with the same highest count are merged into one window. Example: with the current feed the peak is 6:00pm–9:00pm, with 9 deals.

**Q: Is the end of the peak included?**
A: No, the same as opening hours. "6:00pm–9:00pm" means the peak includes 6:00pm and 8:59pm, but not 9:00pm.

**Q: Several separate windows have the same highest count. Which is returned?**
A: The one that starts earliest in the day, counting from midnight. Example: one deal 9am–11am and one deal 2pm–4pm gives 9:00am–11:00am.

**Q: Can the peak cross midnight?**
A: Yes. Example: a late-night peak is returned as `"11:00pm"`–`"1:00am"`.

**Q: The highest count lasts all day. What's returned?**
A: `"12:00am"`–`"12:00am"`. Equal start and end mean all day, as with opening hours.

**Q: No deal is ever active. What's returned?**
A: `200 OK` with both fields `null`: `{"peakTimeStart": null, "peakTimeEnd": null}`.

**Q: How long is the peak window? Is it a fixed length, e.g. one hour?**
A: No. The data decides the length. The window starts when the number of active deals reaches its highest value and ends when it drops. The current feed gives 3 hours (6:00pm–9:00pm), but the peak could be 1 minute or 24 hours.

Options considered:

| Option | How it works |
|---|---|
| **Maximum overlap** (chosen) | The window where the most deals are active at the same time, however long it is |
| Fixed-length window | The fixed-length window (e.g. 60 minutes) where the most deals are active |

Why maximum overlap was chosen:
- Deals are time ranges, and "when are the most deals available" asks how many are active at once.
- The response has both a start and an end. With a fixed length, the end would just be the start plus that length.
- The task doesn't give a length, so a fixed length would be an assumption with no basis.
- It's exact. A fixed one-hour window would return 6:00pm–7:00pm and miss 7:00pm–9:00pm, which has the same 9 deals.

Trade-off: a very short peak beats a longer one with slightly fewer deals. For example, 10 deals for 1 minute beats 9 deals for 5 hours.

Possible improvements, if the product owner wants them:
- **Minimum length:** ignore peaks shorter than e.g. 30 minutes.
- **Fixed-length option:** an optional `?duration=60` parameter for "the best 60 minutes". This would change the contract, since the task says the API takes no parameters.
- **More detail:** add the number of deals at the peak, or return every tied window instead of only the earliest. This would also change the contract.

## Data quality

**Q: A deal has hours that can't be read, e.g. `"not-a-time"`. What happens?**
A: That deal is skipped, a warning is logged, and every other deal is returned as normal.

**Q: A restaurant has opening hours that can't be read. What happens?**
A: All of that restaurant's deals are skipped, with one warning logged per deal. Other restaurants are unaffected.

**Q: A restaurant has no `deals` list, or the feed has no `restaurants` list. What happens?**
A: That restaurant, or the whole feed, contributes no deals. The response is `200` with whatever deals remain, possibly an empty list.

**Q: Two deals have the same `dealObjectId`. Are both returned?**
A: Yes. They're handled as separate deals. Example: Kekou and Gyoza Gyoza each have a deal with ID `B5713CD0-1111-40C7-AFC3-7D46D26B00BF`, with different hours and dine-in settings, and both appear in the 6:00pm results. Each result includes both `restaurantObjectId` and `dealObjectId`, so the two can be told apart.

**Q: The data source returns an empty response body. What happens?**
A: It's treated as a feed with no restaurants, so the response is `200` with an empty `deals` list.
