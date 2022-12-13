package org.gradle.plugins.fsm.isolationcheck

import de.espirit.mavenplugins.fsmchecker.Category
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpPut
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.impl.DefaultHttpRequestRetryStrategy
import org.apache.hc.client5.http.impl.auth.CredentialsProviderBuilder
import org.apache.hc.client5.http.impl.classic.BasicHttpClientResponseHandler
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.ConnectionClosedException
import org.apache.hc.core5.http.ContentType.APPLICATION_OCTET_STREAM
import org.apache.hc.core5.http.HttpHost
import org.apache.hc.core5.http.HttpStatus
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.net.URIBuilder
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import java.io.Closeable
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.URI
import java.net.UnknownHostException
import java.nio.file.Path
import java.time.Duration
import javax.net.ssl.SSLException

class WebServiceConnector(
    uri: URI,
    private val firstSpiritVersion: String?,
    private val maxBytecodeVersion: Int,
    isolationDetectorUsername: String?,
    isolationDetectorPassword: String?
): Closeable {
    private val baseUri: URI
    private val client: CloseableHttpClient

    init {
        val clientBuilder = HttpClientBuilder.create()

        baseUri = if (uri.toString().endsWith("/")) {
            URI.create(uri.toString().substring(0, uri.toString().length-1))
        } else {
            uri
        }

        isolationDetectorUsername?.let { username ->
            isolationDetectorPassword?.let { password ->
                val credentials = UsernamePasswordCredentials(username, password.toCharArray())
                val provider = CredentialsProviderBuilder.create().add(HttpHost(baseUri.host), credentials).build()
                clientBuilder.setDefaultCredentialsProvider(provider)
            }
        }

        val requestConfig = RequestConfig.custom().setResponseTimeout(Timeout.of(Duration.ofMinutes(5))).build()
        clientBuilder.setDefaultRequestConfig(requestConfig)

        val retryStrategy = object : DefaultHttpRequestRetryStrategy(3, TimeValue.ofSeconds(5),
            listOf(
                InterruptedIOException::class.java,
                UnknownHostException::class.java,
                ConnectException::class.java,
                ConnectionClosedException::class.java,
                NoRouteToHostException::class.java,
                SSLException::class.java),
            listOf(
                HttpStatus.SC_TOO_MANY_REQUESTS,
                HttpStatus.SC_BAD_GATEWAY,
                HttpStatus.SC_SERVICE_UNAVAILABLE,
            )) {}

        clientBuilder.setRetryStrategy(retryStrategy)

        client = clientBuilder.build()
    }


    fun addWhitelistedResource(resourceInfo: String) {
        val addUri = URIBuilder("$baseUri/rest/ignored-resources")
            .appendPathSegments(resourceInfo)
            .build()

        val put = HttpPut(addUri)
        put.entity = StringEntity(resourceInfo)

        client.execute(put, BasicHttpClientResponseHandler())
    }

    fun addContentCreatorComponent(componentName: String) {
        val addUri = URIBuilder("$baseUri/rest/content-creator-components")
            .appendPathSegments(componentName)
            .build()

        val put = HttpPut(addUri)
        put.entity = StringEntity(componentName)

        client.execute(put, BasicHttpClientResponseHandler())
    }

    fun uploadRequest(files: List<Path>) {
        val builder = MultipartEntityBuilder.create()

        files.forEach {
            builder.addBinaryBody("input-id", it.toFile(), APPLICATION_OCTET_STREAM, it.fileName.toString())
        }

        val post = HttpPost("$baseUri/rest/upload")
        post.entity = builder.build()

        client.execute(post, BasicHttpClientResponseHandler())
    }

    fun analyzeRequest(): String {
        val uriBuilder = URIBuilder("$baseUri/rest/analyze")

        firstSpiritVersion?.let { uriBuilder.addParameter("version", firstSpiritVersion) }
        uriBuilder.addParameter("maxBytecodeVersion", maxBytecodeVersion.toString())

        return client.execute(HttpGet(uriBuilder.build()), BasicHttpClientResponseHandler())
    }

    /**
     * Requests an overview of all categories and their respective violation count.
     *
     * @return a Response with JSON Payload containing all violation categories
     */
    fun requestCategories(): String {
        return client.execute(HttpGet("$baseUri/rest/categories"), BasicHttpClientResponseHandler())
    }

    /**
     * Requests violation details for one specific category.
     *
     * @return a Response with JSON Payload containing all violations for a category
     */
    fun requestCategory(category: Category): String {
        val categoryUri = URIBuilder("$baseUri/rest/classesforcategory")
            .addParameter("category", category.name)
            .build()

        return client.execute(HttpGet(categoryUri), BasicHttpClientResponseHandler())
    }

    override fun close() {
        client.close()
    }

}