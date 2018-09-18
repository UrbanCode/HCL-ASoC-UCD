/**
 * (c) Copyright IBM Corporation 2015.
 * (c) Copyright HCL Technologies Ltd. 2018. All Rights Reserved.
 * This is licensed under the following license.
 * The Eclipse Public 1.0 License (http://www.eclipse.org/legal/epl-v10.html)
 * U.S. Government Users Restricted Rights:  Use, duplication or disclosure restricted by GSA ADP Schedule Contract with IBM Corp.
 */

package com.urbancode.air.plugin.AppScanSaaS

import com.urbancode.commons.httpcomponentsutil.CloseableHttpClientBuilder

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.client.methods.HttpDelete
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPut
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.entity.StringEntity
import org.apache.http.util.EntityUtils
import org.apache.http.entity.mime.MultipartFormEntity
import org.apache.http.Header
import org.apache.http.HttpEntity
import org.apache.http.HttpResponse


class RestClientHelper {
    protected CloseableHttpClientBuilder clientBuilder  // Configuration for building a client
    protected CloseableHttpClient client    // Client will be built in the constructor
    protected String serverUrl  // Base URL of the server listening for HTTP requests
    protected HashMap requestHeaders = [:]  // Request headers 'def headers = ["key1":"val1", "key2":"val2"]'
    protected IntRange successRange = 100..300  // Range of successful exit codes

    public RestClientHelper(String serverUrl) {
        this(serverUrl, null, null, false)
    }

    public RestClientHelper(String serverUrl, boolean trustAllCerts) {
        this(serverUrl, null, null, trustAllCerts, null)
    }

    public RestClientHelper(String serverUrl, String username, String password, boolean trustAllCerts) {
        this (serverUrl, username, password, trustAllCerts, null)
    }

    /**
     * @param serverUrl Base URL of the server
     * @param username User for basic auth
     * @param password Password for basic auth
     * @param trustAllCerts Set true to ignore SSL verification
     * @param closure Groovy closure to make changes to builder before client is built
     */
    public RestClientHelper(
        String serverUrl,
        String username,
        String password,
        boolean trustAllCerts,
        Closure closure)
    {
        this.serverUrl = serverUrl
        clientBuilder = new CloseableHttpClientBuilder()

        if (username) {
            clientBuilder.setUsername(username)

            if (password) {
                clientBuilder.setPassword(password)
            }
        }

        if (trustAllCerts) {
            clientBuilder.setTrustAllCerts(trustAllCerts)
        }

        if (serverUrl) {
            if (!serverUrl.substring(0, 7).equalsIgnoreCase("http://")
                && !serverUrl.substring(0, 8).equalsIgnoreCase("https://"))
            {
                println("An HTTP protocol (http:// or https://) must be prepended to server URL: ${serverUrl}")
                throw new RuntimeException("Missing HTTP protocol in server URL: ${serverUrl}.")
            }

            if (serverUrl.endsWith("/")) {
                serverUrl = serverUrl.substring(0, serverUrl.length() - 1)
            }
        }
        else {
            println("No server URL was specified, a connection cannot be created.")
            throw new RuntimeException("Missing server URL.")
        }

        /* Change settings directly on the client builder before building the client */
        if (closure) {
            closure(clientBuilder)
        }

        client = clientBuilder.buildClient()

        /* Default headers can be overwritten if specified in requestHeaders */
        requestHeaders.put("Content-Type", "application/json")
        requestHeaders.put("Accept", "application/json,application/xml")
    }

    /**
     * Directly set a map of HTTP request headers, replacing all existing ones
     *
     * @param headers Map of headers to set 'def headers = ["key1":"val1", "key2":"val2"]'
     */
    public void setRequestHeaders(HashMap headers) {
        this.requestHeaders = headers
    }

    /**
     * Add or replace an existing request header
     *
     * @param name
     * @param value
     */
    public void addRequestHeader(String name, String value) {
        this.requestHeaders.put(name, value)
    }

    /**
     * Remove a request header if it exists
     *
     * @param name
     */
    public void removeRequestHeader(String name) {
        this.requestHeaders.remove(name)
    }

    /**
     * Execute an HTTP PUT request with java.util.properties
     * @param endpoint
     * @param props
     * @return
     */
    public HttpResponse doPutRequest(String endpoint, Properties props) {
        return doPutRequest(endpoint, jsonFromProps(props))
    }

    /**
     * Execute an HTTP PUT request with JSON
     *
     * @param endpoint An absolute URL endpoint, or one relative to the serverUrl
     * @param jsonString String representation of a JSON object
     * @return The HTTP response
     */
    public HttpResponse doPutRequest(String endpoint, String jsonString) {
        HttpPut request = new HttpPut(getAbsoluteUrl(endpoint))
        HttpResponse response = doRequest(request, jsonString)

        return response
    }

    /**
     * Execute an HTTP POST request with java.util.properties request body
     * @param endpoint An absolute URL endpoint, or one relative to the serverUrl
     * @param props
     * @return
     */
    public HttpResponse doPostRequest(String endpoint, Properties props) {
        return doPostRequest(endpoint, jsonFromProps(props))
    }

