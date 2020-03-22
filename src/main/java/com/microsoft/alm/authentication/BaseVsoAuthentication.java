// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.authentication;

import com.microsoft.alm.helpers.Action;
import com.microsoft.alm.helpers.Debug;
import com.microsoft.alm.helpers.Guid;
import com.microsoft.alm.helpers.HttpClient;
import com.microsoft.alm.helpers.StringHelper;
import com.microsoft.alm.gitcredentialmanager.Trace;
import com.microsoft.alm.secret.Credential;
import com.microsoft.alm.secret.Token;
import com.microsoft.alm.secret.TokenPair;
import com.microsoft.alm.secret.VsoTokenScope;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Base functionality for performing authentication operations against Visual Studio Online.
 */
public abstract class BaseVsoAuthentication extends BaseAuthentication
{
    public static final String DefaultResource = "499b84ac-1321-427f-aa17-267ca6975798";
    public static final String DefaultClientId = "97877f11-0fc6-4aee-b1ff-febb0519dd00";
    public static final URI RedirectUri = URI.create("https://java.visualstudio.com");

    protected static final String AdalRefreshPrefix = "ada";

    private BaseVsoAuthentication(final VsoTokenScope tokenScope, final ICredentialStore personalAccessTokenStore, final ITokenStore vsoIdeTokenCache, final ITokenStore adaRefreshTokenStore, final IVsoAuthority vsoAuthority)
    {
        if (tokenScope == null)
            throw new IllegalArgumentException("The `tokenScope` parameter is null.");
        if (personalAccessTokenStore == null)
            throw new IllegalArgumentException("The `personalAccessTokenStore` parameter is null.");

        this.ClientId = DefaultClientId;
        this.Resource = DefaultResource;
        this.TokenScope = tokenScope;
        this.VsoIdeTokenCache = vsoIdeTokenCache;
        this.PersonalAccessTokenStore = personalAccessTokenStore;
        this.AdaRefreshTokenStore = adaRefreshTokenStore != null ? adaRefreshTokenStore : new SecretCache(AdalRefreshPrefix);
        this.VsoAuthority = vsoAuthority;
    }
    /**
     * Invoked by a derived classes implementation. Allows custom back-end implementations to be used.
     *
     * @param tokenScope The desired scope of the acquired personal access token(s).
     * @param personalAccessTokenStore The secret store for acquired personal access token(s).
     * @param adaRefreshTokenStore The secret store for acquired Azure refresh token(s).
     */
    protected BaseVsoAuthentication(
            final VsoTokenScope tokenScope,
            final ICredentialStore personalAccessTokenStore,
            final ITokenStore adaRefreshTokenStore
    )
    {
        this(tokenScope, personalAccessTokenStore, new SecretCache("registry"), adaRefreshTokenStore, new VsoAzureAuthority());
    }
    BaseVsoAuthentication(
            final ICredentialStore personalAccessTokenStore,
            final ITokenStore adaRefreshTokenStore,
            final ITokenStore vsoIdeTokenCache,
            final IVsoAuthority vsoAuthority)
    {
        this(VsoTokenScope.ProfileRead, personalAccessTokenStore, vsoIdeTokenCache, adaRefreshTokenStore, vsoAuthority);

        Debug.Assert(adaRefreshTokenStore != null, "The adaRefreshTokenStore parameter is null.");
        Debug.Assert(vsoIdeTokenCache != null, "The vsoIdeTokenCache parameter is null.");
        Debug.Assert(vsoAuthority != null, "The vsoAuthority parameter is null.");
    }

    /**
     * The application client identity by which access will be requested.
     */
    public final String ClientId;
    /**
     * The Azure resource for which access will be requested.
     */
    public final String Resource;
    /**
     * The desired scope of the authentication token to be requested.
     */
    public final VsoTokenScope TokenScope;

    final ITokenStore VsoIdeTokenCache;

    ICredentialStore PersonalAccessTokenStore;
    ITokenStore AdaRefreshTokenStore;
    IVsoAuthority VsoAuthority;
    UUID TenantId;

