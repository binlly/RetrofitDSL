package com.zxy.kspdemo

import com.zxy.ksp.RetrofitService
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

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

interface BaseRetrofitService

@RetrofitService
interface GithubService : BaseRetrofitService {
    @GET("/repos/{owner}/{repo}")
    fun getRepo(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Call<GithubRepo>
}