// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.authentication;

import com.microsoft.alm.helpers.Action;
import com.microsoft.alm.helpers.Debug;
import com.microsoft.alm.helpers.Environment;
import com.microsoft.alm.helpers.Guid;
import com.microsoft.alm.helpers.HttpClient;
import com.microsoft.alm.helpers.NotImplementedException;
import com.microsoft.alm.helpers.StringContent;
import com.microsoft.alm.helpers.StringHelper;
import com.microsoft.alm.helpers.Trace;
import com.microsoft.alm.secret.Credential;
import com.microsoft.alm.secret.Token;
import com.microsoft.alm.secret.TokenType;
import com.microsoft.alm.secret.VsoTokenScope;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class VsoAzureAuthority extends AzureAuthority implements IVsoAuthority
{
    /**
     * The maximum wait time for a network request before timing out
     */
    public static final int RequestTimeout = 15 * 1000; // 15 second limit

    public VsoAzureAuthority() { this (null); }
    public VsoAzureAuthority(final String authorityHostUrl)
    {
        super();
        if (authorityHostUrl != null)
        {
            this.authorityHostUrl = authorityHostUrl;
        }
    }

    /**
     * Generates a personal access token for use with Visual Studio Online.
     *
     * @param targetUri           The uniform resource indicator of the resource access tokens are being requested for.
     * @param accessToken
     * @param tokenScope
     * @param requireCompactToken
     * @return
     */
    @Override public Token generatePersonalAccessToken(final URI targetUri, final Token accessToken, final VsoTokenScope tokenScope, final boolean requireCompactToken)
    {
        Debug.Assert(targetUri != null, "The targetUri parameter is null");
        Debug.Assert(accessToken != null && !StringHelper.isNullOrWhiteSpace(accessToken.Value) && (accessToken.Type == TokenType.Access || accessToken.Type == TokenType.Federated), "The accessToken parameter is null or invalid");
        Debug.Assert(tokenScope != null, "The tokenScope parameter is invalid");

        Trace.writeLine("VsoAzureAuthority::generatePersonalAccessToken");

        try
        {
            // TODO: 449524: create a `HttpClient` with a minimum number of redirects, default creds, and a reasonable timeout (access token generation seems to hang occasionally)
            final HttpClient client = new HttpClient(Global.getUserAgentRenamed());
            Trace.writeLine("   using token to acquire personal access token");
            accessToken.contributeHeader(client.Headers);

            if (populateTokenTargetId(targetUri, accessToken))
            {
                final URI requestUrl = createPersonalAccessTokenRequestUri(client, targetUri, requireCompactToken);

                final StringContent content = getAccessTokenRequestBody(targetUri, accessToken, tokenScope);
                final HttpURLConnection response = client.post(requestUrl, content);
                if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
                {
                    final String responseText = HttpClient.readToString(response);

                    final Token token = parsePersonalAccessTokenFromJson(responseText);
                    if (token != null)
                    {
                        Trace.writeLine("   personal access token acquisition succeeded.");
                    }
                    return token;
                }
            }
        }
        catch (final IOException e)
        {
            throw new Error(e);
        }
        return null;
    }

    private URI createPersonalAccessTokenRequestUri(final HttpClient client, final URI targetUri,
                                                    final boolean requireCompactToken) throws IOException
    {
        final String SessionTokenUrl = "_apis/token/sessiontokens?api-version=1.0";
        final String CompactTokenUrl = SessionTokenUrl + "&tokentype=compact";

        Debug.Assert(client != null, "The client is null");

        final URI identityServiceUri = getIdentityServiceUri(client, targetUri);
        if (identityServiceUri == null)
        {
            throw new RuntimeException("Failed to find Identity Service for " + targetUri.toString());
        }

        String url = identityServiceUri.toString();

        if (!url.endsWith("/")) {
            url += "/";
        }

        url += requireCompactToken ? CompactTokenUrl : SessionTokenUrl;

        return URI.create(url);
    }

    private URI getIdentityServiceUri(final HttpClient client, final URI targetUri) throws IOException
    {
        final String locationServiceUrlFormat = "https://%1$s/_apis/ServiceDefinitions/LocationService2/951917AC-A960-4999-8464-E3F0AA25B381?api-version=1.0";

        Debug.Assert(client != null, ("The client parameter is null."));
        Debug.Assert(targetUri != null && targetUri.isAbsolute(), "The targetUri parameter is null or invalid");

        final String locationServiceUrl = String.format(locationServiceUrlFormat, targetUri.getHost());
        URI identityServiceUri = null;

        final HttpURLConnection response = client.get(URI.create(locationServiceUrl));
        if (response.getResponseCode() == HttpURLConnection.HTTP_OK)
        {
            final String responseText = HttpClient.readToString(response);

            // Identity Service uri is the "location" field.
            identityServiceUri = parseLocationFromJson(responseText);
            if (identityServiceUri != null)
            {
                Trace.writeLine("   parsed identity service url: " + identityServiceUri);
            }
        }

        return identityServiceUri;
    }

    public boolean populateTokenTargetId(final URI targetUri, final Token accessToken)
    {
        Debug.Assert(targetUri != null && targetUri.isAbsolute(), "The targetUri parameter is null or invalid");
        Debug.Assert(accessToken != null && !StringHelper.isNullOrWhiteSpace(accessToken.Value) && (accessToken.Type == TokenType.Access || accessToken.Type == TokenType.Federated), "The accessToken parameter is null or invalid");

        Trace.writeLine("VsoAzureAuthority::populateTokenTargetId");

        String resultId = null;
        try
        {
            // create an request to the VSO deployment data end-point
            final HttpURLConnection request = createConnectionDataRequest(targetUri, accessToken);

            // send the request and wait for the response
            final String content = HttpClient.readToString(request);

            resultId = parseInstanceIdFromJson(content);
        }
        catch (final IOException e)
        {
            throw new Error(e);
        }

        final AtomicReference<UUID> instanceId = new AtomicReference<UUID>();
        if (Guid.tryParse(resultId, instanceId))
        {
            Trace.writeLine("   target identity is " + resultId);
            accessToken.setTargetIdentity(instanceId.get());

            return true;
        }

        return false;
    }

    /**
     * Validates that {@link Credential} are valid to grant access to the Visual Studio
     * Online service represented by the {@literal targetUri} parameter.
     *
     * @param targetUri   Uniform resource identifier for a VSO service.
     * @param credentials {@link Credential} expected to grant access to the VSO service.
     * @return True if successful; otherwise false.
     */
    @Override public boolean validateCredentials(final URI targetUri, final Credential credentials)
    {
        Debug.Assert(targetUri != null && targetUri.isAbsolute(), "The targetUri parameter is null or invalid");
        Debug.Assert(credentials != null, "The credentials parameter is null or invalid");

        Trace.writeLine("VsoAzureAuthority::validateCredentials");

        try
        {
            // create an request to the VSO deployment data end-point
            final HttpURLConnection request = createConnectionDataRequest(targetUri, credentials);

            // send the request and wait for the response
            request.connect();
            final int statusCode = request.getResponseCode();
            // we're looking for 'OK 200' here, anything else is failure
            Trace.writeLine("   server returned: " + statusCode);
            return statusCode == HttpURLConnection.HTTP_OK;
        }
        catch (final IOException e)
        {
            throw new Error(e);
        }
        catch (final Throwable ignored)
        {
            throw new Error("   unexpected error", ignored);
        }
    }

    /**
     * <p>Validates that {@link Token} are valid to grant access to the Visual Studio
     * Online service represented by the {@literal targetUri} parameter.</p>
     * <p>Tokens of {@link TokenType#Refresh} cannot grant access, and
     * therefore always fail - this does not mean the token is invalid.</p>
     *
     * @param targetUri   Uniform resource identifier for a VSO service.
     * @param token       {@link Token} expected to grant access to the VSO service.
     * @return True if successful; otherwise false.
     */
    @Override public boolean validateToken(final URI targetUri, final Token token)
    {
        throw new NotImplementedException(449243);
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
        "\"token\"\\s*:\\s*\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );
    static Token parsePersonalAccessTokenFromJson(final String json)
    {
        Token token = null;
        if (!StringHelper.isNullOrWhiteSpace(json))
        {
            // find the 'token : <value>' portion of the result content, if any
            final Matcher matcher = TOKEN_PATTERN.matcher(json);
            if (matcher.find())
            {
                final String tokenValue = matcher.group(1);
                token = new Token(tokenValue, TokenType.Personal);
            }
        }
        return token;
    }

     private static final Pattern INSTANCE_ID_PATTERN = Pattern.compile(
        "\"instanceId\"\\s*:\\s*\"([^\"]+)\"",
        Pattern.CASE_INSENSITIVE
    );
    static String parseInstanceIdFromJson(final String json)
    {
        String result = null;

        final Matcher matcher = INSTANCE_ID_PATTERN.matcher(json);
        if (matcher.find())
        {
            result = matcher.group(1);
        }

        return result;
    }

    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "\"location\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );
    static URI parseLocationFromJson(final String json)
    {
        URI locationServiceUri = null;
        if (!StringHelper.isNullOrWhiteSpace(json))
        {
            // find the 'location : <value>' portion of the result content, if any
            final Matcher matcher = LOCATION_PATTERN.matcher(json);
            if (matcher.find())
            {
                final String location = matcher.group(1);
                locationServiceUri = URI.create(location);
            }
        }
        return locationServiceUri;
    }

    private StringContent getAccessTokenRequestBody(final URI targetUri, final Token accessToken, final VsoTokenScope tokenScope)
    {
        final String ContentJsonFormat = "{ \"scope\" : \"%1$s\", \"targetAccounts\" : [\"%2$s\"], \"displayName\" : \"Git: %3$s on %4$s\" }";

        Debug.Assert(accessToken != null && (accessToken.Type == TokenType.Access || accessToken.Type == TokenType.Federated), "The accessToken parameter is null or invalid");
        Debug.Assert(tokenScope != null, "The tokenScope parameter is null");

        final String targetIdentity = accessToken.getTargetIdentity().toString();
        Trace.writeLine("   creating access token scoped to '" + tokenScope + "' for '" + targetIdentity + "'");

        final String jsonContent = String.format(ContentJsonFormat, tokenScope, targetIdentity, targetUri, Environment.getMachineName());
        final StringContent content = StringContent.createJson(jsonContent);
        return content;
    }

    private HttpURLConnection createConnectionDataRequest(final URI targetUri, final Credential credentials) throws IOException
    {
        Debug.Assert(targetUri != null && targetUri.isAbsolute(), "The targetUri parameter is null or invalid");
        Debug.Assert(credentials != null, "The credentials parameter is null or invalid");

        final HttpClient client = new HttpClient(Global.getUserAgentRenamed());

        // create an request to the VSO deployment data end-point
        final URI requestUri = createConnectionDataUri(targetUri);

        credentials.contributeHeader(client.Headers);

        final HttpURLConnection result = client.get(requestUri, new Action<HttpURLConnection>()
        {
            @Override public void call(final HttpURLConnection conn)
            {
                conn.setConnectTimeout(RequestTimeout);
            }
        });
        return result;
    }

    private HttpURLConnection createConnectionDataRequest(final URI targetUri, final Token token) throws IOException
    {
        Debug.Assert(targetUri != null && targetUri.isAbsolute(), "The targetUri parameter is null or invalid");
        Debug.Assert(token != null && (token.Type == TokenType.Access || token.Type == TokenType.Federated), "The token parameter is null or invalid");

        Trace.writeLine("VsoAzureAuthority::createConnectionDataRequest");

        final HttpClient client = new HttpClient(Global.getUserAgentRenamed());

        // create an request to the VSO deployment data end-point
        final URI requestUri = createConnectionDataUri(targetUri);

        Trace.writeLine("   validating token");
        token.contributeHeader(client.Headers);

        final HttpURLConnection result = client.get(requestUri, new Action<HttpURLConnection>()
        {
            @Override public void call(final HttpURLConnection conn)
            {
                conn.setConnectTimeout(RequestTimeout);
            }
        });
        return result;
    }

    private URI createConnectionDataUri(final URI targetUri)
    {
        final String VsoValidationUrlFormat = "https://%1$s/_apis/connectiondata";

        Debug.Assert(targetUri != null & targetUri.isAbsolute(), "The targetUri parameter is null or invalid");

        // create a url to the connection data end-point, it's deployment level and "always on".
        final String validationUrl = String.format(VsoValidationUrlFormat, targetUri.getHost());

        final URI result = URI.create(validationUrl);
        return result;
    }
}
