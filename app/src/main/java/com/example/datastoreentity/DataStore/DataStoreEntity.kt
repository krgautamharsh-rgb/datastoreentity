package com.example.datastoreentity.DataStore

import kotlinx.serialization.Serializable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class DataStoreEntity(val name: String, val dynamicKeys: Boolean = false)


@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.SOURCE)
annotation class DataStoreKey(val name : String)


@DataStoreEntity("temp", dynamicKeys = true)
data class Temp(
    @DataStoreKey("count") val count : Int,
    @DataStoreKey("userName") val userName : String,
    @DataStoreKey("profile") val profile : UserProfile
)

@Serializable
data class UserProfile(val name: String, val age: Int)