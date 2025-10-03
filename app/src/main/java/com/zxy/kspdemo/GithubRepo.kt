package com.zxy.kspdemo

/**
 * Created by y on 2025/10/3-20:34
 *
 * @author y >_
 * .     _                                           ____  _       _ _
 * .    / \__      _____  ___  ___  _ __ ___   ___  | __ )(_)_ __ | | |_   _
 * .   / _ \ \ /\ / / _ \/ __|/ _ \| '_ ` _ \ / _ \ |  _ \| | '_ \| | | | | |
 * .  / ___ \ V  V /  __/\__ \ (_) | | | | | |  __/ | |_) | | | | | | | |_| |
 * . /_/   \_\_/\_/ \___||___/\___/|_| |_| |_|\___| |____/|_|_| |_|_|_|\__, |
 * .                                                                   |___/
 */

data class GithubRepo(
    val id: Long,
    val name: String,
    val fullName: String?,
    val homepage: String?,
    val description: String?,
    val created_at: String?,
    val updated_at: String?,
    val git_url: String?,
    val ssh_url: String?,
    val visibility: String?,
    val open_issues_count: Int,
    val owner: User,
    val stargazers_count: Int
)

data class User(
    val id: Long,
    val login: String?,
    val type: String?,
    val avatar_url: String?
)