    /**
     * Execute an HTTP POST request
     *
     * @param endpoint An absolute url endpoint, or one relative to the serverUrl
     * @param jsonString String representation of a JSON object
     * @return The HTTP response
     */
    public HttpResponse doPostRequest(String endpoint, String jsonString) {
        HttpPost request = new HttpPost(getAbsoluteUrl(endpoint))
        HttpResponse response = doRequest(request, jsonString)

        return response
    }

    /**
     * Execute an HTTP POST request
     *
     * @param endpoint An absolute url endpoint, or one relative to the serverUrl
     * @param jsonString String representation of a JSON object
     * @return The HTTP response
     */
    public HttpResponse doPostRequest(String endpoint, HttpEntity httpEntity) {
        HttpPost request = new HttpPost(getAbsoluteUrl(endpoint))
        HttpResponse response = doRequest(request, httpEntity)

        return response
    }

    /**
     * Execute an HTTP GET request
     * @param endpoint
     * @return
     */
    public HttpResponse doGetRequest(String endpoint) {
        HttpGet request = new HttpGet(getAbsoluteUrl(endpoint))
        HttpResponse response = doRequest(request)

        return response
    }

    /**
     * @param request
     * @return
     */
    public HttpResponse doDeleteRequest(String endpoint) {
        HttpDelete request = new HttpDelete(getAbsoluteUrl(endpoint))
        HttpResponse response = doRequest(request)

        return response
    }


    /**
     * Execute a general HTTP request
     *
     * @param request The HTTP request to send
     * @return The HTTP response
     */
    protected HttpResponse doRequest(HttpUriRequest request) {
        return doRequest(request, (HttpEntity) null)
    }

    /**
     * Specify a JSON string to provide a string entity to the HTTP request
     *
     * @param request
     * @param contentString
     * @param headers Any additional headers to set for the request
     * @return The HTTP response
     */
    protected HttpResponse doRequest(HttpUriRequest request, String contentString) {
        HttpEntity entity = null

        if (contentString) {
            StringEntity input

            try {
                entity = new StringEntity(contentString)
            }
            catch (UnsupportedEncodingException ex) {
                println("Unsupported characters in http request content: ${contentString}")
                throw new RuntimeException(ex)
            }
        }

        return doRequest(request, entity)
    }

    protected HttpResponse doRequest(HttpUriRequest request, HttpEntity httpEntity) {
        if (requestHeaders) {
            for (def header : requestHeaders) {
                request.addHeader(header.key, header.value)
            }
        }

        if (httpEntity) {
            request.setEntity(httpEntity)
        }

        HttpResponse response = client.execute(request)

        def statusLine = response.getStatusLine()
        def statusCode = statusLine.getStatusCode()

        if (!successRange.contains(statusCode)) {
            throw new Exception("HTTP request failed with a response code of ${statusCode}: "
                + "${statusLine.getReasonPhrase()}: " + response.entity?.content?.text)
        }

        return response
    }

    /**
     * Get a string representation of a JSON object from a java.util.Properties object
     *
     * Example java.util.Properties configuration:
     *  Properties props = new Properties()
     *  props.put("people", [["name":"Bob", "age":22], ["name":"Bill", "age":45]])
     *
     * The example properties will return:
     *  {"people":"[{name=Bob, age=22}, {name=Bill, age=45}]"}
     *
     * @param properties
     * @return
     */
    protected String jsonFromProps(Properties properties) {
        JsonBuilder builder = new JsonBuilder()
        builder(properties)

        return builder.toString()
    }

    /**
     * Acquire an absolute URL path from either a relative or absolute one
     * @param endpoint
     * @return
     */
    protected String getAbsoluteUrl(String endpoint) {
        /* Will return the absolute path if it's already provided */
        if (!endpoint.substring(0, 7).equalsIgnoreCase("http://")
            && !endpoint.substring(0, 8).equalsIgnoreCase("https://"))
        {
            if (!endpoint.startsWith('/')) {
                endpoint = '/' + endpoint
            }

            return serverUrl + endpoint
        }
        else {
            return endpoint
        }
    }

    //return an unparsed map of JSON properties from an http response
    public def parseResponse(HttpResponse response) {
        String json = EntityUtils.toString(response.getEntity())
        JsonSlurper slurper = new JsonSlurper()

        return slurper.parseText(json)
    }

    /**
     * Test connection to the server
     */
    public void pingServer() {
        HttpGet ping = new HttpGet(serverUrl)
        HttpResponse response = client.execute(ping)
        def statusLine = response.getStatusLine()
        def statusCode = statusLine.getStatusCode()

        if (statusCode != 200) {
            println("Connection to server URL: ${serverUrl} failed with an http status code of ${statusCode}: ${statusLine.getReasonPhrase()}.")
            throw new RuntimeException("${response.entity?.content?.text}")
        }
        else {
            println("Connection to server URL: ${serverUrl} successful.")
        }
    }
}
