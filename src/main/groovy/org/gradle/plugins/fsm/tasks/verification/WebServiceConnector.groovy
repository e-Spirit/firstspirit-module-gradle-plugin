package org.gradle.plugins.fsm.tasks.verification

import org.glassfish.jersey.client.ClientConfig
import org.glassfish.jersey.media.multipart.FormDataMultiPart
import org.glassfish.jersey.media.multipart.MultiPart
import org.glassfish.jersey.media.multipart.MultiPartFeature
import org.glassfish.jersey.media.multipart.file.FileDataBodyPart
import org.json.JSONArray
import org.json.JSONObject

import javax.ws.rs.ProcessingException
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response

import static IsolationLevel.DEPRECATED_API_USAGE
import static IsolationLevel.IMPL_USAGE
import static IsolationLevel.RUNTIME_USAGE

class WebServiceConnector {

    private final WebTarget uploadTarget

    private final WebTarget analyzeTarget

    private final WebTarget categoriesTarget

    private final String firstSpiritVersion

    private final IsolationLevel isolationLevel

    private Map<String, NewCookie> cookies

    private boolean connectionFailed

    private String connectionFailedMessage

    private JSONObject jsonResult

    private int numberOfRuntimeUsages

    private int numberOfImplUsages

    private int numberOfDeprecations

    WebServiceConnector(final URI uri, final String firstSpiritVersion, final IsolationLevel isolationLevel) {
        final ClientConfig clientConfig = new ClientConfig().register(MultiPartFeature.class)
        final Client client = ClientBuilder.newClient(clientConfig)
        uploadTarget = client.target(uri).path("rest/upload")
        analyzeTarget = client.target(uri).path("rest/analyze")
        categoriesTarget = client.target(uri).path("rest/categories")
        this.firstSpiritVersion = firstSpiritVersion
        this.isolationLevel = isolationLevel
    }

    void checkFiles(final List<File> files) {
        try {
            if (uploadFiles(files) && analyzeUploadedFiles()) {
                parseCategories()
            }
        } catch (final ProcessingException e) {
            markConnectionFailed(e.getMessage())
        }
    }

    private boolean uploadFiles(final List<File> files) {
        MultiPart multiPart = new FormDataMultiPart()

        for (final File file : files) {
            final FileDataBodyPart filePart = new FileDataBodyPart("input-id", file, MediaType.APPLICATION_OCTET_STREAM_TYPE)
            multiPart = multiPart.bodyPart(filePart)
        }

        final Response uploadResponse

        try {
            uploadResponse = uploadTarget.request().post(Entity.entity(multiPart, MediaType.MULTIPART_FORM_DATA_TYPE))
        }
        catch (final ProcessingException e) {
            markConnectionFailed(e.getMessage())
            return false
        }

        if (uploadResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            markConnectionFailed("Upload failed with status '" + uploadResponse.getStatus() + "'")
            return false
        }

        cookies = uploadResponse.getCookies()

        return true
    }

    private boolean analyzeUploadedFiles() {
        final WebTarget target

        if (firstSpiritVersion != null) {
            target = analyzeTarget.queryParam("version", firstSpiritVersion)
        } else {
            target = analyzeTarget
        }

        final Response analyzeResponse = target.request().cookie(cookies.get("JSESSIONID")).get()

        if (analyzeResponse.getStatus() == Response.Status.OK.getStatusCode()) {
            final String analyzeResult = analyzeResponse.readEntity(String.class)
            jsonResult = new JSONObject(analyzeResult)
            return true
        } else {
            markConnectionFailed("Analyze failed with status '" + analyzeResponse.getStatus() + "'")
            return false
        }
    }

    private boolean parseCategories() {
        final Response categoriesResponse = categoriesTarget.request().cookie(cookies.get("JSESSIONID")).get()
        if (categoriesResponse.getStatus() != Response.Status.OK.getStatusCode()) {
            markConnectionFailed("Retrieving categories failed with status '" + categoriesResponse.getStatus() + "'")
            return false
        }

        final String categoriesResult = categoriesResponse.readEntity(String.class)
        final JSONArray categories = new JSONArray(categoriesResult)
        for (int i = 0; i < categories.length(); i++) {
            final JSONObject category = categories.getJSONObject(i)
            if (category.getString("category").contains("@Deprecated")) {
                numberOfDeprecations = category.getInt("count")
            } else if (category.getString("category").contains("internal e-Spirit class")) {
                numberOfRuntimeUsages = category.getInt("count")
            } else if (category.getString("category").contains("not available in isolated mode")) {
                numberOfImplUsages = category.getInt("count")
            }
        }

        return true
    }

    private void markConnectionFailed(final String message) {
        connectionFailed = true
        connectionFailedMessage = message
    }

    boolean isResultValid() {
        if (connectionFailed) {
            return false
        }

        if (isolationLevel == IMPL_USAGE || isolationLevel == RUNTIME_USAGE || isolationLevel == DEPRECATED_API_USAGE) {
            if (numberOfImplUsages > 0) {
                return false
            }
        }

        if (isolationLevel == RUNTIME_USAGE || isolationLevel == DEPRECATED_API_USAGE) {
            if (numberOfRuntimeUsages > 0) {
                return false
            }
        }

        if (isolationLevel == DEPRECATED_API_USAGE) {
            return numberOfDeprecations == 0
        }

        return true
    }

    String resultMessage() {
        if (isResultValid()) {
            return "All files passed the dependency check"
        }
        else {
            if (connectionFailed) {
                return connectionFailedMessage
            }
            else {
                if (isolationLevel == IMPL_USAGE || isolationLevel == RUNTIME_USAGE || isolationLevel == DEPRECATED_API_USAGE) {
                    if (numberOfImplUsages > 0) {
                        return "Usage of classes detected which are not part of the isolated runtime"
                    }
                }

                if (isolationLevel == RUNTIME_USAGE || isolationLevel == DEPRECATED_API_USAGE) {
                    if (numberOfRuntimeUsages > 0) {
                        return "Usage of classes detected which are not part of the public API"
                    }
                }

                if (isolationLevel == DEPRECATED_API_USAGE && numberOfDeprecations > 0) {
                    return "Usage of deprecated API detected"
                } else {
                    return "The result is invalid for build breaker level ${isolationLevel}"
                }
            }
        }
    }


}
