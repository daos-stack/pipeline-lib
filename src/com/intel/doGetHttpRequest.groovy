/* groovylint-disable DuplicateStringLiteral, UnnecessaryGetter, UnnecessarySetter */
// src/com/intel/doGetHttpRequest.groovy
package com.intel

/**
 * doGetHttpRequest.groovy
 *
 * Routine to fetch from a URL over HTTP
 */

// This is working but should be looked at later.
/* groovylint-disable-next-line CompileStatic */
class HttpResponse {

    String body
    String message
    Integer statusCode
    boolean failure = false

    public HttpResponse(HttpURLConnection connection) {
        this['statusCode'] = connection.responseCode
        this['message'] = connection.responseMessage

        println message
        if (statusCode == 200 || statusCode == 201) {
            this['body'] = connection.content.text
        } else {
            this['failure'] = true
            this['body'] = connection.getErrorStream().text
        }

        connection = null
    }

}

String doGetHttpRequest(String requestUrl) {
  /**
   * Fetch over HTTP method.
   *
   * @param requestUrl URL to fetch
   * @return HttpResponse
   */
    URL url = new URL(requestUrl)
    HttpURLConnection connection = url.openConnection()

    connection.setRequestMethod('GET')

    //get the request
    connection.connect()

    //parse the response
    // Jenkins requires us to use 'def' here.
    /* groovylint-disable-next-line NoDef, VariableTypeRequired */
    def resp = new HttpResponse(connection)

    if (resp.isFailure()) {
        error("\nGET from URL: $requestUrl\n  HTTP Status: $resp.statusCode\n" +
              "  Message: $resp.message\n  Response Body: $resp.body")
    }

    return resp.getBody()
}