    /**
     * Deletes a set of stored credentials by their target resource.
     *
     * @param targetUri The 'key' by which to identify credentials.
     */
    @Override public void deleteCredentials(final URI targetUri)
    {
        BaseSecureStore.validateTargetUri(targetUri);

        Trace.writeLine("BaseVsoAuthentication::deleteCredentials");

        AtomicReference<Credential> credentials = new AtomicReference<Credential>();
        AtomicReference<Token> token = new AtomicReference<Token>();
        if (this.PersonalAccessTokenStore.readCredentials(targetUri, credentials))
        {
            this.PersonalAccessTokenStore.deleteCredentials(targetUri);
        }
        else if (this.AdaRefreshTokenStore.readToken(targetUri, token))
        {
            this.AdaRefreshTokenStore.deleteToken(targetUri);
        }
    }

    /**
     * Attempts to get a set of credentials from storage by their target resource.
     *
     * @param targetUri   The 'key' by which to identify credentials.
     * @param credentials Credentials associated with the URI if successful; null otherwise.
     * @return True if successful; false otherwise.
     */
    @Override public boolean getCredentials(final URI targetUri, final AtomicReference<Credential> credentials)
    {
        BaseSecureStore.validateTargetUri(targetUri);

        Trace.writeLine("BaseVsoAuthentication::getCredentials");

        if (this.PersonalAccessTokenStore.readCredentials(targetUri, credentials))
        {
            Trace.writeLine("   successfully retrieved stored credentials, updating credential cache");
        }

        return credentials.get() != null;
    }

    /**
     * Attempts to generate a new personal access token (credentials) via use of a stored
     * Azure refresh token, identified by the target resource.
     *
     * @param targetUri           The 'key' by which to identify the refresh token.
     * @param requireCompactToken Generates a compact token if true; generates a self
     *                            describing token if false.
     * @return True if successful; false otherwise.
     */
    public boolean refreshCredentials(final URI targetUri, final boolean requireCompactToken)
    {
        BaseSecureStore.validateTargetUri(targetUri);

        Trace.writeLine("BaseVsoAuthentication::refreshCredentials");

        try
        {
            TokenPair tokens = null;

            AtomicReference<Token> refreshToken = new AtomicReference<Token>();
            // attempt to read from the local store
            if (this.AdaRefreshTokenStore.readToken(targetUri, refreshToken))
            {
                if ((tokens = this.VsoAuthority.acquireTokenByRefreshToken(targetUri, this.ClientId, this.Resource, refreshToken.get())) !=
                        null)
                {
                    Trace.writeLine("   Azure token found in primary cache.");

                    this.TenantId = tokens.AccessToken.getTargetIdentity();

                    return this.generatePersonalAccessToken(targetUri, tokens.AccessToken, requireCompactToken);
                }
            }

            AtomicReference<Token> federatedAuthToken = new AtomicReference<Token>();
            // attempt to utilize any fedauth tokens captured by the IDE
            if (this.VsoIdeTokenCache.readToken(targetUri, federatedAuthToken))
            {
                Trace.writeLine("   federated auth token found in IDE cache.");

                return this.generatePersonalAccessToken(targetUri, federatedAuthToken.get(), requireCompactToken);
            }
        }
        catch (final Exception exception)
        {
            Debug.Assert(false, exception.getMessage());
        }

        Trace.writeLine("   failed to refresh credentials.");
        return false;
    }

    /**
     * Validates that a set of credentials grants access to the target resource.
     *
     * @param targetUri   The target resource to validate against.
     * @param credentials The credentials to validate.
     * @return True if successful; false otherwise.
     */
    public boolean validateCredentials(final URI targetUri, final Credential credentials)
    {
        Trace.writeLine("BaseVsoAuthentication::validateCredentials");

        return this.VsoAuthority.validateCredentials(targetUri, credentials);
    }

    /**
     *
     *
     * @param targetUri           The target resource for which to acquire the personal access
     *                            token for.
     * @param accessToken         Azure Directory access token with privileges to grant access
     *                            to the target resource.
     * @param requestCompactToken Generates a compact token if true;
     *                            generates a self describing token if false.
     * @return True if successful; false otherwise.
     */
    protected boolean generatePersonalAccessToken(final URI targetUri, final Token accessToken, final boolean requestCompactToken)
    {
        Debug.Assert(targetUri != null, "The targetUri parameter is null");
        Debug.Assert(accessToken != null, "The accessToken parameter is null");

        Trace.writeLine("BaseVsoAuthentication::generatePersonalAccessToken");

        Token personalAccessToken;
        if ((personalAccessToken = this.VsoAuthority.generatePersonalAccessToken(targetUri, accessToken, TokenScope, requestCompactToken)) != null)
        {
            this.PersonalAccessTokenStore.writeCredentials(targetUri, Token.toCredential(personalAccessToken));
        }

        return personalAccessToken != null;

    }

