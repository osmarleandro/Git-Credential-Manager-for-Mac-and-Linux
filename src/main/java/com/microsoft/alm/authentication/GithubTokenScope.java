// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See License.txt in the project root.

package com.microsoft.alm.authentication;

import com.microsoft.alm.helpers.*;
import com.microsoft.alm.secret.TokenScope;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class GithubTokenScope extends TokenScope
{
    public static final GithubTokenScope None = new GithubTokenScope(StringHelper.Empty);
    /**
     * Create gists
     */
    public static final GithubTokenScope Gist = new GithubTokenScope("gist");
    /**
     * Access notifications
     */
    public static final GithubTokenScope Notifications = new GithubTokenScope("notifications");
    /**
     * Full control of orgs and teams
     */
    public static final GithubTokenScope OrgAdmin = new GithubTokenScope("admin:org");
    /**
     * Read org and team membership
     */
    public static final GithubTokenScope OrgRead = new GithubTokenScope("read:org");
    /**
     * Read and write org and team membership
     */
    public static final GithubTokenScope OrgWrite = new GithubTokenScope("write:org");
    /**
     * Full control of organization hooks
     */
    public static final GithubTokenScope OrgHookAdmin = new GithubTokenScope("admin:org_hook");
    /**
     * Full control of user's public keys
     */
    public static final GithubTokenScope PublicKeyAdmin = new GithubTokenScope("admin:public_key");
    /**
     * Read user's public keys
     */
    public static final GithubTokenScope PublicKeyRead = new GithubTokenScope("read:public_key");
    /**
     * Write user's public keys
     */
    public static final GithubTokenScope PublicKeyWrite = new GithubTokenScope("write:public_key");
    /**
     * Access private repositories
     */
    public static final GithubTokenScope Repo = new GithubTokenScope("repo");
    /**
     * Delete repositories
     */
    public static final GithubTokenScope RepoDelete = new GithubTokenScope("delete_repo");
    /**
     * Access deployment status
     */
    public static final GithubTokenScope RepoDeployment = new GithubTokenScope("repo_deployment");
    /**
     * Access public repositories
     */
    public static final GithubTokenScope RepoPublic = new GithubTokenScope("public_repo");
    /**
     * Access commit status
     */
    public static final GithubTokenScope RepoStatus = new GithubTokenScope("repo:status");
    /**
     * Full control of repository hooks
     */
    public static final GithubTokenScope RepoHookAdmin = new GithubTokenScope("admin:repo_hook");
    /**
     * Read repository hooks
     */
    public static final GithubTokenScope RepoHookRead = new GithubTokenScope("read:repo_hook");
    /**
     * Write repository hooks
     */
    public static final GithubTokenScope RepoHookWrite = new GithubTokenScope("write:repo_hook");
    /**
     * Update all user information
     */
    public static final GithubTokenScope User = new GithubTokenScope("user");
    /**
     * Access user email address (read-only)
     */
    public static final GithubTokenScope UserEmail = new GithubTokenScope("user:email");
    /**
     * Follow and unfollow users
     */
    public static final GithubTokenScope UserFollow = new GithubTokenScope("user:follow");

    private GithubTokenScope(final String value)
    {
        super(value);
    }

    private GithubTokenScope(final String[] values)
    {
        super(values);
    }

    private GithubTokenScope(final ScopeSet set)
    {
        super(set);
    }

    private static final List<GithubTokenScope> values = Arrays.asList
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

    public static Iterator<GithubTokenScope> enumerateValues()
    {
        return values.iterator();
    }

    public static GithubTokenScope operatorPlus(final GithubTokenScope scope1, final GithubTokenScope scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScope operatorMinus(final GithubTokenScope scope1, final GithubTokenScope scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScope operatorOr(final GithubTokenScope scope1, final GithubTokenScope scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScope operatorAnd(final GithubTokenScope scope1, final GithubTokenScope scope2)
    {
        throw new NotImplementedException(449506);
    }

    public static GithubTokenScope operatorXor(final GithubTokenScope scope1, final GithubTokenScope scope2)
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
