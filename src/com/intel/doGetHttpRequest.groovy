// src/com/intel/doGetHttpRequest.groovy

package com.intel

/**
 * doGetHttpRequest.groovy
 *
 * Routine to fetch from a URL over HTTP
 */

class HttpResponse {

    String body;
    String message;
    Integer statusCode;
    boolean failure = false;

    public HttpResponse(HttpURLConnection connection) {
        this['statusCode'] = connection.responseCode;
        this['message'] = connection.responseMessage;

        println message;
        if(statusCode == 200 || statusCode == 201) {
            this['body'] = connection.content.text;
        }else{
            this['failure'] = true;
            this['body'] = connection.getErrorStream().text;
        }

        connection = null;
    }
}

def doGetHttpRequest(String requestUrl) {
  /**
   * Fetch over HTTP method.
   *
   * @param requestUrl URL to fetch
   * @return HttpResponse
   */
    URL url = new URL(requestUrl);
    HttpURLConnection connection = url.openConnection();

    connection.setRequestMethod("GET");

    //get the request
    connection.connect();

    //parse the response
    HttpResponse resp = new HttpResponse(connection);

    if(resp.isFailure()) {
        error("\nGET from URL: $requestUrl\n  HTTP Status: $resp.statusCode\n  Message: $resp.message\n  Response Body: $resp.body");
    }

    return resp.getBody();
}