    /**
     * Stores an Azure Directory refresh token.
     *
     * @param targetUri    The 'key' by which to identify the token.
     * @param refreshToken The token to be stored.
     */
    protected void storeRefreshToken(final URI targetUri, final Token refreshToken)
    {
        Debug.Assert(targetUri != null, "The targetUri parameter is null");
        Debug.Assert(refreshToken != null, "The refreshToken parameter is null");

        Trace.writeLine("BaseVsoAuthentication::storeRefreshToken");

        this.AdaRefreshTokenStore.writeToken(targetUri, refreshToken);
    }

    /**
     * Detects the backing authority of the end-point.
     *
     * @param targetUri The resource which the authority protects.
     * @param tenantId  The identity of the authority tenant; null otherwise.
     * @return True if the authority is Visual Studio Online; false otherwise.
     */
    public static boolean detectAuthority(final URI targetUri, final AtomicReference<UUID> tenantId)
    {
        final String VsoBaseUrlHost = "visualstudio.com";
        final String VsoResourceTenantHeader = "X-VSS-ResourceTenant";

        Trace.writeLine("BaseVsoAuthentication::detectAuthority");

        tenantId.set(Guid.Empty);

        if (StringHelper.endsWithIgnoreCase(targetUri.getHost(), VsoBaseUrlHost))
        {
            Trace.writeLine("   detected visualstudio.com, checking AAD vs MSA");

            String tenant = null;

            HttpURLConnection connection = null;
            final HttpClient client = new HttpClient(Global.getUserAgentRenamed());
            try
            {
                connection = client.head(targetUri, new Action<HttpURLConnection>()
                {
                    @Override public void call(final HttpURLConnection conn)
                    {
                        conn.setInstanceFollowRedirects(false);
                    }
                });

                tenant = connection.getHeaderField(VsoResourceTenantHeader);
                Trace.writeLine("   server has responded");

                return !StringHelper.isNullOrWhiteSpace(tenant)
                        && Guid.tryParse(tenant, tenantId);
            }
            catch (final IOException e)
            {
                throw new Error(e);
            }
        }

        Trace.writeLine("   failed detection");

        // if all else fails, fallback to basic authentication
        return false;
    }

    /**
     * Creates a new authentication broker based for the specified resource.
     *
     * @param targetUri                The resource for which authentication is being requested.
     * @param scope                    The scope of the access being requested.
     * @param personalAccessTokenStore Storage container for personal access token secrets.
     * @param adaRefreshTokenStore     Storage container for Azure access token secrets.
     * @param authentication           An implementation of {@link BaseAuthentication} if one was detected;
     *                                 null otherwise.
     * @return True if an authority could be determined; false otherwise.
     */
    public static boolean getAuthentication(
            final URI targetUri,
            final VsoTokenScope scope,
            final ICredentialStore personalAccessTokenStore,
            final ITokenStore adaRefreshTokenStore,
            final AtomicReference<IAuthentication> authentication)
    {
        Trace.writeLine("BaseVsoAuthentication::getAuthentication");

        final AtomicReference<UUID> tenantId = new AtomicReference<UUID>();
        if (detectAuthority(targetUri, tenantId))
        {
            // empty Guid is MSA, anything else is AAD
            if (Guid.Empty.equals(tenantId.get()))
            {
                Trace.writeLine("   MSA authority detected");
                authentication.set(new VsoMsaAuthentication(scope, personalAccessTokenStore, adaRefreshTokenStore));
            }
            else
            {
                Trace.writeLine("   AAD authority for tenant '" + tenantId + "' detected");
                authentication.set(new VsoAadAuthentication(tenantId.get(), scope, personalAccessTokenStore, adaRefreshTokenStore));
                ((BaseVsoAuthentication)authentication.get()).TenantId = tenantId.get();
            }
        }
        else
        {
            authentication.set(null);
        }

        return authentication.get() != null;
    }
}
