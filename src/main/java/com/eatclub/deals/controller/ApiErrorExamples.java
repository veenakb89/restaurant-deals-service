package com.eatclub.deals.controller;

/** Example error bodies shown in the API docs, shared by every endpoint that can return them. */
final class ApiErrorExamples {

    static final String INVALID_TIME_OF_DAY = """
            {
              "error": {
                "type": "invalid_request_error",
                "code": "invalid_time_of_day",
                "message": "Invalid time of day: '25:00'. Expected a time such as '3:00pm', '10:30AM' or '15:00'.",
                "param": "timeOfDay"
              }
            }""";

    static final String MISSING_PARAMETER = """
            {
              "error": {
                "type": "invalid_request_error",
                "code": "missing_parameter",
                "message": "Required parameter 'timeOfDay' is not present.",
                "param": "timeOfDay"
              }
            }""";

    static final String UNAUTHORIZED = """
            {
              "error": {
                "type": "invalid_request_error",
                "code": "unauthorized",
                "message": "A valid access token is required: send 'Authorization: Bearer <token>'."
              }
            }""";

    static final String FORBIDDEN = """
            {
              "error": {
                "type": "invalid_request_error",
                "code": "forbidden",
                "message": "The access token does not allow this request."
              }
            }""";

    static final String INTERNAL_ERROR = """
            {
              "error": {
                "type": "api_error",
                "code": "internal_error",
                "message": "An unexpected error occurred."
              }
            }""";

    static final String FEED_UNAVAILABLE = """
            {
              "error": {
                "type": "api_error",
                "code": "feed_unavailable",
                "message": "Deal data source is currently unavailable."
              }
            }""";

    private ApiErrorExamples() {
    }
}
