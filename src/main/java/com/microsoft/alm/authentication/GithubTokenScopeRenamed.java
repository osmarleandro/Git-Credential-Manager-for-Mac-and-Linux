// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.authentication;

import com.microsoft.alm.helpers.*;
import com.microsoft.alm.secret.TokenScope;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GithubTokenScopeRenamed extends TokenScope
{
    public static final GithubTokenScopeRenamed None = new GithubTokenScopeRenamed(StringHelper.Empty);
    /**
     * Create gists
     */
    public static final GithubTokenScopeRenamed Gist = new GithubTokenScopeRenamed("gist");
    /**
     * Access notifications
     */
    public static final GithubTokenScopeRenamed Notifications = new GithubTokenScopeRenamed("notifications");
    /**
     * Full control of orgs and teams
     */
    public static final GithubTokenScopeRenamed OrgAdmin = new GithubTokenScopeRenamed("admin:org");
    /**
     * Read org and team membership
     */
    public static final GithubTokenScopeRenamed OrgRead = new GithubTokenScopeRenamed("read:org");
    /**
     * Read and write org and team membership
     */
    public static final GithubTokenScopeRenamed OrgWrite = new GithubTokenScopeRenamed("write:org");
    /**
     * Full control of organization hooks
     */
    public static final GithubTokenScopeRenamed OrgHookAdmin = new GithubTokenScopeRenamed("admin:org_hook");
    /**
     * Full control of user's public keys
     */
    public static final GithubTokenScopeRenamed PublicKeyAdmin = new GithubTokenScopeRenamed("admin:public_key");
    /**
     * Read user's public keys
     */
    public static final GithubTokenScopeRenamed PublicKeyRead = new GithubTokenScopeRenamed("read:public_key");
    /**
     * Write user's public keys
     */
    public static final GithubTokenScopeRenamed PublicKeyWrite = new GithubTokenScopeRenamed("write:public_key");
    /**
     * Access private repositories
     */
    public static final GithubTokenScopeRenamed Repo = new GithubTokenScopeRenamed("repo");
    /**
     * Delete repositories
     */
    public static final GithubTokenScopeRenamed RepoDelete = new GithubTokenScopeRenamed("delete_repo");
    /**
     * Access deployment status
     */
    public static final GithubTokenScopeRenamed RepoDeployment = new GithubTokenScopeRenamed("repo_deployment");
    /**
     * Access public repositories
     */
    public static final GithubTokenScopeRenamed RepoPublic = new GithubTokenScopeRenamed("public_repo");
    /**
     * Access commit status
     */
    public static final GithubTokenScopeRenamed RepoStatus = new GithubTokenScopeRenamed("repo:status");
    /**
     * Full control of repository hooks
     */
    public static final GithubTokenScopeRenamed RepoHookAdmin = new GithubTokenScopeRenamed("admin:repo_hook");
    /**
     * Read repository hooks
     */
    public static final GithubTokenScopeRenamed RepoHookRead = new GithubTokenScopeRenamed("read:repo_hook");
    /**
     * Write repository hooks
     */
    public static final GithubTokenScopeRenamed RepoHookWrite = new GithubTokenScopeRenamed("write:repo_hook");
    /**
     * Update all user information
     */
    public static final GithubTokenScopeRenamed User = new GithubTokenScopeRenamed("user");
    /**
     * Access user email address (read-only)
     */
    public static final GithubTokenScopeRenamed UserEmail = new GithubTokenScopeRenamed("user:email");
    /**
     * Follow and unfollow users
     */
    public static final GithubTokenScopeRenamed UserFollow = new GithubTokenScopeRenamed("user:follow");

    private GithubTokenScopeRenamed(final String value)
    {
        super(value);
    }

    private GithubTokenScopeRenamed(final String[] values)
    {
        super(values);
    }

    private GithubTokenScopeRenamed(final ScopeSet set)
    {
        super(set);
    }

    private static final List<GithubTokenScopeRenamed> values = Arrays.asList
    (
        Gist,
        Notifications,
        OrgAdmin,
        OrgRead,
        OrgWrite,
        OrgHookAdmin,
        PublicKeyAdmin,
        PublicKeyRead,
        PublicKeyWrite,
        Repo,
        RepoDelete,
        RepoDeployment,
        RepoPublic,
        RepoStatus,
        RepoHookAdmin,
        RepoHookRead,
        RepoHookWrite,
        User,
        UserEmail,
        UserFollow
    );

    public static Iterator<GithubTokenScopeRenamed> enumerateValues()
    {
        return values.iterator();
    }

    public static GithubTokenScopeRenamed operatorPlus(final GithubTokenScopeRenamed scope1, final GithubTokenScopeRenamed scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScopeRenamed operatorMinus(final GithubTokenScopeRenamed scope1, final GithubTokenScopeRenamed scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScopeRenamed operatorOr(final GithubTokenScopeRenamed scope1, final GithubTokenScopeRenamed scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScopeRenamed operatorAnd(final GithubTokenScopeRenamed scope1, final GithubTokenScopeRenamed scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScopeRenamed operatorXor(final GithubTokenScopeRenamed scope1, final GithubTokenScopeRenamed scope2)
    {
        throw new NotImplementedException(449506);
    }

    /**
     * Gets the path to the Git global configuration file.
     *
     * @param path Path to the Git global configuration.
     * @return True if succeeds; false otherwise.
     */
    public static boolean gitGlobalConfig(final AtomicReference<String> path)
    {
        final String GlobalConfigFileName = ".gitconfig";

        path.set(null);

        String globalPath = Path.combine(Environment.getFolderPath(Environment.SpecialFolder.UserProfile), GlobalConfigFileName);

        if (Path.fileExists(globalPath)) {
            path.set(globalPath);
        }

        return path.get() != null;
    }
}
